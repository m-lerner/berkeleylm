package edu.berkeley.nlp.lm.values;

import edu.berkeley.nlp.lm.array.LongArray;
import edu.berkeley.nlp.lm.collections.LongHashSet;
import edu.berkeley.nlp.lm.map.HashNgramMap;
import edu.berkeley.nlp.lm.map.NgramMap;
import edu.berkeley.nlp.lm.util.Annotations.OutputParameter;
import edu.berkeley.nlp.lm.util.Annotations.PrintMemoryCount;

/**
 * Stored type and token counts necessary for estimating a Kneser-Ney language
 * model
 * 
 * @author adampauls
 * 
 */
public final class KneserNeyCountValueContainer implements ValueContainer<KneserNeyCountValueContainer.KneserNeyCounts>
{

	/**
	 * Warning: type counts are stored internally as 32-bit ints.
	 * 
	 * @author adampauls
	 * 
	 */
	public static class KneserNeyCounts
	{
		public long tokenCounts = 0; // only stored for the highest- and second-highest-order n-grams

		public long leftDotTypeCounts = 0; // N_{1+}(\cdot w) as in Chen and Goodman (1998), not stored for highest-order

		public long rightDotTypeCounts = 0; // N_{1+}(w \cdot) as in Chen and Goodman (1998), not stored for highest-order

		public long dotdotTypeCounts = 0; // N_{1+}(\dot w \dot) as in Chen and Goodman (1998), not stored for highest-order

		// these two are used to compute the Kneser-Ney discount
		public boolean isOneCount = false;

		public boolean isTwoCount = false;

		boolean isInternal = false;
	}

	private static final long serialVersionUID = 964277160049236607L;

	@PrintMemoryCount
	private LongArray tokenCounts; // for highest-order ngrams

	private LongArray prefixTokenCounts;// for second-highest order n-grams

	@PrintMemoryCount
	private final LongArray[] rightDotTypeCounts;

	@PrintMemoryCount
	private final LongArray[] dotdotTypeCounts;

	@PrintMemoryCount
	private final LongArray[] leftDotTypeCounts;

	//	@PrintMemoryCount
	//	private final LongArray[] lowestOrderTokenCounts;

	@PrintMemoryCount
	private final LongHashSet[] oneCountOffsets;

	@PrintMemoryCount
	private final LongHashSet[] twoCountOffsets;

	private long bigramTypeCounts = 0;

	private HashNgramMap<KneserNeyCounts> map;

	public KneserNeyCountValueContainer(final int maxNgramOrder) {
		this.tokenCounts = LongArray.StaticMethods.newLongArray(Long.MAX_VALUE, Integer.MAX_VALUE);
		this.prefixTokenCounts = LongArray.StaticMethods.newLongArray(Long.MAX_VALUE, Integer.MAX_VALUE);
		this.oneCountOffsets = new LongHashSet[maxNgramOrder];
		this.twoCountOffsets = new LongHashSet[maxNgramOrder];
		rightDotTypeCounts = new LongArray[maxNgramOrder - 1];
		leftDotTypeCounts = new LongArray[maxNgramOrder - 1];
		dotdotTypeCounts = new LongArray[maxNgramOrder - 1];
		for (int i = 0; i < maxNgramOrder; ++i) {
			oneCountOffsets[i] = new LongHashSet();
			twoCountOffsets[i] = new LongHashSet();
			if (i < maxNgramOrder - 1) {
				rightDotTypeCounts[i] = LongArray.StaticMethods.newLongArray(Long.MAX_VALUE, Integer.MAX_VALUE);
				leftDotTypeCounts[i] = LongArray.StaticMethods.newLongArray(Long.MAX_VALUE, Integer.MAX_VALUE);
				dotdotTypeCounts[i] = LongArray.StaticMethods.newLongArray(Long.MAX_VALUE, Integer.MAX_VALUE);
			}
		}
	}

	@Override
	public KneserNeyCountValueContainer createFreshValues() {
		final KneserNeyCountValueContainer kneseryNeyCountValueContainer = new KneserNeyCountValueContainer(rightDotTypeCounts.length + 1);
		kneseryNeyCountValueContainer.bigramTypeCounts = this.bigramTypeCounts;

		return kneseryNeyCountValueContainer;
	}

