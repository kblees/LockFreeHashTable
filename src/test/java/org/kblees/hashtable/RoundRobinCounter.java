/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

/**
 * Counts through a defined range of integers over and over again.
 */
public class RoundRobinCounter
{
	private int min;
	private int max;
	private int index;
	private Runnable onReset;

	/**
	 * Creates an uninitialized instance, use {@link #init} to initialize.
	 */
	public RoundRobinCounter()
	{
	}

	/**
	 * Creates a counter that counts repeatedly from 0 to max-1, starting with a random value.
	 *
	 * @param max the maximum (exclusive)
	 */
	public RoundRobinCounter(int max)
	{
		init(max);
	}

	/**
	 * Creates a counter that counts repeatedly from 0 to max-1, starting with a random value, and calling {@code onReset}
	 * when the counter is reset to 0.
	 *
	 * @param max the maximum (exclusive)
	 * @param onReset callback to call on counter reset
	 */
	public RoundRobinCounter(int max, Runnable onReset)
	{
		init(max);
		onReset(onReset);
	}

	/**
	 * Initializes the counter.
	 *
	 * @param min the minimum (inclusive)
	 * @param max the maximum (exclusive)
	 * @param index the start index
	 */
	public void init(int min, int max, int index)
	{
		this.min = min;
		this.max = max;
		this.index = index;
	}

	/**
	 * Initializes the counter to count repeatedly from 0 to max-1, starting with a random value.
	 *
	 * @param max the maximum (exclusive)
	 */
	public void init(int max)
	{
		init(0, max, BenchUtils.randomInt(max));
	}

	/**
	 * Initializes the counter to a thread-specific sub-range of min..max-1. Divides the range {@code min..max-1} in
	 * {@code threads} equal-sized (+-1) sub-ranges and initializes the counter with the {@code thread}'th sub-range.
	 *
	 * @param min the minimum of the entire range (inclusive)
	 * @param max the maximum of the entire range (exclusive)
	 * @param threads the number of treads / sub-ranges
	 * @param thread the index of the current thread / sub-range to use
	 */
	public void init(int min, int max, int threads, int thread)
	{
		long size = max - min;
		min = (int)(size * thread / threads);
		max = (int)(size * (thread + 1) / threads);
		init(min, max, min - 1);
	}

	/**
	 * Initializes the callback to call when the counter is reset to the minimum value.
	 *
	 * @param onReset the new callback
	 */
	public void onReset(Runnable onReset)
	{
		this.onReset = onReset;
	}

	/**
	 * @return the current index
	 */
	public int getIndex()
	{
		return index;
	}

	/**
	 * @return the minimum value
	 */
	public int getMin()
	{
		return min;
	}

	/**
	 * @return the maximum value
	 */
	public int getMax()
	{
		return max;
	}

	/**
	 * Increments the counter and returns the result.
	 *
	 * @return the next index
	 */
	public int nextIndex()
	{
		if (++index >= max)
		{
			index = min;
			if (onReset != null)
				onReset.run();
		}
		return getIndex();
	}
}
