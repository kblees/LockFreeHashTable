/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import static org.kblees.hashtable.core.Sentinels.TOMBSTONE;

import java.io.*;
import java.util.*;

import org.kblees.hashtable.core.*;

/**
 * Lock free Set implementation.
 * <p>
 * All operations are at least lock free, lookup and iteration are wait free (population oblivious).
 *
 * @param <E> the element type
 */
public class LockFreeHashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, Serializable
{
	private static final long serialVersionUID = 1L;

	static class Table extends LockFreeHashTable<Table>
	{
		final Object[] entries;

		Table(int tabSize)
		{
			super(tabSize);
			entries = new Object[capacity()];
		}

		@Override
		protected void copy(Table oldTable, int oldIndex, int index)
		{
			entries[index] = oldTable.entries[oldIndex];
		}

		@Override
		protected void reset(int index)
		{
			entries[index] = null;
		}
	}

	@SuppressWarnings("rawtypes")
	private static final Cas<LockFreeHashSet, Table> TABLE = Cas.create(LockFreeHashSet.class, Table.class, "table");

	private volatile Table table;

	/**
	 * Creates a new, empty instance.
	 */
	public LockFreeHashSet()
	{
		this(0);
	}

	/**
	 * Creates a new instance.
	 *
	 * @param initialCapacity initial capacity of the set, i.e. number of entries the set can hold before resizing. Note:
	 * this is different from Java's HashSet, where you need to specify the desired capacity divided by load factor!
	 */
	public LockFreeHashSet(int initialCapacity)
	{
		table = new Table(initialCapacity + (initialCapacity >>> 4));
	}

	/**
	 * Creates a new instance containing the entries of the specified collection.
	 *
	 * @param c the collection to copy entries from
	 */
	public LockFreeHashSet(Collection<? extends E> c)
	{
		this(c.size());
		addAll(c);
	}

	/**
	 * Override to customize hash code calculation (e.g. use a good integer hash function for numeric types).
	 *
	 * @param e the entry to calculate the hash code for
	 * @return hash code
	 */
	protected int hash(Object e)
	{
		return Objects.hashCode(e);
	}

	protected boolean equals(Object a, Object b)
	{
		return Objects.equals(a, b);
	}

	/** {@inheritDoc} */
	public Iterator<E> iterator()
	{
		return new Iter();
	}

	class Iter implements Iterator<E>
	{
		private final Table table = LockFreeHashSet.this.table;
		private Table.Iterator iter;
		private Object current = TOMBSTONE;
		private Object next;

		private void findNext()
		{
			for (;;)
			{
				int index = iter.next();
				if (index == Table.NONE)
				{
					next = TOMBSTONE;
					return;
				}
				next = table.entries[index];
				if (next != TOMBSTONE)
					return;
			}
		}

		@Override
		public boolean hasNext()
		{
			if (iter == null)
			{
				iter = table.iterator();
				findNext();
			}
			return next != TOMBSTONE;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E next()
		{
			if (!hasNext())
				throw new NoSuchElementException();
			current = next;
			findNext();
			return (E) current;
		}

		@Override
		public void remove()
		{
			if (current == TOMBSTONE)
				throw new IllegalStateException();
			LockFreeHashSet.this.remove(current);
			current = TOMBSTONE;
		}
	}

	/** {@inheritDoc} */
	public int size()
	{
		return table.size();
	}

	/** {@inheritDoc} */
	public boolean contains(Object o)
	{
		Table t = table;
		Table.Finder f = t.finder(hash(o));
		for (int index = f.next(); index != Table.NONE; index = f.next())
		{
			if (equals(o, t.entries[index]))
				return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	public boolean add(E e)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(hash(e)))
			{
				for (;;)
				{
					int index = f.next();
					if (index == Table.NONE)
					{
						index = f.alloc();
						if (index == Table.RESIZE)
							break;

						t.entries[index] = e;
						if (f.insert())
							return true;

						f.restart();
					}
					else if (index == Table.RESIZE)
					{
						break;
					}
					else if (equals(e, t.entries[index]))
					{
						return false;
					}
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	/** {@inheritDoc} */
	public boolean remove(Object o)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(hash(o)))
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
					else if (equals(o, t.entries[index]))
					{
						if (f.remove())
						{
							t.reset(index);
							return true;
						}
						f.restart();
					}
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	/** {@inheritDoc} */
	public void clear()
	{
		table = new Table(0);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object clone()
	{
		try
		{
			LockFreeHashSet<E> copy = (LockFreeHashSet<E>) super.clone();
			copy.table = new Table(table.capacity());
			copy.addAll(this);
			return copy;
		}
		catch (CloneNotSupportedException e)
		{
			throw new InternalError(e);
		}
	}

	private void writeObject(ObjectOutputStream s) throws IOException
	{
		s.writeInt(table.capacity());
		for (E e : this)
			s.writeObject(e);
		s.writeObject(TOMBSTONE);
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException
	{
		table = new Table(s.readInt());
		for (;;)
		{
			Object e = s.readObject();
			if (e == TOMBSTONE)
				break;
			add((E) e);
		}
	}
}
