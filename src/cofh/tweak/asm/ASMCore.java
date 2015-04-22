package cofh.tweak.asm;

import static org.objectweb.asm.Opcodes.*;

import cofh.tweak.asmhooks.Config;

import gnu.trove.map.hash.TObjectByteHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class ASMCore {

	static void init() {

	}

	static Logger log = LogManager.getLogger("CoFHTweak ASM");

	static TObjectByteHashMap<String> hashes = new TObjectByteHashMap<String>(30, 1, (byte) 0);

	private static String HooksCore = "cofh/tweak/asmhooks/HooksCore";

	static {

		hashes.put("net.minecraft.util.LongHashMap", (byte) 1);
		if (!Config.lightChunks) {
			hashes.put("net.minecraft.world.chunk.Chunk", (byte) 2);
		}
		hashes.put("net.minecraft.client.Minecraft", (byte) 3);
		hashes.put("net.minecraft.entity.Entity", (byte) 4);
		hashes.put("cofh.tweak.asmhooks.HooksCore", (byte) 5);
		hashes.put("net.minecraft.entity.item.EntityItem", (byte) 6);
		hashes.put("net.minecraft.world.World", (byte) 7);
	}

	static byte[] transform(int index, String name, String transformedName, byte[] bytes) {

		ClassReader cr = new ClassReader(bytes);

		switch (index) {
		case 1:
			return alterLongHashMap(transformedName, bytes, cr);
		case 2:
			return alterChunk(transformedName, bytes, cr);
		case 3:
			return alterMinecraft(transformedName, bytes, cr);
		case 4:
			return alterEntity(transformedName, bytes, cr);
		case 5:
			return alterHooksCore(transformedName, bytes, cr);
		case 6:
			return alterEntityItem(transformedName, bytes, cr);
		case 7:
			return alterWorld(transformedName, bytes, cr);

		default:
			return bytes;
		}
	}

	private static byte[] alterWorld(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_72945_a" };
		} else {
			names = new String[] { "getCollidingBoundingBoxes" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		l: {
			MethodNode boundingBoxes = null;
			for (MethodNode n : cn.methods) {
				if (names[0].equals(n.name)) {
					boundingBoxes = n;
					break;
				}
			}

			if (boundingBoxes == null)
				break l;

			boundingBoxes.localVariables = null;

			boundingBoxes.instructions.clear();
			boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 0));
			boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 1));
			boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 2));
			String sig = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)"
					+ "Ljava/util/List;";
			boundingBoxes.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "getWorldCollisionBoxes", sig, false));
			boundingBoxes.instructions.add(new InsnNode(ARETURN));

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}

		return bytes;
	}

	private static byte[] alterHooksCore(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_70104_M" };
		} else {
			names = new String[] { "canBePushed" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		for (MethodNode m : cn.methods) {
			for (int i = 0, e = m.instructions.size(); i < e; ++i) {
				AbstractInsnNode n = m.instructions.get(i);
				if (n.getOpcode() == INVOKEVIRTUAL) {
					MethodInsnNode mn = (MethodInsnNode) n;
					if (names[0].equals(mn.name)) {
						mn.name = "cofh_collideCheck";
					}
				}
			}
		}

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		bytes = cw.toByteArray();

		return bytes;
	}

	private static byte[] alterEntityItem(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_85054_d" };
		} else {
			names = new String[] { "searchForOtherItemsNearby" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		l: {
			MethodNode m = null;
			for (MethodNode n : cn.methods) {
				if (names[0].equals(n.name)) {
					m = n;
					break;
				}
			}

			if (m == null) {
				break l;
			}

			m.localVariables = null;

			m.instructions.clear();
			m.instructions.add(new VarInsnNode(ALOAD, 0));
			m.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "stackItems",
					"(Lnet/minecraft/entity/item/EntityItem;)V", false));
			m.instructions.add(new InsnNode(RETURN));

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}

		return bytes;
	}

	private static byte[] alterEntity(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_70091_d", "func_72945_a", "func_70104_M" };
		} else {
			names = new String[] { "moveEntity", "getCollidingBoundingBoxes", "canBePushed" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		String mOwner = "net/minecraft/world/World";

		l: {
			MethodNode m = null;
			boolean hasMethod = false;
			for (MethodNode n : cn.methods) {
				if (names[0].equals(n.name)) {
					m = n;
				} else if ("cofh_collideCheck".equals(n.name)) {
					hasMethod = true;
				}
			}

			if (m == null) {
				break l;
			}

			for (int i = 0, e = m.instructions.size(); i < e; ++i) {
				AbstractInsnNode n = m.instructions.get(i);
				if (n.getOpcode() == INVOKEVIRTUAL) {
					MethodInsnNode mn = (MethodInsnNode) n;
					if (mOwner.equals(mn.owner) && names[1].equals(mn.name)) {
						mn.setOpcode(INVOKESTATIC);
						mn.owner = HooksCore;
						mn.desc = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;";
						mn.name = "getEntityCollisionBoxes";
					}
				} else if (n.getOpcode() == INVOKESTATIC) {
					MethodInsnNode mn = (MethodInsnNode) n;
					if ("cofh/asmhooks/HooksCore".equals(mn.owner) && "getEntityCollisionBoxes".equals(mn.name)) {
						mn.owner = HooksCore;
					}
				}
			}

			if (!hasMethod) {
				m = new MethodNode(ACC_PUBLIC, "cofh_collideCheck", "()Z", null, null);
				cn.methods.add(m);
				m.instructions.insert(new InsnNode(IRETURN));
				m.instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, name, names[2], "()Z", false));
				m.instructions.insert(new VarInsnNode(ALOAD, 0));
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}

		return bytes;
	}

	private static byte[] alterMinecraft(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_71407_l", "func_110550_d" };
		} else {
			names = new String[] { "runTick", "tick" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		String mOwner = "net/minecraft/client/renderer/texture/TextureManager";

		l: {
			boolean updated = false;
			mc: for (MethodNode m : cn.methods) {
				String mName = m.name;
				if (names[0].equals(mName) && "()V".equals(m.desc)) {
					updated = true;
					for (int i = 0, e = m.instructions.size(); i < e; ++i) {
						AbstractInsnNode n = m.instructions.get(i);
						if (n.getOpcode() == INVOKEVIRTUAL) {
							MethodInsnNode mn = (MethodInsnNode) n;
							if (mOwner.equals(mn.owner) && names[1].equals(mn.name) && "()V".equals(mn.desc)) {
								m.instructions.set(mn, new MethodInsnNode(INVOKESTATIC, HooksCore, "tickTextures",
										"(Lnet/minecraft/client/renderer/texture/ITickable;)V", false));
								break mc;
							}
						}
					}
				}
			}

			if (!updated) {
				break l;
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

	private static byte[] alterChunk(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_150803_c", "field_76650_s" };
		} else {
			names = new String[] { "recheckGaps", "isGapLightingUpdated" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		l: {
			boolean updated = false;
			for (MethodNode m : cn.methods) {
				String mName = m.name;
				if (names[0].equals(mName) && "(Z)V".equals(m.desc)) {
					updated = true;
					for (int i = 0, e = m.instructions.size(); i < e; ++i) {
						AbstractInsnNode n = m.instructions.get(i);
						if (n.getOpcode() == RETURN) {
							m.instructions.insertBefore(n, new VarInsnNode(ALOAD, 0));
							m.instructions.insertBefore(n, new InsnNode(ICONST_0));
							m.instructions.insertBefore(n, new FieldInsnNode(PUTFIELD, name, names[1], "Z"));
							break;
						}
					}
				}
			}

			if (!updated) {
				break l;
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

	private static byte[] alterLongHashMap(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_76155_g", "func_76160_c", "func_76161_b" };
		} else {
			names = new String[] { "getHashedKey", "getEntry", "containsItem" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, ClassReader.EXPAND_FRAMES);

		l: {
			boolean updated = false;
			MethodNode getEntry = null, containsItem = null;
			for (MethodNode m : cn.methods) {
				String mName = m.name;
				if (names[0].equals(mName) && "(J)I".equals(m.desc)) {
					updated = true;
					for (int i = 0, e = m.instructions.size(); i < e; ++i) {
						AbstractInsnNode n = m.instructions.get(i);
						if (n.getOpcode() == LXOR) {
							m.instructions.insertBefore(n, new LdcInsnNode(new Long(13L)));
							m.instructions.insertBefore(n, new InsnNode(LMUL));
							break;
						}
					}
					if (containsItem != null) {
						break;
					}
				} else if (names[2].equals(mName) && "(J)Z".equals(m.desc)) {
					containsItem = m;
					if (updated) {
						break;
					}
				}
			}

			mc: if (containsItem != null) {
				// { cloning methods to get a different set of instructions to avoid erasing getEntry
				ClassNode clone = new ClassNode(ASM5);
				cr.accept(clone, ClassReader.EXPAND_FRAMES);
				String sig = "(J)Lnet/minecraft/util/LongHashMap$Entry;";
				for (MethodNode m : clone.methods) {
					String mName = m.name;
					if (names[1].equals(mName) && sig.equals(m.desc)) {
						getEntry = m;
						break;
					}
				}
				// }
				if (getEntry == null) {
					break mc;
				}
				updated = true;
				containsItem.instructions.clear();
				containsItem.instructions.add(getEntry.instructions);
				/**
				 * this looks counter intuitive (replacing getEntry != null
				 * check with the full method) but due to how the JVM handles
				 * inlining, this needs to
				 * be done manually
				 */
				for (AbstractInsnNode n = containsItem.instructions.get(0); n != null; n = n.getNext()) {
					if (n.getOpcode() == ARETURN) {
						AbstractInsnNode n2 = n.getPrevious();
						if (n2.getOpcode() == ACONST_NULL) {
							containsItem.instructions.set(n2, new InsnNode(ICONST_0));
						} else {
							containsItem.instructions.set(n2, new InsnNode(ICONST_1));
						}
						containsItem.instructions.set(n, n = new InsnNode(IRETURN));
					}
				}
			}

			if (!updated) {
				break l;
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

}
