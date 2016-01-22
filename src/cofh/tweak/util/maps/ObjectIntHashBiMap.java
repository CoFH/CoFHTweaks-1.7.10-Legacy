/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * Based upon HashBiMap in Guava
 */
package cofh.tweak.util.maps;

import static com.google.common.base.Preconditions.*;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

public class ObjectIntHashBiMap<K> {

	private static final class BiEntry<K> {

		final K key;
		final int value;
		final int keyHash;
		final int valueHash;

		BiEntry<K> nextInKToVBucket;

		BiEntry<K> nextInVToKBucket;

		BiEntry(K key, int keyHash, int value, int valueHash) {

			this.key = key;
			this.value = value;
			this.keyHash = keyHash;
			this.valueHash = valueHash;
		}
	}

	static final double LOAD_FACTOR = 1.0;

	private transient BiEntry<K>[] hashTableKToV;
	private transient BiEntry<K>[] hashTableVToK;
	private transient int size;
	private transient int mask;
	private transient int modCount;

	protected ObjectIntHashBiMap() {

	}

	public ObjectIntHashBiMap(int expectedSize) {

		init(expectedSize);
	}

	private void init(int expectedSize) {

		checkNonnegative(expectedSize, "expectedSize");
		int tableSize = closedTableSize(expectedSize, LOAD_FACTOR);
		this.hashTableKToV = createTable(tableSize);
		this.hashTableVToK = createTable(tableSize);
		this.mask = tableSize - 1;
		this.modCount = 0;
		this.size = 0;
	}

	/**
	 * Finds and removes {@code entry} from the bucket linked lists in both the
	 * key-to-value direction and the value-to-key direction.
	 */
	private void delete(BiEntry<K> entry) {

		int keyBucket = entry.keyHash & mask;
		BiEntry<K> prevBucketEntry = null;
		for (BiEntry<K> bucketEntry = hashTableKToV[keyBucket]; true; bucketEntry = bucketEntry.nextInKToVBucket) {
			if (bucketEntry == entry) {
				if (prevBucketEntry == null) {
					hashTableKToV[keyBucket] = entry.nextInKToVBucket;
				} else {
					prevBucketEntry.nextInKToVBucket = entry.nextInKToVBucket;
				}
				break;
			}
			prevBucketEntry = bucketEntry;
		}

		int valueBucket = entry.valueHash & mask;
		prevBucketEntry = null;
		for (BiEntry<K> bucketEntry = hashTableVToK[valueBucket];; bucketEntry = bucketEntry.nextInVToKBucket) {
			if (bucketEntry == entry) {
				if (prevBucketEntry == null) {
					hashTableVToK[valueBucket] = entry.nextInVToKBucket;
				} else {
					prevBucketEntry.nextInVToKBucket = entry.nextInVToKBucket;
				}
				break;
			}
			prevBucketEntry = bucketEntry;
		}

		size--;
		modCount++;
	}

	private void insert(BiEntry<K> entry) {

		int keyBucket = entry.keyHash & mask;
		entry.nextInKToVBucket = hashTableKToV[keyBucket];
		hashTableKToV[keyBucket] = entry;

		int valueBucket = entry.valueHash & mask;
		entry.nextInVToKBucket = hashTableVToK[valueBucket];
		hashTableVToK[valueBucket] = entry;

		size++;
		modCount++;
	}

	protected int hash(Object o) {

		return smear((o == null) ? 0 : o.hashCode());
	}

	protected int hash(int o) {

		return smear(o);
	}

	protected boolean equal(Object a, Object b) {

		return a == b || (a != null && a.equals(b));
	}

	private BiEntry<K> seekByKey(Object key, int keyHash) {

		for (BiEntry<K> entry = hashTableKToV[keyHash & mask]; entry != null; entry = entry.nextInKToVBucket) {
			if (keyHash == entry.keyHash && equal(key, entry.key)) {
				return entry;
			}
		}
		return null;
	}

	private BiEntry<K> seekByValue(int value, int valueHash) {

		for (BiEntry<K> entry = hashTableVToK[valueHash & mask]; entry != null; entry = entry.nextInVToKBucket) {
			if (valueHash == entry.valueHash & value == entry.value) {
				return entry;
			}
		}
		return null;
	}

	public boolean containsKey(Object key) {

		return seekByKey(key, hash(key)) != null;
	}

	public boolean containsValue(int value) {

		return seekByValue(value, hash(value)) != null;
	}

	public int get(Object key) {

		BiEntry<K> entry = seekByKey(key, hash(key));
		return (entry == null) ? 0 : entry.value;
	}

	public int put(K key, int value) {

		return put(key, value, false, true);
	}

	public int forcePut(K key, int value) {

		return put(key, value, true, true);
	}

