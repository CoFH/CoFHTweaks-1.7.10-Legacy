package cofh.tweak.asmhooks.render;

import cofh.repack.cofh.lib.util.IdentityLinkedHashList;
import cofh.repack.net.minecraft.client.renderer.chunk.VisGraph;
import cofh.tweak.IdentityArrayHashList;
import cofh.tweak.asmhooks.world.ClientChunk;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;

public class RenderGlobal extends net.minecraft.client.renderer.RenderGlobal {

	private int prevRotationPitch = -9999;
	private int prevRotationYaw = -9999;
	private IdentityLinkedHashList<WorldRenderer> worldRenderersToUpdateList;
	private IdentityLinkedHashList<WorldRenderer> workerWorldRenderers;

	public RenderGlobal(Minecraft minecraft) {

		super(minecraft);
		worldRenderersToUpdate = worldRenderersToUpdateList = new IdentityLinkedHashList<WorldRenderer>();
		workerWorldRenderers = new IdentityLinkedHashList<WorldRenderer>();
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

		for (int i = 0; i < 10 && workerWorldRenderers.size() > 0; ++i) {
			WorldRenderer rend = workerWorldRenderers.shift();
			worldRenderersToUpdateList.remove(rend);
			worldRenderersToUpdateList.unshift(rend);
		}

	}

	@Override
	public boolean updateRenderers(EntityLivingBase view, boolean p_72716_2_) {

		int yaw = MathHelper.floor_float(view.rotationYaw) >> 2;
		int pitch = MathHelper.floor_float(view.rotationPitch + 45) >> 3;
		if (yaw != prevRotationYaw || pitch != prevRotationPitch) {
			prevRotationYaw = yaw;
			prevRotationPitch = pitch;
			worker.clean = true;
			worker.interrupt();
		}
		int lim = worldRenderersToUpdate.size();
		if (lim == 0) {
			return true;
		}
		theWorld.theProfiler.startSection("rebuild");
		long start = System.nanoTime();

		for (int c = 0, i = 0; c < lim; ++c) {
			++i;
			WorldRenderer worldrenderer = worldRenderersToUpdateList.shift();

			if (worldrenderer != null) {

				if (!worldrenderer.isInFrustum || !worldrenderer.isVisible) {
					worldrenderer.needsUpdate = false;
					continue;
				}

				worldrenderer.updateRenderer(view);
				worldrenderer.needsUpdate = false;

				if (i > 5) {
					i = 0;
					long t = (System.nanoTime() - start) >>> 1;
					if (t > 10000000L >>> 1)
						break;
				}
			}
		}

		theWorld.theProfiler.endSection();
		return true;
	}

