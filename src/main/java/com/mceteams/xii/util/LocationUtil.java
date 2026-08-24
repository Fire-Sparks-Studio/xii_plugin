package com.mceteams.xii.util;

import com.mceteams.xii.model.GameZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Random;
import java.util.UUID;

/**
 * Utilitaires de localisation : sérialisation, points aléatoires dans
 * la zone, positions de surface. Aucune responsabilité métier (spec §38).
 */
public final class LocationUtil {

    private static final Random RANDOM = new Random();

    private LocationUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Sérialise une location sous la forme "monde;x;y;z;yaw;pitch".
     */
    public static String serialize(Location location) {
        if (location == null) {
            return "";
        }
        return location.getWorld().getName()
                + ";" + location.getX()
                + ";" + location.getY()
                + ";" + location.getZ()
                + ";" + location.getYaw()
                + ";" + location.getPitch();
    }

    /**
     * Désérialise une chaîne produite par {@link #serialize(Location)}.
     *
     * @return la location, ou null si la chaîne est vide/invalide.
     */
    public static Location deserialize(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        String[] parts = data.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Choisit un point aléatoire À LA SURFACE du terrain dans la zone.
     * Utilisé pour les colis et les impacts de météorites.
     */
    public static Location randomSurfaceIn(GameZone zone) {
        World world = zone.getWorld();
        if (world == null) {
            return null;
        }
        int margin = 50; // marge pour éviter les bords exacts de la zone
        int minX = (int) zone.getMinX() + margin;
        int maxX = (int) zone.getMaxX() - margin;
        int minZ = (int) zone.getMinZ() + margin;
        int maxZ = (int) zone.getMaxZ() - margin;

        int x = minX + RANDOM.nextInt(Math.max(1, maxX - minX));
        int z = minZ + RANDOM.nextInt(Math.max(1, maxZ - minZ));

        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    /**
     * Point aléatoire en altitude au-dessus d'une location (utilisé
     * pour faire spawn les boules de feu des météorites).
     */
    public static Location highAbove(Location base, int height) {
        return base.clone().add(0, height, 0);
    }

    /**
     * Distance horizontale (X/Z) entre deux locations.
     */
    public static double horizontalDistance(Location a, Location b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Identifiant stable d'un chunk (pour l'exploration). */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Téléporte un joueur de façon sécurisée (charge le chunk si besoin).
     * Retourne true si la téléportation a été demandée.
     */
    public static boolean teleport(UUID playerUuid, Location target) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline() || target == null) {
            return false;
        }
        return player.teleport(target);
    }
}