	public int putIfAbsent(K key, int value) {

		return put(key, value, false, false);
	}

	private int put(K key, int value, boolean force, boolean toss) {

		int keyHash = hash(key);
		int valueHash = hash(value);

		BiEntry<K> oldEntryForKey = seekByKey(key, keyHash);
		if (oldEntryForKey != null) {
			if (!toss)
				return oldEntryForKey.value;
			if (valueHash == oldEntryForKey.valueHash && value == oldEntryForKey.value)
				return value;
		}

		BiEntry<K> oldEntryForValue = seekByValue(value, valueHash);
		if (oldEntryForValue != null) {
			if (force) {
				delete(oldEntryForValue);
			} else {
				if (toss)
					throw new IllegalArgumentException("value already present: " + value);
				return (oldEntryForKey == null) ? 0 : oldEntryForKey.value;
			}
		}

		if (oldEntryForKey != null) {
			delete(oldEntryForKey);
		}
		BiEntry<K> newEntry = new BiEntry<K>(key, keyHash, value, valueHash);
		insert(newEntry);
		rehashIfNecessary();
		return (oldEntryForKey == null) ? value : oldEntryForKey.value;
	}

	private K putInverse(int value, K key, boolean force, boolean toss) {

		int valueHash = hash(value);
		int keyHash = hash(key);

		BiEntry<K> oldEntryForValue = seekByValue(value, valueHash);
		if (oldEntryForValue != null) {
			if (!toss)
				return oldEntryForValue.key;
			if (keyHash == oldEntryForValue.keyHash && equal(key, oldEntryForValue.key))
				return key;
		}

		BiEntry<K> oldEntryForKey = seekByKey(key, keyHash);
		if (oldEntryForKey != null) {
			if (force) {
				delete(oldEntryForKey);
			} else {
				if (toss)
					throw new IllegalArgumentException("value already present: " + key);
				return (oldEntryForValue == null) ? null : oldEntryForValue.key;
			}
		}

		if (oldEntryForValue != null) {
			delete(oldEntryForValue);
		}
		BiEntry<K> newEntry = new BiEntry<K>(key, keyHash, value, valueHash);
		insert(newEntry);
		rehashIfNecessary();
		return (oldEntryForValue == null) ? key : oldEntryForValue.key;
	}

	private void rehashIfNecessary() {

		BiEntry<K>[] oldKToV = hashTableKToV;
		if (needsResizing(size, oldKToV.length, LOAD_FACTOR)) {
			int newTableSize = oldKToV.length * 2;

			this.hashTableKToV = createTable(newTableSize);
			this.hashTableVToK = createTable(newTableSize);
			this.mask = newTableSize - 1;
			this.size = 0;

			for (int bucket = 0; bucket < oldKToV.length; bucket++) {
				BiEntry<K> entry = oldKToV[bucket];
				while (entry != null) {
					BiEntry<K> nextEntry = entry.nextInKToVBucket;
					insert(entry);
					entry = nextEntry;
				}
			}
			this.modCount++;
		}
	}

	@SuppressWarnings("unchecked")
	private BiEntry<K>[] createTable(int length) {

		return new BiEntry[length];
	}

	public int remove(Object key) {

		BiEntry<K> entry = seekByKey(key, hash(key));
		if (entry == null) {
			return 0;
		} else {
			delete(entry);
			return entry.value;
		}
	}

	public void clear() {

		size = 0;
		Arrays.fill(hashTableKToV, null);
		Arrays.fill(hashTableVToK, null);
		modCount++;
	}

	public int size() {

		return size;
	}

	public boolean isEmpty() {

		return size() == 0;
	}

	abstract class Itr<T> implements Iterator<T> {

		int nextBucket = 0;
		BiEntry<K> next = null;
		BiEntry<K> toRemove = null;
		int expectedModCount = modCount;

		private void checkForConcurrentModification() {

			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public boolean hasNext() {

			checkForConcurrentModification();
			if (next != null) {
				return true;
			}
			while (nextBucket < hashTableKToV.length) {
				if (hashTableKToV[nextBucket] != null) {
					next = hashTableKToV[nextBucket++];
					return true;
				}
				nextBucket++;
			}
			return false;
		}

		@Override
		public T next() {

			checkForConcurrentModification();
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			BiEntry<K> entry = next;
			next = entry.nextInKToVBucket;
			toRemove = entry;
			return output(entry);
		}

		@Override
		public void remove() {

			checkForConcurrentModification();
			checkRemove(toRemove != null);
			delete(toRemove);
			expectedModCount = modCount;
			toRemove = null;
		}

		abstract T output(BiEntry<K> entry);
	}

	public Set<K> keySet() {

		return new KeySet();
	}

	private final class KeySet extends AbstractSet<K> {

