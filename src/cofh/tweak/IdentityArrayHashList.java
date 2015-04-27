package cofh.tweak;

import cofh.repack.cofh.lib.util.ArrayHashList;

import java.util.Collection;

@SuppressWarnings("unchecked")
public class IdentityArrayHashList<E extends Object> extends ArrayHashList<E> {

	private static final long serialVersionUID = -3917299756249384163L;

	public IdentityArrayHashList() {

		super();
	}

	public IdentityArrayHashList(int size) {

		super(size);
	}

	public IdentityArrayHashList(Collection<E> col) {

		super(col);
	}

	@Override
	protected Entry seek(Object obj, int hash) {

		for (Entry entry = hashTable[hash & mask]; entry != null; entry = entry.nextInBucket) {
			if (obj == entry.key) {
				return entry;
			}
		}

		return null;
	}

}
