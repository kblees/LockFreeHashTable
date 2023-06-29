/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.testing.TestIntegerSetGenerator;

/**
 * TestIntegerSetGenerator that uses a supplier to create the hash set under test.
 */
public class IntSetGenerator extends TestIntegerSetGenerator
{
	private final Supplier<Set<Integer>> supplier;

	public IntSetGenerator(Supplier<Set<Integer>> supplier)
	{
		this.supplier = supplier;
	}

	@Override
	protected Set<Integer> create(Integer[] elements)
	{
		Set<Integer> set = supplier.get();
		for (Integer e : elements)
			set.add(e);
		return set;
	}
}
