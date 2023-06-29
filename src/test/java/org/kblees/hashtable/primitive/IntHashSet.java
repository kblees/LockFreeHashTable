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
public class IntHashSet extends AbstractIntSet
{
	private static final int MIN_CAPACITY = 8;

	/**
	 * Number of used entries.
	 */
	private int size;

	/**
	 * The data array, organized as follows:
	 * <ul>
	 * <li>{@code [0..mask]}: the hash table</li>
	 * <li>{@code ]mask..length[}: the collision area (see Knuth TAoCP section 6.4
	 * exercise 43)</li>
	 * </ul>
	 * <p>
	 * Each entry stores:
	 * <ul>
	 * <li>Low bits ({@code entry & mask}): Index of the next entry in the collision
	 * area. Special value {@code 0} means the entry is unused, {@code mask} means
	 * there are no more entries (end of chain).</li>
	 * <li>High bits ({@code entry & ~mask}): The quotient of the key (i.e. the part
	 * of the key that has not been used to select the bucket, see Knuth TAoCP
	 * section 6.4 exercise 13)</li>
	 * </ul>
	 */
	private int[] data;

	/**
	 * Bit mask of the hash function.
	 */
	private int mask;

	/**
	 * Head of the free list in the collision area.
	 */
	private int head = 1;

	private int modcount = 0;

	/**
	 * Creates a new instance.
	 */
	public IntHashSet()
	{
		this(MIN_CAPACITY);
	}

	/**
	 * Creates a new instance with the specified capacity.
	 *
	 * @param capacity the capacity
	 */
	public IntHashSet(int capacity)
	{
		if (capacity < MIN_CAPACITY)
			capacity = MIN_CAPACITY;
		// round up to next power of two
		int sz = Integer.highestOneBit(capacity - 1) << 1;
		mask = sz - 1;
		// allocate hash table with 3/8 (.375) collision area
		int tabsize = sz + (sz >>> 2) + (sz >>> 3);
		data = new int[tabsize];
		// store end of chain marker at end of collision area
		data[tabsize - 1] = mask;
	}

	@Override
	public int size()
	{
		return size;
	}

	@Override
	public boolean contains(int key)
	{
		int index = key & mask;
		int d = data[index];
		if (d == 0)
			return false;

		int keydata = key | mask;
		for (;;)
		{
			if ((d | mask) == keydata)
				return true;
			if ((d & mask) == mask)
				return false;
			index = (d & mask) + mask;
			d = data[index];
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
		int index = key & mask;
		int d = data[index];
		if (d == 0)
		{
			data[index] = key | mask;
			size++;
			modcount++;
			return true;
		}

		int keydata = key | mask;
		for (;;)
		{
			if ((d | mask) == keydata)
				return false;
			if ((d & mask) == mask)
				break;
			index = (d & mask) + mask;
			d = data[index];
		}

		if (head != mask)
		{
			int collIndex = head + mask;
			int collData = data[collIndex];

			data[collIndex] = key | mask;
			data[index] = (d & ~mask) | head;
			head = (collData == 0) ? head + 1 : collData;
			size++;
		}
		else
		{
			IntHashSet s = new IntHashSet(data.length);
			for (IntIterator it = iterator(); it.hasNext();)
				s.add(it.nextInt());
			s.add(key);

			data = s.data;
			head = s.head;
			mask = s.mask;
			size = s.size;
		}
		modcount++;
		return true;
	}

	@Override
	public boolean remove(int key)
	{
		int index = key & mask;
		int d = data[index];
		if (d == 0)
			return false;

		int keydata = key | mask;
		int lastIndex = -1;
		for (;;)
		{
			if ((d | mask) == keydata)
			{
				if (lastIndex < 0)
				{
					// main hash table
					int next = d & mask;
					if (next == mask)
					{
						data[index] = 0;
					}
					else
					{
						data[index] = data[next + mask];
						data[next + mask] = head;
						head = next;
					}
				}
				else
				{
					// collision area
					int next = d & mask;
					data[lastIndex] = (data[lastIndex] & ~mask) | next;
					data[index] = head;
					head = index - mask;
				}
				size--;
				modcount++;
				return true;
			}

			if ((d & mask) == mask)
				return false;
			lastIndex = index;
			index = (d & mask) + mask;
			d = data[index];
		}
	}

	@Override
	public void clear()
	{
		IntHashSet s = new IntHashSet();
		data = s.data;
		head = s.head;
		mask = s.mask;
		size = s.size;
		modcount++;
	}

	private class IntIter implements IntIterator
	{
		private int mod = modcount;
		private int index;
		private int next;
		private int current;
		private boolean currentValid;

		IntIter()
		{
			for (index = 0; index <= mask; index++)
				if ((next = data[index]) != 0)
					break;
		}

		private void findNext()
		{
			if (mod != modcount)
				throw new ConcurrentModificationException();

			if ((next & mask) == mask)
			{
				for (index++; index <= mask; index++)
					if ((next = data[index]) != 0)
						break;
			}
			else
			{
				next = data[(next & mask) + mask];
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
			current = (next & ~mask) | index;
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
			IntHashSet.this.remove(current);
			mod = modcount;
		}
	}
}
