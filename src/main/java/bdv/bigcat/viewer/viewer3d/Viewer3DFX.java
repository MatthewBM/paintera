package bdv.bigcat.viewer.viewer3d;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import bdv.bigcat.viewer.bdvfx.MouseDragFX;
import bdv.bigcat.viewer.util.InvokeOnJavaFXApplicationThread;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import net.imglib2.Interval;

public class Viewer3DFX extends Pane
{

	private final Group root;

	private final Group meshesGroup;

	private final SubScene scene;

	private final PerspectiveCamera camera;

	private final Group cameraGroup;

	private double centerX = 0;

	private double centerY = 0;

	final private static double step = 1.0;// Math.PI / 180;

	private final AmbientLight lightAmbient = new AmbientLight( new Color( 0.1, 0.1, 0.1, 1 ) );

	private final PointLight lightSpot = new PointLight( new Color( 1.0, 0.95, 0.85, 1 ) );

	private final PointLight lightFill = new PointLight( new Color( 0.35, 0.35, 0.65, 1 ) );

	private final Point3D cameraNormal = new Point3D( 0, 0, 1 );

	private final Point3D xNormal = new Point3D( 1, 0, 0 );

	private final Point3D yNormal = new Point3D( 0, 1, 0 );

	private final Affine initialTransform = new Affine();

	private final Affine affine = new Affine();

