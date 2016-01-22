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

import static cofh.tweak.util.maps.ObjectIntHashBiMap.*;
import static com.google.common.base.Preconditions.*;

import com.google.common.collect.Sets;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

public class IntObjectHashBiMap<V> {

	private static final class BiEntry<V> {

		final V value;
		final int key;
		final int keyHash;
		final int valueHash;

		BiEntry<V> nextInKToVBucket;

		BiEntry<V> nextInVToKBucket;

		BiEntry(int key, int keyHash, V value, int valueHash) {

			this.key = key;
			this.value = value;
			this.keyHash = keyHash;
			this.valueHash = valueHash;
		}
	}

	private transient BiEntry<V>[] hashTableKToV;
	private transient BiEntry<V>[] hashTableVToK;
	private transient int size;
	private transient int mask;
	private transient int modCount;

	protected IntObjectHashBiMap() {

	}

	public IntObjectHashBiMap(int expectedSize) {

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
	private void delete(BiEntry<V> entry) {

		int keyBucket = entry.keyHash & mask;
		BiEntry<V> prevBucketEntry = null;
		for (BiEntry<V> bucketEntry = hashTableKToV[keyBucket]; true; bucketEntry = bucketEntry.nextInKToVBucket) {
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
		for (BiEntry<V> bucketEntry = hashTableVToK[valueBucket];; bucketEntry = bucketEntry.nextInVToKBucket) {
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

	private void insert(BiEntry<V> entry) {

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

	private BiEntry<V> seekByKey(int key, int keyHash) {

		for (BiEntry<V> entry = hashTableKToV[keyHash & mask]; entry != null; entry = entry.nextInKToVBucket) {
			if (keyHash == entry.keyHash & key == entry.key) {
				return entry;
			}
		}
		return null;
	}

	private BiEntry<V> seekByValue(Object value, int valueHash) {

		for (BiEntry<V> entry = hashTableVToK[valueHash & mask]; entry != null; entry = entry.nextInVToKBucket) {
			if (valueHash == entry.valueHash && equal(value, entry.value)) {
				return entry;
			}
		}
		return null;
	}

	public boolean containsKey(int key) {

		return seekByKey(key, hash(key)) != null;
	}

	public boolean containsValue(Object value) {

		return seekByValue(value, hash(value)) != null;
	}

	public V get(int key) {

		BiEntry<V> entry = seekByKey(key, hash(key));
		return (entry == null) ? null : entry.value;
	}

	public V put(int key, V value) {

		return put(key, value, false, true);
	}

	public V forcePut(int key, V value) {

		return put(key, value, true, true);
	}

	public V putIfAbsent(int key, V value) {

		return put(key, value, false, false);
	}

	private V put(int key, V value, boolean force, boolean toss) {

		int keyHash = hash(key);
		int valueHash = hash(value);

		BiEntry<V> oldEntryForKey = seekByKey(key, keyHash);
		if (oldEntryForKey != null) {
			if (!toss)
				return oldEntryForKey.value;
			if (valueHash == oldEntryForKey.valueHash && equal(value, oldEntryForKey.value))
				return value;
		}

		BiEntry<V> oldEntryForValue = seekByValue(value, valueHash);
		if (oldEntryForValue != null) {
			if (force) {
				delete(oldEntryForValue);
			} else {
				if (toss)
					throw new IllegalArgumentException("value already present: " + value);
				return (oldEntryForKey == null) ? null : oldEntryForKey.value;
			}
		}

		if (oldEntryForKey != null) {
			delete(oldEntryForKey);
		}
		BiEntry<V> newEntry = new BiEntry<V>(key, keyHash, value, valueHash);
		insert(newEntry);
		rehashIfNecessary();
		return (oldEntryForKey == null) ? value : oldEntryForKey.value;
	}

	private int putInverse(V value, int key, boolean force, boolean toss) {

		int valueHash = hash(value);
		int keyHash = hash(key);

		BiEntry<V> oldEntryForValue = seekByValue(value, valueHash);
		if (oldEntryForValue != null) {
			if (!toss)
				return oldEntryForValue.key;
			if (keyHash == oldEntryForValue.keyHash && key == oldEntryForValue.key)
				return key;
		}

		BiEntry<V> oldEntryForKey = seekByKey(key, keyHash);
		if (oldEntryForKey != null) {
			if (force) {
				delete(oldEntryForKey);
			} else {
				if (toss)
					throw new IllegalArgumentException("value already present: " + key);
				return (oldEntryForValue == null) ? 0 : oldEntryForValue.key;
			}
		}

		if (oldEntryForValue != null) {
			delete(oldEntryForValue);
		}
		BiEntry<V> newEntry = new BiEntry<V>(key, keyHash, value, valueHash);
		insert(newEntry);
		rehashIfNecessary();
		return (oldEntryForValue == null) ? key : oldEntryForValue.key;
	}

	private void rehashIfNecessary() {

		BiEntry<V>[] oldKToV = hashTableKToV;
		if (needsResizing(size, oldKToV.length, LOAD_FACTOR)) {
			int newTableSize = oldKToV.length * 2;

			this.hashTableKToV = createTable(newTableSize);
			this.hashTableVToK = createTable(newTableSize);
			this.mask = newTableSize - 1;
			this.size = 0;

			for (int bucket = 0; bucket < oldKToV.length; bucket++) {
				BiEntry<V> entry = oldKToV[bucket];
				while (entry != null) {
					BiEntry<V> nextEntry = entry.nextInKToVBucket;
					insert(entry);
					entry = nextEntry;
				}
			}
			this.modCount++;
		}
	}

	@SuppressWarnings("unchecked")
	private BiEntry<V>[] createTable(int length) {

		return new BiEntry[length];
	}

	public V remove(int key) {

		BiEntry<V> entry = seekByKey(key, hash(key));
		if (entry == null) {
			return null;
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
		BiEntry<V> next = null;
		BiEntry<V> toRemove = null;
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

			BiEntry<V> entry = next;
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

		abstract T output(BiEntry<V> entry);
	}

	public Set<Integer> keySet() {

		return new KeySet();
	}

	private final class KeySet extends AbstractSet<Integer> {

		KeySet() {

		}

		@Override
		public Iterator<Integer> iterator() {

			return new Itr<Integer>() {

				@Override
				Integer output(BiEntry<V> entry) {

					return entry.key;
				}
			};
		}

		@Override
		public boolean remove(Object o) {

			if (!(o instanceof Number))
				return false;
			int k = ((Number) o).intValue();
			BiEntry<V> entry = seekByKey(k, hash(k));
			if (entry == null) {
				return false;
			} else {
				delete(entry);
				return true;
			}
		}

		@Override
		public int size() {

			return IntObjectHashBiMap.this.size();
		}
	}

	public Set<V> values() {

		return inverse().keySet();
	}

	public Set<Entry<Integer, V>> entrySet() {

		return new EntrySet();
	}

	class EntrySet extends AbstractSet<Entry<Integer, V>> {

		IntObjectHashBiMap<V> map() {

			return IntObjectHashBiMap.this;
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
				Object k = entry.getKey();
				if (k instanceof Number) {
					Number key = (Number) k;
					BiEntry<V> mapEntry = map().seekByKey(key.intValue(), hash(key));
					return mapEntry != null && equal(mapEntry.value, entry.getValue());
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
		public Iterator<Entry<Integer, V>> iterator() {

			return new Itr<Entry<Integer, V>>() {

				@Override
				Entry<Integer, V> output(BiEntry<V> entry) {

					return new MapEntry(entry);
				}

				class MapEntry implements Entry<Integer, V> {

					BiEntry<V> delegate;

					MapEntry(BiEntry<V> entry) {

						this.delegate = entry;
					}

					@Override
					public Integer getKey() {

						return delegate.key;
					}

					@Override
					public V getValue() {

						return delegate.value;
					}

					@Override
					public V setValue(V value) {

						V oldValue = delegate.value;
						int valueHash = hash(value);
						if (valueHash == delegate.valueHash && equal(value, oldValue)) {
							return value;
						}
						checkArgument(
							seekByValue(value, valueHash) == null, "value already present: %s", value);
						delete(delegate);
						BiEntry<V> newEntry = new BiEntry<V>(delegate.key, delegate.keyHash, value, valueHash);
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

	private transient ObjectIntHashBiMap<V> inverse;

	public ObjectIntHashBiMap<V> inverse() {

		return (inverse == null) ? inverse = new Inverse() : inverse;
	}

	private final class Inverse extends ObjectIntHashBiMap<V> {

		IntObjectHashBiMap<V> forward() {

			return IntObjectHashBiMap.this;
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

			return IntObjectHashBiMap.this.equal(a, b);
		}

		@Override
		public boolean containsKey(Object value) {

			return forward().containsValue(value);
		}

		@Override
		public int get(Object value) {

			BiEntry<V> entry = seekByValue(value, hash(value));
			return (entry == null) ? 0 : entry.key;
		}

		@Override
		public int put(V value, int key) {

			return putInverse(value, key, false, true);
		}

		@Override
		public int forcePut(V value, int key) {

			return putInverse(value, key, true, true);
		}

		@Override
		public int putIfAbsent(V value, int key) {

			return putInverse(value, key, false, false);
		}

		@Override
		public int remove(Object value) {

			BiEntry<V> entry = seekByValue(value, hash(value));
			if (entry == null) {
				return 0;
			} else {
				delete(entry);
				return entry.key;
			}
		}

		@Override
		public IntObjectHashBiMap<V> inverse() {

			return forward();
		}

		@Override
		public Set<V> keySet() {

			return new InverseKeySet();
		}

		private final class InverseKeySet extends AbstractSet<V> {

			InverseKeySet() {

			}

			@Override
			public boolean remove(Object o) {

				BiEntry<V> entry = seekByValue(o, hash(o));
				if (entry == null) {
					return false;
				} else {
					delete(entry);
					return true;
				}
			}

			@Override
			public Iterator<V> iterator() {

				return forward().new Itr<V>() {

					@Override
					V output(BiEntry<V> entry) {

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
		public Set<Integer> values() {

			return forward().keySet();
		}

		@Override
		public Set<Entry<V, Integer>> entrySet() {

			return Inverse.this.new EntrySet() {

				@Override
				ObjectIntHashBiMap<V> map() {

					return Inverse.this;
				}

				@Override
				public Iterator<Entry<V, Integer>> iterator() {

					return IntObjectHashBiMap.this.new Itr<Entry<V, Integer>>() {

						@Override
						Entry<V, Integer> output(BiEntry<V> entry) {

							return new InverseEntry(entry);
						}

						class InverseEntry implements Entry<V, Integer> {

							BiEntry<V> delegate;

							InverseEntry(BiEntry<V> entry) {

								this.delegate = entry;
							}

							@Override
							public V getKey() {

								return delegate.value;
							}

							@Override
							public Integer getValue() {

								return delegate.key;
							}

							@Override
							public Integer setValue(Integer key) {

								checkNotNull(key, "value cannot be null");
								int oldKey = delegate.key;
								int keyHash = hash(key);
								int k = key.intValue();
								if (keyHash == delegate.keyHash && k == oldKey) {
									return key;
								}
								checkArgument(seekByKey(k, keyHash) == null, "value already present: %s", key);
								delete(delegate);
								BiEntry<V> newEntry = new BiEntry<V>(k, keyHash, delegate.value, delegate.valueHash);
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

}
