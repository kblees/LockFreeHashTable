/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import sun.misc.Unsafe;

/**
 * Atomic array operations.
 * <p>
 * Uses {@code sun.misc.Unsafe} (before Java 9) or
 * {@code java.lang.invoke.VarHandle} (since Java 9).
 */
public class AtomicUtils
{
	private static final Atomics ATOMICS = createAtomics();

	private static Atomics createAtomics()
	{
		try
		{
			Class.forName("java.lang.invoke.VarHandle");
			return new VarHandleAtomics();
		}
		catch (ClassNotFoundException ex)
		{
			return new UnsafeAtomics();
		}
	}

	/**
	 * Cannot be instantiated.
	 */
	private AtomicUtils()
	{
	}

	public static int get(int array[], int i)
	{
		return ATOMICS.get(array, i);
	}

	public static int getVolatile(int array[], int i)
	{
		return ATOMICS.getVolatile(array, i);
	}

	public static void set(int array[], int i, int value)
	{
		ATOMICS.set(array, i, value);
	}

	public static void setVolatile(int array[], int i, int value)
	{
		ATOMICS.setVolatile(array, i, value);
	}

	public static boolean cas(int array[], int i, int expect, int update)
	{
		return ATOMICS.cas(array, i, expect, update);
	}

	public static long get(long array[], int i)
	{
		return ATOMICS.get(array, i);
	}

	public static long getVolatile(long array[], int i)
	{
		return ATOMICS.getVolatile(array, i);
	}

	public static void set(long array[], int i, long value)
	{
		ATOMICS.set(array, i, value);
	}

	public static void setVolatile(long array[], int i, long value)
	{
		ATOMICS.setVolatile(array, i, value);
	}

	public static boolean cas(long array[], int i, long expect, long update)
	{
		return ATOMICS.cas(array, i, expect, update);
	}

	public static long add(long array[], int i, long value)
	{
		return ATOMICS.add(array, i, value);
	}

	public static Object get(Object array[], int i)
	{
		return ATOMICS.get(array, i);
	}

	public static Object getVolatile(Object array[], int i)
	{
		return ATOMICS.getVolatile(array, i);
	}

	public static void set(Object array[], int i, Object value)
	{
		ATOMICS.set(array, i, value);
	}

	public static void setVolatile(Object array[], int i, Object value)
	{
		ATOMICS.setVolatile(array, i, value);
	}

	public static boolean cas(Object array[], int i, Object expect, Object update)
	{
		return ATOMICS.cas(array, i, expect, update);
	}

	public static <T,V> Cas<T,V> createCas(Class<T> type, Class<V> fieldType, String fieldName)
	{
		return ATOMICS.createCas(type, fieldType, fieldName);
	}

	interface Atomics
	{
		<T,V> Cas<T,V> createCas(Class<T> type, Class<V> fieldType, String fieldName);

		int get(int array[], int i);

		int getVolatile(int array[], int i);

		void set(int array[], int i, int value);

		void setVolatile(int array[], int i, int value);

		boolean cas(int array[], int i, int expect, int update);

		long get(long array[], int i);

		long getVolatile(long array[], int i);

		void set(long array[], int i, long value);

		void setVolatile(long array[], int i, long value);

		boolean cas(long array[], int i, long expect, long update);

		long add(long array[], int i, long value);

		Object get(Object array[], int i);

		Object getVolatile(Object array[], int i);

		void set(Object array[], int i, Object value);

		void setVolatile(Object array[], int i, Object value);

		boolean cas(Object array[], int i, Object expect, Object update);
	}

	static class UnsafeAtomics implements Atomics
	{
		private static final Unsafe UNSAFE;

		private static final int INT_BASE;

		private static final int INT_SHIFT;

		private static final int LONG_BASE;

		private static final int LONG_SHIFT;

		private static final int OBJ_BASE;

		private static final int OBJ_SHIFT;

		static
		{
			try
			{
				Field f = Unsafe.class.getDeclaredField("theUnsafe");
				f.setAccessible(true);
				UNSAFE = (Unsafe) f.get(null);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}

			INT_BASE = UNSAFE.arrayBaseOffset(int[].class);
			int scale = UNSAFE.arrayIndexScale(int[].class);
			if ((scale & (scale - 1)) != 0)
				throw new InternalError("Integer size is not a power of two.");
			INT_SHIFT = Integer.numberOfTrailingZeros(scale);

			LONG_BASE = UNSAFE.arrayBaseOffset(long[].class);
			scale = UNSAFE.arrayIndexScale(long[].class);
			if ((scale & (scale - 1)) != 0)
				throw new InternalError("Long size is not a power of two.");
			LONG_SHIFT = Integer.numberOfTrailingZeros(scale);

			OBJ_BASE = UNSAFE.arrayBaseOffset(Object[].class);
			scale = UNSAFE.arrayIndexScale(Object[].class);
			if ((scale & (scale - 1)) != 0)
				throw new InternalError("Object size is not a power of two.");
			OBJ_SHIFT = Integer.numberOfTrailingZeros(scale);
		}

		@Override
		public <T, V> Cas<T, V> createCas(Class<T> type, Class<V> fieldType, String fieldName)
		{
			AtomicReferenceFieldUpdater<T, V> updater = AtomicReferenceFieldUpdater.newUpdater(type, fieldType, fieldName);
			return (obj, expect, update) -> updater.compareAndSet(obj, expect, update);
		}

		private static long rawIndex(int array[], int i)
		{
			return INT_BASE + ((long) i << INT_SHIFT);
		}

