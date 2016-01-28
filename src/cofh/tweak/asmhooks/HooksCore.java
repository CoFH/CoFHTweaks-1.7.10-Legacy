package cofh.tweak.asmhooks;

import cofh.tweak.CoFHTweaks;
import cofh.tweak.util.ClassInheritenceArrayList;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import gnu.trove.map.hash.TIntByteHashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.ITickable;
import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAIFollowParent;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class HooksCore {

	public static String getBrand() {

		return "CoFHTweaks v" + CoFHTweaks.version.substring(CoFHTweaks.version.indexOf('R') + 1);
	}

	public static int computeLightValue(World world, int x, int y, int z, EnumSkyBlock type) {

		Chunk chunk = world.getChunkFromBlockCoords(x, z);
		return computeLightValue(world, chunk, x, y, z, type);
	}

	public static boolean shouldChildContinueFollowParent(EntityAIFollowParent task) {

		if (!task.parentAnimal.isEntityAlive()) {
			return false;
		} else {
			double d0 = task.childAnimal.getDistanceSqToEntity(task.parentAnimal);
			if (d0 <= (3 * 3) * 3)
				task.field_75345_d = 30;
			return d0 <= 256.0D;
		}
	}

	public static boolean shouldChildFollowParent(EntityAIFollowParent task) {

		if (task.childAnimal.getGrowingAge() >= 0) {
			return false;
		} else {
			if (task.parentAnimal != null && task.parentAnimal.isEntityAlive()) {
				double dist = task.childAnimal.getDistanceSqToEntity(task.parentAnimal);
				if (dist <= 256)
					return true;
			}
			if (--task.field_75345_d > 0) {
				return false;
			}
			List<? extends EntityAnimal> list = task.childAnimal.worldObj.getEntitiesWithinAABB(task.childAnimal.getClass(),
				task.childAnimal.boundingBox.expand(8.0D, 4.0D, 8.0D));
			EntityAnimal entityanimal = null;
			double d0 = Double.MAX_VALUE;

			for (EntityAnimal entityanimal1 : list) {

				if (entityanimal1.getGrowingAge() >= 0) {
					double d1 = task.childAnimal.getDistanceSqToEntity(entityanimal1);

					if (d1 <= d0) {
						d0 = d1;
						entityanimal = entityanimal1;
					}
				}
			}

			if (entityanimal == null) {
				task.field_75345_d = 100;
				return false;
			} else {
				task.parentAnimal = entityanimal;
				return true;
			}
		}
	}

	public static int computeLightValue(World world, Chunk chunk, int x, int y, int z, EnumSkyBlock type) {

		int x2 = x & 15, z2 = z & 15;
		if (type == EnumSkyBlock.Sky && chunk.canBlockSeeTheSky(x2, y, z2)) {
			return 15;
		} else {
			Block block = chunk.getBlock(x2, y, z2);
			int blockLight = block.getLightValue(world, x, y, z);
			int lightValue = type == EnumSkyBlock.Sky ? 0 : blockLight;
			int opacity = block.getLightOpacity(world, x, y, z);

			if (opacity >= 15 & blockLight > 0) {
				opacity = 1;
			}

			opacity &= ~opacity >> 31;
			opacity -= (opacity - 1) >> 31;

			if (opacity >= 15) {
				return 0;
			} else if (lightValue >= 14) {
				return lightValue;
			} else {
				if (x2 == 0 | z2 == 0 | x2 == 15 | z2 == 15 | y == 0 | y == 255) {
					for (int i = 0; i < 6; ++i) {
						int k1 = x + Facing.offsetsXForSide[i];
						int l1 = y + Facing.offsetsYForSide[i];
						int i2 = z + Facing.offsetsZForSide[i];
						int nLightValue = world.getSavedLightValue(type, k1, l1, i2) - opacity;

						int t;
						lightValue -= (t = lightValue - nLightValue) & (t >> 31);

						if (lightValue >= 14) {
							break;
						}
					}
				} else {
					for (int i = 0; i < 6; ++i) {
						int k1 = x2 + Facing.offsetsXForSide[i];
						int l1 = y + Facing.offsetsYForSide[i];
						int i2 = z2 + Facing.offsetsZForSide[i];
						int nLightValue = chunk.getSavedLightValue(type, k1, l1, i2) - opacity;

						int t;
						lightValue -= (t = lightValue - nLightValue) & (t >> 31);

						if (lightValue >= 14) {
							break;
						}
					}
				}

				return lightValue;
			}
		}
	}

	public static boolean updateLightByType(World world, EnumSkyBlock type, int x, int y, int z) {

		world.theProfiler.startSection("updateLightByType");
		if (!world.doChunksNearChunkExist(x, y, z, 17)) {
			world.theProfiler.endSection();
			return false;
		} else {
			int chunkWidth = 5, chunkRadius = chunkWidth >> 1;
			int chunkX = (x >> 4) - chunkRadius, chunkZ = (z >> 4) - chunkRadius;
			Chunk[] chunks = new Chunk[chunkWidth * chunkWidth];
			for (int i = 0; i < chunks.length; ++i) {
				int x2 = x + (i % chunkWidth - chunkRadius) * 16;
				int z2 = z + (i / chunkWidth - chunkRadius) * 16;
				if (world.blockExists(x2, y, z2)) {
					chunks[i] = world.getChunkFromBlockCoords(x2, z2);
				}
			}
			world.theProfiler.startSection("getBrightness");
			int minX = x, minY = y, minZ = z;
			int maxX = x, maxY = y, maxZ = z;

			int arrayRead = 0, arrayEnd = 0;

			Chunk chunk = chunks[((z >> 4) - chunkZ) * chunkWidth + ((x >> 4) - chunkX)];
			if (chunk == null | y < 0 | y > 255) {
				return false;
			}
			int savedLight = chunk.getSavedLightValue(type, x & 15, y, z & 15);
			int computedLight = computeLightValue(world, chunk, x, y, z, type);
			int posLight;

			int xO, yO, zO;
			int xAbs, yAbs, zAbs;

			int[] lightUpdateBlockList = world.lightUpdateBlockList;

			if (computedLight > savedLight) {
				lightUpdateBlockList[arrayEnd++] = 133152;
			} else if (computedLight < savedLight) {
				lightUpdateBlockList[arrayEnd++] = 133152 | savedLight << 18;

				while (arrayRead < arrayEnd) {
					posLight = lightUpdateBlockList[arrayRead++];
					xO = (posLight & 63) - 32 + x;
					yO = (posLight >> 6 & 63) - 32 + y;
					zO = (posLight >> 12 & 63) - 32 + z;
					savedLight = posLight >> 18 & 15;
					int x2 = xO & 15, z2 = zO & 15;

					chunk = chunks[((zO >> 4) - chunkZ) * chunkWidth + ((xO >> 4) - chunkX)];
					if (chunk == null | yO < 0 | yO > 255) {
						continue;
					}

					{
						int t;
						minX = xO + ((t = minX - xO) & (t >> 31));
						minY = yO + ((t = minY - yO) & (t >> 31));
						minZ = zO + ((t = minZ - zO) & (t >> 31));
						maxX = maxX - ((t = maxX - xO) & (t >> 31));
						maxY = maxY - ((t = maxY - yO) & (t >> 31));
						maxZ = maxZ - ((t = maxZ - zO) & (t >> 31));
					}
					computedLight = chunk.getSavedLightValue(type, x2, yO, z2);

					if (computedLight == savedLight) {
						chunk.setLightValue(type, x2, yO, z2, 0);

						if (savedLight > 0) {
							{
								int t;
								xAbs = xO - x;
								xAbs = (xAbs + (t = xAbs >> 31)) ^ t;
								yAbs = yO - y;
								yAbs = (yAbs + (t = yAbs >> 31)) ^ t;
								zAbs = zO - z;
								zAbs = (zAbs + (t = zAbs >> 31)) ^ t;
							}

							if (xAbs + yAbs + zAbs < 17) {
								if (x2 == 0 | x2 == 15 | z2 == 0 | z2 == 15 | yO == 0 | yO == 255) {
									for (int i = 0; i < 6; ++i) {
										int j4 = xO + Facing.offsetsXForSide[i];
										int k4 = yO + Facing.offsetsYForSide[i];
										int l4 = zO + Facing.offsetsZForSide[i];
										computedLight = world.getSavedLightValue(type, j4, k4, l4);

										int opacity = world.getBlock(j4, k4, l4).getLightOpacity(world, j4, k4, l4);
										opacity &= ~opacity >> 31;
										opacity -= (opacity - 1) >> 31;

										if (computedLight == savedLight - opacity && arrayEnd < lightUpdateBlockList.length) {
											if (k4 >= 0 && k4 <= 255) {
												lightUpdateBlockList[arrayEnd++] = (j4 - x + 32) | ((k4 - y + 32) << 6) | ((l4 - z + 32) << 12) |
														((savedLight - opacity) << 18);
											}
										}
									}
								} else {
									for (int i = 0; i < 6; ++i) {
										int j4 = x2 + Facing.offsetsXForSide[i];
										int k4 = yO + Facing.offsetsYForSide[i];
										int l4 = z2 + Facing.offsetsZForSide[i];
										computedLight = chunk.getSavedLightValue(type, j4, k4, l4);

										int opacity = chunk.getBlock(j4, k4, l4).getLightOpacity(world, j4, k4, l4);
										opacity &= ~opacity >> 31;
										opacity -= (opacity - 1) >> 31;

										if (computedLight == savedLight - opacity && arrayEnd < lightUpdateBlockList.length) {
											j4 = xO + Facing.offsetsXForSide[i];
											l4 = zO + Facing.offsetsZForSide[i];
											lightUpdateBlockList[arrayEnd++] = (j4 - x + 32) | ((k4 - y + 32) << 6) | ((l4 - z + 32) << 12) | ((savedLight - opacity) << 18);
										}
									}
								}
							}
						}
					}
				}

				arrayRead = 0;
			}

			world.theProfiler.endStartSection("checkedPosition < toCheckCount");

			while (arrayRead < arrayEnd) {
				posLight = lightUpdateBlockList[arrayRead++];
				xO = (posLight & 63) - 32 + x;
				yO = (posLight >> 6 & 63) - 32 + y;
				zO = (posLight >> 12 & 63) - 32 + z;
				int x2 = xO & 15, z2 = zO & 15;

				chunk = chunks[((zO >> 4) - chunkZ) * chunkWidth + ((xO >> 4) - chunkX)];
				if (chunk == null | yO < 0 | yO > 255) {
					continue;
				}

				{
					int t;
					minX = xO + ((t = minX - xO) & (t >> 31));
					minY = yO + ((t = minY - yO) & (t >> 31));
					minZ = zO + ((t = minZ - zO) & (t >> 31));
					maxX = maxX - ((t = maxX - xO) & (t >> 31));
					maxY = maxY - ((t = maxY - yO) & (t >> 31));
					maxZ = maxZ - ((t = maxZ - zO) & (t >> 31));
				}
				savedLight = chunk.getSavedLightValue(type, x2, yO, z2);
				computedLight = computeLightValue(world, chunk, xO, yO, zO, type);

				if (computedLight != savedLight) {
					chunk.setLightValue(type, x2, yO, z2, computedLight);

					if (computedLight > savedLight & arrayEnd < lightUpdateBlockList.length) {
						{
							int t;
							xAbs = xO - x;
							xAbs = (xAbs + (t = xAbs >> 31)) ^ t;
							yAbs = yO - y;
							yAbs = (yAbs + (t = yAbs >> 31)) ^ t;
							zAbs = zO - z;
							zAbs = (zAbs + (t = zAbs >> 31)) ^ t;
						}

						if (xAbs + yAbs + zAbs < 17) {
							if (x2 == 0 | x2 == 15 | z2 == 0 | z2 == 15 | yO == 0 | yO == 255) {
								for (int i = 0; i < 6; ++i) {
									int j4 = xO + Facing.offsetsXForSide[i];
									int k4 = yO + Facing.offsetsYForSide[i];
									int l4 = zO + Facing.offsetsZForSide[i];
									if (arrayEnd < lightUpdateBlockList.length && world.getSavedLightValue(type, j4, k4, l4) < computedLight) {
										if (k4 >= 0 && k4 <= 255) {
											lightUpdateBlockList[arrayEnd++] = j4 - x + 32 + (k4 - y + 32 << 6) + (l4 - z + 32 << 12);
										}
									}
								}
							} else {
								for (int i = 0; i < 6; ++i) {
									int j4 = x2 + Facing.offsetsXForSide[i];
									int k4 = yO + Facing.offsetsYForSide[i];
									int l4 = z2 + Facing.offsetsZForSide[i];
									if (arrayEnd < lightUpdateBlockList.length && chunk.getSavedLightValue(type, j4, k4, l4) < computedLight) {
										j4 = xO + Facing.offsetsXForSide[i];
										l4 = zO + Facing.offsetsZForSide[i];
										lightUpdateBlockList[arrayEnd++] = j4 - x + 32 + (k4 - y + 32 << 6) + (l4 - z + 32 << 12);
									}
								}
							}
						}
					}
				}
			}

			world.theProfiler.endSection();
			world.markBlockRangeForRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ);
			world.theProfiler.endSection();
			return true;
		}
	}

	@SuppressWarnings("unused")
	public static void stackItems(EntityItem entity) {

		if (!Config.stackItems) {
			return;
		}

		ItemStack stack = entity.getEntityItem();
		if (stack == null || stack.stackSize >= stack.getMaxStackSize()) {
			return;
		}

		@SuppressWarnings("rawtypes")
		Iterator iterator = entity.worldObj.getEntitiesWithinAABB(EntityItem.class, entity.boundingBox.expand(0.5D, 0.0D, 0.5D))
				.iterator();

		while (iterator.hasNext()) {
			entity.combineItems((EntityItem) iterator.next());
		}
	}

	public static void getEntities(Chunk chunk, Class<?> clazz, AxisAlignedBB aabb, List<Entity> out, IEntitySelector selector) {

		if (!chunk.hasEntities)
			return;

		int start = MathHelper.floor_double((aabb.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
		int end = MathHelper.floor_double((aabb.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);
		start = MathHelper.clamp_int(start, 0, chunk.entityLists.length - 1);
		end = MathHelper.clamp_int(end, 0, chunk.entityLists.length - 1);

		for (int i = start; i <= end; ++i) {
			@SuppressWarnings("unchecked")
			List<Entity> list1 = chunk.entityLists[i];

			if (list1.getClass() == ClassInheritenceArrayList.class) {
				Iterator<? extends Entity> iter = ((ClassInheritenceArrayList<Entity>) list1).getIteratorFor(clazz);
				if (iter != null) while (iter.hasNext()) {
					Entity entity = iter.next();
					if (entity.boundingBox.intersectsWith(aabb) && (selector == null || selector.isEntityApplicable(entity))) {
						out.add(entity);
					}
				}
				continue;
			}

			// compat with things that break our logic
			for (int k = 0; k < list1.size(); ++k) {
				Entity entity = list1.get(k);

				if (clazz.isAssignableFrom(entity.getClass()) && entity.boundingBox.intersectsWith(aabb) && (selector == null || selector.isEntityApplicable(entity))) {
					out.add(entity);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static List<AxisAlignedBB> getBlockCollisionBoxes(World world, Entity entity, AxisAlignedBB bb) {

		world.theProfiler.startSection("blockCollision");
		List<AxisAlignedBB> collidingBoundingBoxes = world.collidingBoundingBoxes;
		if (collidingBoundingBoxes == null) {
			collidingBoundingBoxes = world.collidingBoundingBoxes = new ArrayList<AxisAlignedBB>();
		}
		collidingBoundingBoxes.clear();
		int i = MathHelper.floor_double(bb.minX);
		int j = MathHelper.floor_double(bb.maxX) + 1;
		int i1 = MathHelper.floor_double(bb.minZ);
		int j1 = MathHelper.floor_double(bb.maxZ) + 1;
		int k = MathHelper.floor_double(bb.minY) - 1;
		int l = MathHelper.floor_double(bb.maxY) + 1;
		{
			k &= ~k >> 31;
			;
			k -= 255;
			k &= k >> 31;
			;
			k += 255;
			l &= ~l >> 31;
			;
			l -= 255;
			l &= l >> 31;
			;
			l += 255;
		}

		if (k != l) {
			for (int x = i; x < j; ++x) {
				boolean xBound = x >= -30000000 & x < 30000000;
				for (int z = i1; z < j1; ++z) {
					boolean def = xBound & z >= -30000000 & z < 30000000;
					if (!world.blockExists(x, 64, z)) {
						continue;
					}
					if (def) {
						Chunk chunk = world.getChunkFromBlockCoords(x, z);
						int cX = x & 15, cZ = z & 15;
						for (int y = k; y < l; ++y) {
							chunk.getBlock(cX, y, cZ).addCollisionBoxesToList(world, x, y, z, bb, collidingBoundingBoxes, entity);
						}
					} else {
						for (int y = k; y < l; ++y) {
							Blocks.bedrock.addCollisionBoxesToList(world, x, y, z, bb, collidingBoundingBoxes, entity);
						}
					}
				}
			}
		}
		world.theProfiler.endSection();

		return collidingBoundingBoxes;
	}

	public static List<AxisAlignedBB> getEntityCollisionBoxes(World world, Entity entity, AxisAlignedBB bb) {

		world.theProfiler.startSection("entityCollision");
		List<AxisAlignedBB> collidingBoundingBoxes = null;
		if (!Config.collideEntities || !entity.canBePushed()) {
			collidingBoundingBoxes = getBlockCollisionBoxes(world, entity, bb);
		} else {
			collidingBoundingBoxes = getWorldCollisionBoxes(world, entity, bb);
		}
		world.theProfiler.endSection();

		return collidingBoundingBoxes;
	}

	public static List<AxisAlignedBB> getWorldCollisionBoxes(World world, Entity entity, AxisAlignedBB bb) {

		List<AxisAlignedBB> collidingBoundingBoxes = getBlockCollisionBoxes(world, entity, bb);

		world.theProfiler.startSection("entity_on_entity_collision");
		AxisAlignedBB bb2 = bb.expand(.25, .25, .25);
		int x = MathHelper.floor_double((bb2.minX - World.MAX_ENTITY_RADIUS) * 0.0625);
		int xE = MathHelper.floor_double((bb2.maxX + World.MAX_ENTITY_RADIUS) * 0.0625);
		int zS = MathHelper.floor_double((bb2.minZ - World.MAX_ENTITY_RADIUS) * 0.0625);
		int zE = MathHelper.floor_double((bb2.maxZ + World.MAX_ENTITY_RADIUS) * 0.0625);

		for (; x <= xE; ++x)
			for (int z = zS; z <= zE; ++z) {
				if (!world.chunkExists(x, z))
					continue;
				Chunk chunk = world.getChunkFromChunkCoords(x, z);
				int s = MathHelper.floor_double((bb2.minY - World.MAX_ENTITY_RADIUS) * 0.0625);
				int e = MathHelper.floor_double((bb2.maxY + World.MAX_ENTITY_RADIUS) * 0.0625);
				s = MathHelper.clamp_int(s, 0, chunk.entityLists.length - 1);
				e = MathHelper.clamp_int(e, -1, chunk.entityLists.length - 1);
				CoFHTweaks_FZ_Hook(chunk, entity, bb, collidingBoundingBoxes);

				for (; s <= e; ++s) {
					@SuppressWarnings("unchecked")
					List<Entity> list1 = chunk.entityLists[s];

					for (int i = 0; i < list1.size(); ++i) {
						Entity entity1 = list1.get(i);

						if (entity1 != entity && entity1.boundingBox.intersectsWith(bb2)) {
							AxisAlignedBB axisalignedbb1 = (entity1).getBoundingBox();
							if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(bb)) {
								collidingBoundingBoxes.add(axisalignedbb1);
							}

							axisalignedbb1 = entity.getCollisionBox(entity1);
							if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(bb)) {
								collidingBoundingBoxes.add(axisalignedbb1);
							}

							Entity[] aentity = entity1.getParts();
							if (aentity != null) {
								for (int i1 = 0; i1 < aentity.length; ++i1) {
									entity1 = aentity[i1];

									if (entity1 != entity) {
										axisalignedbb1 = (entity1).getBoundingBox();
										if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(bb)) {
											collidingBoundingBoxes.add(axisalignedbb1);
										}

										axisalignedbb1 = entity.getCollisionBox(entity1);
										if (axisalignedbb1 != null && axisalignedbb1.intersectsWith(bb)) {
											collidingBoundingBoxes.add(axisalignedbb1);
										}
									}
								}
							}
						}
					}
				}
			}
		world.theProfiler.endSection();

		return collidingBoundingBoxes;
	}

	private static void CoFHTweaks_FZ_Hook(Chunk chunk, Entity entity, AxisAlignedBB bb, List<AxisAlignedBB> found) {

		return;
	}

	@SideOnly(Side.CLIENT)
	private static long tick, farEntities;

	@SideOnly(Side.CLIENT)
	private static TIntByteHashMap nearData;

	@SideOnly(Side.CLIENT)
	public static boolean renderEntity(Entity ent) {

		if (!Config.agressiveCulling) {
			return true;
		}

		long t = Minecraft.getMinecraft().entityRenderer.renderEndNanoTime;
		if (t != tick) {
			tick = t;
			if (nearData == null) {
				nearData = new TIntByteHashMap();
			}
			nearData.clear();
			farEntities = 0;
		}

		Entity cmp = RenderManager.instance.livingPlayer;
		if (ent.getDistanceSqToEntity(cmp) < 12288) {
			int p;
			{
				int x = MathHelper.floor_double(ent.posX - cmp.posX), y, z = MathHelper.floor_double(ent.posZ - cmp.posZ);
				y = MathHelper.floor_double(ent.posY - cmp.posY);
				p = (x & 255) | ((z & 255) << 8) | ((y & 255) << 16);
			}
			if (nearData.get(p) > 20) {
				return false;
			}
			++farEntities;
			nearData.adjustOrPutValue(p, (byte) 1, (byte) 1);
			return true;
		}
		return Config.distantCulling ? ++farEntities <= 60 : true;
	}

	@SideOnly(Side.CLIENT)
	public static boolean setClientBlock(WorldClient world, int x, int y, int z, Block block, int meta) {

		world.invalidateBlockReceiveRegion(x, y, z, x, y, z);
		if (!Config.fastBlocks) {
			return world.setBlock(x, y, z, block, meta, 3);
		}

		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
			if (y < 0) {
				return false;
			} else if (y >= 256) {
				return false;
			}

			Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
			Block block1 = chunk.getBlock(x & 15, y, z & 15);
			if (block1 == block) {
				if (chunk.getBlockMetadata(x & 15, y, z & 15) == meta) {
					return true;
				}
			}
			int light = chunk.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15);

			boolean flag = chunk.func_150807_a(x & 15, y, z & 15, block, meta);

			if (flag) {
				int light2 = block.getLightValue(world, x, y, z);
				if (light <= light2)
					chunk.setLightValue(EnumSkyBlock.Block, x & 15, y, z & 15, light2);
				world.markAndNotifyBlock(x, y, z, chunk, block1, block, 3);

				if (light != block.getLightValue(world, x, y, z)) {
					x = (x & 15) - 1;
					x &= ~x >> 31;
					int p = ((y >> 4) & 15) | ((x & 15) << 4) | ((z & 15) << 4);
					if (chunk.queuedLightChecks >= p)
						chunk.queuedLightChecks = p;
				}
			}

			return flag;
		} else {
			return false;
		}
	}

	@SideOnly(Side.CLIENT)
	public static void tickTextures(ITickable obj) {

		if (Config.animateTextures) {
			obj.tick();
		}
	}

}
