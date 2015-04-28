package cofh.tweak.asmhooks.render;

import cofh.repack.cofh.lib.util.IdentityLinkedHashList;
import cofh.tweak.IdentityArrayHashList;
import cofh.tweak.asmhooks.world.ClientChunk;
import com.google.common.primitives.Ints;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
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
		occlusionEnabled = false;
	}

	@Override
	public void clipRenderersByFrustum(ICamera camera, float p_72729_2_) {

		int o = frustumCheckOffset++;
		for (int i = 0, e = worldRenderers.length; i < e; ++i) {
			WorldRenderer rend = worldRenderers[i];
			if (rend.skipAllRenderPasses())
				continue;
			boolean frustrum = rend.isInFrustum;
			if (!frustrum || ((i + o) & 15) == 0) {
				rend.updateInFrustum(camera);
				rend.needsUpdate |= !rend.isInitialized;
			}
		}

		updated = false;
		prevUpdated = false;
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

				if (!worldrenderer.isVisible) {
					continue;
				}
				if (!worker.working) {
					if (!worldrenderer.isWaitingOnOcclusionQuery) {
						worldrenderer.isVisible = false;
						continue;
					}
				}

				if (!worldrenderer.isInFrustum) {
					worldrenderer.needsUpdate = false;
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

	private void markRenderers(int x, int y, int z) {

		x -= 8;
		y -= 8;
		z -= 8;
		minBlockX = Integer.MAX_VALUE;
		minBlockY = Integer.MAX_VALUE;
		minBlockZ = Integer.MAX_VALUE;
		maxBlockX = Integer.MIN_VALUE;
		maxBlockY = Integer.MIN_VALUE;
		maxBlockZ = Integer.MIN_VALUE;
		int l = renderChunksWide * 16;
		int i1 = l / 2;

		for (int j1 = 0; j1 < renderChunksWide; ++j1) {
			int k1 = j1 * 16;
			int l1 = k1 + i1 - x;

			if (l1 < 0) {
				l1 -= l - 1;
			}

			l1 /= l;
			k1 -= l1 * l;

			if (k1 < minBlockX) {
				minBlockX = k1;
			}

			if (k1 > maxBlockX) {
				maxBlockX = k1;
			}

			for (int i2 = 0; i2 < renderChunksDeep; ++i2) {
				int j2 = i2 * 16;
				int k2 = j2 + i1 - z;

				if (k2 < 0) {
					k2 -= l - 1;
				}

				k2 /= l;
				j2 -= k2 * l;

				if (j2 < minBlockZ) {
					minBlockZ = j2;
				}

				if (j2 > maxBlockZ) {
					maxBlockZ = j2;
				}

				for (int l2 = 0; l2 < renderChunksTall; ++l2) {
					int i3 = l2 * 16;

					if (i3 < minBlockY) {
						minBlockY = i3;
					}

					if (i3 > maxBlockY) {
						maxBlockY = i3;
					}

					WorldRenderer worldrenderer = worldRenderers[(i2 * renderChunksTall + l2) * renderChunksWide + j1];
					boolean flag = worldrenderer.needsUpdate;
					worldrenderer.isWaitingOnOcclusionQuery = false;
					worldrenderer.setPosition(k1, i3, j2);

					if (!flag && worldrenderer.needsUpdate) {
						worldRenderersToUpdate.add(worldrenderer);
					}
				}
			}
		}
	}

	@Override
	public void setWorldAndLoadRenderers(WorldClient world) {

		worker.setWorld(this, world);
		super.setWorldAndLoadRenderers(world);
	}

	@Override
	public void loadRenderers() {

		worker.lock();
		super.loadRenderers();
		worker.working = true;
		worker.unlock();
	}

	@Override
	protected void markRenderersForNewPosition(int x, int y, int z) {

		worker.lock();
		markRenderers(x, y, z);
		worker.working = true;
		worker.unlock();
	}

	private static RenderWorker worker = new RenderWorker();
	static {
		worker.start();
	}

	private static class RenderWorker extends Thread {

		public RenderWorker() {

			super("Render Worker");
		}

		public void lock() {

			interrupt();
			lock.lock();
		}

		public void unlock() {

			lock.unlock();
			interrupt();
		}

		public void setWorld(RenderGlobal rg, WorldClient world) {

			lock();
			render = rg;
			theWorld = world;
			unlock();
		}

		public volatile boolean working = false;
		private final ReentrantLock lock = new ReentrantLock();
		private IdentityArrayHashList<WorldRenderer> queue = new IdentityArrayHashList<WorldRenderer>();
		private WorldClient theWorld;
		private RenderGlobal render;

		@Override
		public void run() {

			for (;;) {
				run(true);
			}
		}

		private int fixPos(int pos, int amt) {

			int r = MathHelper.bucketInt(pos, 16) % amt;
			return r < 0 ? r + amt : r;
		}

		private WorldRenderer getRender(int x, int y, int z) {

			x = fixPos(x, render.renderChunksWide);
			y = fixPos(y, render.renderChunksTall);
			z = fixPos(z, render.renderChunksDeep);
			return render.worldRenderers[(z * render.renderChunksTall + y) * render.renderChunksWide + x];
		}

		private void checkOcclusion(WorldRenderer rend, int side, int x, int z) {

			Chunk chunk = theWorld.getChunkFromBlockCoords(rend.posX + x, rend.posZ + z);
			if (chunk instanceof ClientChunk) {
				rend.isVisible |= !((ClientChunk) chunk).solidSides.get(side);
			} else {
				rend.isVisible = true;
			}
		}

		public void run(boolean d) {

			//for (;;)
			{
				l: try {
					EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
					if (theWorld == null || view == null || !working) {
						sleep(10000);
						break l;
					}
					sleep(300);
					lock.lockInterruptibly();
					WorldRenderer center;
					WorldClient theWorld = this.theWorld;
					IdentityArrayHashList<WorldRenderer> queue = this.queue;
					{
						int x = MathHelper.floor_double(view.posX);
						int y = MathHelper.floor_double(view.posY + view.getEyeHeight());
						int z = MathHelper.floor_double(view.posZ);
						center = getRender(x, y, z);
						queue.add(center);
					}
					for (int i = 0; working && !isInterrupted(); working = ++i < queue.size()) {
						WorldRenderer rend = queue.get(i);
						rend.isWaitingOnOcclusionQuery = true;
						Chunk chunk = theWorld.getChunkFromBlockCoords(rend.posX, rend.posZ);
						rend.isVisible = true;
						int yp = (rend.posY >> 4) * 8;
						if (chunk instanceof ClientChunk) {
							rend.isVisible = rend == center;
							BitSet solidSides = ((ClientChunk) chunk).solidSides;
							rend.isVisible |= !solidSides.get((yp + 8) + 0);
							if (yp > 0)
								rend.isVisible |= !solidSides.get((yp - 8) + 1);
							if (!solidSides.get(yp + 0)) {
								queue.add(getRender(rend.posX, rend.posY - 1, rend.posZ));
							}
							if (!solidSides.get(yp + 1)) {
								queue.add(getRender(rend.posX, rend.posY + 16, rend.posZ));
							}
							if (!solidSides.get(yp + 2)) {
								queue.add(getRender(rend.posX - 1, rend.posY, rend.posZ));
							}
							if (!solidSides.get(yp + 3)) {
								queue.add(getRender(rend.posX + 16, rend.posY, rend.posZ));
							}
							if (!solidSides.get(yp + 4)) {
								queue.add(getRender(rend.posX, rend.posY, rend.posZ - 1));
							}
							if (!solidSides.get(yp + 5)) {
								queue.add(getRender(rend.posX, rend.posY, rend.posZ + 16));
							}
						} else {
							rend.isVisible = true;
						}
						if (!rend.isVisible) {
							checkOcclusion(rend, yp + 2, 16, 0);
						}
						if (!rend.isVisible) {
							checkOcclusion(rend, yp + 3, -1, 0);
						}
						if (!rend.isVisible) {
							checkOcclusion(rend, yp + 4, 0, 16);
						}
						if (!rend.isVisible) {
							checkOcclusion(rend, yp + 5, 0, -1);
						}
						if ((i & 7) == 0) {
							lock.unlock();
							sleep(1);
							lock.lockInterruptibly();
						}
					}
					working = false;
					queue.clear();
					lock.unlock();
					sleep(6000);
				} catch (InterruptedException e) {
				}
			}
		}
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
