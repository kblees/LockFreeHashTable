/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

/**
 * Factory for different kinds of map keys.
 */
public enum KeyType
{

	/**
	 * Integer with identity hash code ({@code h(x) := x}).
	 */
	Int
	{
		@Override
		public Object create(int i)
		{
			return new Key(i);
		}
	},

	/**
	 * Integer with colliding hash codes.
	 */
	CollidingInt
	{
		@Override
		public Object create(int i)
		{
			return new Key(i)
			{
				@Override
				public int hashCode()
				{
					return super.hashCode() & 0xfffcfffc;
				}
			};
		}
	},

	/**
	 * Integer with slow hashCode function.
	 */
	SlowHashInt
	{
		@Override
		public Object create(int i)
		{
			return new Key(i)
			{
				@Override
				public int hashCode()
				{
					int h = super.hashCode();
					for (int i = 0; i < 10; i++)
						h= Integer.reverse(h);
					return h;
				}
			};
		}
	},

	/**
	 * String with provided number as suffix (base 10).
	 */
	String
	{
		@Override
		public Object create(int i)
		{
			return "Key" + Integer.toString(i);
		}
	},

	/**
	 * String with provided number as suffix (base 32).
	 */
	Base32String
	{
		@Override
		public Object create(int i)
		{
			return "Key" + Integer.toString(i, 32);
		}
	},

	LongString
	{
		@Override
		public Object create(int i)
		{
			String s = Integer.toString(i);
			return LONG_STRING.substring(s.length()) + s;
		}
	};

	private static final String LONG_STRING;
	static
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 500; i++)
			sb.append('X');
		LONG_STRING = sb.toString();
	}

	public abstract Object create(int i);

	public Object[] create(int[] keys)
	{
		int len = keys.length;
		Object[] result = new Object[len];
		for (int i = 0; i < len; i++)
			result[i] = create(keys[i]);
		return result;
	}
}
