/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.*;

/**
 * Hash Set of primitive ints, using open addressing / linear probing.
 */
public class OpenIntHashSet extends AbstractIntSet
{
	protected static final int MIN_CAPACITY = 8;

	protected static final int PHI = 0x9e3779b9;

	/**
	 * Number of used entries.
	 */
	protected int size;

	/**
	 * The data array.
	 */
	protected int[] data;

	/**
	 * Bit shift of the hash function.
	 */
	protected int shift;

	protected int modcount = 0;

	protected boolean containsZero;

	/**
	 * Creates a new instance.
	 */
	public OpenIntHashSet()
	{
		this(MIN_CAPACITY);
	}

	/**
	 * Creates a new instance with the specified capacity.
	 *
	 * @param capacity the capacity
	 */
	public OpenIntHashSet(int capacity)
	{
		if (capacity < MIN_CAPACITY)
			capacity = MIN_CAPACITY;
		// round up to next power of two
		int sz = Integer.highestOneBit(capacity - 1) << 1;
		data = new int[sz];
		shift = Integer.numberOfLeadingZeros(sz) + 1;
	}

	protected int hash(int key)
	{
		return fibonacciHash(key);
	}

	/**
	 * True multiplicative hashing using golden ratio ("fibonacci hashing").
	 *
	 * @param key the key to hash
	 * @return the hash code
	 */
	protected int fibonacciHash(int key)
	{
		return (key * PHI) >>> shift;
	}

	/**
	 * Hashing using a custom mixing step (multiplying by golden ratio followed by shift-xor) and modulo division (as
	 * implemented by FastUtil).
	 * 
	 * @param key the key to hash
	 * @return the hash code
	 */
	protected int mixModHash(int key)
	{
		final int h = key * PHI;
		return (h ^ (h >>> 16)) & (data.length - 1);
	}

	@Override
	public int size()
	{
		return size;
	}

	@Override
	public boolean contains(int key)
	{
		if (key == 0)
			return containsZero;
		int index = hash(key);
		int mask = data.length - 1;
		for (;;)
		{
			int d = data[index];
			if (d == key)
				return true;
			if (d == 0)
				return false;
			index = (index + 1) & mask;
		}
	}

	@Override
	public IntIterator iterator()
	{
		return new IntIter();
	}

	@Override
	public boolean add(int key)
	{
		if (key == 0)
		{
			if (containsZero)
				return false;
			containsZero = true;
		}
		else
		{
			int index = hash(key);
			int mask = data.length - 1;
			for (;;)
			{
				int d = data[index];
				if (d == key)
					return false;
				if (d == 0)
					break;
				index = (index + 1) & mask;
			}
			data[index] = key;
		}
		size++;
		modcount++;
		if ((size >>> (30 - shift)) >= 3)
		{
			// resize
			OpenIntHashSet s = new OpenIntHashSet(data.length << 1);
			for (IntIterator it = iterator(); it.hasNext();)
				s.add(it.nextInt());

			data = s.data;
			shift = s.shift;
			assert containsZero == s.containsZero;
			assert size == s.size;
		}
		return true;
	}

	@Override
	public boolean remove(int key)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear()
	{
		throw new UnsupportedOperationException();
	}

	private class IntIter implements IntIterator
	{
		private int mod = modcount;
		private int index = -1;
		private int next;
		private int current;
		private boolean currentValid;

		IntIter()
		{
			if (!containsZero)
				findNext();
		}

		private void findNext()
		{
			if (mod != modcount)
				throw new ConcurrentModificationException();

			for (index++; index < data.length; index++)
				if ((next = data[index]) != 0)
					break;
		}

		@Override
		public boolean hasNext()
		{
			return index < data.length;
		}

		@Override
		public int nextInt()
		{
			if (!hasNext())
				throw new NoSuchElementException();
			current = next;
			currentValid = true;
			findNext();
			return current;
		}

		@Override
		public void remove()
		{
			if (!currentValid)
				throw new IllegalStateException();
			currentValid = false;
			OpenIntHashSet.this.remove(current);
			mod = modcount;
		}
	}
}
