package cofh.tweak.asmhooks.render;

import java.util.HashSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class TweakedRenderer extends net.minecraft.client.renderer.WorldRenderer {

	@SuppressWarnings("rawtypes")
	public TweakedRenderer(World world, List tileEntities, int x, int y, int z, int glListBase) {

		super(world, tileEntities, x, y, z, glListBase);
	}

	/**
	 * Sets a new position for the renderer and setting it up so it can be reloaded with the new data for that position
	 */
	@Override
	public void setPosition(int x, int y, int z) {

		if (x != posX || y != posY || z != posZ) {
			setDontDraw();
			posX = x;
			posY = y;
			posZ = z;
			posXPlus = x + 8;
			posYPlus = y + 8;
			posZPlus = z + 8;
			posXClip = x & 1023;
			posYClip = y;
			posZClip = z & 1023;
			posXMinus = x - this.posXClip;
			posYMinus = y - this.posYClip;
			posZMinus = z - this.posZClip;
			final double o = 6;
			rendererBoundingBox = AxisAlignedBB.getBoundingBox(x - o, y - o, z - o, x + 16 + o, y + 16 + o, z + 16 + o);
			markDirty();
			tileEntities.removeAll(tileEntityRenderers);
			tileEntityRenderers.clear();
		}
	}

	/**
	 * Will update this chunk renderer
	 */
	@Override
	public void updateRenderer(EntityLivingBase view) {

		if (needsUpdate) {
			needsUpdate = false;
			skipRenderPass[0] = true;
			skipRenderPass[1] = true;
			bytesDrawn = 0;
			vertexState = null;

			final int xStart = posX;
			final int yStart = posY;
			final int zStart = posZ;
			final int xEnd = posX + 16;
			final int yEnd = posY + 16;
			final int zEnd = posZ + 16;

			Chunk.isLit = false;

			HashSet<TileEntity> existingTiles = null;
			if (tileEntityRenderers.size() > 0) {
				existingTiles = new HashSet<>();
				existingTiles.addAll(tileEntityRenderers);
				tileEntityRenderers.clear();
			}

			EntityLivingBase view2 = Minecraft.getMinecraft().renderViewEntity;
			int viewX = MathHelper.floor_double(view2.posX);
			int viewY = MathHelper.floor_double(view2.posY);
			int viewZ = MathHelper.floor_double(view2.posZ);

			final int off = 1;
			ChunkCache chunkcache = new ChunkCache(worldObj, xStart - off, yStart - off, zStart - off, xEnd + off, yEnd + off, zEnd + off, off);

				++chunksUpdated;
			if (!chunkcache.extendedLevelsInChunkCache()) {
				RenderBlocks renderblocks = null;

				TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
				List<TileEntity> tileEntityRenderers = this.tileEntityRenderers;

				for (int pass = 0; pass < 2; ++pass) {
					boolean hasNextPass = false;
					boolean renderedBlock = false;
					boolean startedDrawing = false;

					for (int y = yStart; y < yEnd; ++y) {
						for (int z = zStart; z < zEnd; ++z) {
							for (int x = xStart; x < xEnd; ++x) {

								Block block = chunkcache.getBlock(x, y, z);
								if (block.getMaterial() != Material.air) {

									if (pass == 0 && block.hasTileEntity(chunkcache.getBlockMetadata(x, y, z))) {
										TileEntity tileentity = chunkcache.getTileEntity(x, y, z);

										if (dispatcher.hasSpecialRenderer(tileentity)) {
											tileEntityRenderers.add(tileentity);
										}
									}

									hasNextPass |= block.getRenderBlockPass() > pass;

									if (block.canRenderInPass(pass)) {
										if (!startedDrawing) {
											if (renderblocks == null) {
												renderblocks = new RenderBlocks(chunkcache);
												net.minecraftforge.client.ForgeHooksClient.setWorldRendererRB(renderblocks);
											}
											startedDrawing = true;
											preRenderBlocks(pass);
										}

										renderedBlock |= renderblocks.renderBlockByRenderType(block, x, y, z);

										if (block.getRenderType() == 0 && x == viewX && y == viewY && z == viewZ) {
											renderblocks.setRenderFromInside(true);
											renderblocks.setRenderAllFaces(true);
											renderblocks.renderBlockByRenderType(block, x, y, z);
											renderblocks.setRenderFromInside(false);
											renderblocks.setRenderAllFaces(false);
										}
									}
								}
							}
						}
					}

					if (startedDrawing) {
						if (renderedBlock) {
							skipRenderPass[pass] = false;
						}
						postRenderBlocks(pass, view);
					}

					if (!hasNextPass) {
						break;
					}
				}
				if (renderblocks != null) {
					net.minecraftforge.client.ForgeHooksClient.setWorldRendererRB(null);
				}
			}

			if (tileEntityRenderers.size() > 0) {
				if (existingTiles == null) {
					tileEntities.addAll(tileEntityRenderers);
				} else {
					HashSet<TileEntity> hashset1 = new HashSet<>();
					hashset1.addAll(tileEntityRenderers);
					hashset1.removeAll(existingTiles);
					tileEntities.addAll(hashset1);
				}
			}
			if (existingTiles != null) {
				existingTiles.removeAll(tileEntityRenderers);
				tileEntities.removeAll(existingTiles);
			}

			isChunkLit = Chunk.isLit;
			isInitialized = true;
		}
	}

    @Override
	public void setDontDraw() {

    	skipRenderPass[0] = true;
    	skipRenderPass[1] = true;
        isInFrustum = false;
        isInitialized = false;
        vertexState = null;
    }

	@Override
	public void callOcclusionQueryList() {

	}

}
