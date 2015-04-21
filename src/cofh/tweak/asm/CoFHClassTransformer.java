package cofh.tweak.asm;

import net.minecraft.launchwrapper.IClassTransformer;

public class CoFHClassTransformer implements IClassTransformer {

	public CoFHClassTransformer() {

		ASMCore.init();
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {

		if (bytes == null) {
			return null;
		}

		int index = ASMCore.hashes.get(transformedName);
		if (index != 0) {
			bytes = ASMCore.transform(index, name, transformedName, bytes);
		}

		return bytes;
	}
}
