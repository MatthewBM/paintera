package org.janelia.saalfeldlab.paintera.state

import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.control.TitledPane
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.TilePane
import javafx.scene.paint.Color
import javafx.stage.Modality
import net.imglib2.converter.ARGBColorConverter
import org.janelia.saalfeldlab.fx.TitledPaneExtensions
import org.janelia.saalfeldlab.fx.ui.NumericSliderWithField
import org.janelia.saalfeldlab.fx.util.DoubleStringFormatter
import org.janelia.saalfeldlab.paintera.ui.PainteraAlerts
import org.janelia.saalfeldlab.util.Colors
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles

class RawSourceStateConverterNode(private val converter: ARGBColorConverter<*>) {

    private val colorProperty = SimpleObjectProperty(Color.WHITE)

    private val argbProperty = converter.colorProperty()

    private val alphaProperty = converter.alphaProperty()

    private val min = converter.minProperty()

    private val max = converter.maxProperty()

	init {
		this.colorProperty.addListener { _, _, new -> argbProperty.set(Colors.toARGBType(new)) }
		this.argbProperty.addListener { _, _, new -> colorProperty.set(Colors.toColor(new)) }
		this.colorProperty.value = Colors.toColor(argbProperty.value)
	}

    val converterNode: Node
        get() {

            val tilePane = TilePane(Orientation.VERTICAL)
            tilePane.minWidth = 0.0

            val picker = ColorPicker(Colors.toColor(argbProperty.get()))
            picker.valueProperty().bindBidirectional(this.colorProperty)
            val colorPickerBox = HBox(picker)
            HBox.setHgrow(picker, Priority.ALWAYS)
            tilePane.children.add(colorPickerBox)

            val min = this.min.asString()
            val max = this.max.asString()
            val minInput = TextField(min.get())
            val maxInput = TextField(max.get())
            minInput.promptTextProperty().bind(this.min.asString("min=%f"))
            minInput.promptTextProperty().bind(this.max.asString("max=%f"))

            min.addListener { obs, oldv, newv -> minInput.text = newv }
            max.addListener { obs, oldv, newv -> maxInput.text = newv }

            val minFormatter = DoubleStringFormatter.createFormatter(this.min.get(), 2)
            val maxFormatter = DoubleStringFormatter.createFormatter(this.max.get(), 2)

            minInput.textFormatter = minFormatter
            maxInput.textFormatter = maxFormatter

            minInput.setOnKeyPressed { event ->
                if (event.code == KeyCode.ENTER) {
                    minInput.commitValue()
                    event.consume()
                }
            }
            maxInput.setOnKeyPressed { event ->
                if (event.code == KeyCode.ENTER) {
                    maxInput.commitValue()
                    event.consume()
                }
            }

            minInput.tooltip = Tooltip("min")
            maxInput.tooltip = Tooltip("max")

            minFormatter.valueProperty().addListener { obs, oldv, newv -> this.min.set(newv!!) }
            maxFormatter.valueProperty().addListener { obs, oldv, newv -> this.max.set(newv!!) }

            this.min.addListener { obs, oldv, newv -> minFormatter.setValue(newv.toDouble()) }
            this.max.addListener { obs, oldv, newv -> maxFormatter.setValue(newv.toDouble()) }

            val minMaxBox = HBox(minInput, maxInput)
            tilePane.children.add(minMaxBox)

            val alphaSliderWithField = NumericSliderWithField(0.0, 1.0, this.alphaProperty.get())
            alphaSliderWithField.slider().valueProperty().bindBidirectional(this.alphaProperty)
            alphaSliderWithField.textField().minWidth = 48.0
            alphaSliderWithField.textField().maxWidth = 48.0
            val alphaBox = HBox(alphaSliderWithField.slider(), alphaSliderWithField.textField())
            Tooltip.install(alphaSliderWithField.slider(), Tooltip("alpha"))
            HBox.setHgrow(alphaSliderWithField.slider(), Priority.ALWAYS)
            tilePane.children.add(alphaBox)

            LOG.debug("Returning TilePane with children: ", tilePane.children)


			val helpDialog = PainteraAlerts
					.alert(Alert.AlertType.INFORMATION, true)
					.also { it.initModality(Modality.NONE) }
					.also { it.headerText = "Conversion of raw data into ARGB color space." }
					.also { it.contentText = DESCRIPTION }

			val tpGraphics = HBox(
					Label("Color Conversion"),
					Region().also { HBox.setHgrow(it, Priority.ALWAYS) },
					Button("?").also { bt -> bt.onAction = EventHandler { helpDialog.show() } })
					.also { it.alignment = Pos.CENTER }

			return with (TitledPaneExtensions) {
				TitledPane(null, tilePane)
						.also { it.isExpanded = false }
						.also { it.graphicsOnly(tpGraphics) }
						.also { it.alignment = Pos.CENTER_RIGHT }
						.also { it.tooltip = Tooltip(DESCRIPTION) }
			}
		}

    companion object {

        private val LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

		private const val DESCRIPTION = "" +
				"Convert scalar real values into RGB color space with the contrast range " +
				"specified by the min and max values."

    }

}
