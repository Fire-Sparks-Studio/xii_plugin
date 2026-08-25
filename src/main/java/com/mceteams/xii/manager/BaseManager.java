package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gère les bases des équipes (spec §7).
 *
 * Disposition : les 4 bases sont posées sur les axes cardinaux à
 * distance fixe du centre :
 *   BLEU  au nord (-Z), ROUGE au sud (+Z),
 *   JAUNE à l'ouest (-X), VERT à l'est (+X).
 *
 * RÈGLE IMPORTANTE : une base n'est JAMAIS posée au-dessus de l'eau.
 * Si la position cardinale idéale tombe sur un plan d'eau, la base est
 * DÉCALÉE le long de son axe (vers l'extérieur en priorité pour
 * préserver l'espacement, puis vers l'intérieur) jusqu'à la première
 * surface sèche. L'ancrage est ensuite posé AU NIVEAU DU SOL (hauteur
 * réelle du terrain). Le point de spawn subit le même traitement.
 *
 * Ce choix des axes garantit un espacement maximal entre bases ET une
 * compatibilité avec les donjons qui occupent les 4 diagonales
 * (+/-500 ; +/-500) — aucune superposition possible.
 *
 * Chaque base regarde vers le centre de la map (StructureRotation).
 * Les lectures de terrain sont faites APRÈS chargement ASYNCHRONE des
 * chunks concernés (sinon gel du serveur, cf. LocationUtil).
 */
public class BaseManager {

    /** Pas de recherche le long de l'axe (en blocs). */
    private static final int SEARCH_STEP = 16;
    /** Nombre de pas de recherche max dans chaque sens (+/-48 blocs). */
    private static final int AXIS_SEARCH_STEPS = 3;
    /** Distance minimale centre-base : jamais collées au centre. */
    private static final int MIN_RADIUS_DIVISOR = 2;
    /** Recherche locale autour du point de spawn voulu (rayon max). */
    private static final int SPAWN_SEARCH_RADIUS = 40;
    /** Enveloppe de préchargement de chunks autour d'une ancre. */
    private static final int CHUNK_ENVELOPE = 56;

    private final XiiPlugin plugin;
    /** Bases placées, par couleur. */
    private final Map<TeamColor, GameBase> bases = new EnumMap<>(TeamColor.class);

    public BaseManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Construit et place les bases de toutes les équipes actives.
     * Appelé par GameManager après /zone set.
     *
     * ASYNCHRONE : les chunks des positions candidates sont chargés via
     * getChunkAtAsync AVANT toute lecture de terrain ; le placement
     * effectif a lieu sur le thread principal une fois tout chargé.
     */
    public void buildBases(GameZone zone) {
        bases.clear();

        Location center = zone.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();

        // Toutes les colonnes susceptibles d'être lues pendant les
        // recherches (ancres idéales + voisinage + spawns).
        Set<Long> chunkKeys = new HashSet<>();
        addAreaKeys(chunkKeys,
                center.getBlockX(),
                center.getBlockZ() - plugin.getConfigManager().getBaseRadius());
        addAreaKeys(chunkKeys,
                center.getBlockX(),
                center.getBlockZ() + plugin.getConfigManager().getBaseRadius());
        addAreaKeys(chunkKeys,
                center.getBlockX() - plugin.getConfigManager().getBaseRadius(),
                center.getBlockZ());
        addAreaKeys(chunkKeys,
                center.getBlockX() + plugin.getConfigManager().getBaseRadius(),
                center.getBlockZ());

        List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>> futures =
                new ArrayList<>();
        for (long key : chunkKeys) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            futures.add(world.getChunkAtAsync(chunkX, chunkZ));
        }

        java.util.concurrent.CompletableFuture
                .allOf(futures.toArray(
                        new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> {
                    if (org.bukkit.Bukkit.isPrimaryThread()) {
                        placeBases(zone);
                    } else {
                        // Sécurité : le placement touche aux blocs/entités.
                        org.bukkit.Bukkit.getScheduler().runTask(plugin,
                                () -> placeBases(zone));
                    }
                })
                .exceptionally(throwable -> {
                    // Sans ça, une erreur async resterait totalement muette.
                    plugin.getLogger().severe("[Bases] Erreur de génération : "
                            + throwable);
                    throwable.printStackTrace();
                    return null;
                });
    }

