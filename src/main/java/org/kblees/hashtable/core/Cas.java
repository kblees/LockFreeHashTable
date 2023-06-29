/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

/**
 * Compare-And-Set a field of type V in class T.
 * <p>
 * Uses {@link java.util.concurrent.atomic.AtomicReferenceFieldUpdater#compareAndSet} (before Java 9) or
 * {@link java.lang.invoke.VarHandle#compareAndSet} (since Java 9).
 *
 * @param <T> the type that contains the field
 * @param <V> the type of the field
 */
public interface Cas<T,V>
{
	/**
	 * Atomically sets the field managed by this Cas instance to updated value if the current value {@code ==} the expected
	 * value.
	 *
	 * @param object the object whose field to set
	 * @param expect the expected value
	 * @param update the new value
	 * @return {@code true} if successful
	 */
	boolean cas(T object, V expect, V update);

	/**
	 * Creates a new Cas instance to compare-and-set a field in class {@code type}.
	 *
	 * @param type the type that contains the member variable
	 * @param fieldType the type of the member variable
	 * @param fieldName the name of the member variable
	 * @return the new Cas instance
	 */
	static <T,V> Cas<T,V> create(Class<T> type, Class<V> fieldType, String fieldName)
	{
		return AtomicUtils.createCas(type, fieldType, fieldName);
	}
}
