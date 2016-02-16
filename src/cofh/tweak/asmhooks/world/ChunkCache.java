package cofh.tweak.asmhooks.world;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class ChunkCache {

	private Chunk[][] chunkArray;
	private int xPos, zPos;
	private int width, depth;

	public ChunkCache(World world, int x, int z, int radius) {

		int d = radius * 2 + 1;
		chunkArray = new Chunk[d][d];
		xPos = x - radius;
		zPos = z - radius;
		for (int a = 0; a < d; ++a) {
			int xPos = a - radius + x;
			for (int b = 0; b < d; ++b) {
				int zPos = b - radius + z;
				if (world.blockExists(xPos * 16, 64, zPos * 16)) {
					chunkArray[a][b] = world.getChunkFromChunkCoords(xPos, zPos);
				}
			}
		}
	}

	public final Chunk getChunk(int x, int z) {

		int cX = x - xPos, cZ = z - zPos;
		if (x < xPos | z < zPos | cX > width | cZ > depth) {
			return null;
		}

		return chunkArray[cX][cZ];
	}

}
