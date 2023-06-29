/**
 * Copyright (C) 2023 Karsten Blees
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file or at https://opensource.org/licenses/MIT.
 */
package org.kblees.hashtable.core;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Core implementation of a fast, memory efficient, lock free hash table.
 *
 * @param <T> type of the concrete subclass in use
 */
public class LockFreeHashTable<T extends LockFreeHashTable<T>>
{
	/**
	 * Return value for no entry.
	 */
	public static final int NONE = -1;

	/**
	 * Return value if the hash table needs resizing.
	 */
	public static final int RESIZE = -2;

	/**
	 * Number of reserved slots at the start of the hash table.
	 */
	private static final int RESERVED = 2;

	/**
	 * Minimum capacity of the hash table.
	 */
	private static final int MIN_CAPACITY = 16;

	/**
	 * Maximum capacity of the hash table.
	 */
	private static final int MAX_CAPACITY = 1 << 30;

	/**
	 * Flag indicating that an entry is in use and contains valid data.
	 */
	private static final long USED = 0x200000000L;

	/**
	 * Flag indicating that the entry is being resized and is unmodifiable.
	 */
	private static final long RESIZING = 0x100000000L;

	/**
	 * Flag indicating that an unused entry has been removed.
	 */
	private static final long REMOVED = 0x80000000L;

	/**
	 * Number of bits to shift right to get the head field.
	 */
	private static final int HEAD_SHIFT = 34;

	/**
	 * Bit mask for the head field.
	 */
	private static final long HEAD_MASK = -1L << HEAD_SHIFT;

	/**
	 * Number of linear probes before switching to quadratic probing.
	 */
	private static final int LINEAR_PROBES = 8;

	/**
	 * The golden ratio, to generate quasi random hash codes.
	 */
	private static final int PHI = 0x9e3779b9;

	/**
	 * Modular multiplicative inverse of PHI.
	 */
	private static final int INVPHI = 0x144cbc89;

	/**
	 * Field updater for the resizer field.
	 */
	@SuppressWarnings("rawtypes")
	private static final Cas<LockFreeHashTable, Resizer> RESIZER = Cas.create(LockFreeHashTable.class, Resizer.class,
			"resizer");

	/**
	 * Concurrently tracks the size of the hash table. The low word counts added entries, the high word counts removed
	 * entries.
	 */
	private final LongAdder sizes = new LongAdder();

	/**
	 * Bit mask to convert an integer to slot index.
	 */
	private final int slotmask;

	/**
	 * Number of bits in slotmask.
	 */
	private final int slotbits;

	/**
	 * Bits of the hash code to store / compare.
	 */
	private final int hashmask;

	/**
	 * The actual data of the hash table.
	 */
	private final long states[];

	/**
	 * Resizer object if the hash table is currently being resized.
	 */
	private volatile Resizer<T> resizer;

	/**
	 * Creates a new instance with the specified capacity.
	 *
	 * @param capacity the capacity of the hash table
	 */
	public LockFreeHashTable(int tabSize)
	{
		if (tabSize > MAX_CAPACITY)
			throw new IllegalArgumentException(tabSize + " exceeds the maximum capacity of 2^30 entries!");
		tabSize = roundUpPow2(Math.max(tabSize, MIN_CAPACITY));
		// allocate and initialize data arrays
		slotmask = tabSize - 1;
		slotbits = 32 - Integer.numberOfLeadingZeros(slotmask);
		hashmask = ~slotmask >>> slotbits;
		states = new long[tabSize];
		states[0] = states[1] = REMOVED;
	}

	/**
	 * @return the number of entries in the hash table
	 */
	public final int size()
	{
		long sz = getSizes();
		return (int) ((int) sz - (sz >>> 32));
	}

	/**
	 * @return the size of the hash table (always a power of two)
	 */
	public final int tabSize()
	{
		return states.length;
	}

	/**
	 * @return the maximum capacity of the hash table (slightly less than {@link #tabSize()} because some slots are
	 * reserved for internal use)
	 */
	public final int capacity()
	{
		return tabSize() - RESERVED;
	}

	/**
	 * Adds to the sizes (low word: added entries, high word: removed entries).
	 *
	 * @param v the value to add
	 */
	final void addSizes(long v)
	{
		sizes.add(v);
	}

	/**
	 * @return the sizes (low word: added entries, high word: removed entries)
	 */
	final long getSizes()
	{
		return sizes.sum();
	}

