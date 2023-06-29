/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * ConcurrentMap that supports {@code null} keys and values.
 * <p>
 * ConcurrentMap lacks a method to atomically check if a key is mapped to
 * {@code null}. In consequence, all the default implementations in the
 * ConcurrentMap interface are broken with respect to {@code null}.
 * ConcurrentMap implementations that need to support {@code null} values have
 * to re-implement all these methods for correctness. This interface fills the
 * gap by defining a {@link #getEntry(Object)} method and re-implementing the
 * default methods in a null-safe way.
 */
public interface ConcurrentNullMap<K, V> extends ConcurrentMap<K, V>
{
	/**
	 * Returns the Entry of the specified key, or {@code null} if there is no such
	 * mapping.
	 * <p>
	 * Equivalent to (but as an atomic operation):
	 *
	 * <pre>
	 * if (!containsKey(key))
	 * 	return null;
	 * return new Entry(key, get(key));
	 * </pre>
	 *
	 * @param key the key to look up
	 * @return the Entry mapped to the specified key, or {@code null} if there is no
	 *         such mapping
	 */
	Map.Entry<K, V> getEntry(Object key);

	@Override
	default V getOrDefault(Object key, V defaultValue)
	{
		Map.Entry<K, V> e = getEntry(key);
		return e != null ? e.getValue() : defaultValue;
	}

	@Override
	default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function)
	{
		Objects.requireNonNull(function);
		forEach((k, v) -> {
			while (!replace(k, v, function.apply(k, v)))
			{
				// v changed or k is gone
				Map.Entry<K, V> e = getEntry(k);
				if (e == null)
					break;
				v = e.getValue();
			}
		});
	}

	/**
	 * Put value if key is absent or mapped to {@code null}.
	 *
	 * @param key   the key
	 * @param value the value
	 * @return the currently mapped value (Note: this is different from putIfAbsent,
	 *         which returns the old value!)
	 */
	default V putIfAbsentOrNull(K key, V value)
	{
		V v;
		for (;;)
		{
			Map.Entry<K, V> e = getEntry(key);
			if (e == null)
			{
				if ((v = putIfAbsent(key, value)) != null)
					return v;
				// if v == null, we cannot tell whether its absent or mapped
				// to null, so retry
			}
			else if ((v = e.getValue()) != null)
			{
				return v;
			}
			else if (replace(key, null, value))
			{
				return value;
			}
		}
	}

	@Override
	default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
	{
		Objects.requireNonNull(mappingFunction);
		V v;
		return ((v = get(key)) == null && (v = mappingFunction.apply(key)) != null) ? putIfAbsentOrNull(key, v) : v;
	}

	@Override
	default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction)
	{
		Objects.requireNonNull(remappingFunction);
		V oldValue = get(key);
		for (;;)
		{
			V newValue = remappingFunction.apply(key, oldValue);
			if (newValue == null)
			{
				if (oldValue != null || containsKey(key))
				{
					if (remove(key, oldValue))
						return null;
					oldValue = get(key);
				}
				else
				{
					return null;
				}
			}
			else
			{
				if (oldValue != null)
				{
					if (replace(key, oldValue, newValue))
						return newValue;
					oldValue = get(key);
				}
				else
				{
					if ((oldValue = putIfAbsentOrNull(key, newValue)) == newValue)
						return newValue;
				}
			}
		}
	}

	@Override
	default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction)
	{
		Objects.requireNonNull(remappingFunction);
		Objects.requireNonNull(value);
		V oldValue = get(key);
		for (;;)
		{
			if (oldValue != null)
			{
				V newValue = remappingFunction.apply(oldValue, value);
				if (newValue != null)
				{
					if (replace(key, oldValue, newValue))
						return newValue;
				}
				else if (remove(key, oldValue))
				{
					return null;
				}
				oldValue = get(key);
			}
			else
			{
				if ((oldValue = putIfAbsentOrNull(key, value)) == value)
				{
					return value;
				}
			}
		}
	}
}