	@Override
	public void getFromOffset(final long offset, final int ngramOrder, @OutputParameter final KneserNeyCounts outputVal) {
		final boolean isHighestOrder = isHighestOrder(ngramOrder);
		final boolean isSecondHighestOrder = isSecondHighestOrder(ngramOrder);
		outputVal.tokenCounts = isHighestOrder ? tokenCounts.get(offset) : (isSecondHighestOrder ? getSafe(offset, prefixTokenCounts) : -1);
		outputVal.rightDotTypeCounts = (int) ((isHighestOrder || (offset >= rightDotTypeCounts[ngramOrder].size())) ? -1 : rightDotTypeCounts[ngramOrder]
			.get(offset));
		outputVal.leftDotTypeCounts = (int) ((isHighestOrder || (offset >= leftDotTypeCounts[ngramOrder].size())) ? -1 : leftDotTypeCounts[ngramOrder]
			.get(offset));
		outputVal.dotdotTypeCounts = (int) ((isHighestOrder || (offset >= dotdotTypeCounts[ngramOrder].size())) ? -1 : dotdotTypeCounts[ngramOrder].get(offset));
		outputVal.isOneCount = oneCountOffsets[ngramOrder].containsKey(offset);
		outputVal.isTwoCount = twoCountOffsets[ngramOrder].containsKey(offset);
		outputVal.isInternal = true;
	}

	private static long getSafe(final long offset, final LongArray array) {
		return offset >= array.size() ? 0 : array.get(offset);
	}

	@Override
	public void trimAfterNgram(final int ngramOrder, final long size) {

	}

	@Override
	public KneserNeyCounts getScratchValue() {
		return new KneserNeyCounts();
	}

	@Override
	public boolean add(final int[] ngram, final int startPos, final int endPos, final int ngramOrder, final long offset, final long contextOffset,
		final int word, final KneserNeyCounts val, final long suffixOffset, final boolean ngramIsNew) {
		if (val == null) return true;

		if (val.tokenCounts > 0) {
			if (ngramIsNew) {
				oneCountOffsets[ngramOrder].put(offset);
			} else if (oneCountOffsets[ngramOrder].containsKey(offset)) {
				oneCountOffsets[ngramOrder].remove(offset);
				twoCountOffsets[ngramOrder].put(offset);
			} else if (twoCountOffsets[ngramOrder].containsKey(offset)) {
				twoCountOffsets[ngramOrder].remove(offset);
			}

		} else {
			if (val.isOneCount) oneCountOffsets[ngramOrder].put(offset);
			if (val.isTwoCount) twoCountOffsets[ngramOrder].put(offset);
		}
		assert !map.isReversed();
		if (isHighestOrder(ngramOrder)) {
			tokenCounts.incrementCount(offset, val.tokenCounts);
			prefixTokenCounts.incrementCount(contextOffset, val.tokenCounts);
		}
		assert !(val.isInternal && !ngramIsNew);
		if (ngramIsNew) {
			if (val.isInternal) {
				if (val.dotdotTypeCounts > 0) dotdotTypeCounts[ngramOrder].incrementCount(offset, val.dotdotTypeCounts);
				if (val.leftDotTypeCounts > 0) leftDotTypeCounts[ngramOrder].incrementCount(offset, val.leftDotTypeCounts);
				if (val.rightDotTypeCounts > 0) rightDotTypeCounts[ngramOrder].incrementCount(offset, val.rightDotTypeCounts);

			} else {
				if (ngramOrder > 0) {
					if (ngramOrder == 1) {
						bigramTypeCounts++;
					} else {
						final long dotDotOffset = map.getPrefixOffset(suffixOffset, endPos - startPos - 2);//map.getOffsetForNgramInModel(ngram, startPos + 1, endPos - 1);
						dotdotTypeCounts[ngramOrder - 2].incrementCount(dotDotOffset, 1);
					}
					final long leftDotOffset = suffixOffset; //map.getOffsetForNgramInModel(ngram, startPos + 1, endPos);
					assert suffixOffset >= 0;
					leftDotTypeCounts[ngramOrder - 1].incrementCount(leftDotOffset, 1);
					final long rightDotOffset = contextOffset;//map.getOffsetForNgramInModel(ngram, startPos, endPos - 1);
					assert contextOffset >= 0;
					rightDotTypeCounts[ngramOrder - 1].incrementCount(rightDotOffset, 1);
				}
			}
		}
		return true;
	}

