package bdv.bigcat.viewer;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;

import com.beust.jcommander.JCommander;
import com.sun.javafx.application.PlatformImpl;

import bdv.AbstractViewerSetupImgLoader;
import bdv.bigcat.viewer.atlas.Atlas;
import bdv.bigcat.viewer.atlas.data.HDF5LabelMultisetSourceSpec;
import bdv.bigcat.viewer.atlas.data.HDF5UnsignedByteSpec;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import javafx.application.Platform;
import javafx.stage.Stage;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleApplicationCremi
{
	/** logger */
	static Logger LOGGER;

	public static void main( final String[] args ) throws Exception
	{
		// Set the log level
		final String USER_HOME = System.getProperty( "user.home" );

		String rawFile = USER_HOME + "/Downloads/sample_A_padded_20160501.hdf";
		PlatformImpl.startup( () -> {} );
		String rawDataset = "volumes/raw";
		String labelsFile = rawFile;
		String labelsDataset = "volumes/labels/neuron_ids";

		double[] resolution = { 4, 4, 40 };
//		final double[] offset = { 424, 424, 560 };
		int[] rawCellSize = { 192, 96, 7 };
		int[] labelCellSize = { 79, 79, 4 };
		
		final Parameters params = getParameters( args );
		if ( params != null )
		{
			System.out.println( "parameters are not null" );
			rawFile = params.filePath;
			rawDataset = params.rawDatasetPath;
			labelsFile = rawFile;
			labelsDataset = params.labelDatasetPath;
			resolution = new double[] { params.resolution.get( 0 ), params.resolution.get( 1 ), params.resolution.get( 2 ) };
			rawCellSize = new int[] { params.rawCellSize.get( 0 ), params.rawCellSize.get( 1 ), params.rawCellSize.get( 2 ) };
			labelCellSize = new int[] { params.labelCellSize.get( 0 ), params.labelCellSize.get( 1 ), params.labelCellSize.get( 2 ) };
		}

		final HDF5UnsignedByteSpec rawSource = new HDF5UnsignedByteSpec( rawFile, rawDataset, rawCellSize, resolution, "raw" );

		final double[] min = Arrays.stream( Intervals.minAsLongArray( rawSource.getSource().getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final double[] max = Arrays.stream( Intervals.maxAsLongArray( rawSource.getSource().getSource( 0, 0 ) ) ).mapToDouble( v -> v ).toArray();
		final AffineTransform3D affine = new AffineTransform3D();
		rawSource.getSource().getSourceTransform( 0, 0, affine );
		affine.apply( min, min );
		affine.apply( max, max );

		final Atlas viewer = new Atlas( new FinalInterval( Arrays.stream( min ).mapToLong( Math::round ).toArray(), Arrays.stream( max ).mapToLong( Math::round ).toArray() ) );

		final CountDownLatch latch = new CountDownLatch( 1 );
		Platform.runLater( () -> {
			final Stage stage = new Stage();
			try
			{
				viewer.start( stage );
			}
			catch ( final InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			stage.show();
			latch.countDown();
		} );

		latch.await();

//		AffineTransform3D transform = new AffineTransform3D();
//		final long label = 7;
//		controller.renderAtSelectionMultiset( volumeLabels, transform, location, label );
//		Viewer3DController.generateMesh( volumeLabels, location );

		viewer.addRawSource( rawSource, 0., 255. );

		final HDF5LabelMultisetSourceSpec labelSpec2 = new HDF5LabelMultisetSourceSpec( labelsFile, labelsDataset, labelCellSize, "labels" );
		viewer.addLabelSource( labelSpec2 );

	}

	private static Parameters getParameters( String[] args )
	{
		// get the parameters
		final Parameters params = new Parameters();
		JCommander.newBuilder()
				.addObject( params )
				.build()
				.parse( args );

		boolean success = validateParameters( params );
		if ( !success )
		{
			return null;
		}

		return params;
	}

	private static boolean validateParameters( Parameters params )
	{
		if ( params.filePath == "" )
		{
			return false;
		}

		return true;
	}

	public static class VolatileRealARGBConverter< T extends RealType< T > > extends AbstractLinearRange implements Converter< Volatile< T >, VolatileARGBType >
	{

		public VolatileRealARGBConverter( final double min, final double max )
		{
			super( min, max );
		}

		@Override
		public void convert( final Volatile< T > input, final VolatileARGBType output )
		{
			final boolean isValid = input.isValid();
			output.setValid( isValid );
			if ( isValid )
			{
				final double a = input.get().getRealDouble();
				final int b = Math.min( 255, roundPositive( Math.max( 0, ( a - min ) / scale * 255.0 ) ) );
				final int argb = 0xff000000 | ( b << 8 | b ) << 8 | b;
				output.set( argb );
			}
		}

	}

	public static class ARGBConvertedSource< T > implements Source< VolatileARGBType >
	{
		final private AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader;

		private final int setupId;

		private final Converter< Volatile< T >, VolatileARGBType > converter;

		final protected InterpolatorFactory< VolatileARGBType, RandomAccessible< VolatileARGBType > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[] {
					new NearestNeighborInterpolatorFactory< VolatileARGBType >(),
					new ClampingNLinearInterpolatorFactory< VolatileARGBType >()
			};
		}

		public ARGBConvertedSource(
				final int setupId,
				final AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > loader,
				final Converter< Volatile< T >, VolatileARGBType > converter )
		{
			this.setupId = setupId;
			this.loader = loader;
			this.converter = converter;
		}

		final public AbstractViewerSetupImgLoader< T, ? extends Volatile< T > > getLoader()
		{
			return loader;
		}

		@Override
		public RandomAccessibleInterval< VolatileARGBType > getSource( final int t, final int level )
		{
			return Converters.convert(
					loader.getVolatileImage( t, level ),
					converter,
					new VolatileARGBType() );
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.set( loader.getMipmapTransforms()[ level ] );
		}

		/**
		 * TODO Store this in a field
		 */
		@Override
		public int getNumMipmapLevels()
		{
			return loader.getMipmapResolutions().length;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return t == 0;
		}

		@Override
		public RealRandomAccessible< VolatileARGBType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{

			final ExtendedRandomAccessibleInterval< VolatileARGBType, RandomAccessibleInterval< VolatileARGBType > > extendedSource =
					Views.extendValue( getSource( t, level ), new VolatileARGBType( 0 ) );
			switch ( method )
			{
			case NLINEAR:
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default:
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}

		@Override
		public VolatileARGBType getType()
		{
			return new VolatileARGBType();
		}

		@Override
		public String getName()
		{
			return "1 2 3";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		// TODO: make ARGBType version of this source
		public Source nonVolatile()
		{
			return this;
		}
	}

}