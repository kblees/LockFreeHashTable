/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */

package org.kblees.hashtable;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.*;

/**
 * Tests LockFreeHashTable with multiple threads and large data.
 */
public class LockFreeHashTableTest
{
	@Test
	public void testConcurrentSet()
	{
		int threads = 8;
		int end = 1 << 24;
		for (double size = 16; size < end; size *= 1.618)
			testConcurrentSet((int) size, threads);
	}

	private void testConcurrentSet(int size, int threads)
	{
		Object[] keys = KeyType.Int.create(BenchUtils.generateInts(size, true));
		Set<Object> set = new LockFreeHashSet<>();
		TaskExecutor executor = new TaskExecutor();

		for (int thread = 0; thread < threads; thread++)
		{
			int start = thread;
			executor.schedule(() -> {
				for (int i = start; i < size; i += threads)
					assertTrue("add(" + keys[i] + ") != true", set.add(keys[i]));
				for (int i = start; i < size; i += threads)
					assertTrue("contains(" + keys[i] + ") != true", set.contains(keys[i]));
				for (int i = start; i < size; i += threads)
					assertFalse("add(" + keys[i] + ") != false", set.add(keys[i]));
				Set<Object> copy = new HashSet<>(set);
				for (int i = start; i < size; i += threads)
					assertTrue("iterator() is missing " + keys[i], copy.contains(keys[i]));
				for (int i = start; i < size; i += threads)
					assertTrue("remove(" + keys[i] + ") != true", set.remove(keys[i]));
				for (int i = start; i < size; i += threads)
					assertFalse("contains(" + keys[i] + ") != false", set.contains(keys[i]));
				copy = new HashSet<>(set);
				for (int i = start; i < size; i += threads)
					assertFalse("iterator() contains " + keys[i], copy.contains(keys[i]));
				for (int i = start; i < size; i += threads)
					assertTrue("add(" + keys[i] + ") != true", set.add(keys[i]));
			});
		}

		executor.join();

		assertEquals(keys.length, set.size());
		for (int i = 0; i < keys.length; i++)
			assertTrue(set.contains(keys[i]));

		Set<Object> set2 = new HashSet<>();
		for (Object obj : set)
			set2.add(obj);
		assertEquals(keys.length, set2.size());
		for (int i = 0; i < keys.length; i++)
			assertTrue(set2.contains(keys[i]));
	}

	@Test
	public void testConcurrentMap()
	{
		int threads = 8;
		int end = 1 << 24;
		for (double size = 16; size < end; size *= 1.618)
			testConcurrentMap((int) size, threads);
	}

	private void testConcurrentMap(int size, int threads)
	{
		Object[] keys = KeyType.Int.create(BenchUtils.generateInts(size, true));
		Map<Object, Object> map = new LockFreeHashMap<>();
		TaskExecutor executor = new TaskExecutor();

		for (int thread = 0; thread < threads; thread++)
		{
			int start = thread;
			executor.schedule(() -> {
				for (int i = start; i < size; i += threads)
					assertNull("put(" + keys[i] + ") != null", map.put(keys[i], Boolean.FALSE));
				for (int i = start; i < size; i += threads)
					assertEquals("get(" + keys[i] + ") != FALSE", Boolean.FALSE, map.get(keys[i]));
				for (int i = start; i < size; i += threads)
					assertTrue("containsKey(" + keys[i] + ") != true", map.containsKey(keys[i]));
				for (int i = start; i < size; i += threads)
					assertEquals("put(" + keys[i] + ") != FALSE", Boolean.FALSE, map.put(keys[i], Boolean.TRUE));
				Map<Object, Object> copy = new HashMap<>(map);
				for (int i = start; i < size; i += threads)
					assertEquals("iterator() is missing " + keys[i], Boolean.TRUE, copy.get(keys[i]));
				for (int i = start; i < size; i += threads)
					assertEquals("remove(" + keys[i] + ") != TRUE", Boolean.TRUE, map.remove(keys[i]));
				for (int i = start; i < size; i += threads)
					assertFalse("containsKey(" + keys[i] + ") != false", map.containsKey(keys[i]));
				copy = new HashMap<>(map);
				for (int i = start; i < size; i += threads)
					assertFalse("iterator() contains " + keys[i], copy.containsKey(keys[i]));
				for (int i = start; i < size; i += threads)
					assertNull("put(" + keys[i] + ") != null", map.put(keys[i], Boolean.FALSE));
			});
		}

		executor.join();

		assertEquals(keys.length, map.size());
		for (int i = 0; i < keys.length; i++)
			assertTrue(map.containsKey(keys[i]));

		Map<Object, Object> map2 = new HashMap<>();
		for (Map.Entry<Object, Object> entry : map.entrySet())
			map2.put(entry.getKey(), entry.getValue());
		assertEquals(keys.length, map2.size());
		for (int i = 0; i < keys.length; i++)
			assertTrue(map2.containsKey(keys[i]));
	}

	/**
	 * Executes multiple tasks in parallel.
	 */
	public static class TaskExecutor
	{
		private ExecutorService executor = Executors.newCachedThreadPool();

		private List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

		private CountDownLatch latch = new CountDownLatch(1);

		public static interface Task
		{
			void run() throws Exception;
		}

		public void schedule(Task task)
		{
			executor.execute(() -> {
				try
				{
					latch.await();
					task.run();
				}
				catch (Throwable t)
				{
					exceptions.add(t);
				}
			});
		}

		public void join()
		{
			try
			{
				latch.countDown();
				executor.shutdown();
				executor.awaitTermination(1, TimeUnit.HOURS);
			}
			catch (Throwable t)
			{
				exceptions.add(t);
			}
			if (!exceptions.isEmpty())
				throw new RuntimeException("Exception in tasks: " + exceptions.toString(), exceptions.get(0));
		}
	}
}
