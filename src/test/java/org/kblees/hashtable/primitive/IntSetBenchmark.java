/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.primitive;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.koloboke.collect.impl.hash.QHashIntSetFactoryImpl;
import com.koloboke.collect.set.hash.*;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Base class to benchmark sets of primitive ints.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 5, time = 10)
public class IntSetBenchmark
{
	/**
	 * Minimal interface to benchmark sets of primitive ints.
	 */
	interface MinIntSet
	{
		boolean contains(int key);
		boolean add(int key);
	}

	/**
	 * Set implementations to test.
	 */
	public enum IntSetType
	{
		SeparateChaining
		{
			@Override
			MinIntSet create(int capacity)
			{
				IntHashSet set = new IntHashSet(capacity);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},

		LockFree
		{
			@Override
			MinIntSet create(int capacity)
			{
				LockFreeIntHashSet set = new LockFreeIntHashSet(capacity);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},

		FastUtil
		{
			@Override
			MinIntSet create(int capacity)
			{
				IntOpenHashSet set = new IntOpenHashSet(capacity);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},

		Koloboke
		{
			@Override
			MinIntSet create(int capacity)
			{
				HashIntSet set = HashIntSets.newMutableSet(capacity);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},

		KolobokeQuadratic
		{
			@Override
			MinIntSet create(int capacity)
			{
				HashIntSet set = new QHashIntSetFactoryImpl().newMutableSet(capacity);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},
		
		OpenLinearFibonacci 
		{
			@Override
			MinIntSet create(int capacity)
			{
				OpenIntHashSet set = new OpenIntHashSet(capacity * 4 / 3);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},
		
		OpenLinearMixModulo 
		{
			@Override
			MinIntSet create(int capacity)
			{
				OpenIntHashSet set = new OpenIntHashSet(capacity * 4 / 3)
				{
					protected int hash(int key)
					{
						return mixModHash(key);
					};
				};
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		},

		RobinHood 
		{
			@Override
			MinIntSet create(int capacity)
			{
				RobinHoodIntHashSet set = new RobinHoodIntHashSet(capacity * 4 / 3);
				return new MinIntSet()
				{
					public boolean add(int key)
					{
						return set.add(key);
					};

					public boolean contains(int key)
					{
						return set.contains(key);
					};
				};
			}
		};

		abstract MinIntSet create(int capacity);
	}

	@Param({"SeparateChaining", "LockFree", "FastUtil", "Koloboke", "OpenLinearFibonacci", "OpenLinearMixModulo", "RobinHood"})
	IntSetType type;

	@Param({ "true", "false" })
	boolean randomKeys;

	@Param({ "1024", "1149", "1290", "1448", "1625", "1825", //
		"2048", "2299", "2580", "2896", "3251", "3649", //
		"4096", "4598", "5161", "5793", "6502", "7298", //
		"8192", "9195", "10321", "11585", "13004", "14596", "16384", //
		"1024k", "1149k", "1290k", "1448k", "1625k", "1825k", //
		"2048k", "2299k", "2580k", "2896k", "3251k", "3649k", //
		"4096k", "4598k", "5161k", "5793k", "6502k", "7298k", //
		"8192k", "9195k", "10321k", "11585k", "13004k", "14596k", //
		"16384k", "18390k", "20643k", "23170k", "26008k", "29193k", //
		"32768k", "36781k", "41285k", "46341k", "52016k", "58386k", //
		"65536k", "73562k", "82570k", "92682k", "104032k", "116772k", //
		"131072k", "147123k", "165140k", "185364k", "208064k", "233544k", "262144k"})
	String size;

	int[] insert;
}
