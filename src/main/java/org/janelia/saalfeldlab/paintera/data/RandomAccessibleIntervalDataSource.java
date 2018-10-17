package org.janelia.saalfeldlab.paintera.data;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import bdv.viewer.Interpolation;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.util.Triple;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.util.n5.ImagesWithInvalidate;
import org.janelia.saalfeldlab.paintera.cache.InvalidateAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomAccessibleIntervalDataSource<D extends Type<D>, T extends Type<T>> implements DataSource<D, T>
{

	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private final AffineTransform3D[] mipmapTransforms;

	private final RandomAccessibleInterval<T>[] sources;

	private final RandomAccessibleInterval<D>[] dataSources;

	private final InvalidateAll invalidateAll;

	private final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation;

	private final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation;

	private final Supplier<D> dataTypeSupplier;

	private final Supplier<T> typeSupplier;

	private final String name;

	public static class DataWithInvalidate<D, T> {
		public final RandomAccessibleInterval<D>[] data;

		public final RandomAccessibleInterval<T>[] viewData;

		public final AffineTransform3D[] transforms;

		public final InvalidateAll invalidateAll;

		public DataWithInvalidate(RandomAccessibleInterval<D>[] data, RandomAccessibleInterval<T>[] viewData, AffineTransform3D[] transforms, InvalidateAll invalidateAll) {
			this.data = data;
			this.viewData = viewData;
			this.transforms = transforms;
			this.invalidateAll = invalidateAll;
		}
	}

	public RandomAccessibleIntervalDataSource(
			final DataWithInvalidate<D, T> dataWithInvalidate,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {
		this(dataWithInvalidate.data, dataWithInvalidate.viewData, dataWithInvalidate.transforms, dataWithInvalidate.invalidateAll, dataInterpolation, interpolation, name);
	}

	public RandomAccessibleIntervalDataSource(
			final Triple<RandomAccessibleInterval<D>[], RandomAccessibleInterval<T>[], AffineTransform3D[]> data,
			final InvalidateAll invalidateAll,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {
		this(data.getA(), data.getB(), data.getC(), invalidateAll, dataInterpolation, interpolation, name);
	}

	@SuppressWarnings("unchecked")
	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D> dataSource,
			final RandomAccessibleInterval<T> source,
			final AffineTransform3D mipmapTransform,
			final InvalidateAll invalidateAll,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {
		this(
				new RandomAccessibleInterval[] {dataSource},
				new RandomAccessibleInterval[] {source},
				new AffineTransform3D[] {mipmapTransform},
				invalidateAll,
				dataInterpolation,
				interpolation,
				name
		    );
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final InvalidateAll invalidateAll,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final String name) {
		this(
				dataSources,
				sources,
				mipmapTransforms,
				invalidateAll,
				dataInterpolation,
				interpolation,
				() -> Util.getTypeFromInterval(dataSources[0]).createVariable(),
				() -> Util.getTypeFromInterval(sources[0]).createVariable(),
				name
		    );
	}

	public RandomAccessibleIntervalDataSource(
			final RandomAccessibleInterval<D>[] dataSources,
			final RandomAccessibleInterval<T>[] sources,
			final AffineTransform3D[] mipmapTransforms,
			final InvalidateAll invalidateAll,
			final Function<Interpolation, InterpolatorFactory<D, RandomAccessible<D>>> dataInterpolation,
			final Function<Interpolation, InterpolatorFactory<T, RandomAccessible<T>>> interpolation,
			final Supplier<D> dataTypeSupplier,
			final Supplier<T> typeSupplier,
			final String name) {
		super();
		this.mipmapTransforms = mipmapTransforms;
		this.dataSources = dataSources;
		this.sources = sources;
		this.invalidateAll = invalidateAll;
		this.dataInterpolation = dataInterpolation;
		this.interpolation = interpolation;
		this.dataTypeSupplier = dataTypeSupplier;
		this.typeSupplier = typeSupplier;
		this.name = name;
	}

	@SuppressWarnings("unchecked")
	public static <D, T>
	RandomAccessibleIntervalDataSource.DataWithInvalidate<D, T> asDataWithInvalidate(
			final ImagesWithInvalidate<D, T>[] imagesWithInvalidate
	)
	{
		RandomAccessibleInterval<T>[] data = Stream.of(imagesWithInvalidate).map(i -> i.data).toArray(RandomAccessibleInterval[]::new);
		RandomAccessibleInterval<T>[] vdata = Stream.of(imagesWithInvalidate).map(i -> i.vdata).toArray(RandomAccessibleInterval[]::new);
		AffineTransform3D[] transforms = Stream.of(imagesWithInvalidate).map(i -> i.transform).toArray(AffineTransform3D[]::new);
		InvalidateAll invalidateAll = () -> Stream.of(imagesWithInvalidate).forEach( i -> {i.invalidate.invalidateAll(); i.vinvalidate.invalidateAll();});
		return new RandomAccessibleIntervalDataSource.DataWithInvalidate(data, vdata, transforms, invalidateAll);
	}

	@Override
	public boolean isPresent(final int t)
	{
		return true;
	}

	@Override
	public RandomAccessibleInterval<T> getSource(final int t, final int level)
	{
		LOG.debug("Requesting source at t={}, level={}", t, level);
		return sources[level];
	}

	@Override
	public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method)
	{
		LOG.debug("Requesting source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getSource(t, level), typeSupplier.get()),
				interpolation.apply(method)
		                        );
	}

	@Override
	public void getSourceTransform(final int t, final int level, final AffineTransform3D transform)
	{
		LOG.trace("Requesting mipmap transform for level {} at time {}: {}", level, t, mipmapTransforms[level]);
		transform.set(mipmapTransforms[level]);
	}

	@Override
	public T getType()
	{
		return typeSupplier.get();
	}

	@Override
	public String getName()
	{
		return name;
	}

	// TODO VoxelDimensions is the only class pulled in by spim_data
	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		// TODO What to do about this? Do we need this at all?
		return null;
	}

	@Override
	public int getNumMipmapLevels()
	{
		return mipmapTransforms.length;
	}

	@Override
	public RandomAccessibleInterval<D> getDataSource(final int t, final int level)
	{
		LOG.debug("Requesting data source at t={}, level={}", t, level);
		return dataSources[level];
	}

	@Override
	public RealRandomAccessible<D> getInterpolatedDataSource(final int t, final int level, final Interpolation method)
	{
		LOG.debug("Requesting data source at t={}, level={} with interpolation {}: ", t, level, method);
		return Views.interpolate(
				Views.extendValue(getDataSource(t, level), dataTypeSupplier.get()),
				dataInterpolation.apply(method)
		                        );
	}

	@Override
	public D getDataType()
	{
		return dataTypeSupplier.get();
	}

	public RandomAccessibleIntervalDataSource<D, T> copy() {
		return new RandomAccessibleIntervalDataSource<>(
				dataSources,
				sources,
				mipmapTransforms,
				invalidateAll,
				dataInterpolation,
				interpolation,
				dataTypeSupplier,
				typeSupplier,
				name
		);
	}

	@Override
	public void invalidateAll() {
		this.invalidateAll.invalidateAll();
	}
}