	/**
	 * @return the bit mask to convert a hash code to hash table slot index
	 */
	final int slotmask()
	{
		return slotmask;
	}

	/**
	 * @param hashCode the full hash code
	 * @return slot index of the hash table
	 */
	final int slot(int hashCode)
	{
		return hashCode >>> -slotbits;
	}

	/**
	 * Round up to the next power of two.
	 *
	 * @param x the number to round up
	 * @return smallest power of two >= x
	 */
	static final int roundUpPow2(int x)
	{
		return Integer.highestOneBit(x - 1) << 1;
	}

	/**
	 * Creates a state value from the individual fields.
	 *
	 * @param used {@code true} if the entry is used
	 * @param head the head index
	 * @param hash the hash code
	 * @param next the next index
	 * @return the resulting state value
	 */
	final long state(boolean used, int head, int hash, int next)
	{
		assert (head & slotmask()) == head;
		assert (next & slotmask()) == next;
		return (used ? USED : 0) | (long) head << HEAD_SHIFT | Integer.toUnsignedLong((hash << slotbits) | next);
	}

	final boolean isFree(long state)
	{
		return (state & ~HEAD_MASK) == 0;
	}

	final long setFree(long state)
	{
		return state & HEAD_MASK;
	}

	final boolean isUsed(long state)
	{
		return (state & USED) != 0;
	}

	final long setUsed(long state, int hash, int next)
	{
		return state(true, getHead(state), hash, next);
	}

	final boolean isResizing(long state)
	{
		return (state & RESIZING) != 0;
	}

	final long setResizing(long state)
	{
		return state | RESIZING;
	}

	final boolean isRemoved(long state)
	{
		return !isUsed(state) && (state & REMOVED) != 0;
	}

	final long setRemoved(long state)
	{
		return state & ~USED | REMOVED;
	}

	final int getHead(long state)
	{
		return (int) (state >>> HEAD_SHIFT);
	}

	final long setHead(long state, int head)
	{
		assert (head & slotmask()) == head;
		return state & ~HEAD_MASK | ((long) head << HEAD_SHIFT);
	}

	final int getHash(long state)
	{
		return (int) state >>> slotbits;
	}

	final int getHash(long state, int slot)
	{
		return (slot << -slotbits) | getHash(state);
	}

	final int getNext(long state)
	{
		return (int) state & slotmask();
	}

	final long setNext(long state, int next)
	{
		assert (next & slotmask()) == next;
		return state & ~((long) slotmask()) | next;
	}

	final long getState(int index)
	{
		return AtomicUtils.get(states, index);
	}

	final boolean setState(int index, long state, long newState)
	{
		return AtomicUtils.cas(states, index, state, newState);
	}

	/**
	 * Allocates a free entry.
	 *
	 * @param index the start index
	 * @param hash the hash code to store
	 * @param next the next index to store
	 * @return index of the allocated entry, or {@code 0} if the table needs resizing
	 */
	final int alloc(int index, int hash, int next)
	{
		for (int i = -LINEAR_PROBES; i <= slotmask(); i++)
		{
			index = (index + Math.max(1, i)) & slotmask();
			long state = getState(index);
			if (isFree(state))
			{
				long newState = setUsed(state, hash, next);
				if (setState(index, state, newState))
					return index;
			}
			else if (i == 0)
			{
				long sz = getSizes();
				if (shouldResize((int) sz, (int) (sz >>> 32)))
					return 0;
			}
			else if (resizer != null)
			{
				return 0;
			}
		}
		return 0;
	}

	/**
	 * Frees the entry.
	 *
	 * @param index index of the entry to free
	 */
	final void free(int index)
	{
		// call subclass callback
		reset(index - RESERVED);

		for (;;)
		{
			long state = getState(index);
			long newState = setFree(state);
			if (setState(index, state, newState))
				return;
		}
	}

	/**
	 * Links the specified entries.
	 *
	 * @param slot index of the bucket
	 * @param prevIndex index to link from, or {@code 0} if the bucket is empty
	 * @param prevState state of prevIndex, or slot if prevIndex is {@code 0}
	 * @param index index to link to
	 * @return {@code true} if successful
	 */
	final boolean linkTo(int slot, int prevIndex, long prevState, int index)
	{
		if (prevIndex == 0)
		{
			long newState = setHead(prevState, index);
			return setState(slot, prevState, newState);
		}
		else
		{
			long newState = setNext(prevState, index);
			return setState(prevIndex, prevState, newState);
		}
	}

