/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.Set;

import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.TestStringSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.SetFeature;

import junit.framework.Test;

/**
 * Generic functional test for LockFreeHashSet based on Guava testlib.
 */
public class LockFreeHashSetTest
{

	public static Test suite()
	{
		return SetTestSuiteBuilder.using(new TestStringSetGenerator()
		{
			@Override
			protected Set<String> create(String[] elements)
			{
				Set<String> set = new LockFreeHashSet<>(elements.length);
				for (String s : elements)
					set.add(s);
				return set;
			}
		}).named("LockFreeHashSet").withFeatures(SetFeature.GENERAL_PURPOSE, CollectionFeature.ALLOWS_NULL_VALUES,
				CollectionFeature.SERIALIZABLE, CollectionSize.ANY).createTestSuite();
	}
}