		KeySet() {

		}

		@Override
		public Iterator<K> iterator() {

			return new Itr<K>() {

				@Override
				K output(BiEntry<K> entry) {

					return entry.key;
				}
			};
		}

		@Override
		public boolean remove(Object o) {

			BiEntry<K> entry = seekByKey(o, hash(o));
			if (entry == null) {
				return false;
			} else {
				delete(entry);
				return true;
			}
		}

		@Override
		public int size() {

			return ObjectIntHashBiMap.this.size();
		}
	}

	public Set<Integer> values() {

		return inverse().keySet();
	}

	public Set<Entry<K, Integer>> entrySet() {

		return new EntrySet();
	}

	class EntrySet extends AbstractSet<Entry<K, Integer>> {

		ObjectIntHashBiMap<K> map() {

			return ObjectIntHashBiMap.this;
		}

		@Override
		public int size() {

			return map().size();
		}

		@Override
		public void clear() {

			map().clear();
		}

		@Override
		public boolean contains(Object o) {

			if (o instanceof Entry) {
				Entry<?, ?> entry = (Entry<?, ?>) o;
				Object v = entry.getValue();
				if (v instanceof Number) {
					int value = ((Number) v).intValue();
					Object key = entry.getKey();
					BiEntry<K> mapEntry = map().seekByKey(key, hash(key));
					return mapEntry != null && mapEntry.value == value;
				}
			}
			return false;
		}

		@Override
		public boolean isEmpty() {

			return map().isEmpty();
		}

