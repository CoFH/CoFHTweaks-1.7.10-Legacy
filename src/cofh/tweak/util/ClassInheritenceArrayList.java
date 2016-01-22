package cofh.tweak.util;

import static com.google.common.base.Preconditions.checkNotNull;

import cofh.repack.java.util.ShiftingBitSet;
import cofh.tweak.util.maps.ObjectIntHashBiMap;
import com.google.common.base.Throwables;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.ClassUtils;

public class ClassInheritenceArrayList<E> extends ArrayList<E> {

	private static final long serialVersionUID = 693698853710457585L;

	private static final IdentityHashMap<Class<?>, BitSet> classMapping = new IdentityHashMap<Class<?>, BitSet>();
	private static final ObjectIntHashBiMap<Class<?>> classIds = new ObjectIntHashBiMap<Class<?>>(32) {

		@Override
		protected final boolean equal(Object a, Object b) {

			return a == b;
		}
	};
	static {
		classIds.put(null, 0);
		BitSet nil = new BitSet();
		nil.set(0);
		classMapping.put(null, nil);
	}

	private synchronized static void compileClass(Class<?> clazz) {

		BitSet classData = new BitSet();
		classData.set(classIds.putIfAbsent(clazz, classIds.size()));
		int newClass = classIds.size(), id;
		for (Class<?> t : ClassUtils.hierarchy(clazz, ClassUtils.Interfaces.INCLUDE)) {
			id = classIds.putIfAbsent(t, newClass);
			if (id == newClass) {
				compileClass(t);
				newClass = classIds.size();
			}
			if (t != clazz) {
				classData.or(classMapping.get(t));
				if (!t.isInterface()) {
					// superclass that we've already iterated
					break;
				}
			}
		}
		classMapping.put(clazz, classData);
	}

	private int size;
	private BitSet containedClasses = new BitSet();
	private TIntObjectHashMap<ShiftingBitSet> classIndexes = new TIntObjectHashMap<ShiftingBitSet>();

	// { Constructors
	public ClassInheritenceArrayList() {

		this(8);
	}

	public ClassInheritenceArrayList(int initialCapacity) {

		super();
		elementData = new Object[initialCapacity];
		setSuperElementData();
	}

	public ClassInheritenceArrayList(Collection<? extends E> c) {

		this(c.size());
		addAll(c);
	}

	// }

	private void addClasses(int index, Class<?> clazz) {

		if (!classIds.containsKey(clazz)) {
			compileClass(clazz);
		}
		BitSet classes = classMapping.get(clazz);
		containedClasses.or(classes);
		int i = -1;
		do {
			i = classes.nextSetBit(++i);
			if (i < 0) {
				break;
			}
			ShiftingBitSet indexes = classIndexes.get(i);
			if (indexes == null) {
				classIndexes.put(i, indexes = new ShiftingBitSet());
			}
			indexes.set(index);
		} while (true);
	}

	private void removeClasses(int index, Class<?> clazz) {

		BitSet classes = classMapping.get(clazz);
		int i = -1;
		do {
			i = classes.nextSetBit(++i);
			if (i < 0) {
				break;
			}
			ShiftingBitSet indexes = classIndexes.get(i);
			indexes.clear(index);
			if (indexes.cardinality() == 0) {
				classIndexes.remove(i);
				containedClasses.clear(i);
			}
		} while (true);
	}

	@Override
	public boolean add(E element) {

		addClasses(size(), checkNotNull(element, "element cannot be null").getClass());
		ensureCapacity(size + 1);  // Increments modCount!!
		elementData[size++] = element;
		return true;
	}

	@Override
	public void add(int index, E element) {

		RangeCheck(index, false);
		if (index == size()) {
			; // no-op: bitsets still valid
		} else {
			int i = -1;
			do {
				i = containedClasses.nextSetBit(++i);
				if (i < 0) {
					break;
				}
				ShiftingBitSet indexes = classIndexes.get(i);
				if (indexes.nextSetBit(index) >= index) {
					indexes.shiftRight(index);
				} else {
					; // no-op: bitset still valid
				}
			} while (true);
		}
		addClasses(index, checkNotNull(element, "element cannot be null").getClass());

		ensureCapacity(size + 1);  // Increments modCount!!
		if (index < size)
			System.arraycopy(elementData, index, elementData, index + 1, size - index);
		elementData[index] = element;
		size++;
	}

	@Override
	public E set(int index, E element) {

		RangeCheck(index, true);
		checkNotNull(element, "element cannot be null");

		@SuppressWarnings("unchecked")
		E oldValue = (E) elementData[index];
		removeClasses(index, oldValue.getClass());
		elementData[index] = element;
		addClasses(index, element.getClass());
		return oldValue;
	}

