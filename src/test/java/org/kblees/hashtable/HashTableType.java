/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for different hash table implementations.
 */
public enum HashTableType
{

	Concurrent
	{
		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new ConcurrentHashMap<>(capacity);
		}
	},

	LockFree
	{
		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new LockFreeHashMap<>(capacity);
		}

		@Override
		public Set<Object> createSet(int capacity)
		{
			return new LockFreeHashSet<>(capacity);
		}
	},

	ConcurrentV7
	{
		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new ConcurrentHashMapV7<>(capacity);
		}
	},

	NonBlocking
	{
		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new org.cliffc.high_scale_lib.NonBlockingHashMap<>(capacity);
		}

		@Override
		public Set<Object> createSet(int capacity)
		{
			return new org.cliffc.high_scale_lib.NonBlockingHashSet<Object>();
		}
	},

	FastUtil
	{
		@Override
		public boolean isUpdateThreadSafe()
		{
			return false;
		}

		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<Object, Object>(capacity);
		}

		@Override
		public Set<Object> createSet(int capacity)
		{
			return new it.unimi.dsi.fastutil.objects.ObjectOpenHashSet<Object>(capacity);
		}
	},

	Koloboke
	{
		@Override
		public boolean isUpdateThreadSafe()
		{
			return false;
		}

		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return com.koloboke.collect.map.hash.HashObjObjMaps.newMutableMap(capacity);
		}

		@Override
		public Set<Object> createSet(int capacity)
		{
			return com.koloboke.collect.set.hash.HashObjSets.newMutableSet(capacity);
		}
	},

	Smoothie
	{
		@Override
		public boolean isUpdateThreadSafe()
		{
			return false;
		}

		@Override
		public boolean isLookupThreadSafe()
		{
			return false;
		}

		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return io.timeandspace.smoothie.SmoothieMap.newBuilder().build();
		}
	},

	SwissTable
	{
		@Override
		public boolean isUpdateThreadSafe()
		{
			return false;
		}

		@Override
		public Map<Object, Object> createMap(int capacity)
		{
			return new io.timeandspace.smoothie.SwissTable<>(Math.max(capacity, 10));
		}

		@Override
		public Set<Object> createSet(int capacity)
		{
			// the SwissTable implementation is *very* incomplete, create a minimum
			// Set implementation just to run the benchmarks (note:
			// Collections.newSetFromMap does not work as SwissTable.containsKey is
			// not implemented).
			return new AbstractSet<Object>()
			{
				private final Map<Object, Object> m = createMap(capacity);

				public int size()
				{
					return m.size();
				}

				public boolean contains(Object o)
				{
					return m.get(o) == Boolean.TRUE;
				}

				public boolean add(Object e)
				{
					return m.put(e, Boolean.TRUE) == null;
				}

				public Iterator<Object> iterator()
				{
					throw new UnsupportedOperationException();
				}
			};
		}
	};

	/**
	 * @return {@code true} if the implementation supports thread safe updates
	 */
	public boolean isUpdateThreadSafe()
	{
		return true;
	}

	/**
	 * @return {@code true} if the implementation supports thread safe lookups
	 */
	public boolean isLookupThreadSafe()
	{
		return true;
	}

	/**
	 * Creates a new Map with the specified initial capacity.
	 *
	 * @param capacity the initial capacity
	 * @return new Map
	 */
	public abstract Map<Object, Object> createMap(int capacity);

	/**
	 * Creates a new Set with the specified initial capacity.
	 *
	 * @param capacity the initial capacity
	 * @return new Set
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Set<Object> createSet(int capacity)
	{
		return Collections.newSetFromMap((Map) createMap(capacity));
	}
}