		@Override
		public boolean remove(Object o) {

			if (contains(o)) {
				Entry<?, ?> entry = (Entry<?, ?>) o;
				return map().keySet().remove(entry.getKey());
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {

			try {
				return super.removeAll(checkNotNull(c));
			} catch (UnsupportedOperationException e) {
				boolean changed = false;
				Iterator<?> iterator = c.iterator();
				while (iterator.hasNext()) {
					changed |= remove(iterator.next());
				}
				return changed;
			}
		}

		@Override
		public boolean retainAll(Collection<?> c) {

			try {
				return super.retainAll(checkNotNull(c));
			} catch (UnsupportedOperationException e) {
				Set<Object> keys = Sets.newHashSetWithExpectedSize(c.size());
				for (Object o : c) {
					if (contains(o)) {
						Entry<?, ?> entry = (Entry<?, ?>) o;
						keys.add(entry.getKey());
					}
				}
				return map().keySet().retainAll(keys);
			}
		}

		@Override
		public Iterator<Entry<K, Integer>> iterator() {

			return new Itr<Entry<K, Integer>>() {

				@Override
				Entry<K, Integer> output(BiEntry<K> entry) {

					return new MapEntry(entry);
				}

				class MapEntry implements Entry<K, Integer> {

					BiEntry<K> delegate;

					MapEntry(BiEntry<K> entry) {

						this.delegate = entry;
					}

					@Override
					public K getKey() {

						return delegate.key;
					}

					@Override
					public Integer getValue() {

						return delegate.value;
					}

					@Override
					public Integer setValue(Integer value) {

						checkNotNull(value, "value cannot be null");
						int oldValue = delegate.value;
						int valueHash = hash(value);
						int v = value.intValue();
						if (valueHash == delegate.valueHash & v == oldValue) {
							return value;
						}
						checkArgument(seekByValue(v, valueHash) == null, "value already present: %s", value);
						delete(delegate);
						BiEntry<K> newEntry = new BiEntry<K>(delegate.key, delegate.keyHash, v, valueHash);
						insert(newEntry);
						expectedModCount = modCount;
						if (toRemove == delegate) {
							toRemove = newEntry;
						}
						delegate = newEntry;
						return oldValue;
					}

				}
			};
		}
	}

	private transient IntObjectHashBiMap<K> inverse;

	public IntObjectHashBiMap<K> inverse() {

		return (inverse == null) ? inverse = new Inverse() : inverse;
	}

	private final class Inverse extends IntObjectHashBiMap<K> {

		ObjectIntHashBiMap<K> forward() {

			return ObjectIntHashBiMap.this;
		}

		@Override
		public int size() {

			return size;
		}

		@Override
		public void clear() {

			forward().clear();
		}

		@Override
		protected boolean equal(Object a, Object b) {

			return ObjectIntHashBiMap.this.equal(a, b);
		}

		@Override
		public boolean containsKey(int value) {

			return forward().containsValue(value);
		}

		@Override
		public K get(int value) {

			BiEntry<K> entry = seekByValue(value, hash(value));
			return (entry == null) ? null : entry.key;
		}

		@Override
		public K put(int value, K key) {

			return putInverse(value, key, false, true);
		}

		@Override
		public K forcePut(int value, K key) {

			return putInverse(value, key, true, true);
		}

		@Override
		public K putIfAbsent(int value, K key) {

			return putInverse(value, key, false, false);
		}

		@Override
		public K remove(int value) {

			BiEntry<K> entry = seekByValue(value, hash(value));
			if (entry == null) {
				return null;
			} else {
				delete(entry);
				return entry.key;
			}
		}

		@Override
		public ObjectIntHashBiMap<K> inverse() {

			return forward();
		}

		@Override
		public Set<Integer> keySet() {

			return new InverseKeySet();
		}

		private final class InverseKeySet extends AbstractSet<Integer> {

			InverseKeySet() {

			}

			@Override
			public boolean remove(Object o) {

				if (!(o instanceof Number))
					return false;
				int v = ((Number) o).intValue();
				BiEntry<K> entry = seekByValue(v, hash(v));
				if (entry == null) {
					return false;
				} else {
					delete(entry);
					return true;
				}
			}

			@Override
			public Iterator<Integer> iterator() {

				return forward().new Itr<Integer>() {

					@Override
					Integer output(BiEntry<K> entry) {

						return entry.value;
					}
				};
			}

			@Override
			public int size() {

				return forward().size();
			}
		}

		@Override
		public Set<K> values() {

			return forward().keySet();
		}

		@Override
		public Set<Entry<Integer, K>> entrySet() {

			return Inverse.this.new EntrySet() {

				@Override
				IntObjectHashBiMap<K> map() {

					return Inverse.this;
				}

				@Override
				public Iterator<Entry<Integer, K>> iterator() {

					return ObjectIntHashBiMap.this.new Itr<Entry<Integer, K>>() {

						@Override
						Entry<Integer, K> output(BiEntry<K> entry) {

							return new InverseEntry(entry);
						}

						class InverseEntry implements Entry<Integer, K> {

							BiEntry<K> delegate;

							InverseEntry(BiEntry<K> entry) {

								this.delegate = entry;
							}

							@Override
							public Integer getKey() {

								return delegate.value;
							}

							@Override
							public K getValue() {

								return delegate.key;
							}

							@Override
							public K setValue(K key) {

								K oldKey = delegate.key;
								int keyHash = hash(key);
								if (keyHash == delegate.keyHash && equal(key, oldKey)) {
									return key;
								}
								checkArgument(seekByKey(key, keyHash) == null, "value already present: %s", key);
								delete(delegate);
								BiEntry<K> newEntry = new BiEntry<K>(key, keyHash, delegate.value, delegate.valueHash);
								insert(newEntry);
								expectedModCount = modCount;
								// This is safe because entries can only get bumped up to earlier in the iteration,
								// so they can't get revisited.
								return oldKey;
							}
						}
					};
				}
			};
		}
	}

	static void checkEntryNotNull(Object key, Object value) {

		if (key == null) {
			throw new NullPointerException("null key in entry: null=" + value);
		} else if (value == null) {
			throw new NullPointerException("null value in entry: " + key + "=null");
		}
	}

	static int checkNonnegative(int value, String name) {

		if (value < 0) {
			throw new IllegalArgumentException(name + " cannot be negative but was: " + value);
		}
		return value;
	}

	static void checkRemove(boolean canRemove) {

		checkState(canRemove, "no calls to next() since the last call to remove()");
	}

	private static final int C1 = 0xcc9e2d51;
	private static final int C2 = 0x1b873593;

	/*
	 * This method was rewritten in Java from an intermediate step of the Murmur hash function in
	 * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp, which contained the
	 * following header:
	 *
	 * MurmurHash3 was written by Austin Appleby, and is placed in the public domain. The author
	 * hereby disclaims copyright to this source code.
	 */
	static int smear(int hashCode) {

		return C2 * Integer.rotateLeft(hashCode * C1, 15);
	}

	static int smearedHash(Object o) {

		return smear((o == null) ? 0 : o.hashCode());
	}

	private static int MAX_TABLE_SIZE = Ints.MAX_POWER_OF_TWO;

	static int closedTableSize(int expectedEntries, double loadFactor) {

		// Get the recommended table size.
		// Round down to the nearest power of 2.
		expectedEntries = Math.max(expectedEntries, 2);
		int tableSize = Integer.highestOneBit(expectedEntries);
		// Check to make sure that we will not exceed the maximum load factor.
		if (expectedEntries > (int) (loadFactor * tableSize)) {
			tableSize <<= 1;
			return (tableSize > 0) ? tableSize : MAX_TABLE_SIZE;
		}
		return tableSize;
	}

	static boolean needsResizing(int size, int tableSize, double loadFactor) {

		return size > loadFactor * tableSize && tableSize < MAX_TABLE_SIZE;
	}

}