	@Override
	public E remove(int index) {

		RangeCheck(index, true);
		return internalRemove(index);
	}

	@Override
	public boolean remove(Object o) {

		int id = o == null ? 0 : classIds.get(o.getClass());
		if (!containedClasses.get(id))
			return false;
		int size = size();
		if (o == null) {
			for (int index = 0; index < size; index++)
				if (elementData[index] == null) {
					internalRemove(index);
					return true;
				}
		} else {
			for (int index = 0; index < size; index++)
				if (o.equals(elementData[index])) {
					internalRemove(index);
					return true;
				}
		}
		return false;
	}

	private E internalRemove(int index) {

		modCount++;
		@SuppressWarnings("unchecked")
		E o = (E) elementData[index];

		int numMoved = size - index - 1;
		if (numMoved > 0)
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		elementData[--size] = null; // Let gc do its work

		removeClasses(index, o.getClass());
		if (index == size()) {
			; // no-op: bitsets still valid
		} else {
			int i = -1;
			do {
				i = containedClasses.nextSetBit(++i);
				if (i < 0) {
					break;
				}
				ShiftingBitSet indexes = classIndexes.get(i);
				if (indexes.nextSetBit(index) >= index) {
					indexes.shiftLeft(index);
				} else {
					; // no-op: bitset still valid
				}
			} while (true);
		}
		return o;
	}

	public <T> Iterator<T> getIteratorFor(Class<?> clazz) {

		int id = classIds.get(clazz);
		if (containedClasses.get(id))
			return new Itr<T>(classIndexes.get(id));
		return null;
	}

	private class Itr<T> implements Iterator<T> {

		final ShiftingBitSet cursor;

		int nextRet = -1;
		int lastRet = -1;

		int expectedModCount = modCount;

		boolean movedNext = false;

		public Itr(ShiftingBitSet indicies) {

			cursor = indicies;
		}

		@Override
		public boolean hasNext() {

			return nextRet > lastRet || (nextRet = cursor.nextSetBit(++nextRet)) > lastRet;
		}

