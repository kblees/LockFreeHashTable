/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.features.*;

import junit.framework.Test;

/**
 * Tests RobinHoodIntHashSet.
 */
public class RobinHoodIntHashSetTest
{
	public static Test suite()
	{
		return SetTestSuiteBuilder.using(new IntSetGenerator(() -> new RobinHoodIntHashSet())).named("RobinHoodIntHashSet")
				.withFeatures(CollectionFeature.SUPPORTS_ADD, CollectionSize.ANY).createTestSuite();
	}
}
