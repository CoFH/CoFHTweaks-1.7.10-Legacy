package cofh.tweak.asmhooks.render;

import cofh.repack.cofh.lib.util.IdentityLinkedHashList;
import cofh.tweak.asmhooks.Config;
import cofh.tweak.asmhooks.world.ClientChunk;
import com.google.common.primitives.Ints;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;

public class RenderGlobal extends net.minecraft.client.renderer.RenderGlobal {

	private int rendersPerFrame = 30;
	private boolean updated = false, prevUpdated = false;

	public RenderGlobal(Minecraft minecraft) {

		super(minecraft);
		worldRenderersToUpdate = new IdentityLinkedHashList<WorldRenderer>();
		occlusionEnabled &= !Config.alternateOcclusion;
	}

	@Override
	public void clipRenderersByFrustum(ICamera p_72729_1_, float p_72729_2_) {

		int o = frustumCheckOffset++;
		for (int i = 0, e = worldRenderers.length; i < e; ++i) {
			WorldRenderer rend = worldRenderers[i];
			if (rend.skipAllRenderPasses())
				continue;
			if ((!rend.isInFrustum || ((i + o) & 15) == 0)) {
				rend.updateInFrustum(p_72729_1_);
			}
		}

		updated = false;
		prevUpdated = false;
	}

	@Override
	protected void markRenderersForNewPosition(int x, int y, int z) {

		super.markRenderersForNewPosition(x, y, z);
	}

	@Override
	public boolean updateRenderers(EntityLivingBase view, boolean p_72716_2_) {

		int m = worldRenderersToUpdate.size();
		int i = Math.min(m, rendersPerFrame);
		if (i == 0) {
			return true;
		}
		theWorld.theProfiler.startSection("rebuild");
		if (!updated) {
			updated = true;
		} else if (!prevUpdated) {
			prevUpdated = true;
			if (m > i) {
				rendersPerFrame = Math.max(rendersPerFrame - 1, 21);
			}
		} else {
			++rendersPerFrame;
		}

		for (int k = 0; k < i; ++k) {
			WorldRenderer worldrenderer = (WorldRenderer) worldRenderersToUpdate.remove(0);

			if (worldrenderer != null) {

				if (Config.alternateOcclusion) {
					Chunk chunk = theWorld.getChunkFromBlockCoords(worldrenderer.posX, worldrenderer.posZ);
					worldrenderer.isVisible = true;
					if (chunk instanceof ClientChunk) {
						worldrenderer.isVisible = false;
						for (int v = 1; v <= 6; ++v) {
							int yp = 1 + (worldrenderer.posY >> 4);
							worldrenderer.isVisible |= !((ClientChunk)chunk).solidSides.get(yp * v);
						}
					}
				}

				if (!worldrenderer.isInFrustum || !worldrenderer.isVisible) {
					continue;
				}

				worldrenderer.updateRenderer(view);
				worldrenderer.needsUpdate = false;
			}
		}

		theWorld.theProfiler.endSection();
		return i <= rendersPerFrame;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected int renderSortedRenderers(int start, int end, int pass, double tick) {

		glRenderLists.clear();
		int l = 0;
		int i1 = start;
		int j1 = end;
		byte b0 = 1;

		if (pass == 1) {
			i1 = sortedWorldRenderers.length - 1 - start;
			j1 = sortedWorldRenderers.length - 1 - end;
			b0 = -1;
		}

		for (int k1 = i1; k1 != j1; k1 += b0) {
			WorldRenderer rend = sortedWorldRenderers[k1];
			if (pass == 0) {
				++renderersLoaded;

				if (rend.skipRenderPass[pass]) {
					++renderersSkippingRenderPass;
				} else if (!rend.isInFrustum) {
					++renderersBeingClipped;
				} else if (!rend.isVisible) {
					++renderersBeingOccluded;
				} else {
					++renderersBeingRendered;
				}
			}

			if (rend.isInFrustum && rend.isVisible && !rend.skipRenderPass[pass]) {
				int l1 = rend.getGLCallListForPass(pass);

				if (l1 >= 0) {
					glRenderLists.add(rend);
					++l;
				}
			}
		}

		EntityLivingBase entitylivingbase = mc.renderViewEntity;
		double d3 = entitylivingbase.lastTickPosX + (entitylivingbase.posX - entitylivingbase.lastTickPosX) * tick;
		double d1 = entitylivingbase.lastTickPosY + (entitylivingbase.posY - entitylivingbase.lastTickPosY) * tick;
		double d2 = entitylivingbase.lastTickPosZ + (entitylivingbase.posZ - entitylivingbase.lastTickPosZ) * tick;
		int i2 = 0;
		int j2;

		for (j2 = 0; j2 < allRenderLists.length; ++j2) {
			allRenderLists[j2].resetList();
		}

		int k2;
		int l2;

		int e = glRenderLists.size();
		for (j2 = 0; j2 < e; ++j2) {
			WorldRenderer rend = (WorldRenderer) glRenderLists.get(j2);
			k2 = -1;

			for (l2 = 0; l2 < i2; ++l2) {
				if (allRenderLists[l2].rendersChunk(rend.posXMinus, rend.posYMinus, rend.posZMinus)) {
					k2 = l2;
				}
			}

			if (k2 < 0) {
				k2 = i2++;
				allRenderLists[k2].setupRenderList(rend.posXMinus, rend.posYMinus, rend.posZMinus, d3, d1, d2);
			}

			allRenderLists[k2].addGLRenderList(rend.getGLCallListForPass(pass));
		}

		j2 = MathHelper.floor_double(d3);
		int i3 = MathHelper.floor_double(d2);
		k2 = j2 - (j2 & 1023);
		l2 = i3 - (i3 & 1023);
		Arrays.sort(allRenderLists, new RenderDistanceSorter(k2, l2));
		renderAllRenderLists(pass, tick);
		return l;
	}

	@SideOnly(Side.CLIENT)
	public class RenderDistanceSorter implements Comparator<RenderList> {

		int field_152632_a;
		int field_152633_b;

		public RenderDistanceSorter(int p_i1051_1_, int p_i1051_2_) {

			field_152632_a = p_i1051_1_;
			field_152633_b = p_i1051_2_;
		}

		@Override
		public int compare(RenderList p_compare_1_, RenderList p_compare_2_) {

			int i = p_compare_1_.renderChunkX - field_152632_a;
			int j = p_compare_1_.renderChunkZ - field_152633_b;
			int k = p_compare_2_.renderChunkX - field_152632_a;
			int l = p_compare_2_.renderChunkZ - field_152633_b;
			int i1 = i * i + j * j;
			int j1 = k * k + l * l;
			return Ints.compare(j1, i1);
		}

	}

}
