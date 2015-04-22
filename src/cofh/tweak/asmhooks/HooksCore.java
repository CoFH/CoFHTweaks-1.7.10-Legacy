package cofh.tweak.asmhooks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.renderer.texture.ITickable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class HooksCore {

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

	@SuppressWarnings("unchecked")
	private static List<AxisAlignedBB> getBlockCollisionBoxes(World world, Entity entity, AxisAlignedBB bb) {

		List<AxisAlignedBB> collidingBoundingBoxes = world.collidingBoundingBoxes;
		if (collidingBoundingBoxes == null) {
			collidingBoundingBoxes = world.collidingBoundingBoxes = new ArrayList<AxisAlignedBB>();
		}
		collidingBoundingBoxes.clear();
		int i = MathHelper.floor_double(bb.minX);
		int j = MathHelper.floor_double(bb.maxX + 1.0D);
		int k = MathHelper.floor_double(bb.minY);
		int l = MathHelper.floor_double(bb.maxY + 1.0D);
		int i1 = MathHelper.floor_double(bb.minZ);
		int j1 = MathHelper.floor_double(bb.maxZ + 1.0D);

		for (int x = i; x < j; ++x) {
			boolean xBound = x >= -30000000 & x < 30000000;
			for (int z = i1; z < j1; ++z) {
				boolean def = xBound & z >= -30000000 & z < 30000000;
				if (!world.blockExists(x, 64, z)) {
					continue;
				}
				if (def) {
					for (int y = k - 1; y < l; ++y) {
						world.getBlock(x, y, z).addCollisionBoxesToList(world, x, y, z, bb, collidingBoundingBoxes, entity);
					}
				} else {
					for (int y = k - 1; y < l; ++y) {
						Blocks.bedrock.addCollisionBoxesToList(world, x, y, z, bb, collidingBoundingBoxes, entity);
					}
				}
			}
		}

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

		AxisAlignedBB bb2 = bb.expand(.25, .25, .25);
		int x  = MathHelper.floor_double((bb2.minX - World.MAX_ENTITY_RADIUS) * 0.0625);
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

		return collidingBoundingBoxes;
	}

	@SideOnly(Side.CLIENT)
	public static void tickTextures(ITickable obj) {

		if (Config.animateTextures) {
			obj.tick();
		}
	}

}
