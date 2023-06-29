/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

/**
 * Simple key class that is not comparable, with configurable hash code.
 */
public class Key
{
	private final int key;

	/**
	 * Creates a new instance with specified key and hash code.
	 *
	 * @param key  the key
	 * @param hash the hash code
	 */
	public Key(int key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return "Key[key=0x" + Integer.toHexString(key) + ", hash=0x" + Integer.toHexString(hashCode()) + "]";
	}

	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof Key))
			return false;
		return key == ((Key) obj).key;
	}

	@Override
	public int hashCode()
	{
		return key;
	}
}