	@Override
	public void setSizeAtLeast(final long size, final int ngramOrder) {
		if (isHighestOrder(ngramOrder)) {
			tokenCounts.setAndGrowIfNeeded(size - 1, 0);
		} else {
			if (isSecondHighestOrder(ngramOrder)) prefixTokenCounts.setAndGrowIfNeeded(size - 1, 0);
			leftDotTypeCounts[ngramOrder].setAndGrowIfNeeded(size - 1, 0);
			rightDotTypeCounts[ngramOrder].setAndGrowIfNeeded(size - 1, 0);
			dotdotTypeCounts[ngramOrder].setAndGrowIfNeeded(size - 1, 0);

		}

	}

	/**
	 * @param ngramOrder
	 * @return
	 */
	private boolean isHighestOrder(final int ngramOrder) {
		return ngramOrder == dotdotTypeCounts.length;
	}

	/**
	 * @param ngramOrder
	 * @return
	 */
	private boolean isSecondHighestOrder(final int ngramOrder) {
		return ngramOrder == dotdotTypeCounts.length - 1;
	}

	@Override
	public void setFromOtherValues(final ValueContainer<KneserNeyCounts> other) {
		final KneserNeyCountValueContainer other_ = (KneserNeyCountValueContainer) other;
		tokenCounts = other_.tokenCounts;
		System.arraycopy(other_.dotdotTypeCounts, 0, dotdotTypeCounts, 0, dotdotTypeCounts.length);
		System.arraycopy(other_.rightDotTypeCounts, 0, rightDotTypeCounts, 0, rightDotTypeCounts.length);
		System.arraycopy(other_.leftDotTypeCounts, 0, leftDotTypeCounts, 0, leftDotTypeCounts.length);
		System.arraycopy(other_.oneCountOffsets, 0, oneCountOffsets, 0, oneCountOffsets.length);
		System.arraycopy(other_.twoCountOffsets, 0, twoCountOffsets, 0, twoCountOffsets.length);
		prefixTokenCounts = other_.prefixTokenCounts;
		bigramTypeCounts = other_.bigramTypeCounts;
	}

	@Override
	public void trim() {
		tokenCounts.trim();
		prefixTokenCounts.trim();
		for (int i = 0; i < rightDotTypeCounts.length; ++i) {
			rightDotTypeCounts[i].trim();
			leftDotTypeCounts[i].trim();
			dotdotTypeCounts[i].trim();
		}
	}

	@Override
	public void setMap(final NgramMap<KneserNeyCounts> map) {
		this.map = (HashNgramMap<KneserNeyCounts>) map;
	}

	@Override
	public void clearStorageForOrder(int ngramOrder) {
		oneCountOffsets[ngramOrder].clear();
		twoCountOffsets[ngramOrder].clear();
		if (ngramOrder == rightDotTypeCounts.length) {
			tokenCounts = null;
		} else if (ngramOrder == rightDotTypeCounts.length - 1) {
			prefixTokenCounts = null;
		}
		if (ngramOrder < rightDotTypeCounts.length) {
			rightDotTypeCounts[ngramOrder] = null;
			leftDotTypeCounts[ngramOrder] = null;
			dotdotTypeCounts[ngramOrder] = null;
		}
	}

	@Override
	public boolean storeSuffixoffsets() {
		return true;
	}

	public long getBigramTypeCounts() {
		return bigramTypeCounts;
	}

	public int getNumOneCountNgrams(int ngramOrder) {
		return oneCountOffsets[ngramOrder].size();
	}

	public int getNumTwoCountNgrams(int ngramOrder) {
		return twoCountOffsets[ngramOrder].size();
	}

}