		@Override
		public int get(int array[], int i)
		{
			return UNSAFE.getIntVolatile(array, rawIndex(array, i));
		}

		@Override
		public int getVolatile(int[] array, int i)
		{
			return UNSAFE.getIntVolatile(array, rawIndex(array, i));
		}

		@Override
		public void set(int array[], int i, int value)
		{
			UNSAFE.putInt(array, rawIndex(array, i), value);
		}

		@Override
		public void setVolatile(int[] array, int i, int value)
		{
			UNSAFE.putIntVolatile(array, rawIndex(array, i), value);
		}

		@Override
		public boolean cas(int array[], int i, int expect, int update)
		{
			return UNSAFE.compareAndSwapInt(array, rawIndex(array, i), expect, update);
		}

		private long rawIndex(long array[], int i)
		{
			return LONG_BASE + ((long) i << LONG_SHIFT);
		}

		@Override
		public long get(long array[], int i)
		{
			return UNSAFE.getLong(array, rawIndex(array, i));
		}

		@Override
		public long getVolatile(long array[], int i)
		{
			return UNSAFE.getLongVolatile(array, rawIndex(array, i));
		}

		@Override
		public void set(long array[], int i, long value)
		{
			UNSAFE.putLong(array, rawIndex(array, i), value);
		}

		@Override
		public void setVolatile(long array[], int i, long value)
		{
			UNSAFE.putLongVolatile(array, rawIndex(array, i), value);
		}

		@Override
		public boolean cas(long array[], int i, long expect, long update)
		{
			return UNSAFE.compareAndSwapLong(array, rawIndex(array, i), expect, update);
		}

		@Override
		public long add(long array[], int i, long value)
		{
			return UNSAFE.getAndAddLong(array, rawIndex(array, i), value);
		}

		private long rawIndex(Object array[], int i)
		{
			return OBJ_BASE + ((long) i << OBJ_SHIFT);
		}

		@Override
		public Object get(Object array[], int i)
		{
			return UNSAFE.getObject(array, rawIndex(array, i));
		}

		@Override
		public Object getVolatile(Object array[], int i)
		{
			return UNSAFE.getObjectVolatile(array, rawIndex(array, i));
		}

		@Override
		public void set(Object array[], int i, Object value)
		{
			UNSAFE.putObject(array, rawIndex(array, i), value);
		}

		@Override
		public void setVolatile(Object array[], int i, Object value)
		{
			UNSAFE.putObjectVolatile(array, rawIndex(array, i), value);
		}

		@Override
		public boolean cas(Object array[], int i, Object expect, Object update)
		{
			return UNSAFE.compareAndSwapObject(array, rawIndex(array, i), expect, update);
		}
	}

	static class VarHandleAtomics implements Atomics
	{
		private static final VarHandle INTS = MethodHandles.arrayElementVarHandle(int[].class);
		private static final VarHandle LONGS = MethodHandles.arrayElementVarHandle(long[].class);
		private static final VarHandle OBJECTS = MethodHandles.arrayElementVarHandle(Object[].class);

		@Override
		public <T, V> Cas<T, V> createCas(Class<T> type, Class<V> fieldType, String fieldName)
		{
			try
			{
				VarHandle vh = MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findVarHandle(type, fieldName, fieldType);
				return (T obj, V expect, V update) -> vh.compareAndSet(obj, expect, update);
			}
			catch (Exception ex)
			{
				throw new RuntimeException(ex);
			}
		}

		@Override
		public int get(int[] array, int i)
		{
			return (int) INTS.get(array, i);
		}

		@Override
		public int getVolatile(int[] array, int i)
		{
			return (int) INTS.getVolatile(array, i);
		}

		@Override
		public void set(int[] array, int i, int value)
		{
			INTS.set(array, i, value);
		}

		@Override
		public void setVolatile(int[] array, int i, int value)
		{
			INTS.setVolatile(array, i, value);
		}

		@Override
		public boolean cas(int[] array, int i, int expect, int update)
		{
			return INTS.compareAndSet(array, i, expect, update);
		}

		@Override
		public long get(long[] array, int i)
		{
			return (long) LONGS.get(array, i);
		}

		@Override
		public long getVolatile(long[] array, int i)
		{
			return (long) LONGS.getVolatile(array, i);
		}

		@Override
		public void set(long[] array, int i, long value)
		{
			LONGS.set(array, i, value);
		}

		@Override
		public void setVolatile(long[] array, int i, long value)
		{
			LONGS.setVolatile(array, i, value);
		}

		@Override
		public boolean cas(long[] array, int i, long expect, long update)
		{
			return LONGS.compareAndSet(array, i, expect, update);
		}

		@Override
		public long add(long[] array, int i, long value)
		{
			return (long) LONGS.getAndAdd(array, i, value);
		}

		@Override
		public Object get(Object[] array, int i)
		{
			return OBJECTS.get(array, i);
		}

		@Override
		public Object getVolatile(Object[] array, int i)
		{
			return OBJECTS.getVolatile(array, i);
		}

		@Override
		public void set(Object[] array, int i, Object value)
		{
			OBJECTS.set(array, i, value);
		}

		@Override
		public void setVolatile(Object[] array, int i, Object value)
		{
			OBJECTS.setVolatile(array, i, value);
		}

		@Override
		public boolean cas(Object[] array, int i, Object expect, Object update)
		{
			return OBJECTS.compareAndSet(array, i, expect, update);
		}
	}
}