    /**
     * Ajoute les clés des chunks couvrant le carré +/-CHUNK_ENVELOPE
     * autour d'un point (zone de recherche d'une base).
     */
    private void addAreaKeys(Set<Long> keys, int x, int z) {
        int minChunkX = (x - CHUNK_ENVELOPE) >> 4;
        int maxChunkX = (x + CHUNK_ENVELOPE) >> 4;
        int minChunkZ = (z - CHUNK_ENVELOPE) >> 4;
        int maxChunkZ = (z + CHUNK_ENVELOPE) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                keys.add(LocationUtil.chunkKey(chunkX, chunkZ));
            }
        }
    }

    /**
     * Placement effectif (thread principal, chunks chargés).
     */
    private void placeBases(GameZone zone) {
        Location center = zone.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            return; // zone supprimée pendant le chargement async
        }
        putBase(TeamColor.BLUE, 0, -1, center);   // nord : -Z
        putBase(TeamColor.RED, 0, 1, center);     // sud : +Z
        putBase(TeamColor.YELLOW, -1, 0, center); // ouest : -X
        putBase(TeamColor.GREEN, 1, 0, center);   // est : +X

        plugin.getLogger().info("[Bases] " + bases.size() + " base(s) placée(s).");
    }

    /**
     * Place une base et enregistre son GameBase.
     *
     * @param unitX direction cardinale (-1/0/+1)
     * @param unitZ direction cardinale (-1/0/+1)
     */
    private void putBase(TeamColor color, int unitX, int unitZ, Location center) {
        World world = center.getWorld();
        String colorName = color.name().toLowerCase();

        // Position SÈCHE le long de l'axe, ancrée au niveau du sol.
        Location anchor = resolveDryAnchor(color, world, center, unitX, unitZ);

        boolean placed = plugin.getStructureManager()
                .placeBase(colorName, anchor, center);

        // Même si la structure manque (.nbt non fournie), on crée le model
        // pour que le reste du jeu fonctionne pendant le développement.
        if (!placed) {
            plugin.getLogger().warning("[Bases] Structure manquante pour "
                    + color.getColoredName() + " §7(base_" + colorName + ".nbt)");
        }

        int protectionRadius = 25; // rayon de la zone protégée autour du centre

        // Spawn : légèrement vers l'intérieur depuis l'ancrage.
        // LUI AUSSI jamais au-dessus de l'eau (repli : colonne de l'ancre).
        Location spawnWanted = anchor.clone()
                .add(anchor.toVector().subtract(center.toVector())
                        .normalize().multiply(-plugin.getConfigManager().getSpawnOffset()));
        Location spawn = resolveDrySpawnNear(world, spawnWanted, anchor);
        spawn.setY(worldGroundY(spawn));

        // Coeur : au-dessus de l'ancrage (offset configurable, à aligner
        // avec la structure fournie par le développeur).
        Location core = anchor.clone()
                .add(0, plugin.getConfigManager().getCoreOffsetY(), 0);

        // MESURE PROVISOIRE : tant que les structures .nbt définitives ne
        // contiennent pas leur propre coeur, on pose un BEACON visible
        // près du spawn pour permettre les tests de destruction.
        // (On n'écrase jamais un bloc existant : si la structure fournit
        // déjà son coeur à cet endroit, il reste en place.)
        if (core.getBlock().getType().isAir()) {
            core.getBlock().setType(org.bukkit.Material.BEACON);
        }

        bases.put(color, new GameBase(
                color, anchor, anchor.clone(), protectionRadius, spawn, core));

        // IMPORTANT : câble le spawn dans le GameTeam (source de vérité).
        // Sans ça, GameManager/RespawnManager ne trouvaient aucun spawn
        // et envoyaient les joueurs en spectateur par erreur !
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team != null) {
            team.setSpawn(spawn);
        }

        // Enregistrement du coeur auprès du CoreService.
        plugin.getCoreService().registerCore(color, core);
    }

    /**
     * Résout la position de la base le long de son axe cardinal :
     * distance idéale d'abord, puis décalages vers l'EXTÉRIEUR (préserve
     * l'espacement inter-bases) puis vers l'INTÉRIEUR, jusqu'à la
     * première surface sèche. L'ancre est posée au sol (Y réel).
     */
    private Location resolveDryAnchor(TeamColor color, World world,
                                      Location center, int unitX, int unitZ) {
        int radius = plugin.getConfigManager().getBaseRadius();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int minRadius = Math.max(1, radius / MIN_RADIUS_DIVISOR);
        GameZone zone = plugin.getZoneManager().getZone();

        for (int step = 0; step <= AXIS_SEARCH_STEPS; step++) {
            int[] deltas = step == 0
                    ? new int[]{0}
                    : new int[]{step * SEARCH_STEP, -step * SEARCH_STEP};
            for (int delta : deltas) {
                int dist = radius + delta;
                if (dist < minRadius) {
                    continue;
                }
                int x = centerX + unitX * dist;
                int z = centerZ + unitZ * dist;
                if (zone != null && !zone.contains(
                        new Location(world, x, center.getY(), z))) {
                    continue; // on ne sort jamais de la zone
                }
                if (LocationUtil.isDryColumn(world, x, z)) {
                    if (delta != 0) {
                        plugin.getLogger().info("[Bases] "
                                + color.getColoredName() + " §7décalée de "
                                + (delta > 0 ? "+" : "") + delta
                                + " blocs le long de son axe (eau détectée).");
                    }
                    return new Location(world, x + 0.5,
                            worldGroundY(world, x, z), z + 0.5);
                }
            }
        }

        // Aucune surface sèche sur toute la plage de recherche (océan
        // immense ?) : position idéale, avec alerte forte.
        plugin.getLogger().severe("[Bases] AUCUNE surface sèche trouvée le "
                + "long de l'axe " + color + " : base posée sur la position "
                + "idéale malgré l'eau.");
        int x = centerX + unitX * radius;
        int z = centerZ + unitZ * radius;
        return new Location(world, x + 0.5, worldGroundY(world, x, z), z + 0.5);
    }

    /**
     * Garantit un point de spawn SUR UNE COLONNE SÈCHE : recherche en
     * anneaux croissants autour du point voulu ; repli sur la colonne de
     * l'ancre (garantie sèche) en dernier recours.
     * NB : le Y est recalé par l'appelant (worldGroundY).
     */
    private Location resolveDrySpawnNear(World world, Location wanted,
                                         Location fallback) {
        int baseX = wanted.getBlockX();
        int baseZ = wanted.getBlockZ();
        if (LocationUtil.isDryColumn(world, baseX, baseZ)) {
            return wanted;
        }
        for (int ring = 8; ring <= SPAWN_SEARCH_RADIUS; ring += 8) {
            for (int dz = -ring; dz <= ring; dz += 4) {
                for (int dx = -ring; dx <= ring; dx += 4) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue; // périmètre uniquement
                    }
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    if (LocationUtil.isDryColumn(world, x, z)) {
                        plugin.getLogger().info("[Bases] Spawn décalé de "
                                + Math.abs(dx) + "/" + Math.abs(dz)
                                + " blocs (eau détectée).");
                        return new Location(world, x + 0.5, 0, z + 0.5);
                    }
                }
            }
        }
        plugin.getLogger().warning("[Bases] Colonne de spawn humide sans "
                + "secours proche : repli sur la colonne de l'ancre.");
        return fallback.clone();
    }

    /** Trouve une hauteur "posable" au-dessus du sol pour un point. */
    private double worldGroundY(Location location) {
        var world = location.getWorld();
        if (world == null) {
            return location.getY();
        }
        return worldGroundY(world,
                location.getBlockX(), location.getBlockZ());
    }

    /** Hauteur du sol (+1) pour une colonne donnée. */
    private double worldGroundY(World world, int x, int z) {
        return world.getHighestBlockYAt(x, z) + 1.0;
    }

    /** @return la base d'une équipe, ou null si non construite. */
    public GameBase getBase(TeamColor color) {
        return bases.get(color);
    }

    /**
     * @return la base CONTENANT cette location (zone de protection),
     * ou null. Utilisé par ProtectionService pour les règles PvP.
     */
    public GameBase baseAt(org.bukkit.Location location) {
        for (GameBase base : bases.values()) {
            if (base.contains(location)) {
                return base;
            }
        }
        return null;
    }

    /** Vide toutes les bases (retour WAITING / zone supprimée). */
    public void clearAll() {
        bases.clear();
    }
}
