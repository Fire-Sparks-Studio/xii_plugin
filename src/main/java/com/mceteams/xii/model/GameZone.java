package com.mceteams.xii.model;

import org.bukkit.Location;
import org.bukkit.World;

public class GameZone {

    private final String worldName;

    private final double centerX;
    private final double centerY;
    private final double centerZ;

    private final int size;

    public GameZone(
            String worldName,
            double centerX,
            double centerY,
            double centerZ,
            int size
    ) {
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.size = size;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public int getSize() {
        return size;
    }

    public double getMinX() {
        return centerX - (size / 2.0);
    }

    public double getMaxX() {
        return centerX + (size / 2.0);
    }

    public double getMinZ() {
        return centerZ - (size / 2.0);
    }

    public double getMaxZ() {
        return centerZ + (size / 2.0);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        return location.getX() >= getMinX()
                && location.getX() <= getMaxX()
                && location.getZ() >= getMinZ()
                && location.getZ() <= getMaxZ();
    }
}