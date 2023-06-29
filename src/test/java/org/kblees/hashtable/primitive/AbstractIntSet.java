/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.AbstractSet;

/**
 * Abstract set of Integers that maps some methods to unboxed IntSet equivalents. 
 */
public abstract class AbstractIntSet extends AbstractSet<Integer> implements IntSet
{
	@Override
	public boolean contains(Object key)
	{
		return contains(((Integer) key).intValue());
	}

	@Override
	public boolean add(Integer key)
	{
		return add(key.intValue());
	}

	@Override
	public boolean remove(Object key)
	{
		return remove(((Integer) key).intValue());
	}
}
