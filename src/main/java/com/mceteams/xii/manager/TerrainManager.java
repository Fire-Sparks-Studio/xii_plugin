package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameZone;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerrainManager {
    private static final int FLAT_RADIUS = 200;
    private static final int BATCH_CHUNKS = 4;
    private static final long BATCH_TICKS = 1L;

    private final XiiPlugin plugin;
    private final AtomicBoolean flattening = new AtomicBoolean(false);

    public TerrainManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    public void flattenCenter(GameZone zone) {
        // Désactivé : le monde est déjà plat via le datapack flat. Éviter de détruire/remplir les blocs.
    }

    private void flattenChunk(World world, int chunkX, int chunkZ, int cx, int cz) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;
        if (cx + FLAT_RADIUS < startX || cx - FLAT_RADIUS > endX || cz + FLAT_RADIUS < startZ || cz - FLAT_RADIUS > endZ) {
            return;
        }

        int surfaceY = 64;
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - cx;
                double dz = z - cz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > FLAT_RADIUS) {
                    continue;
                }
                int top = world.getHighestBlockYAt(x, z);
                if (top < 0) {
                    continue;
                }
                // find ground level near surfaceY approximation
                while (top > world.getMinHeight() && isReplaceable(world.getBlockAt(x, top, z).getType())) {
                    top--;
                }
                // if there is water above ground we don't want to fill massively; just level
                for (int y = top + 1; y <= surfaceY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir() || isReplaceable(b.getType())) {
                        b.setType(Material.AIR, false);
                    }
                }
                // fill down to surfaceY with dirt if below
                if (top < surfaceY) {
                    for (int y = top + 1; y <= surfaceY; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType().isAir()) {
                            b.setType(Material.DIRT, false);
                        }
                    }
                } else if (top > surfaceY) {
                    for (int y = top; y > surfaceY; y--) {
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir() && b.getType() != Material.BEDROCK && b.getType() != Material.END_PORTAL_FRAME) {
                            b.setType(Material.AIR, false);
                        }
                    }
                    // place grass block at surface
                    Block topBlock = world.getBlockAt(x, surfaceY, z);
                    if (!isStructureProtected(x, z)) {
                        topBlock.setType(Material.GRASS_BLOCK, false);
                    }
                }
                // ensure surface block
                Block surf = world.getBlockAt(x, surfaceY, z);
                if (surf.getType().isAir()) {
                    if (!isStructureProtected(x, z)) {
                        surf.setType(Material.GRASS_BLOCK, false);
                    }
                }
            }
        }
    }

    private boolean isReplaceable(Material m) {
        if (m.isAir()) return true;
        String name = m.name();
        if (name.contains("LEAVES")) return true;
        if (name.equals("SHORT_GRASS") || name.equals("TALL_GRASS")) return true;
        return switch (m) {
            case FERN, LARGE_FERN, DEAD_BUSH,
                 DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, OXEYE_DAISY, CORNFLOWER,
                 LILY_OF_THE_VALLEY, SUNFLOWER, LILAC, ROSE_BUSH, PEONY,
                 SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT, VINE, GLOW_LICHEN,
                 SNOW, SNOW_BLOCK -> true;
            default -> false;
        };
    }

    private boolean isStructureProtected(int x, int z) {
        return false;
    }
}