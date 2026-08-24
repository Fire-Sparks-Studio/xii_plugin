package com.mceteams.xii.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Zone de jeu carrée de 2000 x 2000 blocs centrée sur le point choisi
 * par l'opérateur avec /zone set (spec §3).
 *
 * Model simple : on stocke le nom du monde et les coordonnées, jamais
 * d'objet World (qui peut être déchargé/rechargé).
 */
public class GameZone {

    /** Nom du monde dans lequel la zone a été définie. */
    private final String worldName;

    /** Centre exact = position de l'opérateur au moment du /zone set. */
    private final double centerX;
    private final double centerY;
    private final double centerZ;

    /** Taille du côté du carré (2000 par défaut). */
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

    /**
     * @return l'objet World Bukkit résolu à la demande, ou null si le
     * monde n'existe plus (cas géré au démarrage, spec §9).
     */
    public World getWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
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

    /**
     * Le centre sous forme de Location (utile pour placer la zone
     * d'attente et calculer les positions relatives).
     */
    public Location getCenterLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }
        return new Location(world, centerX, centerY, centerZ);
    }

    /**
     * Test d'appartenance en X/Z (indépendant de Y).
     */
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
