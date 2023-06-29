/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Allows multiple threads to iterate over a given range of integers in
 * parallel.
 * <p>
 * The values returned to participating threads are as much apart from each
 * other as possible. At the end of the iteration, the same value will be
 * returned to multiple threads, so that threads can assist each other.
 */
public class IntSpliterator
{
	public static final long NONE = -1L;

	private static final int PARALLEL_BITS = 8;
	public static final int MAX_PARALLEL = 1 << PARALLEL_BITS;

	private static final int PARALLEL_MASK = MAX_PARALLEL - 1;
	private static final int ALL_INT_BITS = Long.SIZE - PARALLEL_BITS;
	private static final int INT_BITS = ALL_INT_BITS / 2;

	public static final int MAX_INT = 1 << INT_BITS;

	private static final int INT_MASK = MAX_INT - 1;
	private static final long RESULT_MASK = range(PARALLEL_MASK, INT_MASK, 0);

	private static final int STRIDE = 0;

	private static final int NCPUS = Runtime.getRuntime().availableProcessors();

	private static final AtomicIntegerFieldUpdater<IntSpliterator> FIRST_UPDATER = AtomicIntegerFieldUpdater
			.newUpdater(IntSpliterator.class, "first");

	private final int len;
	private final long ranges[];
	private volatile int first;

	/**
	 * Creates a new instance.
	 *
	 * @param size number of values to iterate (iterates over 0..size-1)
	 */
	public IntSpliterator(int size)
	{
		this(0, size);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param start start of iteration (inclusive)
	 * @param end end of iteration (exclusive)
	 */
	public IntSpliterator(int start, int end)
	{
		this(start, end, Math.max(MAX_PARALLEL, NCPUS));
	}

	/**
	 * Creates a new instance.
	 *
	 * @param start start of iteration (inclusive)
	 * @param end end of iteration (exclusive)
	 * @param maxParallel max expected parallelism
	 */
	public IntSpliterator(int start, int end, int maxParallel)
	{
		assert start >= 0;
		assert start <= end;
		assert end < MAX_INT;
		assert maxParallel > 0;
		assert maxParallel <= MAX_PARALLEL;

		len = maxParallel;
		ranges = new long[((len - 1) << STRIDE) + 1];
		if (start < end)
			ranges[0] = range(0, start, end);
		first = 1;
	}

	private static long range(int index, int start, int end)
	{
		return (long) index << ALL_INT_BITS | (long) end << INT_BITS | (long) start;
	}

	private static int getStart(long range)
	{
		return (int) range & INT_MASK;
	}

	private static int getEnd(long range)
	{
		return (int) (range >>> INT_BITS) & INT_MASK;
	}

	private static long result(long range)
	{
		return range & RESULT_MASK;
	}

	private static int getIndex(long range)
	{
		return (int) (range >>> ALL_INT_BITS) & PARALLEL_MASK;
	}

	private boolean set(int index, long range, long newRange)
	{
		return AtomicUtils.cas(ranges, index << STRIDE, range, newRange);
	}

	/**
	 * Gets the raw range of the specified index (without resolving splits).
	 *
	 * @param index the index of the range to return
	 * @return the range of the specified index
	 */
	private long getRaw(int index)
	{
		return AtomicUtils.get(ranges, index << STRIDE);
	}

	/**
	 * Gets the range of specified index with all splits resolved.
	 *
	 * @param index the index of the range to return
	 * @return
	 */
	private long get(int index)
	{
		for (;;)
		{
			long range = getRaw(index);
			int toIndex = getIndex(range);
			if (range == 0 || index == toIndex)
				return range;

			int start = getStart(range);
			int end = getEnd(range);
			assert end - start >= 2;
			int mid = (start + end + 1) >> 1;
			long toRange = getRaw(toIndex);
			if (toRange == 0)
			{
				long newToRange = range(toIndex, mid, end);
				if (!set(toIndex, 0, newToRange))
					continue;
			}
			else if (!(getStart(toRange) >= mid && getEnd(toRange) <= end))
			{
				// some other range has been split into toIndex, mark split failed
				long newRange = range(index, start, end);
				set(index, range, newRange);
				continue;
			}

			// split succeeded
			long newRange = range(index, start, mid);
			if (set(index, range, newRange))
				return newRange;
		}
	}

	private long split()
	{
		for (;;)
		{
			long maxRange = 0;
			int maxSize = -1;
			int maxIndex = -1;
			int freeIndex = -1;
			for (int index = 0; index < len; index++)
			{
				long range = get(index);
				if (range == 0)
				{
					if (freeIndex < 0)
						freeIndex = index;
				}
				else
				{
					int size = getEnd(range) - getStart(range);
					if (size > maxSize)
					{
						maxIndex = index;
						maxSize = size;
						maxRange = range;
					}
				}
			}

			if (maxIndex < 0)
				return NONE;
			if (freeIndex < 0 || maxSize < 2)
				return maxRange;

			long newRange = range(freeIndex, getStart(maxRange), getEnd(maxRange));
			if (set(maxIndex, maxRange, newRange))
			{
				get(maxIndex);
				long toRange = get(freeIndex);
				if (getStart(toRange) > getStart(maxRange) && getEnd(toRange) <= getEnd(maxRange))
					return toRange;
			}
		}
	}

	/**
	 * Starts iteration for a thread.
	 *
	 * @return the iteration state, cast to {@code int} to get the iteration value,
	 *         special value {@link #NONE} indicates end of iteration
	 */
	public long first()
	{
		return next(NONE);
	}

	/**
	 * Continues iteration.
	 *
	 * @param previous the previous iteration state (as previously returned from
	 *                 {@link #first()} / {@link #next(long)})
	 * @return the iteration state, cast to {@code int} to get the iteration value,
	 *         special value {@link #NONE} indicates end of iteration
	 */
	public long next(long previous)
	{
		if (previous != NONE)
		{
			int idx = getIndex(previous);
			for (;;)
			{
				long range = get(idx);
				if (range == 0)
					break;
				int start = getStart(range);
				if (start != getStart(previous))
					return result(range);

				start++;
				int end = getEnd(range);
				if (start < end)
				{
					long newRange = range(idx, start, end);
					if (set(idx, range, newRange))
						return result(newRange);
				}
				else
				{
					if (set(idx, range, 0))
						break;
				}
			}
		}
		else if (first == 1)
		{
			long range = getRaw(0);
			if (FIRST_UPDATER.compareAndSet(this, 1, 0))
				return range == 0 ? NONE : result(range);
		}

		long range = split();
		if (range == NONE)
			return NONE;
		return result(range);
	}
}
