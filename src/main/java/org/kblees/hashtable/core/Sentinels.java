/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

/**
 * Sentinel values for use in hash table implementations, as enum for
 * serializability.
 */
public enum Sentinels
{
	/**
	 * Represents a removed object.
	 */
	TOMBSTONE,

	/**
	 * Represents null.
	 */
	NULL
}
