package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.structure.StructureRotation;
import com.mceteams.xii.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        // NB : NE PAS vider la map ici. Les ancres conservées permettent
        // de REPOSER les bases exactement au même endroit lors d'une
        // reconstruction (rebuildModels / régénération). Sans cela,
        // l'ancre était recalculée sur le toit de la base déjà posée et
        // une copie EMPILÉE apparaissait ("2 bases l'une sur l'autre").
        // La map n'est vidée que par /zone delete (clearAll()).

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

        // ÉQUIPE SANS MEMBRE dès le lancement : AUCUNE base, et élimination
        // automatique. Le joueur qui le rejoindrait plus tard rejoindrait
        // une équipe déjà éliminée sans spawn (règle utilisateur).
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team != null && team.getPlayerCount() == 0) {
            removeBase(color);
            if (!team.isEliminated()) {
                team.setEliminated(true);
                plugin.getLogger().info("[Bases] Équipe " + color.getColoredName()
                        + " §7sans membre au lancement : éliminée automatiquement, "
                        + "aucune base posée.");
            }
            plugin.getGameManager().checkVictoryConditions();
            return;
        }

        // Position SÈCHE le long de l'axe, ancrée au niveau du sol.
        // IDEMPOTENCE : si une base existe déjà pour cette couleur, on
        // réutilise SON ancre (jamais recalculée sur le toit de la base
        // déjà posée => plus de double empilement).
        GameBase existing = bases.get(color);
        Location anchor;
        if (existing != null && existing.getAnchor().getWorld() != null) {
            anchor = existing.getAnchor();
        } else {
            anchor = resolveDryAnchor(color, world, center, unitX, unitZ);
        }

        boolean placed = plugin.getStructureManager()
                .placeBase(color, anchor, center);

        // Même si la structure manque (.nbt non fournie), on crée le model
        // pour que le reste du jeu fonctionne pendant le développement.
        if (!placed) {
            plugin.getLogger().warning("[Bases] Structure manquante pour "
                    + color.getColoredName() + " §7(base_" + colorName + ".nbt)");
        }

        int protectionRadius = 27; // rayon de la zone protégée autour du centre

        // Le centre de la base = CENTRE GÉOMÉTRIQUE de la boîte (la zone de
        // protection PvP couvre alors TOUTE l'empreinte 35x35).
        Location boxCenter = anchor.clone()
                .add(GameBase.HALF, 0, GameBase.HALF);

        // Spawn : DEVANT la façade (côté du centre de la map), jamais
        // au-dessus de l'eau (repli : colonne de l'ancre).
        StructureRotation rotation = StructureRotation.facingToward(anchor, center);
        Location spawnWanted = facadeSpawnInFront(anchor, rotation);
        Location spawn = resolveDrySpawnNear(world, spawnWanted, anchor);
        // Le SPAWN reste SUR la surface réelle (pas le décalage -9 appliqué
        // à la structure) : les joueurs sortent à hauteur du sol, devant la
        // façade, au lieu d'être enfouis sous la base abaissée.
        spawn.setY(surfaceY(world, spawn.getBlockX(), spawn.getBlockZ()));

        bases.put(color, new GameBase(
                color, anchor, boxCenter, protectionRadius, spawn, anchor.clone()));

        // IMPORTANT : câble le spawn dans le GameTeam (source de vérité).
        // Sans ça, GameManager/RespawnManager ne trouvaient aucun spawn
        // et envoyaient les joueurs en spectateur par erreur !
        if (team != null) {
            team.setSpawn(spawn);
        }

        // Les cristaux (5 sea_lantern) et le coffre de dépôt sont fournis
        // PAR LA STRUCTURE .nbt : on les localise dans le monde puis on
        // les enregistre auprès du CoreService.
        locateAndRegisterCrystals(color, anchor);
    }

    /** Retire la base (et ses cristaux enregistrés) d'une équipe. */
    private void removeBase(TeamColor color) {
        bases.remove(color);
        plugin.getCoreService().unregisterBase(color);
    }

    /**
     * Point de spawn devant la FAÇADE de la base (bord du côté du centre),
     * sortie à {@code spawn-offset} blocs de l'empreinte.
     * La structure fait 35x35 et sa boîte occupe [anchor..anchor+34] sur
     * X et Z quelle que soit la rotation (cf. GameBase.SIZE).
     */
    private Location facadeSpawnInFront(Location anchor,
                                        StructureRotation rotation) {
        World world = anchor.getWorld();
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        int center = GameBase.HALF;
        int offset = plugin.getConfigManager().getSpawnOffset();
        double x;
        double z;
        switch (rotation) {
            case CLOCKWISE_180 -> { // face au nord : sortie au nord
                x = ax + center;
                z = az - offset;
            }
            case CLOCKWISE_90 -> { // face à l'ouest : sortie à l'ouest
                x = ax - offset;
                z = az + center;
            }
            case COUNTERCLOCKWISE_90 -> { // face à l'est : sortie à l'est
                x = ax + GameBase.SIZE - 1 + offset;
                z = az + center;
            }
            default -> { // NONE : face au sud => sortie au sud
                x = ax + center;
                z = az + GameBase.SIZE - 1 + offset;
            }
        }
        return new Location(world, x + 0.5, 0, z + 0.5);
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

    /** Hauteur du sol réel (surface), sans le décalage de la structure. */
    private double surfaceY(World world, int x, int z) {
        return majorityGroundY(world, x, z);
    }

    /**
     * Rayon de SCRUTATION autour de la colonne d'ancrage pour estimer le
     * niveau de sol MAJORITAIRE d'une base (couvre l'empreinte 35x35, rayon
     * ~18 => le quadrant centré sur l'ancre).
     */
    private static final int GROUND_SAMPLE_RADIUS = 17;

    /**
     * NIVEAU DE SOL MAJORITAIRE (surface + 1) sur le rayon d'une base.
     *
     * RÈGLE UTILISATEUR : on ne se fie PAS au niveau d'un seul bloc (un
     * arbre isolé ou un pic ferait voler la base) : on échantillonne le
     * terrain sur tout le rayon de la base et on retient la hauteur la
     * plus fréquente. Un relief ponctuel compté une seule fois n'est
     * jamais retenu (ni pour rehausser ni pour creuser).
     */
    private double majorityGroundY(World world, int x, int z) {
        // Échantillonnage régulier sur la zone (pas de 3 blocs) : assez
        // fin pour capter la topographie, sans mesurer chaque colonne.
        final int step = 3;
        java.util.HashMap<Integer, Integer> histogram = new java.util.HashMap<>();
        for (int dx = -GROUND_SAMPLE_RADIUS; dx <= GROUND_SAMPLE_RADIUS; dx += step) {
            for (int dz = -GROUND_SAMPLE_RADIUS; dz <= GROUND_SAMPLE_RADIUS; dz += step) {
                int sx = x + dx;
                int sz = z + dz;
                if (LocationUtil.isDryColumn(world, sx, sz)) {
                    int ground = world.getHighestBlockYAt(sx, sz) + 1;
                    histogram.merge(ground, 1, Integer::sum);
                }
            }
        }

        // Niveau majoritaire : la hauteur AU SOL la plus fréquente.
        int majority = 0;
        int bestCount = -1;
        for (var entry : histogram.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                majority = entry.getKey();
            }
        }
        // Repli sûr : on garde la colonne centrale si aucun échantillon.
        if (histogram.isEmpty()) {
            majority = world.getHighestBlockYAt(x, z) + 1;
        }
        return majority;
    }

    /**
     * Hauteur du sol (+1) pour une base, calculée comme le NIVEAU
     * MAJORITAIRE du relief dans le rayon de la base.
     *
     * RÈGLE UTILISATEUR : on ne se fie PAS au niveau d'un seul bloc
     * (un arbre isolé ou un pic ferait voler la base) : on échantillonne
     * le terrain sur tout le rayon de la base et on retient la hauteur
     * la plus fréquente. Un relief ponctuel compté une seule fois n'est
     * jamais retenu (ni pour rehausser ni pour creuser).
     */
    private double worldGroundY(World world, int x, int z) {
        // RÈGLE UTILISATEUR : la base (et son spawn) doit reposer AU SOL
        // et non flotter. On abaisse de 9 blocs la position d'ancrage
        // calculée (les troncs/feuilles du footprint sont nettoyés à la
        // pose, voir RawTemplatePlacer.clearTreeIntersections).
        return majorityGroundY(world, x, z) - 9.0;
    }

    // -----------------------------------------------------------------
    // Cristaux + coffre de dépôt (fournis par la structure .nbt)
    // -----------------------------------------------------------------

    /**
     * Rayon de la zone scannée autour de l'ancrage (la structure fait
     * 35x35, l'ancre est son coin d'origine : rayon 45 couvre tout).
     */
    private static final int CRYSTAL_SCAN_RADIUS = 45;
    /** Hauteur maximale scrutée au-dessus de l'ancrage. */
    private static final int CRYSTAL_SCAN_MAX_Y = 16;
    /** Tentatives de scan différées (placement bascule éventuellement async). */
    private static final int CRYSTAL_SCAN_RETRIES = 6;

    /**
     * Localise les 5 cristaux (sea_lantern) et le coffre de dépôt de la
     * base puis les enregistre dans le CoreService.
     *
     * La structure fournit ses propres cristaux ; le centre est identifié
     * comme le cristal le plus proche du centroïde des lanternes trouvées
     * (les 4 tours étant symétriques autour du centre).
     *
     * Si le scan ne trouve rien (placement asynchrone du poseur de
     * secours), on réessaie un peu plus tard.
     */
    private void locateAndRegisterCrystals(TeamColor color, Location anchor) {
        tryScanCrystals(color, anchor, 0);
    }

    private void tryScanCrystals(TeamColor color, Location anchor, int attempt) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }

        List<Location> lanterns = new ArrayList<>();
        List<Location> chests = new ArrayList<>();
        scanBaseRegion(anchor, lanterns, chests);

        if (!chests.isEmpty() && lanterns.size() >= 5) {
            // Dédoublonnage PAR COLONNE XZ : on ne garde que le cristal le
            // PLUS BAS de chaque colonne. Cela neutralise un éventuel
            // double-empilement résiduel de la structure (deux lanternes
            // à la même colonne, à des altitudes différentes).
            Map<Long, Location> byColumn = new HashMap<>();
            for (Location lantern : lanterns) {
                long key = columnKey(lantern);
                Location existing = byColumn.get(key);
                if (existing == null || lantern.getBlockY() < existing.getBlockY()) {
                    byColumn.put(key, lantern);
                }
            }
            List<Location> unique = new ArrayList<>(byColumn.values());
            if (unique.size() >= 5) {
                registerCrystals(color, unique, chests);
                return;
            }
            // Moins de 5 colonnes distinctes => placement pas encore en
            // place (ou structure trop endommagée) : on réessaie plus tard.
        }

        if (attempt < CRYSTAL_SCAN_RETRIES) {
            // La pose de secours (RawTemplatePlacer) est asynchrone :
            // on rescanne un peu plus tard.
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> tryScanCrystals(color, anchor, attempt + 1), 20L);
            return;
        }
        plugin.getLogger().severe("[Bases] Cristaux introuvables pour "
                + color.getColoredName() + " §7(" + lanterns.size()
                + " cristal/lanternes, " + chests.size()
                + " coffre(s) trouvés, " + (CRYSTAL_SCAN_RETRIES + 1)
                + " scans).");
    }

    /**
     * Sélection ROBUSTE des 5 cristaux :
     * - le CENTRE est le cristal le plus proche du centroïde XZ ;
     * - les 4 TOURS sont les 4 cristaux les plus éloignés du centre ;
     * - les cristaux supplémentaires (lanternes décoratives) sont ignorés.
     */
    private void registerCrystals(TeamColor color, List<Location> unique,
                                  List<Location> chests) {
        double cx = unique.stream().mapToDouble(Location::getX)
                .average().orElse(0);
        double cz = unique.stream().mapToDouble(Location::getZ)
                .average().orElse(0);
        Location center = unique.get(0);
        double best = Double.MAX_VALUE;
        for (Location lantern : unique) {
            double d = squaredXZ(lantern, cx, cz);
            if (d < best) {
                best = d;
                center = lantern;
            }
        }

        // Les 4 tours = les 4 cristaux les PLUS LOIN du centre (XZ).
        List<Location> remaining = new ArrayList<>(unique);
        remaining.remove(center);
        Location heart = center; // final pour le comparateur
        remaining.sort((a, b) -> Double.compare(
                squaredXZDistance(b, heart), squaredXZDistance(a, heart)));
        List<Location> towers = new ArrayList<>(remaining.subList(0, 4));

        // Le coffre de dépôt = le coffre le plus proche du centre.
        Location chest = chests.get(0);
        double bestChest = Double.MAX_VALUE;
        for (Location candidate : chests) {
            double d = candidate.distanceSquared(center);
            if (d < bestChest) {
                bestChest = d;
                chest = candidate;
            }
        }

        plugin.getCoreService()
                .registerBaseCrystals(color, center, towers, chest);
        plugin.getLogger().info("[Bases] " + color.name().toLowerCase()
                + " : 5 cristaux + coffre de dépôt enregistrés.");
    }

    private double squaredXZ(Location location, double x, double z) {
        double dx = location.getX() - x;
        double dz = location.getZ() - z;
        return dx * dx + dz * dz;
    }

    private double squaredXZDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Clé compacte (x,z) d'une Location. */
    private long columnKey(Location location) {
        return ((long) location.getBlockX() << 32)
                | (location.getBlockZ() & 0xffffffffL);
    }

    /** Collecte les lanternes (cristaux) et coffres d'une base. */
    private void scanBaseRegion(Location anchor, List<Location> lanterns,
                                List<Location> chests) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int ax = anchor.getBlockX();
        int ay = anchor.getBlockY();
        int az = anchor.getBlockZ();
        int minY = ay;
        int maxY = ay + CRYSTAL_SCAN_MAX_Y;
        for (int dx = -CRYSTAL_SCAN_RADIUS; dx <= CRYSTAL_SCAN_RADIUS; dx++) {
            for (int dz = -CRYSTAL_SCAN_RADIUS; dz <= CRYSTAL_SCAN_RADIUS; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(ax + dx, y, az + dz);
                    Material type = block.getType();
                    if (type == com.mceteams.xii.service.CoreService.CRYSTAL_MATERIAL) {
                        lanterns.add(block.getLocation());
                    } else if (type == Material.CHEST) {
                        chests.add(block.getLocation());
                    }
                }
            }
        }
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

    /**
     * @return la base dont l'EMPREINTE (rectangle de structure) contient
     * cette location, ou null. Utilisé pour les règles de protection des
     * blocs (structure, champs, blocs posés par l'équipe).
     */
    public GameBase baseContainingBlock(org.bukkit.Location location) {
        for (GameBase base : bases.values()) {
            if (base.containsBlock(location)) {
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
