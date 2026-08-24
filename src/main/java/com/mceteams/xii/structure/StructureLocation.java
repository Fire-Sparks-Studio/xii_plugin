package com.mceteams.xii.structure;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Emplacement planifié d'une structure : monde + ancrage + rotation.
 *
 * Simple value object partagé entre les managers (BaseManager,
 * DungeonManager...) et le StructurePlacer.
 */
public class StructureLocation {

    private final World world;
    /** Coordonnées d'ancrage (bloc origine du placement). */
    private final int x;
    private final int y;
    private final int z;
    /** Rotation appliquée au placement. */
    private final StructureRotation rotation;

    public StructureLocation(World world, int x, int y, int z, StructureRotation rotation) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation;
    }

    /** Crée une location depuis un point Bukkit + rotation. */
    public static StructureLocation of(Location anchor, StructureRotation rotation) {
        return new StructureLocation(
                anchor.getWorld(),
                anchor.getBlockX(),
                anchor.getBlockY(),
                anchor.getBlockZ(),
                rotation);
    }

    public World getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public StructureRotation getRotation() {
        return rotation;
    }

    /** Représentation lisible pour les logs de debug. */
    @Override
    public String toString() {
        return "StructureLocation{world=" + world.getName()
                + ", x=" + x + ", y=" + y + ", z=" + z
                + ", rotation=" + rotation + "}";
    }
}
