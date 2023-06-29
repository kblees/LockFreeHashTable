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
 * Lock free ConcurrentMap implementation that supports {@code null} keys and values.
 * <p>
 * All operations are at least lock free, lookup and iteration are wait free (population oblivious).
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class LockFreeHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentNullMap<K, V>, Cloneable, Serializable
{
	private static final long serialVersionUID = 1L;

	static class Table extends LockFreeHashTable<Table>
	{
		final Object[] data;

		Table(int tabSize)
		{
			super(tabSize);
			data = new Object[capacity() << 1];
		}

		@Override
		protected void copy(Table oldTable, int oldIndex, int index)
		{
			setKey(index, oldTable.getKey(oldIndex));
			setValue(index, oldTable.getValue(oldIndex));
		}

		@Override
		protected void reset(int index)
		{
			setKey(index, null);
			setValue(index, null);
		}

		static int keyIndex(int index)
		{
			return (index << 1);
		}

		static int valueIndex(int index)
		{
			return (index << 1) + 1;
		}

		Object getKey(int index)
		{
			return data[keyIndex(index)];
		}

		void setKey(int index, Object key)
		{
			data[keyIndex(index)] = key;
		}

		Object getValue(int index)
		{
			return data[valueIndex(index)];
		}

		void setValue(int index, Object value)
		{
			data[valueIndex(index)] = value;
		}
	}

	@SuppressWarnings({ "rawtypes" })
	private static final Cas<LockFreeHashMap, Table> TABLE = Cas.create(LockFreeHashMap.class, Table.class, "table");

	private volatile Table table;

	public LockFreeHashMap()
	{
		table = new Table(0);
	}

	public LockFreeHashMap(int initialCapacity)
	{
		table = new Table(initialCapacity + (initialCapacity >>> 4));
	}

	public LockFreeHashMap(Map<? extends K, ? extends V> map)
	{
		this(map.size());
		putAll(map);
	}

	protected int hash(Object key)
	{
		return Objects.hashCode(key);
	}

	protected boolean equals(Object o1, Object o2)
	{
		return Objects.equals(o1, o2);
	}

	@SuppressWarnings("unchecked")
	private K K(Object o)
	{
		return (K) o;
	}

	@SuppressWarnings("unchecked")
	private V V(Object o)
	{
		return (V) o;
	}

	@Override
	public Set<Entry<K, V>> entrySet()
	{
		return new EntrySet();
	}

	@Override
	public int size()
	{
		return table.size();
	}

	@Override
	public void clear()
	{
		table = new Table(0);
	}

	@Override
	public boolean containsKey(Object key)
	{
		Table t = table;
		Table.Finder f = t.finder(hash(key));
		for (int index = f.next(); index != Table.NONE; index = f.next())
		{
			Object k = t.getKey(index);
			if (k == TOMBSTONE)
				f.reload();
			else if (equals(key, k))
				return true;
		}
		return false;
	}

	@Override
	public V get(Object key)
	{
		Table t = table;
		Table.Finder f = t.finder(hash(key));
		for (int index = f.next(); index != Table.NONE; index = f.next())
		{
			Object k = t.getKey(index);
			Object v = t.getValue(index);
			if (k == TOMBSTONE || v == TOMBSTONE)
				f.reload();
			else if (equals(key, k))
				return V(v);
		}
		return null;
	}

	@Override
	public Map.Entry<K, V> getEntry(Object key)
	{
		Table t = table;
		Table.Finder f = t.finder(hash(key));
		for (int index = f.next(); index != Table.NONE; index = f.next())
		{
			Object k = t.getKey(index);
			Object v = t.getValue(index);
			if (k == TOMBSTONE || v == TOMBSTONE)
				f.reload();
			else if (equals(key, k))
				return new MapEntry(k, v);
		}
		return null;
	}

	private static enum Mode
	{
		IF_PRESENT, IF_ABSENT, ALWAYS
	}

	@Override
	public V put(K key, V value)
	{
		return V(putImpl(key, value, Mode.ALWAYS));
	}

	@Override
	public V putIfAbsent(K key, V value)
	{
		return V(putImpl(key, value, Mode.IF_ABSENT));
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		return putImpl(key, newValue, oldValue) != TOMBSTONE;
	}

	@Override
	public V replace(K key, V value)
	{
		Object v = putImpl(key, value, Mode.IF_PRESENT);
		if (v == TOMBSTONE)
			return null;
		return V(v);
	}

	private Object putImpl(K key, V value, Object mode)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(hash(key)))
			{
				for (;;)
				{
					int index = f.next();
					if (index == Table.RESIZE)
						break;

					Object v = null;
					if (index == Table.NONE && mode != Mode.IF_ABSENT && mode != Mode.ALWAYS)
						return TOMBSTONE;
					if (index != Table.NONE)
					{
						// potential match, check key
						Object k = t.getKey(index);
						v = t.getValue(index);
						if (k == TOMBSTONE || v == TOMBSTONE || !equals(key, k))
							continue;

						if (!(mode instanceof Mode) && !equals(mode, v))
							return TOMBSTONE;
						if (mode == Mode.IF_ABSENT || v == value)
							return v;
					}

					int newIndex = f.alloc();
					if (newIndex == Table.RESIZE)
						break;

					t.setKey(newIndex, key);
					t.setValue(newIndex, value);
					if (index == Table.NONE ? f.insert() : f.replace())
						return v;

					f.restart();
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	@Override
	public boolean remove(Object key, Object value)
	{
		return removeImpl(key, value) != TOMBSTONE;
	}

	@Override
	public V remove(Object key)
	{
		Object v = removeImpl(key, Mode.ALWAYS);
		if (v == TOMBSTONE)
			return null;
		return V(v);
	}

	private Object removeImpl(Object key, Object value)
	{
		for (;;)
		{
			Table t = table;
			try (Table.Updater f = t.updater(hash(key)))
			{
				for (;;)
				{
					int index = f.next();
					if (index == Table.RESIZE)
						break;

					if (index == Table.NONE)
						return TOMBSTONE;

					Object k = t.getKey(index);
					Object v = t.getValue(index);
					if (k == TOMBSTONE || v == TOMBSTONE || !equals(key, k))
						continue;

					if (value != Mode.ALWAYS && !equals(value, v))
						return TOMBSTONE;

					if (f.remove())
					{
						t.setKey(index, TOMBSTONE);
						t.setValue(index, TOMBSTONE);
						return v;
					}
					f.restart();
				}
			}

			TABLE.cas(this, t, (Table) t.resize());
		}
	}

	class MapEntry implements Entry<K, V>
	{
		private K key;
		private V value;

		MapEntry(Object k, Object v)
		{
			key = K(k);
			value = V(v);
		}

		@Override
		public K getKey()
		{
			return key;
		}

		@Override
		public V getValue()
		{
			return value;
		}

		@Override
		public V setValue(V value)
		{
			this.value = value;
			return LockFreeHashMap.this.put(key, value);
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(key) ^ Objects.hashCode(value);
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (!(obj instanceof Entry))
				return false;
			Entry<?, ?> e = (Entry<?, ?>) obj;
			return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
		}

		@Override
		public String toString()
		{
			return key + "=" + value;
		}
	}

	class Iter implements Iterator<Entry<K, V>>
	{
		private final Table table = LockFreeHashMap.this.table;
		private Table.Iterator iter;
		private MapEntry current;
		private MapEntry next;

		private void findNext()
		{
			for (;;)
			{
				int index = iter.next();
				if (index == Table.NONE)
				{
					next = null;
					return;
				}
				Object k = table.getKey(index);
				Object v = table.getValue(index);
				if (k == TOMBSTONE || v == TOMBSTONE)
					continue;
				next = new MapEntry(k, v);
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
			return next != null;
		}

		@Override
		public Entry<K, V> next()
		{
			if (!hasNext())
				throw new NoSuchElementException();
			current = next;
			findNext();
			return current;
		}

		@Override
		public void remove()
		{
			if (current == null)
				throw new IllegalStateException();
			LockFreeHashMap.this.remove(current.key);
			current = null;
		}
	}

	class EntrySet extends AbstractSet<Entry<K, V>>
	{
		@Override
		public Iterator<Entry<K, V>> iterator()
		{
			return new Iter();
		}

		@Override
		public int size()
		{
			return table.size();
		}

		@Override
		public void clear()
		{
			LockFreeHashMap.this.clear();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object clone()
	{
		try
		{
			LockFreeHashMap<K, V> copy = (LockFreeHashMap<K, V>) super.clone();
			copy.table = new Table(table.capacity());
			copy.putAll(this);
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
		for (Entry<K, V> e : entrySet())
		{
			s.writeObject(e.getKey());
			s.writeObject(e.getValue());
		}
		s.writeObject(TOMBSTONE);
	}

	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException
	{
		table = new Table(s.readInt());
		for (;;)
		{
			Object k = s.readObject();
			if (k == TOMBSTONE)
				break;
			Object v = s.readObject();
			put(K(k), V(v));
		}
	}
}
