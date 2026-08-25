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

    /** Plugin hôte : nécessaire pour replanifier sur le thread principal. */
    private static org.bukkit.plugin.Plugin ownerPlugin;

    private LocationUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /** À appeler une fois depuis onEnable (cf. ItemUtil.init). */
    public static void init(org.bukkit.plugin.Plugin plugin) {
        ownerPlugin = plugin;
    }

    /**
     * Exécute la tâche SUR LE THREAD PRINCIPAL (obligatoire pour toute
     * lecture de bloc / spawn d'entité). Les callbacks des futurs de
     * chunks peuvent arriver sur un thread worker selon Paper.
     */
    private static void runOnMain(Runnable task) {
        if (ownerPlugin == null) {
            task.run(); // dernier recours (tests hors serveur)
            return;
        }
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            org.bukkit.Bukkit.getScheduler().runTask(ownerPlugin, task);
        }
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
     *
     * ATTENTION : lecture SYNCHRONE du terrain (getHighestBlockYAt) =>
     * ne jamais appeler sur un chunk potentiellement non chargé
     * (gèlerait le thread serveur). Pour un point aléatoire n'importe
     * où dans la zone, utiliser {@link #randomSurfaceInAsync}.
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
     * Version ASYNCHRONE garantissant une surface SÈCHE (jamais sur ou
     * dans de l'eau) : jusqu'à {@code maxAttempts} essais de coordonnées,
     * chaque essai chargeant le chunk via getChunkAtAsync puis vérifiant
     * que le bloc de surface n'est pas liquide/végétation aquatique.
     * Callback appelé avec null si aucun point sec trouvé.
     */
    public static void randomDrySurfaceInAsync(GameZone zone,
                                               java.util.function.Consumer<Location> callback) {
        tryPickDrySurface(zone, 20, callback);
    }

    /** Essai unique avec réessais restants (récursion async). */
    private static void tryPickDrySurface(GameZone zone, int attemptsLeft,
                                          java.util.function.Consumer<Location> callback) {
        World world = zone.getWorld();
        if (world == null || attemptsLeft <= 0) {
            if (world != null) {
                ownerPlugin.getLogger().warning(
                        "[LocationUtil] Aucune surface sèche trouvée après "
                                + "tous les essais.");
            }
            runOnMain(() -> callback.accept(null));
            return;
        }
        int margin = 50;
        int minX = (int) zone.getMinX() + margin;
        int maxX = (int) zone.getMaxX() - margin;
        int minZ = (int) zone.getMinZ() + margin;
        int maxZ = (int) zone.getMaxZ() - margin;

        int x = minX + RANDOM.nextInt(Math.max(1, maxX - minX));
        int z = minZ + RANDOM.nextInt(Math.max(1, maxZ - minZ));

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            // NB : toute exception ici serait AVALÉE par le futur
            // (aucun log) => try/catch explicite.
            try {
                // Lecture de blocs : TOUJOURS sur le thread principal.
                runOnMain(() -> {
                    try {
                        // Surface humide ? (eau, kelp, glace...) => nouvel essai.
                        if (!isDryColumn(world, x, z)) {
                            tryPickDrySurface(zone, attemptsLeft - 1, callback);
                            return;
                        }
                        int y = world.getHighestBlockYAt(x, z);
                        callback.accept(new Location(world, x + 0.5, y + 1, z + 0.5));
                    } catch (Throwable throwable) {
                        ownerPlugin.getLogger().severe(
                                "[LocationUtil] Erreur sélection surface : "
                                        + throwable);
                        throwable.printStackTrace();
                        runOnMain(() -> callback.accept(null));
                    }
                });
            } catch (Throwable throwable) {
                ownerPlugin.getLogger().severe(
                        "[LocationUtil] Erreur chargement chunk : " + throwable);
                throwable.printStackTrace();
            }
        });
    }

    /**
     * La surface d'une colonne est-elle SÈCHE (structure posable) ?
     * Le bloc le plus haut ne doit être ni liquide, ni végétation
     * aquatique, ni glace. Lecture SYNCHRONE : ne jamais appeler sur un
     * chunk potentiellement non chargé.
     */
    public static boolean isDryColumn(World world, int x, int z) {
        return isDryTopBlock(
                world.getBlockAt(x, world.getHighestBlockYAt(x, z), z).getType());
    }

    /** Ce type de bloc peut-il constituer une surface sèche ? */
    public static boolean isDryTopBlock(org.bukkit.Material type) {
        return !(type == org.bukkit.Material.WATER
                || type == org.bukkit.Material.LAVA
                || type == org.bukkit.Material.KELP
                || type == org.bukkit.Material.KELP_PLANT
                || type == org.bukkit.Material.SEAGRASS
                || type == org.bukkit.Material.TALL_SEAGRASS
                || type == org.bukkit.Material.ICE);
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
