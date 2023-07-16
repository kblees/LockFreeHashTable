/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.*;

/**
 * Hash Set of primitive ints, using separate chaining.
 */
public class ArrayIntHashSet extends AbstractIntSet
{
	private static final int MIN_CAPACITY = 16;

	private static final int MIN_OFFSET = 2;

	/**
	 * Number of used entries.
	 */
	private int size;

	/**
	 * The hash table. Each entry stores:
	 * <ul>
	 * <li>Lowest bit ({@code entry & 1}): {@code 1} if there are more entries, {@code 0}
	 * for end of chain (of the bucket).</li>
	 * <li>Middle bits ({@code (entry & mask) >>> 1}): Offset of the first entry of this bucket + 2.
	 * Special values {@code 0} and {@code 1} mean the bucket is empty.</li>
	 * <li>High bits ({@code entry & ~mask}: The quotient of the key (i.e. the part
	 * of the key that has not been used to select the bucket, see Knuth TAoCP
	 * section 6.4 exercise 13)</li>
	 * </ul>
	 */
	private int[] data;

	/**
	 * Bit mask of the hash function.
	 */
	private int mask;

	private int modcount = 0;

	/**
	 * Creates a new instance.
	 */
	public ArrayIntHashSet()
	{
		this(0);
	}

	/**
	 * Creates a new instance with the specified capacity.
	 *
	 * @param capacity the capacity
	 */
	public ArrayIntHashSet(int capacity)
	{
		// round up to next power of two
		int sz = Integer.highestOneBit(Math.max(capacity, MIN_CAPACITY) - 1) << 1;
		// increase initial table size if load factor would be exceeded
		if (capacity > maxCapacity(sz))
			sz <<= 1;
		mask = sz - 1;
		// allocate hash table
		data = new int[sz];
	}

	/**
	 * Calculates the maximum capacity of the hash table (i.e. before it is resized).
	 *
	 * @param tableSize the size of the hash table
	 * @return maximum capacity
	 */
	protected int maxCapacity(int tableSize)
	{
		// default load factor is 0.75
		return tableSize - (tableSize >>> 2);
	}

	@Override
	public int size()
	{
		return size;
	}

	private final int getOffset(int data)
	{
		return ((data & mask) >>> 1) - MIN_OFFSET;
	}

	private static final boolean isEndOfChain(int data)
	{
		return (data & 1) == 0;
	}

	private final int getKey(int data)
	{
		return data & ~mask;
	}

	private final int entry(int key, int offset, boolean endOfChain)
	{
		assert offset > -MIN_OFFSET && offset <= (mask >>> 1) - MIN_OFFSET;
		return (key & ~mask) | ((offset + MIN_OFFSET) << 1) | (endOfChain ? 0 : 1);
	}

	@Override
	public boolean contains(int key)
	{
		// fast path for direct hit / miss
		int m = mask;
		int e = data[key & m];
		if (((key & ~m) ^ e) >>> 1 == MIN_OFFSET)
			return true;
		if (e == 0)
			return false;

		return contains(key, e);
	}

	private boolean contains(int key, int e)
	{
		// fast path for direct miss
		int offset = getOffset(e);
		if (offset < 0)
			return false;

		// loop over the rest of the bucket
		int index = key + offset;
		key = getKey(key);
		for (;; index++)
		{
			e = data[index & mask];
			int d = key - getKey(e);
			if (d == 0)
				return true;
			if (d < 0 || isEndOfChain(e))
				return false;
		}
	}

	@Override
	public boolean add(int key)
	{
		final int bucket = key & mask;
		int e = data[bucket];
		if (e == 0)
		{
			// fast path: insert at hash location if empty
			data[bucket] = entry(key, 0, true);
			size++;
			modcount++;
			return true;
		}

		int nextBucket = (bucket + 1) & mask;
		if (data[nextBucket] == 0)
		{
			// fast path: insert at hash location + 1 if empty
			if (getOffset(e) == 0)
			{
				key = getKey(key);
				e = getKey(e);
				if (key == e)
					return false;
				data[bucket] = entry(key < e ? key : e, 0, false);
				data[nextBucket]= entry(key < e ? e : key, -1, true);
			}
			else
			{
				data[bucket] = entry(e, 1, true);
				data[nextBucket] = entry(key, -1, true);
			}
			size++;
			modcount++;
			return true;
		}

		// full add algorithm
		return add(key, e);
	}

