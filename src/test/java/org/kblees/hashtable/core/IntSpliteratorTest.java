/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests IntSpliterator.
 */
public class IntSpliteratorTest
{

	private static final int[] SIZES = { 0, 1, 2, 131, 1 << 20 };
	private static final int[] STARTS = SIZES;

	private static int getIndex(long range)
	{
		return (int) (range >>> 56);
	}

	private static int getEnd(long range)
	{
		return (int) (range >>> 28) & 0xfffffff;
	}

	@Test
	public void testSimple()
	{
		for (int parallel = 1; parallel < 3; parallel++)
		{
			for (int sizeIdx = 0; sizeIdx < SIZES.length; sizeIdx++)
			{
				int size = SIZES[sizeIdx];
				for (int startIdx = 0; startIdx < STARTS.length; startIdx++)
				{
					int start = STARTS[startIdx];
					int end = start + size;
					if (end >= IntSpliterator.MAX_INT)
						break;

					IntSpliterator split = new IntSpliterator(start, end, parallel);
					long v = IntSpliterator.NONE;
					for (int i = start; i < end; i++)
					{
						v = split.next(v);
						assertEquals("start=" + start + ", end=" + end, 0, getIndex(v));
						assertEquals("start=" + start + ", end=" + end, 0, getEnd(v));
						assertEquals("start=" + start + ", end=" + end, i, (int) v);
					}
					assertEquals("start=" + start + ", end=" + end, IntSpliterator.NONE, split.next(v));
				}
			}
		}
	}

	@Test
	public void testSplit()
	{
		IntSpliterator split = new IntSpliterator(0, 10, 2);
		long v1 = IntSpliterator.NONE;
		long v2 = IntSpliterator.NONE;
		long v3 = IntSpliterator.NONE;

		assertEquals(0, (int) (v1 = split.next(v1)));
		assertEquals(1, (int) (v1 = split.next(v1)));
		assertEquals(2, (int) (v1 = split.next(v1)));
		assertEquals(3, (int) (v1 = split.next(v1)));

		assertEquals(7, (int) (v2 = split.next(v2)));
		assertEquals(8, (int) (v2 = split.next(v2)));

		assertEquals(3, (int) (v3 = split.next(v3)));
		assertEquals(4, (int) (v3 = split.next(v3)));
		assertEquals(5, (int) (v3 = split.next(v3)));

		assertEquals(5, (int) (v1 = split.next(v1)));

		assertEquals(9, (int) (v2 = split.next(v2)));
		assertEquals(6, (int) (v2 = split.next(v2)));

		assertEquals(6, (int) (v1 = split.next(v1)));
		assertEquals(6, (int) (v3 = split.next(v3)));

		assertEquals(IntSpliterator.NONE, v1 = split.next(v1));
		assertEquals(IntSpliterator.NONE, v2 = split.next(v2));
		assertEquals(IntSpliterator.NONE, v3 = split.next(v3));
	}
}
