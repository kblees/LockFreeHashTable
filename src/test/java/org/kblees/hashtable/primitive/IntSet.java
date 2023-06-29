/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.Iterator;
import java.util.Set;

/**
 * A Set of primitive ints.
 */
public interface IntSet extends Set<Integer>
{
	boolean contains(int key);

	@Override
	IntIterator iterator();

	interface IntIterator extends Iterator<Integer>
	{
		int nextInt();

		@Override
		default Integer next()
		{
			return Integer.valueOf(nextInt());
		}
	}

	boolean add(int key);

	boolean remove(int key);

	default int[] toArray(int[] a)
	{
		int sz = size();
		if (a == null || a.length < sz)
			a = new int[sz];

		IntIterator it = iterator();
		for (int i = 0; i < sz && it.hasNext(); i++)
			a[i] = it.nextInt();
		return a;
	}
}