	/**
	 * Copies an entry from the old to the new hash table.
	 *
	 * @param oldTable the old hash table
	 * @param oldIndex index of the entry in the old hash table
	 * @param index index of the entry in the new (this) hash table
	 */
	protected void copy(T oldTable, int oldIndex, int index)
	{
	}

	/**
	 * Clears the specified entry, e.g. after a failed update.
	 *
	 * @param index index of the entry to clear
	 */
	protected void reset(int index)
	{
	}

	/**
	 * Creates a new hash table instance of specified size.
	 *
	 * @param tabSize the new size
	 * @return new hash table instance
	 */
	@SuppressWarnings("unchecked")
	protected T create(int tabSize)
	{
		try
		{
			// use reflection to create a new instance of the subclass
			Constructor<T> c = (Constructor<T>) getClass().getDeclaredConstructor(int.class);
			c.setAccessible(true);
			return c.newInstance(Integer.valueOf(tabSize));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Checks if the hash table should be resized.
	 *
	 * @param used number of used entries
	 * @param removed number of removed entries
	 * @return {@code true} to resize the hash table
	 */
	protected boolean shouldResize(int used, int removed)
	{
		int cap = capacity();
		return used >= cap - (cap >>> 4);
	}

	/**
	 * Calculates the capacity of the resized hash table.
	 *
	 * @param used number of used entries
	 * @param removed number of removed entries
	 * @return the new capacity
	 */
	protected int newCapacity(int used, int removed)
	{
		int cap = capacity();
		if ((cap - removed) << 1 >= cap)
			cap <<= 1;
		return cap;
	}

	/**
	 * Helper class to iterate and find entries with a specific hash code.
	 */
	public final class Finder
	{
		private final int hash;
		private int index;
		private long state;

		/**
		 * Creates a new instance.
		 *
		 * @param hash the hash code
		 */
		Finder(int hash)
		{
			this.state = slot(hash);
			this.hash = hash & hashmask;
		}

		/**
		 * @return index of the next entry, {@link LockFreeHashTable#NONE} if there are no more entries
		 */
		public int next()
		{
			int last = index;
			if (last == 0)
			{
				last = (int) state;
				state = getState(last);
				index = getHead(state);
			}
			else
			{
				index = getNext(state);
			}

			while (index >= RESERVED)
			{
				if (index != last)
					state = getState(last = index);

				if (isUsed(state))
				{
					int h = getHash(state);
					if (h == hash)
						return index - RESERVED;

					if (h > hash)
						return NONE;
				}
				index = getNext(state);
			}
			return NONE;
		}

		/**
		 * Reloads the current state. This is necessary if an entry has been {@link Updater#replace() replaced}.
		 *
		 * @see Updater#replace()
		 */
		public void reload()
		{
			state = getState(index);
		}
	}

	/**
	 * Returns a Finder to iterate and find entries with matching hash code.
	 *
	 * @param hashCode the hash code
	 * @return the Finder
	 */
	public final Finder finder(int hashCode)
	{
		return new Finder(hashCode * PHI);
	}

	/**
	 * Allocate and reuse a single Updater instance per thread. This implies that update operations cannot be nested. E.g.
	 * the {@link #equals(Object)} method of the hash table keys must not modify another LockFreeHashTable.
	 */
	private static final ThreadLocal<Updater> UPDATER = ThreadLocal.withInitial(() -> new Updater());

	/**
	 * Helper class to iterate and update entries with a specific hash code.
	 */
	public static final class Updater implements AutoCloseable
	{
		private LockFreeHashTable<?> table;
		private int slot;
		private int hash;
		private int index;
		private int prevIndex;
		private int newIndex;
		private long state;
		private long prevState;

		/**
		 * Initializes the instance.
		 *
		 * @param table the hash table
		 * @param hash the hash code
		 * @return the initialized instance (this)
		 */
		Updater init(LockFreeHashTable<?> table, int hash)
		{
			assert this.table == null;
			this.table = table;
			this.hash = hash & table.hashmask;
			slot = table.slot(hash);
			index = prevIndex = newIndex = 0;
			state = prevState = 0;
			return this;
		}

		/**
		 * Closes the Updater.
		 */
		public void close()
		{
			if (newIndex >= RESERVED)
				table.free(newIndex);
			newIndex = 0;
			table = null;
		}

		/**
		 * Restarts iteration.
		 */
		public void restart()
		{
			index = prevIndex = 0;
			state = prevState = 0;
		}

		/**
		 * @return index of the next entry, {@link LockFreeHashTable#NONE} if there are no more entries, or
		 * {@link LockFreeHashTable#RESIZE} if the table needs resizing
		 */
		public int next()
		{
			state = table.getState(index == 0 ? slot : index);
			if (table.isResizing(state))
				return RESIZE;

			for (;;)
			{
				prevIndex = index;
				prevState = state;

				index = index == 0 ? table.getHead(state) : table.getNext(state);
				if (index < RESERVED)
					return NONE;

				state = table.getState(index);
				if (table.isResizing(state))
					return RESIZE;

				if (!table.isUsed(state))
				{
					// found a removed entry. Assist the removing thread by linking the previous entry to current's next entry. If
					// successful, continue with the previous entry, otherwise start over.
					index = linkTo(table.getNext(state)) ? prevIndex : 0;
					state = table.getState(index == 0 ? slot : index);
					continue;
				}

				final int h = table.getHash(state);
				if (h == hash)
					return index - RESERVED;

				if (h > hash)
					return NONE;
			}
		}

		/**
		 * Inserts the allocated entry before the current entry.
		 *
		 * @return {@code true} if successful
		 */
		public boolean insert()
		{
			setNewNext(index);
			if (!linkTo(newIndex))
				return false;

			newIndex = 0;
			table.addSizes(1);
			return true;
		}

		/**
		 * Replaces the current entry with the allocated entry.
		 *
		 * @return {@code true} if successful
		 */
		public boolean replace()
		{
			setNewNext(table.getNext(state));
			if (!remove(newIndex))
				return false;

			newIndex = 0;
			table.addSizes(0x100000001L);
			return true;
		}

		/**
		 * Removes the current entry.
		 *
		 * @return {@code true} if successful
		 */
		public boolean remove()
		{
			if (!remove(table.getNext(state)))
				return false;

			table.addSizes(0x100000000L);
			return true;
		}

		/**
		 * Allocates a free entry.
		 *
		 * @return index of the entry, or {@link LockFreeHashTable#RESIZE} if the table needs resizing
		 */
		public int alloc()
		{
			if (newIndex < RESERVED)
			{
				if (prevIndex == 0 && table.isFree(prevState))
				{
					long newState = table.setUsed(prevState, hash, ptr(index));
					if (setState(slot, prevState, newState))
					{
						newIndex = slot;
						return newIndex - RESERVED;
					}
				}

				newIndex = table.alloc(Math.max(prevIndex, slot), hash, ptr(index));
				if (newIndex < RESERVED)
					return RESIZE;
			}
			return newIndex - RESERVED;
		}

		/**
		 * Sets the next field of the newIndex entry.
		 *
		 * @param next the next index to link to
		 */
		private void setNewNext(int next)
		{
			assert newIndex >= RESERVED : "Use alloc before insert / replace!";
			next = ptr(next);
			for (;;)
			{
				long state = table.getState(newIndex);
				long newState = table.setNext(state, next);
				if (state == newState || setState(newIndex, state, newState))
					return;
			}
		}

		/**
		 * Sets the hash table state, keeping Updater fields in sync.
		 *
		 * @param index the index
		 * @param state the old state
		 * @param newState the new state
		 * @return {@code true} if successful
		 */
		private boolean setState(int index, long state, long newState)
		{
			if (!table.setState(index, state, newState))
				return false;
			// keep prevState up to date if it was successfully changed
			if (index == prevIndex || (prevIndex == 0 && index == slot))
				prevState = newState;
			return true;
		}

		/**
		 * Removes the current entry.
		 *
		 * @param next the next index of the removed entry
		 * @return {@code true} if successful
		 */
		private boolean remove(int next)
		{
			assert index >= RESERVED : "No current entry!";
			// mark current slot removed and set next field
			next = ptr(next);
			long newState = table.setNext(table.setRemoved(state), next);

			// if current entry is the head entry, also set head field to next (optimization to save the second CAS below)
			boolean head = prevIndex == 0 && index == slot;
			if (head)
				newState = table.setHead(newState, next);

			// CAS to remove the entry
			if (!setState(index, state, newState))
				return false;

			// first CAS succeeded, try to CAS prev to next
			if (!head)
				linkTo(next);
			return true;
		}

		/**
		 * Links the previous entry or the slot's head to the specified index.
		 *
		 * @param index the index to link to
		 * @return {@code true} if successful
		 */
		private boolean linkTo(int index)
		{
			return table.linkTo(slot, prevIndex, prevState, ptr(index));
		}

		/**
		 * Converts {@code 0} to {@code 1} so that head / next pointers written by the {@link Updater} can be distinguished
		 * from initial state and pointers written by the {@link Resizer}.
		 *
		 * @param index the index to convert
		 * @return {@code 1} if index is {@code 0}, otherwise index
		 */
		private static int ptr(int index)
		{
			return index == 0 ? 1 : index;
		}
	}

	/**
	 * Returns an Updater to iterate and modify entries with matching hash code.
	 * <p>
	 * The returned Updater needs to be closed after use.
	 *
	 * @param hashCode the hash code
	 * @return the Updater
	 */
	public final Updater updater(int hashCode)
	{
		return UPDATER.get().init(this, hashCode * PHI);
	}

	/**
	 * Helper class to iterate all entries of the hash table.
	 */
	public final class Iterator
	{
		private int slot = -1;
		private int index;

		/**
		 * @return the hash code of the current entry
		 */
		public int getHashCode()
		{
			// restore original hash code
			return getHash(getState(index), slot) * INVPHI;
		}

		/**
		 * @return index of the next entry, {@link LockFreeHashTable#NONE} if there are no more entries
		 */
		public int next()
		{
			if (index >= RESERVED)
				index = getNext(getState(index));
			for (;;)
			{
				if (index < RESERVED)
				{
					if (slot >= slotmask())
						return NONE;
					index = getHead(getState(++slot));
				}
				else
				{
					long state = getState(index);
					if (isUsed(state))
						return index - RESERVED;
					index = getNext(state);
				}
			}
		}
	}

	/**
	 * @return an iterator over all entries
	 */
	public final Iterator iterator()
	{
		return new Iterator();
	}

	/**
	 * Resizes the hash table.
	 *
	 * @return the resized hash table instance
	 */
	@SuppressWarnings("unchecked")
	public final T resize()
	{
		Resizer<T> r = resizer;
		if (r == null)
		{
			// start resizing
			long size = getSizes();
			int newTabSize = roundUpPow2(Math.max(tabSize(), newCapacity((int) size, (int) (size >>> 32))));
			r = new Resizer<>((T) this, newTabSize);
			if (RESIZER.cas(this, null, r))
				r.init();
			else
				r = resizer;
		}
		return r.resize();
	}

	/**
	 * Helper class to resize the hash table.
	 */
	private static final class Resizer<S extends LockFreeHashTable<S>>
	{
		private static final long ALLOC_WAIT = 10_000_000_000L;

		private final S oldTable;

		private final long startTime = System.nanoTime();

		private final AtomicInteger threadCounter = new AtomicInteger();

		private final CompletableFuture<S> allocator = new CompletableFuture<>();

		private S newTable;

		private final IntSpliterator splitter;

		private final int newTabSize;

		private final int factor;

		private volatile boolean done;

		Resizer(S oldTable, int newTabSize)
		{
			this.oldTable = oldTable;
			this.newTabSize = newTabSize;
			factor = newTabSize / oldTable.tabSize();

			splitter = new IntSpliterator(oldTable.tabSize() >>> 4);
		}

		/**
		 * Initializes the Resizer by allocating the new hash table.
		 */
		final void init()
		{
			if (!allocator.isDone())
			{
				try
				{
					S newTab = oldTable.create(newTabSize);
					if (allocator.complete(newTab))
						newTable = newTab;
				}
				catch (Throwable t)
				{
					allocator.completeExceptionally(t);
				}
			}
		}

		/**
		 * Wait for initialization (i.e. wait until the first resizing thread has finished allocating the new hash table).
		 */
		private void waitInit()
		{
			long endWait = 0;
			if (!allocator.isDone())
			{
				// calculate time to wait / work before trying to allocate the new table ourselves
				endWait = startTime + threadCounter.incrementAndGet() * ALLOC_WAIT;

				// burn some time in a useful way by marking old table slots resizing
				S tab = oldTable;
				int batches = tab.tabSize() >>> 4;
				for (int batch = 0; batch < batches && !allocator.isDone() && System.nanoTime() < endWait; batch++)
				{
					int start = batch << 4;
					int end = start + 15;
					for (int slot = start; slot <= end; slot++)
					{
						long state = tab.getState(slot);
						long newState = tab.setResizing(state);
						if (state == newState || !tab.setState(slot, state, newState))
							break;
					}
				}
			}

			if (newTable != null)
				return;

			try
			{
				try
				{
					newTable = allocator.get(endWait - System.nanoTime(), TimeUnit.NANOSECONDS);
				}
				catch (TimeoutException e)
				{
					// other threads are stalled, its our turn to try...
					init();
					newTable = allocator.get();
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new CompletionException(e);
			}
			catch (ExecutionException e)
			{
				Throwable t = e.getCause();
				if (t instanceof Error)
					throw (Error) t;
				if (t instanceof RuntimeException)
					throw (RuntimeException) t;
				throw new CompletionException(t);
			}
		}

		/**
		 * Resizes the hash table.
		 *
		 * @return the new instance
		 */
		final S resize()
		{
			waitInit();

			final int[] tails = new int[factor];
			for (long batch = splitter.first(); batch != IntSpliterator.NONE; batch = splitter.next(batch))
			{
				int start = ((int) batch) << 4;
				for (int slot = start + 15; slot >= start; slot--)
					if (!copyBucket(tails, slot))
						break;
			}
			done = true;
			return newTable;
		}

		/**
		 * Marks the old hash table slot resizing.
		 *
		 * @param slot the slot
		 * @return state of the slot
		 */
		private long markResizing(int slot)
		{
			S tab = oldTable;
			for (;;)
			{
				long state = tab.getState(slot);
				long newState = tab.setResizing(state);
				if (state == newState || tab.setState(slot, state, newState))
					return newState;
			}
		}

		/**
		 * Copies a bucket from the old hash table to the new hash table.
		 *
		 * @param tails per thread state
		 * @param oldSlot start slot of the bucket
		 * @return {@code false} to abort resizing
		 */
		private boolean copyBucket(int[] tails, int oldSlot)
		{
			// reset state
			Arrays.fill(tails, 0);
			long oldState = markResizing(oldSlot);
			for (int oldIdx = oldTable.getHead(oldState); oldIdx >= RESERVED; oldIdx = oldTable.getNext(oldState))
			{
				oldState = markResizing(oldIdx);
				if (oldTable.isUsed(oldState))
				{
					// restore original hash code
					int hash = oldTable.getHash(oldState, oldSlot);

					// calculate slot in new table
					int slot = newTable.slot(hash);
					int facIdx = slot & (factor - 1);
					// copy entry and remember tail index of the bucket
					if ((tails[facIdx] = copyEntry(oldIdx, hash, slot, tails[facIdx])) == NONE)
						return false;
				}
			}
			return true;
		}

		/**
		 * Copies an entry from the old hash table to the new hash table.
		 *
		 * @param oldIdx index of the entry in the old hash table
		 * @param keyHash full hash code of the key
		 * @param slot slot in the new table
		 * @param tail tail of slot's bucket chain
		 * @return index of the entry in the new hash table (i.e. new tail of the bucket chain), or
		 * {@link LockFreeHashTable#NONE} to abort resizing
		 */
		private int copyEntry(int oldIdx, int keyHash, int slot, int tail)
		{
			boolean head = tail == 0;
			if (head)
			{
				// fast path for the common case that the entry can be stored in its native slot
				long state = newTable.getState(slot);
				long newState = newTable.state(true, slot, keyHash, 0);
				if (state == 0)
				{
					if (newTable.setState(slot, state, newState))
					{
						state = newState;
						newTable.addSizes(1);
					}
					else
					{
						state = newTable.getState(slot);
					}
				}

				if (state == newState)
				{
					newTable.copy(oldTable, oldIdx - RESERVED, slot - RESERVED);
					return slot;
				}
			}

			// collision case, entry has to be stored in some other slot
			for (;;)
			{
				long state = newTable.getState(head ? slot : tail);
				// check if resizing has completed *after* reading the state
				if (done)
					return NONE;

				int index = head ? newTable.getHead(state) : newTable.getNext(state);
				if (index != 0)
					return index;

				int newIndex = newTable.alloc(head ? slot : tail, keyHash, 0);
				if (newIndex < RESERVED)
					continue;
				newTable.copy(oldTable, oldIdx - RESERVED, newIndex - RESERVED);

				if (newTable.linkTo(slot, tail, state, newIndex))
				{
					newTable.addSizes(1);
					return newIndex;
				}
				newTable.free(newIndex);
			}
		}
	}
}
