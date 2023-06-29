/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */

package org.kblees.hashtable.primitive;

import java.util.*;

import org.kblees.hashtable.core.*;

/**
 * Proof of concept of using LockFreeHashTable to implement primitive collections (unfortunately largely untested...).
 */
public class LockFreeIntHashSet extends AbstractSet<Integer> implements IntSet
{
	static class Table extends LockFreeHashTable<Table>
	{
		Table(int capacity)
		{
			super(capacity);
		}
	}		

	private static final Cas<LockFreeIntHashSet, Table> TABLE = Cas.create(LockFreeIntHashSet.class, Table.class,
			"table");

	private volatile Table table;

	/**
	 * Creates a new instance.
	 *
	 * @param initialCapacity initial capacity of the set, i.e. number of entries
	 */
	public LockFreeIntHashSet(int initialCapacity)
	{
		table = new Table(initialCapacity + (initialCapacity >>> 4));
	}

	/**
	 * Creates a new, empty instance.
	 */
	public LockFreeIntHashSet()
	{
		this(0);
	}

	/**
	 * Creates a new instance containing the entries of the specified collection.
	 *
	 * @param c the collection to copy entries from
	 */
	public LockFreeIntHashSet(Collection<Integer> c)
	{
		this(c.size());
		addAll(c);
	}

	@Override
	public void clear()
	{
		table = new Table(0);
	}

	@Override
	public boolean contains(int key)
	{
		return table.finder(key).next() != Table.NONE;
	}

	@Override
	public int size()
	{
		return table.size();
	}

	@Override
	public boolean add(int key)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(key))
			{
				for (;;)
				{
					int index = f.next();
					if (index == Table.NONE)
					{
						index = f.alloc();
						if (index == Table.RESIZE)
							break;

						if (f.insert())
							return true;

						f.restart();
					}
					else if (index == Table.RESIZE)
					{
						break;
					}
					else
					{
						return false;
					}
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	@Override
	public boolean remove(int key)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(key))
			{
				for (;;)
				{
					int index = f.next();
					if (index == Table.NONE)
					{
						return false;
					}
					else if (index == Table.RESIZE)
					{
						break;
					}
					else
					{
						if (f.remove())
							return true;

						f.restart();
					}
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	@Override
	public IntIterator iterator()
	{
		return new IntIterator()
		{
			private final Table.Iterator it = table.iterator();
			private boolean next = it.next() != Table.NONE;

			@Override
			public boolean hasNext()
			{
				return next;
			}

			@Override
			public int nextInt()
			{
				int result = it.getHashCode();
				next = it.next() != Table.NONE;
				return result;
			}
		};
	}
}