	private boolean add(int key, int e)
	{
		final int mask = this.mask;
		final int bucket = key & mask;

		// establish start of bucket and insert location
		key = getKey(key);
		int start, insert = Integer.MAX_VALUE;
		if ((start = getOffset(e)) >= 0)
		{
			start += bucket;
			if ((insert = insertIndexOf(start, key)) < 0)
				return false; // key has been found
		}
		else
		{
			start = Integer.MAX_VALUE;
		}

		// resize if load factor is exceeded or there is no empty slot within reach
		if (size >= maxCapacity(mask + 1) || !checkOffsets(bucket))
			return resizeAdd(key | bucket);

		// rewrite the hash table between bucket and the empty slot
		int laste, offset, keybits, next = Integer.MAX_VALUE;
		boolean eoc;
		for (int idx = bucket; e != 0; idx++)
		{
			laste = e;
			e = data[idx & mask];

			offset = getOffset(e);
			if (idx > bucket && offset != -1) // offset == -MIN_OFFSET || offset >= 0
			{
				if (next == Integer.MAX_VALUE)
				{
					next = idx + Math.max(0, offset);
					if (start == Integer.MAX_VALUE)
						start = insert = setOffset(bucket, next);
				}
				offset++;
			}

			if (idx < start)
			{
				eoc = isEndOfChain(e);
				keybits = getKey(e);
			}
			else if (idx <= next)
			{
				eoc = idx == next;
				keybits = idx == insert ? key : getKey(idx < insert ? e : laste);
			}
			else
			{
				eoc = isEndOfChain(laste);
				keybits = getKey(laste);
			}

			data[idx & mask] = entry(keybits, offset, eoc);
		}
		size++;
		modcount++;
		return true;
	}

	private int insertIndexOf(int index, int key)
	{
		for (;;)
		{
			int e = data[index & mask];
			int d = key - getKey(e);
			if (d == 0)
				return -1;
			if (d < 0)
				return index;
			index++;
			if (isEndOfChain(e))
				return index;
		}
	}

	private boolean findEmpty(int start, int end)
	{
		for (int i = start; i <= end; i++)
			if (data[i] == 0)
				return true;
		return false;
	}

	private boolean checkOffsets(int bucket)
	{
		int maxOffset = (mask >>> 1) - MIN_OFFSET;
		int max = (bucket + maxOffset) & mask;
		if (max < bucket)
			return findEmpty(bucket + 1, mask) || findEmpty(0, max);
		else
			return findEmpty(bucket + 1, max);
	}

	private int setOffset(int bucket, int start)
	{
		int d = data[bucket];
		data[bucket] = entry(getKey(d), start - bucket, isEndOfChain(d));
		return start;
	}

	private boolean resizeAdd(int key)
	{
		ArrayIntHashSet s = new ArrayIntHashSet(mask + 2);
		for (IntIterator it = iterator(); it.hasNext();)
			s.add(it.nextInt());
		s.add(key);
		data = s.data;
		mask = s.mask;
		size = s.size;
		modcount++;
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

	@Override
	public IntIterator iterator()
	{
		return new IntIter();
	}

	private class IntIter implements IntIterator
	{
		private int mod = modcount;
		private int index = -1;
		private int bucketIndex;
		private int next;
		private int current;
		private boolean currentValid;

		IntIter()
		{
			findNext();
		}

		private void findNext()
		{
			if (mod != modcount)
				throw new ConcurrentModificationException();

			if (isEndOfChain(next))
			{
				for (index++; index <= mask; index++)
				{
					int offset = getOffset(data[index]);
					if (offset >= 0)
					{
						bucketIndex = (index + offset) & mask;
						next = data[bucketIndex];
						break;
					}
				}
			}
			else
			{
				bucketIndex = (bucketIndex + 1) & mask;
				next = data[bucketIndex];
			}
		}

		@Override
		public boolean hasNext()
		{
			return index <= mask;
		}

		@Override
		public int nextInt()
		{
			if (!hasNext())
				throw new NoSuchElementException();
			current = getKey(next) | index;
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
			ArrayIntHashSet.this.remove(current);
			mod = modcount;
		}
	}
}

