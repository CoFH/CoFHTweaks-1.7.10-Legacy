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
		Iterator iterator = entity.worldObj.getEntitiesWithinAABB(EntityItem.class, entity.boundingBox.expand(0.5D, 0.0D, 0.5D)).iterator();

		while (iterator.hasNext()) {
			entity.combineItems((EntityItem) iterator.next());
		}
	}

	@SuppressWarnings("rawtypes")
	public static List getEntityCollisionBoxes(World world, Entity entity, AxisAlignedBB bb) {

		if (!entity.canBePushed()) {
			List collidingBoundingBoxes = world.collidingBoundingBoxes;
			if (collidingBoundingBoxes == null) {
				collidingBoundingBoxes = world.collidingBoundingBoxes = new ArrayList();
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
		return world.getCollidingBoundingBoxes(entity, bb);
	}

	@SideOnly(Side.CLIENT)
	public static void tickTextures(ITickable obj) {

		if (Config.animateTextures) {
			obj.tick();
		}
	}

}
