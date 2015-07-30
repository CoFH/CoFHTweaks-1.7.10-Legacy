package cofh.tweak.asmhooks.render;

import cofh.repack.cofh.lib.util.IdentityLinkedHashList;
import cofh.repack.net.minecraft.client.renderer.chunk.VisGraph;
import cofh.tweak.CoFHTweaks;
import cofh.tweak.asmhooks.world.ClientChunk;
import cofh.tweak.util.Frustrum;
import cofh.tweak.util.Vector3;
import cpw.mods.fml.common.FMLLog;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

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

	}

	@Override
	public boolean updateRenderers(EntityLivingBase view, boolean p_72716_2_) {

		int yaw = MathHelper.floor_float(view.rotationYaw + 45) >> 5;
		int pitch = MathHelper.floor_float(view.rotationPitch + 45) >> 5;
		if (yaw != prevRotationYaw || pitch != prevRotationPitch) {
			worker.run(true);
		}
		int lim = worldRenderersToUpdate.size() + workerWorldRenderers.size();
		if (lim == 0) {
			return true;
		}
		theWorld.theProfiler.startSection("rebuild");
		long start = System.nanoTime();

		for (int c = 0, i = 0; c < lim; ++c) {
			++i;
			WorldRenderer worldrenderer;
			if (workerWorldRenderers.size() > 0) {
				worldrenderer = workerWorldRenderers.shift();
				worldRenderersToUpdateList.remove(worldrenderer);
			} else {
				worldrenderer = worldRenderersToUpdateList.shift();
			}

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
					if (t > 5000000L >>> 1)
						break;
				}
			}
		}

		theWorld.theProfiler.endSection();
		return true;
	}

	@Override
	public void renderEntities(EntityLivingBase view, ICamera camera, float tick) {

		int pass = MinecraftForgeClient.getRenderPass();
		if (this.renderEntitiesStartupCounter > 0) {
			if (pass > 0) return;
			--this.renderEntitiesStartupCounter;
			return;
		}

		theWorld.theProfiler.startSection("prepare");

		double d0 = view.prevPosX + (view.posX - view.prevPosX) * tick;
		double d1 = view.prevPosY + (view.posY - view.prevPosY) * tick;
		double d2 = view.prevPosZ + (view.posZ - view.prevPosZ) * tick;

		TileEntityRendererDispatcher.instance.cacheActiveRenderInfo(theWorld, mc.getTextureManager(),
			mc.fontRenderer, mc.renderViewEntity, tick);
		RenderManager.instance.cacheActiveRenderInfo(theWorld, mc.getTextureManager(), mc.fontRenderer,
			mc.renderViewEntity, mc.pointedEntity, mc.gameSettings, tick);

		if (pass == 0) {
			countEntitiesTotal = 0;
			countEntitiesRendered = 0;
			countEntitiesHidden = 0;
		}

		view = mc.renderViewEntity;
		double d3 = view.lastTickPosX + (view.posX - view.lastTickPosX) * tick;
		double d4 = view.lastTickPosY + (view.posY - view.lastTickPosY) * tick;
		double d5 = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * tick;

		TileEntityRendererDispatcher.staticPlayerX = d3;
		TileEntityRendererDispatcher.staticPlayerY = d4;
		TileEntityRendererDispatcher.staticPlayerZ = d5;
		theWorld.theProfiler.endStartSection("staticentities");

		if (displayListEntitiesDirty) {
			RenderManager.renderPosX = 0.0D;
			RenderManager.renderPosY = 0.0D;
			RenderManager.renderPosZ = 0.0D;
			rebuildDisplayListEntities();
		}

		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glTranslated(-d3, -d4, -d5);
		GL11.glCallList(displayListEntities);
		GL11.glPopMatrix();

		RenderManager.renderPosX = d3;
		RenderManager.renderPosY = d4;
		RenderManager.renderPosZ = d5;
		theWorld.theProfiler.endStartSection("global");

		mc.entityRenderer.enableLightmap(tick);

		@SuppressWarnings("rawtypes")
		List list = theWorld.getLoadedEntityList();
		if (pass == 0) {
			countEntitiesTotal = list.size();
		}

		for (int i = 0; i < theWorld.weatherEffects.size(); ++i) {
			Entity entity = (Entity) theWorld.weatherEffects.get(i);
			if (!entity.shouldRenderInPass(pass)) continue;

			if (entity.isInRangeToRender3d(d0, d1, d2)) {
				++countEntitiesRendered;
				RenderManager.instance.renderEntitySimple(entity, tick);
			}
		}

		theWorld.theProfiler.endStartSection("entities");

		for (int i = 0; i < list.size(); ++i) {
			Entity entity = (Entity) list.get(i);
			if (!entity.shouldRenderInPass(pass)) continue;
			boolean flag = entity.riddenByEntity == mc.thePlayer;
			if (!flag) {
				flag = entity.isInRangeToRender3d(d0, d1, d2);
				flag &= entity.ignoreFrustumCheck || camera.isBoundingBoxInFrustum(entity.boundingBox);
			}

			if (!flag && entity instanceof EntityLiving) {
				EntityLiving entityliving = (EntityLiving) entity;

				if (entityliving.getLeashed() && entityliving.getLeashedToEntity() != null) {
					Entity entity1 = entityliving.getLeashedToEntity();
					flag = camera.isBoundingBoxInFrustum(entity1.boundingBox);
				}
			}

			if (flag && entity != view || mc.gameSettings.thirdPersonView != 0 || view.isPlayerSleeping()) {
				WorldRenderer rend = getRenderer(entity.posX, entity.posY, entity.posZ);
				if (rend == null || !rend.isVisible) {
					++countEntitiesHidden;
					continue;
				}
				++countEntitiesRendered;
				RenderManager.instance.renderEntitySimple(entity, tick);
			}
		}

		theWorld.theProfiler.endStartSection("blockentities");
		RenderHelper.enableStandardItemLighting();

		for (int i = 0; i < tileEntities.size(); ++i) {
			TileEntity tile = (TileEntity) tileEntities.get(i);
			if (tile.shouldRenderInPass(pass) && camera.isBoundingBoxInFrustum(tile.getRenderBoundingBox())) {
				TileEntityRendererDispatcher.instance.renderTileEntity(tile, tick);
			}
		}

		mc.entityRenderer.disableLightmap(tick);
		theWorld.theProfiler.endSection();
	}

	@Override
	public String getDebugInfoRenders() {

		StringBuilder r = new StringBuilder(3 + 4 + 1 + 4 + 5 + 4 + 5 + 4 + 5 + 4 + 5 + 3 + 5 + 2);
		r.append("C: ").append(renderersBeingRendered).append('/').append(renderersLoaded);
		r.append(". F: ").append(renderersBeingClipped);
		r.append(", O: ").append(renderersBeingOccluded);
		r.append(", E: ").append(renderersSkippingRenderPass);
		r.append("; I: ").append(dummyRenderInt);
		r.append(", U: ").append(workerWorldRenderers.size());
		return r.toString();
	}

	@Override
	public String getDebugInfoEntities() {

		StringBuilder r = new StringBuilder(3 + 4 + 1 + 4 + 5 + 4 + 5 + 4);
		r.append("E: ").append(countEntitiesRendered).append('/').append(countEntitiesTotal);
		r.append(". B: ").append(countEntitiesHidden);
		r.append(", I: ").append(countEntitiesTotal - countEntitiesHidden - countEntitiesRendered);
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
				} else if (!rend.isInFrustum) {
					++renderersBeingClipped;
				} else if (!rend.isVisible) {
					++renderersBeingOccluded;
				} else if (rend.skipRenderPass[0] && rend.skipRenderPass[1]) {
					++renderersSkippingRenderPass;
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
						worldRenderersToUpdate.add(worldrenderer);
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
			worker.run(true);
		}
	}

	@Override
	public void setWorldAndLoadRenderers(WorldClient world) {

		if (world != null) {
			if (!CoFHTweaks.canHaveWorld()) {
				FMLLog.bigWarning("World exists prior to starting the server!");
			}
		}
		worker.setWorld(this, world);
		super.setWorldAndLoadRenderers(world);
	}

	@Override
	public void loadRenderers() {

		super.loadRenderers();
		worker.run(true);
	}

	@Override
	protected void markRenderersForNewPosition(int x, int y, int z) {

		markRenderers(x, y, z);
		worker.run(true);
	}

	private int fixPos(int pos, int amt) {

		int r = MathHelper.bucketInt(pos, 16) % amt;
		return r < 0 ? r + amt : r;
	}

	private WorldRenderer getRenderer(int x, int y, int z) {

		if (y > maxBlockY || y < minBlockY)
			return null;
		if (x > maxBlockX || x < minBlockX)
			return null;
		if (z > maxBlockZ || z < minBlockZ)
			return null;
		x = fixPos(x, renderChunksWide);
		y = fixPos(y, renderChunksTall);
		z = fixPos(z, renderChunksDeep);
		return worldRenderers[(z * renderChunksTall + y) * renderChunksWide + x];
	}

	private WorldRenderer getRenderer(double x, double y, double z) {

		int X = MathHelper.floor_double(x);
		int Y = MathHelper.floor_double(y);
		int Z = MathHelper.floor_double(z);
		return getRenderer(X, Y, Z);
	}

	public static RenderWorker worker = new RenderWorker();
	static {
		//worker.start();
	}

	public static class RenderWorker extends Thread {

		public RenderWorker() {

			super("Render Worker");
			/*for (int i = 0; i < fStack.length; ++i) {
				fStack[i] = new Frustrum();
			}//*/
		}

		public void setWorld(RenderGlobal rg, WorldClient world) {

			render = rg;
			theWorld = world;
		}

		public volatile boolean dirty = false;
		private ArrayDeque<CullInfo> queue = new ArrayDeque<CullInfo>();
		private Frustrum fStack = new Frustrum();
		private IdentityHashMap<WorldRenderer, CullInfo> log = new IdentityHashMap<WorldRenderer, CullInfo>();
		private WorldClient theWorld;
		private RenderGlobal render;

		@Override
		public void run() {

			for (;;) {
				if (dirty) {
					run(false);
				} else {
					try {
						sleep(300);
					} catch (InterruptedException e) {
					}
				}
			}
		}

		public void run(boolean immediate) {

			l: try {
				EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
				if (theWorld == null || view == null) {
					break l;
				} else {
					if (!CoFHTweaks.canHaveWorld()) {
						FMLLog.bigWarning("World exists prior to starting the server!");
						return;
					}
				}
				for (WorldRenderer rend : render.worldRenderers) {
					rend.isWaitingOnOcclusionQuery = false;
				}
				WorldRenderer center;
				WorldClient theWorld = this.theWorld;
				ArrayDeque<CullInfo> queue = this.queue;
				RenderPosition back = RenderPosition.getBackFacingFromVector(view);
				Vector3 p_view = new Vector3();
				Vector3 view_look = new Vector3();
				{
					int x = MathHelper.floor_double(view.posX);
					int y = MathHelper.floor_double(view.posY + view.getEyeHeight());
					int z = MathHelper.floor_double(view.posZ);
					view_look.set(0, 0, -1);
					view_look.rotate(view.rotationPitch * (float)Math.PI / 180, Vector3.axes[3]);
					view_look.rotate(view.rotationYaw * (float)Math.PI / 180, Vector3.axes[1]);
					view_look.normalize();

					p_view.set(view_look).multiply(64).add(x, y, z);

					center = render.getRenderer(x, y, z);
					if (center == null) {
						dirty = false;
						break l;
					}
					markRenderer(center, view);
					Chunk chunk = theWorld.getChunkFromBlockCoords(center.posX, center.posZ);
					if (chunk instanceof ClientChunk) {
						VisGraph sides = ((ClientChunk) chunk).visibility[center.posY >> 4];
						Set<EnumFacing> faces = sides.getVisibleFacingsFrom(x, y, z);
						RenderPosition[] bias = RenderPosition.POSITIONS_BIAS[back.ordinal()];
						for (int p = 0; p < 6; ++p) {
							RenderPosition pos = bias[p];
							if (!faces.contains(pos.facing))
								continue;
							WorldRenderer t = render.getRenderer(center.posX + pos.x, center.posY + pos.y, center.posZ + pos.z);

							if (t == null)
								continue;

							CullInfo info = new CullInfo(t, pos, (render.renderDistanceChunks >> 1) * -1 - 2);
							info.facings.remove(pos);
							log.put(t, info);
							queue.add(info);
						}
					}
				}
				if (!queue.isEmpty()) {
					@SuppressWarnings("unused")
					int visited = queue.size(), considered = visited;
					//fStack.setPosition(p_view.x, p_view.y, p_view.z);
					//Matrix4 view_m = new Matrix4(ClippingHelperImpl.instance.modelviewMatrix);
					{
						//float m = (float) Math.PI / 180;
						//float v = (float) Math.cos(view.rotationPitch * m);
						//view_m.camera(new Vector3(
						//	v * (float) Math.sin((view.rotationYaw +180) * m),
						//	-(float) Math.sin(view.rotationPitch * m),
						//	v * (float) Math.cos((view.rotationYaw +180) * m)
						//));
					}
					//Matrix4 view_p = new Matrix4(ClippingHelperImpl.instance.projectionMatrix);
					//Vector3 p_chunk = new Vector3();
					// TODO: frustrum stack: https://tomcc.github.io/frustum_clamping.html
					RenderPosition[] bias = RenderPosition.POSITIONS_BIAS[back.ordinal() ^ 1];
					for (; !queue.isEmpty() && !isInterrupted();) {
						CullInfo info = queue.pollFirst();
						if (info == null) {
							break;
						}

						info.visited = true;
						WorldRenderer rend = info.rend;
						markRenderer(rend, view);
						if (info.count > render.renderDistanceChunks)
							continue;

						Chunk chunk = theWorld.getChunkFromBlockCoords(rend.posX, rend.posZ);

						if (chunk instanceof ClientChunk) {
							VisGraph sides = ((ClientChunk) chunk).visibility[rend.posY >> 4];
							RenderPosition opp = info.last;

							//p_chunk.set(rend.posX + 8, rend.posY + 8, rend.posZ + 8);
							//view_p.perspective(0, 0, (float) p_chunk.sub(p_view).mag(), 1250);
							//fStack.set(view_m, view_p);

							for (int p = 0; p < 6; ++p) {
								RenderPosition pos = bias[p];
								if (pos == back || pos == opp || info.facings.contains(pos))
									continue;

								if (sides.getVisibility().isVisible(opp.facing, pos.facing)) {
									info.facings.add(pos);

									WorldRenderer t = render.getRenderer(rend.posX + pos.x, rend.posY + pos.y, rend.posZ + pos.z);
									if (t != null) {
										++considered;
										int cost = 1;

										CullInfo prev = log.get(t);
										if (prev != null) {
											if (prev.facings.contains(pos))
												continue;

											t: if (!prev.visited) {
												Chunk o;
												if (t.posX != rend.posX || t.posZ != rend.posZ) {
													o = theWorld.getChunkFromBlockCoords(t.posX, t.posZ);
													if (!(o instanceof ClientChunk)) {
														break t;
													}
												} else
													o = chunk;
												VisGraph oSides = ((ClientChunk) o).visibility[t.posY >> 4];
												if (oSides.getVisibility().isVisible(pos.facing, prev.last.facing)) {
													continue;
												}
											}
										}

										//if (!fStack.isBoundingBoxInFrustum(t.rendererBoundingBox))
											//continue;

										if (t.skipAllRenderPasses())
											cost = 0;

										++visited;
										CullInfo data = new CullInfo(t, pos, info.count + cost);

										if (prev != null) {
											data.facings.addAll(prev.facings);
										}

										log.put(t, data);
										queue.add(data);
									}
								}
							}
						}
					}
				}
						for (WorldRenderer rend : render.worldRenderers) {
							if (!rend.isWaitingOnOcclusionQuery)
								rend.isVisible = false;
						}
					queue.clear();
					log.clear();
			} finally {
				dirty = false;
			}
		}

		private void markRenderer(WorldRenderer rend, EntityLivingBase view) {

			rend.isVisible = rend.isWaitingOnOcclusionQuery = true;
			if (!rend.isInitialized || rend.glOcclusionQuery < 0) {
				rend.glOcclusionQuery = 0;
				rend.needsUpdate = true;
				if (!rend.isInitialized || (rend.distanceToEntitySquared(view) <= 1128.0F)) {
					render.workerWorldRenderers.push(rend);
				}
			}
		}

		private static class CullInfo {

			boolean visited = false;
			final int count;
			final WorldRenderer rend;
			final RenderPosition last;
			final EnumSet<RenderPosition> facings;

			public CullInfo(WorldRenderer rend, RenderPosition pos, int count) {

				this.count = count;
				this.rend = rend;
				this.last = pos.getOpposite();
				this.facings = EnumSet.of(last);
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