		@Override
		public T next() {

			checkForComodification();
			try {
				@SuppressWarnings("unchecked")
				T next = (T) get(lastRet = nextRet);
				movedNext = true;
				return next;
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove() {

			if (!movedNext)
				throw new IllegalStateException();
			checkForComodification();

			try {
				ClassInheritenceArrayList.this.remove(lastRet);
				movedNext = false;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		final void checkForComodification() {

			if (modCount != expectedModCount)
				throw new ConcurrentModificationException();
		}
	}

	@Override
	public int indexOf(Object o) {

		int id = o == null ? 0 : classIds.get(o.getClass());
		if (!containedClasses.get(id))
			return -1;
		int size = size();
		if (o == null) {
			for (int i = 0; i < size; i++)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = 0; i < size; i++)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {

		int id = o == null ? 0 : classIds.get(o.getClass());
		if (!containedClasses.get(id))
			return -1;
		int size = size();
		if (o == null) {
			for (int i = size - 1; i >= 0; i--)
				if (elementData[i] == null)
					return i;
		} else {
			for (int i = size - 1; i >= 0; i--)
				if (o.equals(elementData[i]))
					return i;
		}
		return -1;
	}

	// { Run these through regular add methods to make our logic simpler
	@Override
	public boolean addAll(Collection<? extends E> c) {

		if (c.size() == 0) {
			return false;
		}
		boolean modified = false;
		for (E e : c) {
			modified |= add(e);
		}
		return modified;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {

		if (c.size() == 0) {
			return false;
		}

		for (E e : c) {
			add(index++, e);
		}

		return true;
	}

	// }

	private void RangeCheck(int index, boolean atSize) {

		int size = size();
		if (index < 0 | index > size | (atSize & index == size)) {
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
	}

	// { Yay, ArrayList.elementData and ArrayList.size are private!
	private static final Field superElementData;
	static {
		Field a = null;
		try {
			a = ArrayList.class.getDeclaredField("elementData");
			a.setAccessible(true);
		} catch (Exception e) {
			Throwables.propagate(e);
		}
		superElementData = a;
	}

	private final void setSuperElementData() {

		try {
			superElementData.set(this, elementData);
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}

	@Override
	public Object[] toArray() {

		return Arrays.copyOf(elementData, size);
	}

	@Override
	public <T> T[] toArray(T[] a) {

		if (a.length < size)
			// Make a new array of a's runtime type, but my contents:
			return (T[]) Arrays.copyOf(elementData, size, a.getClass());
		System.arraycopy(elementData, 0, a, 0, size);
		if (a.length > size)
			a[size] = null;
		return a;
	}

	@Override
	public int size() {

		return size;
	}

	@Override
	public boolean isEmpty() {

		return size == 0;
	}

	private transient Object[] elementData;

	@Override
	public void clear() {

		modCount++;

		// Let gc do its work
		for (int i = 0; i < size; i++)
			elementData[i] = null;

		size = 0;
	}

	@Override
	public void trimToSize() {

		modCount++;
		int oldCapacity = elementData.length;
		if (size < oldCapacity) {
			elementData = Arrays.copyOf(elementData, size);
			setSuperElementData();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public E get(int index) {

		RangeCheck(index, true);

		return (E) elementData[index];
	}

	@Override
	public void ensureCapacity(int minCapacity) {

		modCount++;
		int oldCapacity = elementData.length;
		if (minCapacity > oldCapacity) {
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if (newCapacity < minCapacity)
				newCapacity = minCapacity;
			// minCapacity is usually close to size, so this is a win:
			elementData = Arrays.copyOf(elementData, newCapacity);
			setSuperElementData();
		}
	}

	@Override
	protected void removeRange(int fromIndex, int toIndex) {

		modCount++;
		int numMoved = size - toIndex;
		System.arraycopy(elementData, toIndex, elementData, fromIndex,
			numMoved);

		// Let gc do its work
		int newSize = size - (toIndex - fromIndex);
		while (size != newSize)
			elementData[--size] = null;
	}

	@Override
	public Iterator<E> iterator() {

		return new Itr1();
	}

	@Override
	public ListIterator<E> listIterator() {

		return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(final int index) {

		RangeCheck(index, false);

		return new ListItr(index);
	}

	private class Itr1 implements Iterator<E> {

		int cursor = 0;
		int lastRet = -1;
		int expectedModCount = modCount;

		@Override
		public boolean hasNext() {

			return cursor != size();
		}

		@Override
		public E next() {

			checkForComodification();
			try {
				int i = cursor;
				E next = get(i);
				lastRet = i;
				cursor = i + 1;
				return next;
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove() {

			if (lastRet < 0)
				throw new IllegalStateException();
			checkForComodification();

			try {
				ClassInheritenceArrayList.this.remove(lastRet);
				if (lastRet < cursor)
					cursor--;
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		final void checkForComodification() {

			if (modCount != expectedModCount)
				throw new ConcurrentModificationException();
		}
	}

	private class ListItr extends Itr1 implements ListIterator<E> {

		ListItr(int index) {

			cursor = index;
		}

		@Override
		public boolean hasPrevious() {

			return cursor != 0;
		}

		@Override
		public E previous() {

			checkForComodification();
			try {
				int i = cursor - 1;
				E previous = get(i);
				lastRet = cursor = i;
				return previous;
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public int nextIndex() {

			return cursor;
		}

		@Override
		public int previousIndex() {

			return cursor - 1;
		}

		@Override
		public void set(E e) {

			if (lastRet < 0)
				throw new IllegalStateException();
			checkForComodification();

			try {
				ClassInheritenceArrayList.this.set(lastRet, e);
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {

			checkForComodification();

			try {
				int i = cursor;
				ClassInheritenceArrayList.this.add(i, e);
				lastRet = -1;
				cursor = i + 1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {

		return batchRemove(checkNotNull(c), false);
	}

	@Override
	public boolean retainAll(Collection<?> c) {

		return batchRemove(checkNotNull(c), true);
	}

	private boolean batchRemove(Collection<?> c, boolean complement) {

		final Object[] elementData = this.elementData;
		int r = 0, w = 0;
		boolean modified = false;
		try {
			for (; r < size; r++)
				if (c.contains(elementData[r]) == complement)
					elementData[w++] = elementData[r];
		} finally {
			if (r != size) {
				System.arraycopy(elementData, r,
					elementData, w,
					size - r);
				w += size - r;
			}
			if (w != size) {
				// clear to let GC do its work
				for (int i = w; i < size; i++)
					elementData[i] = null;
				modCount += size - w;
				size = w;
				modified = true;
			}
		}
		return modified;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object clone() {

		ClassInheritenceArrayList<E> v = (ClassInheritenceArrayList<E>) super.clone();
		for (Object e : elementData)
			v.add((E) e);
		v.modCount = 0;
		return v;
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

		int expectedModCount = modCount;
		s.defaultWriteObject();

		s.writeInt(size());

		for (int i = 0, size = size(); i < size; i++) {
			s.writeObject(elementData[i]);
		}

		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {

		clear();
		trimToSize();

		s.defaultReadObject();

		int size = s.readInt();

		if (size > 0) {
			ensureCapacity(size);

			for (int i = 0; i < size; i++) {
				add((E) s.readObject());
			}
		}
	}
	// }

}
