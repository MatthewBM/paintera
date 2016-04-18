package bdv.img.h5;

import java.util.Arrays;

import bdv.img.cache.CacheArrayLoader;
import bdv.labels.labelset.LabelMultisetEntry;
import bdv.labels.labelset.LabelMultisetEntryList;
import bdv.labels.labelset.LongMappedAccessData;
import bdv.labels.labelset.VolatileLabelMultisetArray;
import ch.systemsx.cisd.base.mdarray.MDLongArray;
import ch.systemsx.cisd.hdf5.IHDF5LongReader;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongIntHashMap;

/**
 * {@link CacheArrayLoader} for simple HDF5 files
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class H5LongLabelMultisetArrayLoader extends AbstractH5LabelMultisetArrayLoader
{
	final private IHDF5LongReader reader;

	public H5LongLabelMultisetArrayLoader(
			final IHDF5Reader reader,
			final IHDF5Reader scaleReader,
			final String dataset )
	{
		super( scaleReader, dataset );
		this.reader = reader.uint64();
	}

	@Override
	public int getBytesPerElement()
	{
		return 8;
	}

	@Override
	public VolatileLabelMultisetArray loadArrayLevel0(
			final int[] dimensions,
			final long[] min ) throws InterruptedException
	{
		long[] data = null;

		final MDLongArray block = reader.readMDArrayBlockWithOffset(
				dataset,
				new int[]{ dimensions[ 2 ], dimensions[ 1 ], dimensions[ 0 ] },
				new long[]{ min[ 2 ], min[ 1 ], min[ 0 ] } );

		data = block.getAsFlatArray();

		if ( data == null )
		{
			System.out.println(
					"H5 long label multiset array loader failed loading min = " +
					Arrays.toString( min ) +
					", dimensions = " +
					Arrays.toString( dimensions ) );

			data = new long[ dimensions[ 0 ] * dimensions[ 1 ] * dimensions[ 2 ] ];
		}

		final int[] offsets = new int[ dimensions[ 2 ] * dimensions[ 1 ] * dimensions[ 0 ] ];
		final LongMappedAccessData listData = LongMappedAccessData.factory.createStorage( 32 );
		final LabelMultisetEntryList list = new LabelMultisetEntryList( listData, 0 );
		final LabelMultisetEntry entry = new LabelMultisetEntry( 0, 1 );
		int nextListOffset = 0;
		final TLongIntHashMap idOffsetHash = new TLongIntHashMap(
				Constants.DEFAULT_CAPACITY,
				Constants.DEFAULT_LOAD_FACTOR,
				-1,
				-1 );
A:		for ( int i = 0; i < data.length; ++i )
		{
			final long id = data[ i ];

//			does the list [id x 1] already exist?
			final int offset = idOffsetHash.get( id );
			if ( offset == idOffsetHash.getNoEntryValue() )
			{
				list.createListAt( listData, nextListOffset );
				entry.setId( id );
				list.add( entry );
				offsets[ i ] = nextListOffset;
				idOffsetHash.put( id, nextListOffset );
				nextListOffset += list.getSizeInBytes();
			}
			else
			{
				offsets[ i ] = offset;
				continue A;
			}
		}
//		System.out.println( listData.size() );

		return new VolatileLabelMultisetArray( offsets, listData, nextListOffset, true );
	}
}
