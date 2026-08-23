package com.mceteams.xii.world;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

public class FlatChunkGenerator extends ChunkGenerator {

    private static final int BASE_HEIGHT = 64;

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        ChunkData data = createChunkData(world);

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                data.setBlock(bx, 0, bz, org.bukkit.Material.BEDROCK);

                for (int y = 1; y <= 60; y++) {
                    data.setBlock(bx, y, bz, org.bukkit.Material.STONE);
                }

                for (int y = 61; y <= 63; y++) {
                    data.setBlock(bx, y, bz, org.bukkit.Material.DIRT);
                }

                data.setBlock(bx, BASE_HEIGHT, bz, org.bukkit.Material.GRASS_BLOCK);

                for (int y = BASE_HEIGHT + 1; y <= world.getMaxHeight(); y++) {
                    data.setBlock(bx, y, bz, org.bukkit.Material.AIR);
                }
            }
        }

        return data;
    }

    @Override
    public boolean shouldGenerateNoise() { return false; }
    @Override
    public boolean shouldGenerateSurface() { return false; }
    @Override
    public boolean shouldGenerateBedrock() { return false; }
    @Override
    public boolean shouldGenerateCaves() { return false; }
    @Override
    public boolean shouldGenerateDecorations() { return false; }
    @Override
    public boolean shouldGenerateMobs() { return false; }
    @Override
    public boolean shouldGenerateStructures() { return false; }
}
