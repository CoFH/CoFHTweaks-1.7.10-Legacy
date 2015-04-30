package cofh.tweak.asmhooks.world;

import cofh.repack.cofh.lib.util.LinkedHashList;
import cofh.repack.net.minecraft.client.renderer.chunk.VisGraph;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class ClientChunk extends Chunk {

	private static ChunkThread worker = new ChunkThread();
	static {
		worker.start();
	}
	private static class ChunkThread extends Thread {

		public ChunkThread() {

			super("Chunk Worker");
		}

		public LinkedHashList<ClientChunk> data = new LinkedHashList<ClientChunk>();

		@Override
		public void run() {

			for (;;) {

				for (int i = 0; data.size() > 0; ++i) {
					data.shift().buildSides();
					if ((i & 3) == 0) {
						i = 0;
						try {
							Thread.sleep(3);
						} catch (InterruptedException e) {
						}
					}
				}
				try {
					Thread.sleep(30);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	public VisGraph[] visibility = new VisGraph[16];

	private static void init(VisGraph[] internalSides) {

		for (int i = 0; i < 16; ++i) {
			internalSides[i] = new VisGraph();
		}
	}

	public ClientChunk(World world, int x, int z) {

		super(world, x, z);
		init(visibility);
	}

	public ClientChunk(World world, Block[] blocks, int x, int z) {

		super(world, blocks, x, z);
		init(visibility);
	}

	public ClientChunk(World world, Block[] blocks, byte[] metas, int x, int z) {

		super(world, blocks, metas, x, z);
		init(visibility);
	}

	void checkPosSolid(int x, int y, int z, Block block) {

		if (y > 255 || y < 0)
			return;
		if (block == null) {
			block = getBlock(x, y, z);
		}
		VisGraph chunk = this.visibility[y >> 4];
		y &= 15;

		chunk.setOpaque(x, y, z, block.isOpaqueCube());
	}

	@Override
	public boolean func_150807_a(int x, int y, int z, Block block, int meta) {

		boolean r = super.func_150807_a(x, y, z, block, meta);
		if (r) {
			checkPosSolid(x & 15, y, z & 15, block);
		}
		return r;
	}

	@Override
	public void fillChunk(byte[] data, int x, int z, boolean flag) {

		super.fillChunk(data, x, z, flag);
		worker.data.add(this);
	}

	void buildSides() {

		if (!this.worldObj.chunkExists(xPosition, zPosition)) {
			return;
		}
		for (int i = 0; i < 16; ++i) {
			for (int j = 0; j < 16; ++j) {
				for (int y = 0; y < 256; ++y) {
					checkPosSolid(i, y, j, null);
				}
			}
		}
	}

}
