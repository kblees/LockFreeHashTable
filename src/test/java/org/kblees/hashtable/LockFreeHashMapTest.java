/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.Test;

/**
 * Generic functional test for LockFreeHashMap based on Guava testlib.
 */
public class LockFreeHashMapTest
{
	public static Test suite()
	{
		return MapTestSuiteBuilder.using(new TestStringMapGenerator()
		{
			@Override
			protected Map<String, String> create(Entry<String, String>[] entries)
			{
				Map<String, String> map = new LockFreeHashMap<>(entries.length);
				for (Entry<String, String> entry : entries)
					map.put(entry.getKey(), entry.getValue());
				return map;
			}
		}).named("LockFreeHashMap")
				.withFeatures(MapFeature.GENERAL_PURPOSE, MapFeature.ALLOWS_NULL_KEYS, MapFeature.ALLOWS_NULL_VALUES,
						CollectionFeature.SUPPORTS_ITERATOR_REMOVE, CollectionFeature.SERIALIZABLE, CollectionSize.ANY)
				.createTestSuite();
	}
}
