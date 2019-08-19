package org.janelia.saalfeldlab.paintera

import bdv.viewer.ViewerOptions
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.event.ActionEvent
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.web.WebView
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.WindowEvent
import javafx.util.Callback
import javafx.util.StringConverter
import org.apache.commons.io.IOUtils
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.janelia.saalfeldlab.fx.Buttons
import org.janelia.saalfeldlab.fx.event.KeyTracker
import org.janelia.saalfeldlab.fx.event.MouseTracker
import org.janelia.saalfeldlab.n5.N5FSReader
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.paintera.config.ScreenScalesConfig
import org.janelia.saalfeldlab.paintera.serialization.*
import org.janelia.saalfeldlab.paintera.state.SourceState
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.scijava.plugin.Plugin
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Type
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Scanner
import java.util.function.BiConsumer

typealias PropertiesListener = BiConsumer<Properties2?, Properties2?>

class PainteraMainWindow() {

    val baseView = PainteraBaseView(
            PainteraBaseView.reasonableNumFetcherThreads(),
            ViewerOptions.options().screenScales(ScreenScalesConfig.defaultScreenScalesCopy()))

	val namedKeyCombinations = NamedKeyCombination.CombinationMap(
			NamedKeyCombination("open data", KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN)),
			NamedKeyCombination("save", KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)),
			NamedKeyCombination("save as", KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)),
			NamedKeyCombination("toggle menubar visibility", KeyCodeCombination(KeyCode.F2)),
			NamedKeyCombination("toggle menubar mode", KeyCodeCombination(KeyCode.F2, KeyCombination.SHIFT_DOWN)),
			NamedKeyCombination("toggle statusbar visibility", KeyCodeCombination(KeyCode.F3)),
			NamedKeyCombination("toggle statusbar mode", KeyCodeCombination(KeyCode.F3, KeyCombination.SHIFT_DOWN)),
			NamedKeyCombination("open readme in webview", KeyCodeCombination(KeyCode.F1)),
			NamedKeyCombination("toggle side bar", KeyCodeCombination(KeyCode.P)))

	val namedActions = NamedAction.ActionMap(
			NamedAction("save", Runnable { this.saveOrSaveAs() }),
			NamedAction("save as", Runnable { this.saveAs() }),
			NamedAction("toggle menubar visibility", Runnable { this.properties.menuBarConfig.toggleIsVisible() }),
			NamedAction("toggle menubar mode", Runnable { this.properties.menuBarConfig.cycleModes() }),
			NamedAction("toggle statusbar visibility", Runnable { this.properties.statusBarConfig.toggleIsVisible() }),
			NamedAction("toggle statusbar mode", Runnable { this.properties.statusBarConfig.cycleModes() }),
			NamedAction("toggle side bar", Runnable { this.properties.sideBarConfig.toggleIsVisible() } ),
			NamedAction("open readme in webview", Runnable {
				// TODO make rendering better
				val vs = Version.VERSION_STRING
				val tag = vs.let { if (it.endsWith("-SNAPSHOT")) "master" else "paintera-$it" }
				val url = "https://github.com/saalfeldlab/paintera/blob/$tag/README.md"
				val rawUrl = "https://raw.githubusercontent.com/saalfeldlab/paintera/$tag/README.md"
				val md = IOUtils.toString(URL(rawUrl).openStream(), StandardCharsets.UTF_8)
				val parser = Parser.builder().build()
				val document = parser.parse(md)
				val renderer = HtmlRenderer.builder().build()
				val html = renderer.render(document)
				val wv = WebView().also { it.engine.loadContent(html) }
				val dialog = PainteraAlerts.alert(Alert.AlertType.INFORMATION, true).also { it.initModality(Modality.NONE) }
				dialog.dialogPane.content = VBox(TextField(url).also { it.tooltip = Tooltip(url) }.also { it.isEditable = false }, wv)
				dialog.graphic = null
				dialog.headerText = null
				dialog.show()
			}))

    private lateinit var paneWithStatus: BorderPaneWithStatusBars2

    val keyTracker = KeyTracker()

    val mouseTracker = MouseTracker()

    val projectDirectory = ProjectDirectory()

    private lateinit var defaultHandlers: PainteraDefaultHandlers2

	private lateinit var _properties: Properties2

	val pane: Parent
		get() = paneWithStatus.pane

	val properties: Properties2
		get() = _properties

	constructor(properties: Properties2): this() {
		initProperties(properties)
	}

	private fun initProperties(properties: Properties2) {
		this._properties = properties
		this.paneWithStatus = BorderPaneWithStatusBars2(this)
		this.defaultHandlers = PainteraDefaultHandlers2(this, paneWithStatus)
		this._properties.navigationConfig.bindNavigationToConfig(defaultHandlers.navigation())
		this.baseView.orthogonalViews().grid().manage(properties.gridConstraints)
	}

	private fun initProperties(json: JsonObject?, gson: Gson) {
		val properties = json?.let { gson.fromJson(it, Properties2::class.java) }
		initProperties(properties ?: Properties2())
	}

	fun deserialize() {
		val indexToState = mutableMapOf<Int, SourceState<*, *>>()
		val builder = GsonHelpers
				.builderWithAllRequiredDeserializers(
						StatefulSerializer.Arguments(baseView),
						{ projectDirectory.actualDirectory.absolutePath },
						{ indexToState[it] })
		val gson = builder.create()
		val json = projectDirectory
				.actualDirectory
				?.let { N5FSReader(it.absolutePath).getAttribute("/", PAINTERA_KEY, JsonElement::class.java) }
				?.takeIf { it.isJsonObject }
				?.let { it.asJsonObject }
		deserialize(json, gson, indexToState)
	}

	fun save() {
		val builder = GsonHelpers
				.builderWithAllRequiredSerializers(baseView) { projectDirectory.actualDirectory.absolutePath }
				.setPrettyPrinting()
		N5FSWriter(projectDirectory.actualDirectory.absolutePath, builder).setAttribute("/", PAINTERA_KEY, this)
	}

	fun saveAs() {
		val dialog = PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
		dialog.headerText = "Save project directory at location"
		val directoryChooser = DirectoryChooser()
		val directory = SimpleObjectProperty<File?>(null)
		val noDirectorySpecified = directory.isNull
		val directoryField = TextField()
				.also { it.tooltip = Tooltip().also { tt -> tt.textProperty().bindBidirectional(it.textProperty()) } }
				.also { it.promptText = "Project Directory" }
		val converter = object : StringConverter<File?>() {
			override fun toString(file: File?) = file?.path?.homeToTilde()
			override fun fromString(path: String?) = path?.tildeToHome()?.let { Paths.get(it).toAbsolutePath().toFile() }
		}
		Bindings.bindBidirectional(directoryField.textProperty(), directory, converter)
		directoryChooser.initialDirectoryProperty().addListener { _, _, f -> f?.mkdirs() }
		val browseButton = Buttons.withTooltip("_Browse", "Browse") {
			directoryChooser.initialDirectory = directory.get()?.let { it.takeUnless { it.isFile } ?: it.parentFile }
			directoryChooser.showDialog(this.pane.scene.window)?.let { directory.set(it) }
		}
		browseButton.prefWidth = 100.0
		HBox.setHgrow(directoryField, Priority.ALWAYS)
		val box = HBox(directoryField, browseButton)
		dialog.dialogPane.content = box
		box.alignment = Pos.CENTER

		(dialog.dialogPane.lookupButton(ButtonType.OK) as Button).also { bt ->
			bt.addEventFilter(ActionEvent.ACTION) {
				val dir = directory.get()
				var useIt = true
				if (dir === null || dir.isFile) {
					PainteraAlerts.alert(Alert.AlertType.ERROR, true)
							.also { it.headerText = "Invalid directory" }
							.also { it.contentText = "Directory expected but got file `$dir'. Please specify valid directory." }
							.show()
					useIt = false
				} else {
					val attributes = dir.toPath().toAbsolutePath().resolve("attributes.json").toFile()
					if (attributes.exists()) {
						useIt = useIt && PainteraAlerts.alert(Alert.AlertType.CONFIRMATION, true)
								.also { it.headerText = "Container exists" }
								.also { it.contentText = "N5 container (and potentially a Paintera project) exists at `$dir'. Overwrite?" }
								.also { (it.dialogPane.lookupButton(ButtonType.OK) as Button).text = "_Overwrite" }
								.also { (it.dialogPane.lookupButton(ButtonType.CANCEL) as Button).text = "_Cancel" }
								.showAndWait().filter { ButtonType.OK == it }.isPresent
					}

					useIt = useIt && PainteraAlerts.ignoreLockFileDialog(projectDirectory, dir)

				}
				if (!useIt) it.consume()
			}
			bt.disableProperty().bind(noDirectorySpecified)
		}

		directory.value = projectDirectory.directory

		val bt = dialog.showAndWait()
		if (bt.filter { ButtonType.OK == it }.isPresent && directory.value != null) {
			LOG.info("Saving project to directory {}", directory.value)
			save()
		}
	}

	fun saveOrSaveAs() = if (projectDirectory.directory === null) saveAs() else save()

	private fun deserialize(json: JsonObject?, gson: Gson, indexToState: MutableMap<Int, SourceState<*, *>>) {
		initProperties(json, gson)
		json
				?.takeIf { it.has(SOURCES_KEY) }
				?.get(SOURCES_KEY)
				?.takeIf { it.isJsonObject }
				?.asJsonObject
				?.let { SourceInfoSerializer.populate(
						{ baseView.addState(it) },
						{ baseView.sourceInfo().currentSourceIndexProperty().set(it) },
						it.asJsonObject,
						{ k, v -> indexToState.put(k, v) },
						gson)
				}
	}

	fun setupStage(stage: Stage) {
		projectDirectory.addListener { pd -> stage.title = if (pd.directory == null) NAME else "${NAME} ${replaceUserHomeWithTilde(pd.directory.absolutePath)}" }
		stage.addEventHandler(WindowEvent.WINDOW_HIDDEN) { projectDirectory.close() }
		stage.icons.addAll(
				Image(javaClass.getResourceAsStream("/icon-16.png")),
				Image(javaClass.getResourceAsStream("/icon-32.png")),
				Image(javaClass.getResourceAsStream("/icon-48.png")),
				Image(javaClass.getResourceAsStream("/icon-64.png")),
				Image(javaClass.getResourceAsStream("/icon-96.png")),
				Image(javaClass.getResourceAsStream("/icon-128.png")))
		stage.fullScreenExitKeyCombination = KeyCodeCombination(KeyCode.F11)
		// to disable message entirely:
		// stage.fullScreenExitKeyCombination = KeyCombination.NO_MATCH
	}

	companion object{
		@JvmStatic
		val NAME = "Paintera"

		private const val PAINTERA_KEY = "paintera"

		private const val SOURCES_KEY = "sourceInfo"

		private const val VERSION_KEY = "version"

		private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private val USER_HOME = System.getProperty("user.home")

		private val USER_HOME_AT_BEGINNING_REGEX = "^$USER_HOME".toRegex()

		private val TILDE_AT_BEGINNING_REGEX = "^~".toRegex()

		private fun replaceUserHomeWithTilde(path: String) = path.replaceFirst(USER_HOME_AT_BEGINNING_REGEX, "~")

		private fun String.homeToTilde() = replaceUserHomeWithTilde(this)

		private fun String.tildeToHome() = this.replaceFirst(TILDE_AT_BEGINNING_REGEX, USER_HOME)

	}

	@Plugin(type = PainteraSerialization.PainteraSerializer::class)
	class Serializer : PainteraSerialization.PainteraSerializer<PainteraMainWindow> {
		override fun serialize(mainWindow: PainteraMainWindow, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
			val map = context.serialize(mainWindow._properties).asJsonObject
			map.add(SOURCES_KEY, context.serialize(mainWindow.baseView.sourceInfo()))
			map.addProperty(VERSION_KEY, Version.VERSION_STRING)
			return map
		}

		override fun getTargetClass() = PainteraMainWindow::class.java
	}


}
