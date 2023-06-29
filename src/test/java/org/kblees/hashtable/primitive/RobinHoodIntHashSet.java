/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

/**
 * Hash Set of primitive ints, using open addressing / linear probing / robin hood hashing.
 */
public class RobinHoodIntHashSet extends OpenIntHashSet
{
	/**
	 * Creates a new instance.
	 */
	public RobinHoodIntHashSet()
	{
		this(MIN_CAPACITY);
	}

	/**
	 * Creates a new instance with the specified capacity.
	 *
	 * @param capacity the capacity
	 */
	public RobinHoodIntHashSet(int capacity)
	{
		super(capacity);
	}

	@Override
	public boolean contains(int key)
	{
		if (key == 0)
			return containsZero;
		int index = hash(key);
		int mask = data.length - 1;
		int psl = 0;
		for (;;)
		{
			int d = data[index];
			if (d == key)
				return true;
			if (d == 0)
				return false;
			if (((index - hash(d)) & mask) < psl)
				return false;
			index = (index + 1) & mask;
			psl++;
		}
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
			int psl = 0;
			for (;;)
			{
				int d = data[index];
				if (d == key)
					return false;
				if (d == 0)
					break;

				if (((index - hash(d)) & mask) < psl)
				{
					while (d != 0)
					{
						data[index] = key;
						key = d;
						index = (index + 1) & mask;
						d = data[index];
					}
					break;
				}
				
				index = (index + 1) & mask;
				psl++;
			}
			data[index] = key;
		}
		size++;
		modcount++;
		if ((size >>> (30 - shift)) >= 3)
		{
			// resize
			RobinHoodIntHashSet s = new RobinHoodIntHashSet(data.length << 1);
			for (IntIterator it = iterator(); it.hasNext();)
				s.add(it.nextInt());

			data = s.data;
			shift = s.shift;
			assert containsZero == s.containsZero;
			assert size == s.size;
		}
		return true;
	}
}