	public Viewer3DFX( final double width, final double height, final Interval interval )
	{
		super();
		this.root = new Group();
		this.meshesGroup = new Group();
		this.setWidth( width );
		this.setHeight( height );
		this.scene = new SubScene( root, width, height, true, SceneAntialiasing.DISABLED );
		this.scene.setFill( Color.BLACK );

		this.camera = new PerspectiveCamera( true );
		this.camera.setNearClip( 0.01 );
		this.camera.setFarClip( 10.0 );
		this.camera.setTranslateY( 0 );
		this.camera.setTranslateX( 0 );
		this.camera.setTranslateZ( 0 );
		this.scene.setCamera( this.camera );
		this.cameraGroup = new Group();

		this.getChildren().add( this.scene );
//		this.root.getChildren().add( meshesGroup );
		this.root.getChildren().addAll( cameraGroup, meshesGroup );
//		this.root.getChildren().add( light );
//		this.root.getChildren().add( l );
		this.scene.widthProperty().bind( widthProperty() );
		this.scene.heightProperty().bind( heightProperty() );
		lightSpot.setTranslateX( -10 );
		lightSpot.setTranslateY( -10 );
		lightSpot.setTranslateZ( -10 );
		lightFill.setTranslateX( 10 );

		// TODO: specular light

		this.cameraGroup.getChildren().addAll( camera, lightAmbient, lightSpot, lightFill );
//		this.cameraGroup.getTransforms().addAll( translate, rotX, rotY );
		this.cameraGroup.getTransforms().add( new Translate( 0, 0, -1 ) );
		Point3D point = new Point3D( camera.getTranslateX(), camera.getTranslateY(), camera.getTranslateZ() );

		meshesGroup.getTransforms().addAll( affine );
		initialTransform.prependTranslation( -interval.dimension( 0 ) / 2, -interval.dimension( 1 ) / 2, -interval.dimension( 2 ) / 2 );
		System.out.println( "position: " + ( -interval.dimension( 0 ) / 2 ) + " " + ( -interval.dimension( 1 ) / 2 ) + " " + ( -interval.dimension( 2 ) / 2 ) );

		final double sf = 1.0 / interval.dimension( 0 );
		initialTransform.prependScale( sf, sf, sf );
		affine.setToTransform( initialTransform );

		affine.prependRotation( 90, centerX, centerY, 0, new Point3D( 0, 1, 0 ) );

		final Rotate rotate = new Rotate( "rotate 3d", affine, new SimpleDoubleProperty( 1.0 ), 1.0, MouseEvent::isPrimaryButtonDown );
		rotate.installInto( this );

		final TranslateXY translateXY = new TranslateXY( "translate", affine, MouseEvent::isSecondaryButtonDown );
		translateXY.installInto( this );

		camera.setFieldOfView( 90 );

		this.addEventHandler( ScrollEvent.SCROLL, event -> {
			if ( Math.abs( event.getDeltaY() ) > Math.abs( event.getDeltaX() ) )
			{
				final double scroll = event.getDeltaY();
				final double factor = scroll > 0 ? 1.05 : 1 / 1.05;
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					affine.prependScale( factor, factor, factor );
				} );
				event.consume();
			}
		} );

		this.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.Z ) && event.isShiftDown() )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> affine.setToTransform( initialTransform ) );
				event.consume();
			}
		} );

		this.addEventHandler( KeyEvent.KEY_PRESSED, event -> {
			if ( event.getCode().equals( KeyCode.P ) && event.isControlDown() )
			{
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					saveAsPng();
				} );
				event.consume();
			}
		} );

	}

	public SubScene scene()
	{
		return scene;
	}

	public Group root()
	{
		return root;
	}

	public Group meshesGroup()
	{
		return meshesGroup;
	}

	private class Rotate extends MouseDragFX
	{

		private final SimpleDoubleProperty speed = new SimpleDoubleProperty();

		private final double factor;

		private final Affine affine;

		private final Affine affineDragStart = new Affine();

		public Rotate( final String name, final Affine affine, final DoubleProperty speed, final double factor, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter );
			System.out.println( "rotation" );
			this.factor = factor;
			this.speed.set( speed.get() * this.factor );
			speed.addListener( ( obs, old, newv ) -> this.speed.set( this.factor * speed.get() ) );
			this.affine = affine;
		}

		@Override
		public void initDrag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( affine )
			{
				affineDragStart.setToTransform( affine );
			}
		}

		@Override
		public void drag( final javafx.scene.input.MouseEvent event )
		{
			synchronized ( affineDragStart )
			{
				System.out.println( "drag - rotate" );
				final Affine target = new Affine( affineDragStart );
				final double dX = event.getX() - startX;
				final double dY = event.getY() - startY;
				final double v = step * this.speed.get();
				System.out.println( "dx " + dX + " dy: " + dY );

				target.prependRotation( v * dY, centerX, centerY, 0, new Point3D( 1, 0, 0 ) );
				target.prependRotation( v * -dX, centerX, centerY, 0, new Point3D( 0, 1, 0 ) );

				System.out.println( "target: " + target );
				InvokeOnJavaFXApplicationThread.invoke( () -> {
					this.affine.setToTransform( target );
				} );
			}
		}
	}

	private class TranslateXY extends MouseDragFX
	{

		private final Affine affine;

		private final Affine affineDragStart = new Affine();

		public TranslateXY( final String name, final Affine affine, final Predicate< MouseEvent >... eventFilter )
		{
			super( name, eventFilter );
			System.out.println( "translate" );
			this.affine = affine;
		}

		@Override
		public void initDrag( final MouseEvent event )
		{
			synchronized ( affine )
			{
				affineDragStart.setToTransform( affine );
			}
		}

		@Override
		public void drag( final MouseEvent event )
		{
			System.out.println( "drag - translate" );
			final double dX = event.getX() - startX;
			final double dY = event.getY() - startY;

			System.out.println( "dx " + dX + " dy: " + dY );
			final Affine target = new Affine( affineDragStart );
			target.prependTranslation( 2 * dX / getHeight(), 2 * dY / getHeight() );

			System.out.println( "target: " + target );
			centerX = 2 * dX / getHeight();
			centerY = 2 * dY / getHeight();
			System.out.println( "translation value x " + ( 2 * dX / getHeight() ) + " y: " + ( 2 * dY / getHeight() ) );
			InvokeOnJavaFXApplicationThread.invoke( () -> {
				affine.setToTransform( target );
			} );
		}

	}

	private void saveAsPng()
	{
		WritableImage image = scene.snapshot( new SnapshotParameters(), null );

		// TODO: use a file chooser here
		File file = new File( "3dscene-bigcat.png" );

//		FileChooser fileChooser = new FileChooser();
//		fileChooser.setTitle( "Open Resource File" );
//		fileChooser.showOpenDialog( stage );

		try
		{
			ImageIO.write( SwingFXUtils.fromFXImage( image, null ), "png", file );
		}
		catch ( IOException e )
		{
			// TODO: handle exception here
		}
	}

}