	@Override
	public String getDebugInfoRenders() {

		StringBuilder r = new StringBuilder(3 + 4 + 1 + 4 + 5 + 4 + 5 + 4 + 5 + 4 + 5 + 3 + 5 + 2 + 2);
		r.append("C: ").append(renderersBeingRendered).append('/').append(renderersLoaded);
		r.append(". F: ").append(renderersBeingClipped);
		r.append(", O: ").append(renderersBeingOccluded);
		r.append(", E: ").append(renderersSkippingRenderPass);
		r.append("; I: ").append(dummyRenderInt);
		r.append(", U: ").append(worldRenderersToUpdateList.size());
		r.append(" |").append(worker.working ? 'Y' : 'N');
		return r.toString();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected int renderSortedRenderers(int start, int end, int pass, double tick) {

		int l = 0;
		int i1 = start;
		int j1 = end;
		byte b0 = 1;

		if (pass == 1) {
			i1 = sortedWorldRenderers.length - 1 - start;
			j1 = sortedWorldRenderers.length - 1 - end;
			b0 = -1;
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

		for (int k1 = i1; k1 != j1; k1 += b0) {
			WorldRenderer rend = sortedWorldRenderers[k1];
			if (pass == 0) {
				++renderersLoaded;

				if (!rend.isInitialized) {
					++dummyRenderInt;
				} else if (rend.skipRenderPass[0] && rend.skipRenderPass[1]) {
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
					++l;
				}
			}
		}

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
						if (worldrenderer.distanceToEntitySquared(mc.renderViewEntity) > 272.0F) {
							worldRenderersToUpdate.add(worldrenderer);
						} else {
							worldRenderersToUpdateList.unshift(worldrenderer);
						}
					}
				}
			}
		}
	}

	@Override
	public void markBlocksForUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {

		int k1 = MathHelper.bucketInt(x1, 16);
		int l1 = MathHelper.bucketInt(y1, 16);
		int i2 = MathHelper.bucketInt(z1, 16);
		int j2 = MathHelper.bucketInt(x2, 16);
		int k2 = MathHelper.bucketInt(y2, 16);
		int l2 = MathHelper.bucketInt(z2, 16);
		boolean rebuild = false;

		for (int i3 = k1; i3 <= j2; ++i3) {
			int j3 = i3 % this.renderChunksWide;

			if (j3 < 0) {
				j3 += this.renderChunksWide;
			}

			for (int k3 = l1; k3 <= k2; ++k3) {
				int l3 = k3 % this.renderChunksTall;

				if (l3 < 0) {
					l3 += this.renderChunksTall;
				}

				for (int i4 = i2; i4 <= l2; ++i4) {
					int j4 = i4 % this.renderChunksDeep;

					if (j4 < 0) {
						j4 += this.renderChunksDeep;
					}

					int k4 = (j4 * renderChunksTall + l3) * renderChunksWide + j3;
					WorldRenderer worldrenderer = worldRenderers[k4];

					if (!worldrenderer.needsUpdate) {
						worldrenderer.markDirty();
						if (worldrenderer.distanceToEntitySquared(mc.renderViewEntity) > 1728.0F) {
							worldRenderersToUpdate.add(worldrenderer);
						} else {
							Chunk chunk = theWorld.getChunkFromBlockCoords(worldrenderer.posX, worldrenderer.posZ);
							if (chunk instanceof ClientChunk) {
								if (((ClientChunk) chunk).visibility[worldrenderer.posY >> 4].isDirty()) {
									worldrenderer.glOcclusionQuery = -1;
									rebuild = true;
								}
							}
							worldRenderersToUpdateList.unshift(worldrenderer);
						}
					}
				}
			}
		}

		if (rebuild) {
			prevRotationYaw = -9999;
			/*
			 * worker.lock();
			 * worker.working = true;
			 * worker.run(true);
			 * worker.unlock();//
			 */
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
		worker.run(true);
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

		public volatile boolean working = false, clean = false;
		private final ReentrantLock lock = new ReentrantLock();
		private ArrayDeque<CullInfo> queue = new ArrayDeque<CullInfo>();
		private IdentityArrayHashList<WorldRenderer> log = new IdentityArrayHashList<WorldRenderer>();
		private WorldClient theWorld;
		private RenderGlobal render;

		@Override
		public void run() {

			for (;;) {
				run(false);
			}
		}

		private int fixPos(int pos, int amt) {

			int r = MathHelper.bucketInt(pos, 16) % amt;
			return r < 0 ? r + amt : r;
		}

		private WorldRenderer getRender(int x, int y, int z) {

			if (y > render.maxBlockY || y < render.minBlockY)
				return null;
			if (x > render.maxBlockX || x < render.minBlockX)
				return null;
			if (z > render.maxBlockZ || z < render.minBlockZ)
				return null;
			x = fixPos(x, render.renderChunksWide);
			y = fixPos(y, render.renderChunksTall);
			z = fixPos(z, render.renderChunksDeep);
			return render.worldRenderers[(z * render.renderChunksTall + y) * render.renderChunksWide + x];
		}

		public void run(boolean immediate) {

			//for (;;)
			{
				l: try {
					EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
					if (theWorld == null || view == null || !(working || clean || immediate)) {
						if (!immediate)
							sleep(10000);
						break l;
					}
					if (!immediate) {
						sleep(300);
						lock.lockInterruptibly();
					}
					if (clean) {
						working = true;
						clean = false;
					}
					if (!immediate) {
						for (WorldRenderer rend : render.worldRenderers) {
							rend.isWaitingOnOcclusionQuery = false;
						}
						lock.unlock();
						sleep(1);
						lock.lockInterruptibly();
					}
					WorldRenderer center;
					WorldClient theWorld = this.theWorld;
					ArrayDeque<CullInfo> queue = this.queue;
					RenderPosition back = RenderPosition.getBackFacingFromVector(view);
					{
						int x = MathHelper.floor_double(view.posX);
						int y = MathHelper.floor_double(view.posY + view.getEyeHeight());
						int z = MathHelper.floor_double(view.posZ);
						center = getRender(x, y, z);
						if (center == null) {
							working = false;
							if (!immediate)
								lock.unlock();
							break l;
						}
						center.needsUpdate |= !center.isVisible;
						center.isVisible = center.isWaitingOnOcclusionQuery = true;
						log.add(center);
						Chunk chunk = theWorld.getChunkFromBlockCoords(center.posX, center.posZ);
						if (chunk instanceof ClientChunk) {
							VisGraph sides = ((ClientChunk) chunk).visibility[center.posY >> 4];
							Set<EnumFacing> faces = sides.getVisibleFacingsFrom(x, y, z);
							RenderPosition[] bias = RenderPosition.POSITIONS_BIAS[back.ordinal()];
							for (int p = 0; p < 6; ++p) {
								RenderPosition pos = bias[p];
								if (!faces.contains(pos.facing))
									continue;
								WorldRenderer t = getRender(center.posX + pos.x, center.posY + pos.y, center.posZ + pos.z);
								if (t == null || !log.add(t))
									continue;
								queue.add(new CullInfo(t, pos, (render.renderDistanceChunks >> 1) * (immediate ? 1 : -1) - 2));
							}
						}
						working = queue.size() > 0;
					}
					for (int i = 0; working && !isInterrupted(); working = !queue.isEmpty()) {
						CullInfo info = queue.pollLast();
						WorldRenderer rend = info.rend;
						if (rend.glOcclusionQuery < 0) {
							rend.glOcclusionQuery = 0;
							if (!rend.isVisible && rend.distanceToEntitySquared(view) <= 1128.0F) {
								rend.needsUpdate = true;
								rend.isVisible = true;
								render.workerWorldRenderers.push(rend);
							}
						}
						rend.isVisible = rend.isWaitingOnOcclusionQuery = true;
						Chunk chunk = theWorld.getChunkFromBlockCoords(rend.posX, rend.posZ);
						if (info.count <= render.renderDistanceChunks && chunk instanceof ClientChunk) {
							VisGraph sides = ((ClientChunk) chunk).visibility[rend.posY >> 4];
							RenderPosition opp = info.pos.getOpposite();
							RenderPosition[] bias = RenderPosition.POSITIONS_BIAS[back.ordinal()];
							for (int p = 0; p < 6; ++p) {
								RenderPosition pos = bias[p];
								if (pos == back || pos == opp)
									continue;
								if (sides.getVisibility().isVisible(opp.facing, pos.facing)) {
									int cost = 1;
									if (cost == 0) {
										// no-op
										RenderPosition o = pos.getOpposite();
										WorldRenderer t = getRender(rend.posX + o.x, rend.posY + o.y, rend.posZ + o.z);
										if (t != rend && !log.contains(t))
											++cost;
										t = getRender(rend.posX + o.x + opp.x, rend.posY + o.y + opp.y, rend.posZ + o.z + opp.z);
										if (t != rend && !log.contains(t))
											++cost;
									}
									WorldRenderer t = getRender(rend.posX + pos.x, rend.posY + pos.y, rend.posZ + pos.z);
									if (t != null && log.add(t)) {
										if (t.skipAllRenderPasses())
											cost = 0;
										queue.add(new CullInfo(t, pos, info.count + cost));
									}
								}
							}
						}
						if ((++i & 63) == 0 && !immediate) {
							lock.unlock();
							sleep(1);
							lock.lockInterruptibly();
						}
					}
					if (!immediate) {
						if (!isInterrupted()) {
							for (WorldRenderer rend : render.worldRenderers) {
								if (!rend.isWaitingOnOcclusionQuery)
									rend.isVisible = false;
							}
						}
						lock.unlock();
						sleep(6000);
					}
				} catch (InterruptedException e) {
				} finally {
					working = false;
					queue.clear();
					log.clear();
				}
			}
		}

		private static class CullInfo {

			final int count;
			final WorldRenderer rend;
			final RenderPosition pos;

			public CullInfo(WorldRenderer rend, RenderPosition pos, int count) {

				this.count = count;
				this.rend = rend;
				this.pos = pos;
			}

		}
	}

	private static enum RenderPosition {
		DOWN(EnumFacing.DOWN, 0, -1, 0),
		UP(EnumFacing.UP, 0, 16, 0),
		WEST(EnumFacing.WEST, -1, 0, 0),
		EAST(EnumFacing.EAST, 16, 0, 0),
		NORTH(EnumFacing.NORTH, 0, 0, -1),
		SOUTH(EnumFacing.SOUTH, 0, 0, 16),
		NONE(null, 0, 0, 0),
		NONE_opp(null, 0, 0, 0);

		public static final RenderPosition[] POSITIONS = values();
		public static final RenderPosition[][] POSITIONS_BIAS = new RenderPosition[6][6];
		public static final RenderPosition[] FROM_FACING = new RenderPosition[6];
		static {
			for (int i = 0; i < 6; ++i) {
				RenderPosition pos = POSITIONS[i];
				FROM_FACING[pos.facing.ordinal()] = pos;
				RenderPosition[] bias = POSITIONS_BIAS[i];
				int j = 0;
				switch (pos) {
				case DOWN:
				case UP:
					bias[j++] = pos;
					bias[j++] = NORTH;
					bias[j++] = SOUTH;
					bias[j++] = EAST;
					bias[j++] = WEST;
					bias[j++] = pos.getOpposite();
					break;
				case WEST:
				case EAST:
					bias[j++] = pos;
					bias[j++] = NORTH;
					bias[j++] = SOUTH;
					bias[j++] = UP;
					bias[j++] = DOWN;
					bias[j++] = pos.getOpposite();
					break;
				case NORTH:
				case SOUTH:
					bias[j++] = pos;
					bias[j++] = EAST;
					bias[j++] = WEST;
					bias[j++] = UP;
					bias[j++] = DOWN;
					bias[j++] = pos.getOpposite();
					break;
				case NONE:
				case NONE_opp:
					break;
				}
			}
		}

		public final int x, y, z;
		public final EnumFacing facing;

		RenderPosition(EnumFacing face, int x, int y, int z) {

			this.facing = face;
			this.x = x;
			this.y = y;
			this.z = z;
			_x = x > 0 ? 1 : x;
			_y = y > 0 ? 1 : y;
			_z = z > 0 ? 1 : z;
		}

		public RenderPosition getOpposite() {

			return POSITIONS[ordinal() ^ 1];
		}

		private final int _x, _y, _z;

		public static RenderPosition getBackFacingFromVector(EntityLivingBase e) {

			float x, y, z;
			{
				float f = e.rotationPitch;
				float f1 = e.rotationYaw;

				if (Minecraft.getMinecraft().gameSettings.thirdPersonView == 2)
				{
					f += 180.0F;
				}

				float f2 = MathHelper.cos(-f1 * 0.017453292F - (float) Math.PI);
				float f3 = MathHelper.sin(-f1 * 0.017453292F - (float) Math.PI);
				float f4 = -MathHelper.cos(-f * 0.017453292F);
				float f5 = MathHelper.sin(-f * 0.017453292F);
				x = f3 * f4;
				y = f5;
				z = f2 * f4;
			}
			RenderPosition ret = NORTH;
			float max = Float.MIN_VALUE;
			RenderPosition[] values = values();
			int i = values.length;

			for (int j = 0; j < i; ++j) {
				RenderPosition face = values[j];
				float cur = x * -face._x + y * -face._y + z * -face._z;

				if (cur > max) {
					max = cur;
					ret = face;
				}
			}

			return ret;
		}
	}

}
