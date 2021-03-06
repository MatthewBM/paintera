package org.janelia.saalfeldlab.paintera.data.mask;

import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.type.label.FromIntegerTypeConverter;
import net.imglib2.type.label.Label;
import net.imglib2.type.label.LabelMultisetEntry;
import net.imglib2.type.label.LabelMultisetType;
import net.imglib2.type.label.VolatileLabelMultisetType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Triple;
import org.janelia.saalfeldlab.paintera.data.mask.PickOne.PickAndConvert;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PickOneVolatileLabelMultisetType<M extends IntegerType<M>, VM extends Volatile<M>>
		implements PickOne.PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType>
{

	private final Predicate<M> pickThird;

	private final BiPredicate<M, M> pickSecond;

	private final VolatileLabelMultisetType scalarValue;

	private final Converter<M, LabelMultisetType> converter;

	public PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond)
	{
		this(pickThird, pickSecond, 1);
	}

	public PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final int numOccurrences)
	{
		this(
				pickThird,
				pickSecond,
				// TODO Once https://github.com/saalfeldlab/imglib2-label-multisets/pull/17 is merged,
				// TODO go back to calling FromIntegerTypeConverter.getAppropriateType.
				// TODO for now: Just c&p the code from #17.
				// FromIntegerTypeConverter.getAppropriateVolatileType(numOccurences)
				new VolatileLabelMultisetType(new LabelMultisetEntry(Label.INVALID, numOccurrences)));
	}

	private PickOneVolatileLabelMultisetType(
			final Predicate<M> pickThird,
			final BiPredicate<M, M> pickSecond,
			final VolatileLabelMultisetType scalarValue)
	{
		super();
		this.pickThird = pickThird;
		this.pickSecond = pickSecond;
		this.scalarValue = scalarValue;
		this.converter = new FromIntegerTypeConverter<>();
	}

	@Override
	public VolatileLabelMultisetType apply(final Triple<VolatileLabelMultisetType, VM, VM> t)
	{
		final VolatileLabelMultisetType a  = t.getA();
		final VM                        vb = t.getB();
		final VM                        vc = t.getC();

		final boolean isValid = a.isValid() && vb.isValid() && vc.isValid();
		scalarValue.setValid(isValid);

		if (!isValid)
			return scalarValue;

		final M b = vb.get();
		final M c = vc.get();

		if (pickThird.test(c))
		{
			converter.convert(c, scalarValue.get());
			return scalarValue;
		}

		if (pickSecond.test(b, c))
		{
			converter.convert(b, scalarValue.get());
			return scalarValue;
		}

		return a;

	}

	@Override
	public PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType> copy()
	{
		return new PickOneVolatileLabelMultisetType<>(pickThird, pickSecond, scalarValue.copy());
	}

	@Override
	public PickAndConvert<VolatileLabelMultisetType, VM, VM, VolatileLabelMultisetType> copyWithDifferentNumOccurences(int numOccurrences) {
		return new PickOneVolatileLabelMultisetType<>(pickThird, pickSecond, numOccurrences);
	}

}
