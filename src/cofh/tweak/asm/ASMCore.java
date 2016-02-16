package cofh.tweak.asm;

import static org.objectweb.asm.Opcodes.*;

import cofh.tweak.asmhooks.Config;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.Side;

import gnu.trove.map.hash.TObjectByteHashMap;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ASMCore {

	static void init() {

	}

	static Logger log = LogManager.getLogger("CoFHTweak ASM");

	static TObjectByteHashMap<String> hashes = new TObjectByteHashMap<String>(30, 1, (byte) 0);

	private static String HooksCore = "cofh/tweak/asmhooks/HooksCore";
	private static String ConfigCore = "cofh/tweak/asmhooks/Config";

	static {

		hashes.put("net.minecraft.util.LongHashMap", (byte) 1);
		hashes.put("net.minecraft.world.chunk.Chunk", (byte) 2);
		hashes.put("net.minecraft.client.Minecraft", (byte) 3);
		hashes.put("net.minecraft.entity.Entity", (byte) 4);
		hashes.put("cofh.tweak.asmhooks.HooksCore", (byte) 5);
		hashes.put("net.minecraft.entity.item.EntityItem", (byte) 6);
		hashes.put("net.minecraft.world.World", (byte) 7);
		hashes.put("net.minecraft.entity.EntityLiving", (byte) 8);
		hashes.put("net.minecraft.client.multiplayer.WorldClient", (byte) 9);
		hashes.put("net.minecraft.client.multiplayer.ChunkProviderClient", (byte) 10);
		hashes.put("cpw.mods.fml.common.FMLCommonHandler", (byte) 11);
		hashes.put("net.minecraft.entity.passive.EntitySquid", (byte) 12);
		hashes.put("net.minecraft.entity.passive.EntityWaterMob", (byte) 13);
		hashes.put("net.minecraft.entity.ai.EntityAIFollowParent", (byte) 14);
		hashes.put("net.minecraft.network.NetHandlerPlayServer", (byte) 15);
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
		case 8:
			return alterEntityLiving(transformedName, bytes, cr);
		case 9:
			return alterWorldClient(transformedName, bytes, cr);
		case 10:
			return alterCPC(transformedName, bytes, cr);
		case 11:
			return alterbranding(transformedName, bytes, cr);
		case 12:
			return alterSquid(transformedName, bytes, cr);
		case 13:
			return alterWaterMob(transformedName, bytes, cr);
		case 14:
			return alterFollowParent(transformedName, bytes, cr);
		case 15:
			return fixNetHandlerPlayServer(transformedName, bytes, cr);

		default:
			return bytes;
		}
	}

	private static byte[] fixNetHandlerPlayServer(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_147233_a", "func_76319_b", "func_76320_a" };
		} else {
			names = new String[] { "onNetworkTick", "endSection", "startSection" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		l: for (MethodNode m : cn.methods) {
			if (names[0].equals(m.name)) {
				InsnList getProfiler = new InsnList();
				for (int i = 0, e = m.instructions.size(); i < e; ++i) {
					AbstractInsnNode n = m.instructions.get(i);
					c: if (n.getOpcode() == INVOKEVIRTUAL) {
						MethodInsnNode mn = (MethodInsnNode) n;
						if (!"net/minecraft/profiler/Profiler".equals(mn.owner)) {
							break c;
						}
						if (names[1].equals(mn.name)) {
							break l;
						} else if (names[2].equals(mn.name) && getProfiler.size() == 0) {
							for (n = n.getPrevious().getPrevious(); n.getOpcode() != ALOAD; n = n.getPrevious()) {
								getProfiler.insert(n.clone(null));
							}
							getProfiler.insert(n.clone(null));
						}
					}
				}

				if (getProfiler.size() == 0) {
					break l;
				}
				getProfiler.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/profiler/Profiler", names[1], "()V", false));

				for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
					if (n.getOpcode() == RETURN) {
						for (AbstractInsnNode o = getProfiler.getFirst(); o != null; o = o.getNext()) {
							m.instructions.insertBefore(n, o.clone(null));
						}
					}
				}
				break l;
			}
		}

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		bytes = cw.toByteArray();

		return bytes;
	}

	// TODO: alterWorldServer. improve block random ticking. add hasRandomTicks to Chunk

	private static byte[] alterWorld(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_72945_a", "func_72903_x",
					"func_147451_t", "func_98179_a", "func_147463_c", "func_72964_e" };
		} else {
			names = new String[] { "getCollidingBoundingBoxes", "setActivePlayerChunksAndCheckLight",
					"func_147451_t", "computeLightValue", "updateLightByType", "getChunkFromChunkCoords" };
		}

		// TODO: override entity and tile entity ticking to perform per-chunk

		// add active set to IdentityHashList, guard with if(remove(...)) to protect against new additions during tick
		// add hasTileEntities to Chunk to skip over them? group them by chunk location?

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		{
			MethodNode boundingBoxes = null;
			MethodNode playerCheckLight = null;
			MethodNode computeLightValue = null;
			MethodNode updateLightByType = null;
			MethodNode getChunkFromCache = null;
			int m = 0, T = 5;
			for (MethodNode n : cn.methods) {
				if (names[0].equals(n.name)) {
					boundingBoxes = n;
					if (++m == T) break;
				} else if (names[1].equals(n.name)) {
					playerCheckLight = n;
					if (++m == T) break;
				} else if (names[3].equals(n.name)) {
					computeLightValue = n;
					if (++m == T) break;
				} else if (names[4].equals(n.name)) {
					updateLightByType = n;
					if (++m == T) break;
				} else if (names[5].equals(n.name)) {
					getChunkFromCache = n;
					if (++m == T) break;
				}
			}

			if (updateLightByType != null) {
				updateLightByType.localVariables = null;

				updateLightByType.instructions.clear();
				updateLightByType.instructions.add(new VarInsnNode(ALOAD, 0));
				updateLightByType.instructions.add(new VarInsnNode(ALOAD, 1));
				updateLightByType.instructions.add(new VarInsnNode(ILOAD, 2));
				updateLightByType.instructions.add(new VarInsnNode(ILOAD, 3));
				updateLightByType.instructions.add(new VarInsnNode(ILOAD, 4));
				String sig = "(Lnet/minecraft/world/World;Lnet/minecraft/world/EnumSkyBlock;III)Z";
				updateLightByType.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "updateLightByType", sig, false));
				updateLightByType.instructions.add(new InsnNode(IRETURN));
			}

			if (computeLightValue != null) {
				computeLightValue.localVariables = null;

				computeLightValue.instructions.clear();
				computeLightValue.instructions.add(new VarInsnNode(ALOAD, 0));
				computeLightValue.instructions.add(new VarInsnNode(ILOAD, 1));
				computeLightValue.instructions.add(new VarInsnNode(ILOAD, 2));
				computeLightValue.instructions.add(new VarInsnNode(ILOAD, 3));
				computeLightValue.instructions.add(new VarInsnNode(ALOAD, 4));
				String sig = "(Lnet/minecraft/world/World;IIILnet/minecraft/world/EnumSkyBlock;)I";
				computeLightValue.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "computeLightValue", sig, false));
				computeLightValue.instructions.add(new InsnNode(IRETURN));
			}

			if (boundingBoxes != null) {
				boundingBoxes.localVariables = null;

				boundingBoxes.instructions.clear();
				boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 0));
				boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 1));
				boundingBoxes.instructions.add(new VarInsnNode(ALOAD, 2));
				String sig = "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)"
						+ "Ljava/util/List;";
				boundingBoxes.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "getWorldCollisionBoxes", sig, false));
				boundingBoxes.instructions.add(new InsnNode(ARETURN));
			}

			if (getChunkFromCache != null) {
				getChunkFromCache.localVariables = null;

				LabelNode lEnd = new LabelNode();
				InsnList list = new InsnList();
				list.add(new FieldInsnNode(GETSTATIC, HooksCore, "chunkCache", "Lcofh/tweak/asmhooks/world/ChunkCache;"));
				list.add(new InsnNode(DUP));
				list.add(new JumpInsnNode(IFNULL, lEnd));
				list.add(new VarInsnNode(ILOAD, 1));
				list.add(new VarInsnNode(ILOAD, 2));
				list.add(new MethodInsnNode(INVOKEVIRTUAL, "cofh/tweak/asmhooks/world/ChunkCache", "getChunk", "(II)Lnet/minecraft/world/chunk/Chunk;", false));
				list.add(new InsnNode(DUP));
				list.add(new JumpInsnNode(IFNULL, lEnd));
				list.add(new InsnNode(ARETURN));
				list.add(lEnd);
				list.add(new FrameNode(F_SAME1, 0, null, 0, new Object[] { "java/lang/Object" }));
				list.add(new InsnNode(POP));

				getChunkFromCache.instructions.insert(list);
			}

			if (playerCheckLight != null) {
				boolean found = false;
				for (AbstractInsnNode n = playerCheckLight.instructions.getLast(); n != null; n = n.getPrevious()) {
					if (found && n.getOpcode() == INVOKEINTERFACE) {
						if ("isEmpty".equals(((MethodInsnNode) n).name)) {
							playerCheckLight.instructions.insert(n, new InsnNode(IOR));
							playerCheckLight.instructions.insert(n, new InsnNode(IXOR));
							playerCheckLight.instructions.insert(n, new InsnNode(ICONST_1));
							playerCheckLight.instructions.insert(n, new FieldInsnNode(GETSTATIC, ConfigCore, "lightChunks", "Z"));
							break;
						}
					}
					if (n.getOpcode() == INVOKEVIRTUAL) {
						if (names[2].equals(((MethodInsnNode) n).name))
							found = true;
					}
				}
			}

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
		cr.accept(cn, 0);

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

	private static byte[] alterbranding(String name, byte[] bytes, ClassReader cr) {

		String names = "computeBranding";

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		l: for (MethodNode m : cn.methods) {
			if (!names.equals(m.name))
				continue;
			for (int i = 0, e = m.instructions.size(); i < e; ++i) {
				AbstractInsnNode n = m.instructions.get(i);
				if (n.getType() == AbstractInsnNode.METHOD_INSN) {
					MethodInsnNode mn = (MethodInsnNode) n;
					if (!"callForgeMethod".equals(mn.name)) {
						continue;
					}
					if (!"cpw/mods/fml/common/FMLCommonHandler".equals(mn.owner)) {
						continue;
					}
					if (!"(Ljava/lang/String;)Ljava/lang/Object;".equals(mn.desc)) {
						continue;
					}
					mn = new MethodInsnNode(INVOKESTATIC, HooksCore, "getBrand", "()Ljava/lang/String;", false);
					m.instructions.insert(n.getNext().getNext(), mn);
					m.instructions.insertBefore(n = mn, new VarInsnNode(ALOAD, 1));
					mn = new MethodInsnNode(INVOKEVIRTUAL, null, "add", null, false);
					mn.owner = "com/google/common/collect/ImmutableList$Builder";
					mn.desc = "(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;";
					m.instructions.insert(n, mn);
					m.instructions.insert(mn, new InsnNode(POP));
					break l;
				}
			}
		}

		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cn.accept(cw);
		bytes = cw.toByteArray();

		return bytes;
	}

	private static byte[] alterCPC(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_73158_c" };
		} else {
			names = new String[] { "loadChunk" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

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

			for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n == null ? null : n.getNext()) {
				if (n.getOpcode() == NEW) {
					if ("net/minecraft/world/chunk/Chunk".equals(((TypeInsnNode) n).desc)) {
						((TypeInsnNode) n).desc = "cofh/tweak/asmhooks/world/ClientChunk";
						for (; n != null; n = n.getNext()) {
							if (n.getOpcode() == INVOKESPECIAL) {
								((MethodInsnNode) n).owner = "cofh/tweak/asmhooks/world/ClientChunk";
								break;
							}
						}
					}
				}
			}

			ClassWriter cw = new ClassWriter(0);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

	private static byte[] alterWorldClient(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_147492_c" };
		} else {
			names = new String[] { "func_147492_c" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

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
			m.instructions.add(new VarInsnNode(ILOAD, 1));
			m.instructions.add(new VarInsnNode(ILOAD, 2));
			m.instructions.add(new VarInsnNode(ILOAD, 3));
			m.instructions.add(new VarInsnNode(ALOAD, 4));
			m.instructions.add(new VarInsnNode(ILOAD, 5));
			String sig = "(Lnet/minecraft/client/multiplayer/WorldClient;IIILnet/minecraft/block/Block;I)Z";
			m.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "setClientBlock", sig, false));
			m.instructions.add(new InsnNode(IRETURN));

			ClassWriter cw = new ClassWriter(0);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

	private static byte[] alterFollowParent(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_75250_a", "func_75253_b" };
		} else {
			names = new String[] { "shouldExecute", "continueExecuting" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		{
			MethodNode shouldExecute = null;
			MethodNode continueExecute = null;
			for (MethodNode n : cn.methods) {
				if (names[0].equals(n.name)) {
					shouldExecute = n;
				} else if (names[1].equals(n.name)) {
					continueExecute = n;
				}
			}

			if (shouldExecute != null) {
				shouldExecute.localVariables = null;

				shouldExecute.instructions.clear();
				shouldExecute.instructions.add(new VarInsnNode(ALOAD, 0));
				String sig = "(Lnet/minecraft/entity/ai/EntityAIFollowParent;)Z";
				shouldExecute.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "shouldChildFollowParent", sig, false));
				shouldExecute.instructions.add(new InsnNode(IRETURN));
			}

			if (continueExecute != null) {
				continueExecute.localVariables = null;

				continueExecute.instructions.clear();
				continueExecute.instructions.add(new VarInsnNode(ALOAD, 0));
				String sig = "(Lnet/minecraft/entity/ai/EntityAIFollowParent;)Z";
				continueExecute.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "shouldChildContinueFollowParent", sig, false));
				continueExecute.instructions.add(new InsnNode(IRETURN));
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}

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
		cr.accept(cn, 0);

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

			ClassWriter cw = new ClassWriter(0);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}

		return bytes;
	}

	private static byte[] alterSquid(String name, byte[] bytes, ClassReader cr) {

		@SuppressWarnings("unused")
		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_85033_bc" };
		} else {
			names = new String[] { "collideWithNearbyEntities" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		boolean hasMethod = false, hasPush = false;
		for (MethodNode n : cn.methods) {
			if ("cofh_collideCheck".equals(n.name)) {
				hasMethod = true;
			} else if (names[0].equals(n.name)) {
				hasPush = true;
			}
		}

		if (!hasMethod) {
			MethodNode m = new MethodNode(ACC_PUBLIC, "cofh_collideCheck", "()Z", null, null);
			cn.methods.add(m);
			m.instructions.add(new InsnNode(ICONST_0));
			m.instructions.add(new InsnNode(IRETURN));
		}

		if (!hasPush) {
			MethodNode m = new MethodNode(ACC_PUBLIC, names[0], "()V", null, null);
			cn.methods.add(m);
			m.instructions.add(new InsnNode(RETURN));
		}

		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		bytes = cw.toByteArray();

		return bytes;
	}

	private static byte[] alterWaterMob(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_70692_ba" };
		} else {
			names = new String[] { "canDespawn" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		for (Iterator<MethodNode> i = cn.methods.iterator(); i.hasNext();) {
			MethodNode n = i.next();
			if (names[0].equals(n.name)) {
				i.remove();
			}
		}

		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		bytes = cw.toByteArray();

		return bytes;
	}

	private static byte[] alterEntityLiving(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "<init>", "func_70692_ba", "field_70173_aa", "func_70623_bb", "field_70708_bq" };
		} else {
			names = new String[] { "<init>", "canDespawn", "ticksExisted", "despawnEntity", "entityAge" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		MethodNode init = null;
		MethodNode canDespawn = null;
		MethodNode despawnEntity = null;
		for (MethodNode n : cn.methods) {
			if (names[0].equals(n.name)) {
				init = n;
			} else if (names[1].equals(n.name)) {
				canDespawn = n;
			} else if (names[3].equals(n.name)) {
				despawnEntity = n;
			}
		}

		if (init != null) {
			for (AbstractInsnNode n = init.instructions.getFirst(); n != null; n = n.getNext()) {
				if (n.getOpcode() == NEW) {
					TypeInsnNode node = ((TypeInsnNode) n);
					if (!"net/minecraft/entity/ai/EntityAITasks".equals(node.desc))
						continue;
					node.desc = "cofh/tweak/asmhooks/entity/EntityAITasks";
					for (; n.getOpcode() != INVOKESPECIAL; n = n.getNext())
						;
					((MethodInsnNode) n).owner = node.desc;
				}
			}
		}

		if (canDespawn != null) {
			InsnList list = new InsnList();
			LabelNode label = new LabelNode();
			list.add(new VarInsnNode(ALOAD, 0));
			list.add(new FieldInsnNode(GETFIELD, "net/minecraft/entity/Entity", names[2], "I"));
			list.add(new LdcInsnNode(new Integer(700))); // SIPUSH fails because ASM framework injects a WIDE marker on it. bug?
			list.add(new JumpInsnNode(IF_ICMPGE, label));
			list.add(new InsnNode(ICONST_0));
			list.add(new InsnNode(IRETURN));
			list.add(new FrameNode(F_SAME, 0, null, 0, null));
			list.add(label);
			list.add(new InsnNode(ICONST_1));
			list.add(new InsnNode(IRETURN));

			canDespawn.instructions.clear();
			canDespawn.localVariables = null;
			canDespawn.instructions.insert(list);
		}

		if (despawnEntity != null) {
			InsnList list = new InsnList();
			LabelNode label = new LabelNode();
			list.add(new VarInsnNode(ALOAD, 0));
			list.add(new FieldInsnNode(GETFIELD, "net/minecraft/entity/EntityLivingBase", names[4], "I"));
			list.add(new LdcInsnNode(new Integer(0x1F)));
			list.add(new InsnNode(IAND));
			list.add(new LdcInsnNode(new Integer(0x1E)));
			list.add(new JumpInsnNode(IF_ICMPEQ, label));
			list.add(new InsnNode(RETURN));
			list.add(new FrameNode(F_SAME, 0, null, 0, null));
			list.add(label);

			despawnEntity.localVariables = null;
			for (AbstractInsnNode n = despawnEntity.instructions.getFirst(); n != null; n = n.getNext()) {
				c: if (n.getOpcode() == INVOKEVIRTUAL) {
					MethodInsnNode m = (MethodInsnNode) n;
					if (!"net/minecraft/world/World".equals(m.owner)) {
						break c;
					}
					if (!"(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/EntityPlayer;".equals(m.desc)) {
						break c;
					}
					for (; n != null && n.getType() != AbstractInsnNode.LABEL; n = n.getPrevious())
						;
					for (; n != null && n.getOpcode() != ALOAD; n = n.getNext())
						;
					if (n != null)
						despawnEntity.instructions.insertBefore(n, list);
					break;
				}
			}
		}

		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		bytes = cw.toByteArray();
		return bytes;
	}

	private static byte[] alterEntity(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_70091_d", "func_72945_a", "func_70104_M", "shouldRenderInPass" };
		} else {
			names = new String[] { "moveEntity", "getCollidingBoundingBoxes", "canBePushed", "shouldRenderInPass" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		String mOwner = "net/minecraft/world/World";

		MethodNode m = null;
		MethodNode pass = null;
		boolean hasMethod = false;
		for (MethodNode n : cn.methods) {
			if (names[0].equals(n.name)) {
				m = n;
			} else if ("cofh_collideCheck".equals(n.name)) {
				hasMethod = true;
			} else if (names[3].equals(n.name)) {
				pass = n;
			}
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

		if (FMLLaunchHandler.side() == Side.CLIENT) {
			InsnList list = new InsnList();
			LabelNode label = new LabelNode();
			list.add(new VarInsnNode(ALOAD, 0));
			list.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "renderEntity", "(Lnet/minecraft/entity/Entity;)Z", false));
			list.add(new JumpInsnNode(IFNE, label));
			list.add(new InsnNode(ICONST_0));
			list.add(new InsnNode(IRETURN));
			list.add(new FrameNode(F_SAME, 0, null, 0, null));
			list.add(label);

			pass.instructions.insertBefore(pass.instructions.getFirst(), list);
		}

		if (!hasMethod) {
			m = new MethodNode(ACC_PUBLIC, "cofh_collideCheck", "()Z", null, null);
			cn.methods.add(m);
			m.instructions.insert(new InsnNode(IRETURN));
			m.instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, name, names[2], "()Z", false));
			m.instructions.insert(new VarInsnNode(ALOAD, 0));
		}

		ClassWriter cw = new ClassWriter(0);
		cn.accept(cw);
		bytes = cw.toByteArray();

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
		cr.accept(cn, 0);

		String mOwner = "net/minecraft/client/renderer/texture/TextureManager";
		final String sig = "(Lnet/minecraft/client/renderer/texture/ITickable;)V";

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
								m.instructions.set(mn, new MethodInsnNode(INVOKESTATIC, HooksCore, "tickTextures", sig, false));
								break mc;
							}
						} else if (n.getOpcode() == INVOKESTATIC) {
							MethodInsnNode mn = (MethodInsnNode) n;
							if ("cofh/asmhooks/HooksCore".equals(mn.owner) && "tickTextures".equals(mn.name) && sig.equals(mn.desc)) {
								m.instructions.set(mn, new MethodInsnNode(INVOKESTATIC, HooksCore, "tickTextures", sig, false));
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
			names = new String[] { "func_150803_c", "field_76650_s", "func_76618_a", "func_76588_a",
					"field_76644_m", "func_150809_p", "func_76613_n", "field_150814_l", "field_76646_k" };
		} else {
			names = new String[] { "recheckGaps", "isGapLightingUpdated", "getEntitiesOfTypeWithinAAAB", "getEntitiesWithinAABBForEntity",
					"hasEntities", "func_150809_p", "resetRelightChecks", "isLightPopulated", "isTerrainPopulated" };
		}

		name = name.replace('.', '/');
		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		l: {
			boolean updated = false;
			final String sig = "(Lnet/minecraft/world/chunk/Chunk;Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/command/IEntitySelector;)V";
			for (MethodNode m : cn.methods) {
				String mName = m.name;
				if (names[0].equals(mName) && "(Z)V".equals(m.desc)) {
					if (!Config.lightChunks) {
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
				} else if ("<init>".equals(mName)) {
					for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n == null ? null : n.getNext()) {
						if (n.getOpcode() == NEW) {
							if ("java/util/ArrayList".equals(((TypeInsnNode) n).desc)) {
								updated = true;
								((TypeInsnNode) n).desc = "cofh/tweak/util/ClassInheritenceArrayList";
								for (; n != null; n = n.getNext()) {
									if (n.getOpcode() == INVOKESPECIAL) {
										((MethodInsnNode) n).owner = "cofh/tweak/util/ClassInheritenceArrayList";
										break;
									}
								}
							}
						}
					}
				} else if (names[2].equals(mName)) {
					updated = true;
					m.localVariables = null;

					m.instructions.clear();
					m.instructions.add(new VarInsnNode(ALOAD, 0));
					m.instructions.add(new VarInsnNode(ALOAD, 1));
					m.instructions.add(new VarInsnNode(ALOAD, 2));
					m.instructions.add(new VarInsnNode(ALOAD, 3));
					m.instructions.add(new VarInsnNode(ALOAD, 4));
					m.instructions.add(new MethodInsnNode(INVOKESTATIC, HooksCore, "getEntities", sig, false));
					m.instructions.add(new InsnNode(RETURN));
				} else if (names[3].equals(mName)) {
					updated = true;
					m.localVariables = null;

					InsnList list = new InsnList();
					LabelNode label = new LabelNode();
					list.add(new VarInsnNode(ALOAD, 0));
					list.add(new FieldInsnNode(GETFIELD, "net/minecraft/world/chunk/Chunk", names[4], "Z"));
					list.add(new JumpInsnNode(IFNE, label));
					list.add(new InsnNode(RETURN));
					list.add(new FrameNode(F_SAME, 0, null, 0, null));
					list.add(label);
					m.instructions.insert(list);
				} else if (names[5].equals(mName)) {
					updated = true;
					m.localVariables = null;

					if (!Config.fullLightChunks) {
						m.instructions.clear();
						m.instructions.insert(new InsnNode(RETURN));
						m.instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/chunk/Chunk", names[6], "()V", false));
						m.instructions.insert(new VarInsnNode(ALOAD, 0));
						m.instructions.insert(new FieldInsnNode(PUTFIELD, "net/minecraft/world/chunk/Chunk", names[7], "Z"));
						m.instructions.insert(new InsnNode(ICONST_1));
						m.instructions.insert(new VarInsnNode(ALOAD, 0));
						m.instructions.insert(new FieldInsnNode(PUTFIELD, "net/minecraft/world/chunk/Chunk", names[8], "Z"));
						m.instructions.insert(new InsnNode(ICONST_1));
						m.instructions.insert(new VarInsnNode(ALOAD, 0));
					} else {
						m.instructions.insert(new MethodInsnNode(INVOKESTATIC, HooksCore, "setChunkCache", "(Lnet/minecraft/world/chunk/Chunk;)V", false));
						m.instructions.insert(new VarInsnNode(ALOAD, 0));

						for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
							if (n.getOpcode() == RETURN) {
								m.instructions.insertBefore(n, new InsnNode(ACONST_NULL));
								m.instructions.insertBefore(n, new FieldInsnNode(PUTSTATIC, HooksCore, "chunkCache", "Lcofh/tweak/asmhooks/world/ChunkCache;"));
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

	public static int hash6432shift(long key) {

		key = (~key) + (key << 18);
		key ^= key >>> 31;
		key *= 21;
		key ^= key >>> 11;
		key += key << 6;
		key ^= key >>> 22;
		return (int) key;
	}

	private static byte[] alterLongHashMap(String name, byte[] bytes, ClassReader cr) {

		String[] names;
		if (LoadingPlugin.runtimeDeobfEnabled) {
			names = new String[] { "func_76155_g", "func_76160_c", "func_76161_b" };
		} else {
			names = new String[] { "getHashedKey", "getEntry", "containsItem" };
		}

		ClassNode cn = new ClassNode(ASM5);
		cr.accept(cn, 0);

		l: {
			boolean updated = false;
			MethodNode getEntry = null, containsItem = null;
			for (MethodNode m : cn.methods) {
				String mName = m.name;
				if (names[0].equals(mName) && "(J)I".equals(m.desc)) {
					updated = true;
					m.localVariables = null;

					m.instructions.clear();
					// bytecode of hash6432shift(J)I above. better hash function
					m.instructions.add(new IntInsnNode(LLOAD, 0));
					m.instructions.add(new LdcInsnNode(new Long(-1)));
					m.instructions.add(new InsnNode(LXOR));
					m.instructions.add(new IntInsnNode(LLOAD, 0));
					m.instructions.add(new IntInsnNode(BIPUSH, 18));
					m.instructions.add(new InsnNode(LSHL));
					m.instructions.add(new InsnNode(LADD));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new IntInsnNode(BIPUSH, 31));
					m.instructions.add(new InsnNode(LUSHR));
					m.instructions.add(new InsnNode(LXOR));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new LdcInsnNode(new Long(21)));
					m.instructions.add(new InsnNode(LMUL));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new IntInsnNode(BIPUSH, 11));
					m.instructions.add(new InsnNode(LUSHR));
					m.instructions.add(new InsnNode(LXOR));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new IntInsnNode(BIPUSH, 6));
					m.instructions.add(new InsnNode(LSHL));
					m.instructions.add(new InsnNode(LADD));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new InsnNode(DUP2));
					m.instructions.add(new IntInsnNode(BIPUSH, 22));
					m.instructions.add(new InsnNode(LUSHR));
					m.instructions.add(new InsnNode(LXOR));
					m.instructions.add(new InsnNode(L2I));
					m.instructions.add(new InsnNode(IRETURN));
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
				cr.accept(clone, 0);
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
