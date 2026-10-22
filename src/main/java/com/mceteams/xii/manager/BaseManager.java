package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.BaseMarkers;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Gère les bases des équipes (spec §7).
 *
 * DISPOSITION : les 4 bases sont posées sur les axes cardinaux à
 * distance fixe du centre :
 *   nord (-Z), sud (+Z), ouest (-X), est (+X).
 * Ce choix garantit un espacement maximal entre bases ET une
 * compatibilité avec les donjons qui occupent les 4 diagonales
 * (+/-500 ; +/-500) — aucune superposition possible.
 *
 * ATTRIBUTION AU HASARD (RÈGLE UTILISATEUR) : aucune couleur n'a
 * d'emplacement réservé. Les 4 emplacements sont des positions
 * PHYSIQUES ; l'affectation des équipes à ces emplacements est tirée
 * AU SORT AU LANCEMENT de la partie (BaseManager#assignBasesToTeams).
 * Une fois attribuée, l'emplacement est celui de l'équipe jusqu'à la
 * fin de la partie. Les emplacements non attribués (équipes en
 * moins) sont retirés du monde.
 *
 * RÈGLE IMPORTANTE : une base n'est JAMAIS posée au-dessus de l'eau.
 * Si la position cardinale idéale tombe sur un plan d'eau, la base est
 * DÉCALÉE le long de son axe (vers l'extérieur en priorité pour
 * préserver l'espacement, puis vers l'intérieur) jusqu'à la première
 * surface sèche. L'ancrage est ensuite posé AU NIVEAU DU SOL (hauteur
 * réelle du terrain, et NON le sommet de la canopée : en forêt, la
 * base reposait dans les arbres).
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
    /** Rayon de recherche d'un spawn sûr autour du repère (laine). */
    private static final int SAFE_SPAWN_RADIUS = 6;
    /** Profondeur max de comblement des trous sous une base. */
    private static final int GROUND_FILL_DEPTH = 6;
    /**
     * Profondeur max du remblai de la MARGE autour d'une base : au-delà,
     * ce n'est plus un fossé mais une falaise (on ne comble pas).
     */
    private static final int GROUND_MARGIN_MAX_DEPTH = 12;
    /**
     * Délai (en ticks) avant le renvoi des chunks à jour aux clients :
     * le moteur d'éclairage applique ses mises à jour de façon différée.
     */
    private static final long LIGHT_REFRESH_DELAY_TICKS = 40L;
    /** Nombre de vérifications différées (données tardives de pose). */
    private static final int LATE_REFRESH_ATTEMPTS = 30;

    /**
     * Matériaux de SOL naturel : servent à retrouver la VRAIE surface du
     * terrain (en forêt, le bloc le plus haut est une feuille : la base
     * se retrouvait posée DANS la canopée).
     */
    private static final Set<Material> GROUND_MATERIALS = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MUD,
            Material.SAND, Material.SANDSTONE, Material.RED_SAND,
            Material.RED_SANDSTONE, Material.GRAVEL, Material.CLAY,
            Material.STONE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.DEEPSLATE, Material.TUFF,
            Material.CALCITE, Material.SNOW_BLOCK, Material.TERRACOTTA,
            Material.NETHERRACK, Material.END_STONE, Material.OBSIDIAN,
            Material.MAGMA_BLOCK, Material.MYCELIUM, Material.BLACKSTONE,
            Material.BASALT, Material.BLUE_ICE, Material.PACKED_ICE);

    private final XiiPlugin plugin;
    /** Bases placées, par couleur (après attribution). */
    private final Map<TeamColor, GameBase> bases = new EnumMap<>(TeamColor.class);

    /**
     * Les 4 EMPLACEMENTS de base (positions cardinales, physiques).
     * Ils ne dépendent pas des équipes : l'affectation se fait au
     * lancement de la partie.
     */
    private final List<BaseSlot> slots = new ArrayList<>();

    /** Affectation courante : équipe -> emplacement. */
    private final Map<TeamColor, BaseSlot> assignment = new LinkedHashMap<>();

    /** Aléa de l'attribution des bases aux équipes. */
    private final Random random = new Random();

    /** Tâche de vérification différée (repères/cristaux tardifs). */
    private org.bukkit.scheduler.BukkitTask lateRefreshTask;

    /**
     * Instantané du TERRAIN AVANT la pose de chaque base : permet de
     * "régénérer" l'empreinte si l'emplacement doit être déspawn
     * (équipe inexistante au lancement). Paper 26.2 ne permet pas de
     * régénérer un chunk ({@code World#regenerateChunk} lève
     * {@code UnsupportedOperationException}), on restaure donc les blocs
     * d'origine mémorisés ici.
     *
     * Clé = index d'emplacement ; valeur = (x,y,z) -> BlockData
     * sérialisée ("minecraft:grass_block[snowy=false]").
     */
    private final Map<Integer, Map<Long, String>> terrainSnapshots =
            new HashMap<>();

    /**
     * EMPLACEMENT de base : une position cardinale et tout ce qui la
     * concerne (structure posée, repères, cristaux).
     */
    public static final class BaseSlot {
        /** Index de l'emplacement (0..3). */
        public final int index;
        /** Direction cardinale (-1/0/+1). */
        public int unitX;
        public int unitZ;
        /** Ancre de la structure .nbt. */
        public Location anchor;
        /** Rotation appliquée à la structure. */
        public StructureRotation rotation = StructureRotation.NONE;
        /** Repères lus dans l'empreinte (null tant que la pose n'a pas
         *  été traitée). */
        public BaseMarkers markers;
        /** Les repères ont-ils déjà été appliqués à une équipe ? */
        public boolean markersApplied;
        /** Cristal central (coeur de la base). */
        public Location coreCrystal;
        /** Cristaux des 4 tours (boucliers). */
        public final List<Location> towers = new ArrayList<>();
        /** Coffre de dépôt. */
        public Location chest;
        /** L'emplacement a-t-il été retiré du monde ? */
        public boolean removed;

        public BaseSlot(int index, int unitX, int unitZ) {
            this.index = index;
            this.unitX = unitX;
            this.unitZ = unitZ;
        }

        /** Libellé lisible (pour les logs). */
        public String label() {
            String dir;
            if (unitX == 0 && unitZ < 0) {
                dir = "nord";
            } else if (unitX == 0) {
                dir = "sud";
            } else if (unitX < 0) {
                dir = "ouest";
            } else {
                dir = "est";
            }
            return dir;
        }
    }

    public BaseManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Construction des emplacements (au /zone set)
    // -----------------------------------------------------------------

    /**
     * Construit et place les 4 emplacements de base.
     * Appelé par GameManager après /zone set.
     *
     * ASYNCHRONE : les chunks des positions candidates sont chargés via
     * getChunkAtAsync AVANT toute lecture de terrain ; le placement
     * effectif a lieu sur le thread principal une fois tout chargé.
     *
     * NB : l'affectation des emplacements aux équipes n'est PAS faite ici
     * (elle est tirée au sort au LANCEMENT de la partie).
     */
    public void buildBases(GameZone zone) {
        // NB : NE PAS vider la map ici. Les emplacements conservés
        // permettent de REPOSER les bases exactement au même endroit lors
        // d'une reconstruction (rebuildModels / régénération). Sans cela,
        // l'ancre était recalculée sur le toit de la base déjà posée et
        // une copie EMPILÉE apparaissait ("2 bases l'une sur l'autre").
        // Les emplacements ne sont vidés que par /zone delete
        // (clearAll()).

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
     * Placement effectif (thread principal, chunks chargés) : les 4
     * emplacements cardinaux sont posés.
     */
    private void placeBases(GameZone zone) {
        Location center = zone.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            return; // zone supprimée pendant le chargement async
        }
        World world = center.getWorld();

        // Emplacements cardinaux : nord (-Z), sud (+Z), ouest (-X), est (+X).
        int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

        for (int i = 0; i < directions.length; i++) {
            BaseSlot slot;
            if (slots.size() > i) {
                slot = slots.get(i); // réutilisé (ancre conservée)
            } else {
                slot = new BaseSlot(i, directions[i][0], directions[i][1]);
                slots.add(slot);
            }
            slot.unitX = directions[i][0];
            slot.unitZ = directions[i][1];
            slot.removed = false;
            slot.markersApplied = false;

            // Ancre : conservée si l'emplacement existe déjà, sinon
            // calculée (surface sèche, au niveau du VRAI sol).
            if (slot.anchor == null || slot.anchor.getWorld() == null) {
                slot.anchor = resolveDryAnchor(slot, world, center);
            }
            slot.rotation = StructureRotation.facingToward(slot.anchor, center);

            // Instantané du terrain AVANT la pose : permet de restaurer
            // l'empreinte si l'emplacement doit être déspawn (équipe
            // inexistante au lancement).
            captureTerrainSnapshot(slot, slot.anchor);

            boolean placed = plugin.getStructureManager().placeBase(
                    slot.anchor, slot.rotation,
                    markers -> onSlotWritten(slot, markers));
            if (!placed) {
                plugin.getLogger().warning("[Bases] Structure manquante pour "
                        + "l'emplacement " + slot.label() + " (bases.nbt)");
            }
        }

        plugin.getLogger().info("[Bases] " + slots.size()
                + " emplacement(s) de base posé(s) (attribution des équipes "
                + "au lancement de la partie).");
    }

    /**
     * Callback de pose d'un emplacement : la structure est écrite, les
     * repères ont été lus. On en profite pour combler les trous sous la
     * base et pour repérer les cristaux.
     */
    private void onSlotWritten(BaseSlot slot, BaseMarkers markers) {
        slot.markers = markers;
        slot.markersApplied = false;

        // Remblai des trous sous le niveau du sol (dirt) : en forêt, le
        // terrain n'est pas plat et la base "flotte" par endroits.
        if (markers != null && markers.floorY() != null) {
            fillGroundHoles(slot.anchor, markers.floorY(), markers.voidSlots());
            // Marge AUTOUR de la base (entrée + côtés) : sans elle,
            // l'entrée donne sur un fossé et les côtés sont à côté du vide.
            fillGroundMargin(slot.anchor, markers.floorY(),
                    markers.voidSlots());
        }

        // Cristaux (coeur + 4 tours) : conversion des laines repères puis
        // enregistrement.
        tryScanCrystals(slot, 0);

        // Refresh de l'ÉCLAIRAGE des chunks de la base (l'ombre de la
        // base et le niveau de lumière intérieur doivent être recalculés
        // par le serveur, puis renvoyés aux clients).
        scheduleLightRefresh(slot.anchor);

        // Si l'attribution est déjà faite (lancement effectué), on
        // rattache les repères à l'équipe correspondante.
        applyMarkersToAssignedTeam(slot);
    }

    // -----------------------------------------------------------------
    // Attribution AU HASARD des bases aux équipes (lancement de partie)
    // -----------------------------------------------------------------

    /**
     * ATTRIBUTION ALÉATOIRE des bases : tirée AU SORT AU LANCEMENT de la
     * partie. Les équipes présentes sont réparties sur les 4
     * emplacements de base (aucune couleur n'a d'emplacement réservé) ;
     * les emplacements non attribués sont retirés du monde (terrain
     * d'origine restauré).
     *
     * Rappel : les joueurs sont téléportés APRÈS cet appel (cf.
     * GameManager#beginPreparation).
     */
    public void assignBasesToTeams() {
        if (slots.isEmpty()) {
            plugin.getLogger().warning("[Bases] Aucun emplacement de base : "
                    + "attribution impossible.");
            return;
        }

        // 1. Équipes PRÉSENTES au lancement (les autres sont éliminées).
        List<TeamColor> active = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            GameTeam team = plugin.getTeamManager().getTeam(color);
            if (team != null && team.getPlayerCount() > 0) {
                active.add(color);
            }
        }

        // 2. TIRAGE AU SORT des emplacements.
        List<BaseSlot> pool = new ArrayList<>(slots);
        Collections.shuffle(pool, random);

        // 3. Reset des modèles : les bases sont reconstruites.
        bases.clear();
        assignment.clear();
        plugin.getGateManager().clearGates();
        plugin.getNpcManager().resetAll();
        for (TeamColor color : TeamColor.values()) {
            plugin.getCoreService().unregisterBase(color);
        }
        for (BaseSlot slot : slots) {
            slot.markersApplied = false;
        }

        // 4. Affectation des emplacements aux équipes.
        Map<TeamColor, BaseSlot> chosen = new LinkedHashMap<>();
        for (int i = 0; i < active.size() && i < pool.size(); i++) {
            chosen.put(active.get(i), pool.get(i));
        }
        assignment.putAll(chosen);

        for (Map.Entry<TeamColor, BaseSlot> entry : chosen.entrySet()) {
            wireBase(entry.getKey(), entry.getValue());
        }

        // 5. Emplacements non attribués : la structure est retirée.
        for (BaseSlot slot : slots) {
            if (!chosen.containsValue(slot)) {
                removeSlot(slot);
            }
        }

        // 6. Annonce du tirage.
        StringBuilder summary = new StringBuilder("[Bases] Attribution : ");
        for (Map.Entry<TeamColor, BaseSlot> entry : chosen.entrySet()) {
            summary.append(entry.getKey().getColoredName())
                    .append(" §7→ §f").append(entry.getValue().label())
                    .append("§7, ");
        }
        plugin.getLogger().info(summary.toString());

        // 7. Les données de pose (repères, cristaux) peuvent arriver APRÈS
        //    l'attribution (pose asynchrone) : on réessaie un moment.
        scheduleLateRefresh();
    }

    /**
     * Crée (ou met à jour) le modèle d'une base et la câble à son équipe.
     */
    private void wireBase(TeamColor color, BaseSlot slot) {
        World world = slot.anchor.getWorld();
        if (world == null) {
            return;
        }
        int protectionRadius = 27; // rayon de la zone protégée autour du centre

        // Le centre de la base = CENTRE GÉOMÉTRIQUE de la boîte (la zone de
        // protection PvP couvre alors TOUTE l'empreinte 35x35).
        Location boxCenter = slot.anchor.clone()
                .add(GameBase.HALF, 0, GameBase.HALF);

        Location spawn = resolveSpawn(slot);
        GameBase base = new GameBase(color, slot.anchor, boxCenter,
                protectionRadius, spawn, slot.anchor.clone());
        bases.put(color, base);

        // IMPORTANT : câble le spawn dans le GameTeam (source de vérité).
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team != null) {
            team.setSpawn(spawn);
        }

        applyMarkers(color, slot);
        applyCrystals(color, slot);
    }

    /**
     * Rattache les repères d'un emplacement à l'équipe qui l'possède :
     * portillons, PNJ, emplacements à laisser vides, bannières, spawn.
     */
    private void applyMarkers(TeamColor color, BaseSlot slot) {
        BaseMarkers markers = slot.markers;
        if (markers == null || slot.markersApplied) {
            return;
        }
        GameBase base = bases.get(color);
        if (base == null) {
            return;
        }

        // Portillons (ils s'ouvrent à l'approche des propriétaires).
        for (Location gate : markers.gates()) {
            plugin.getGateManager().registerGate(color, gate);
        }
        // PNJ d'équipe.
        if (markers.upgradeNpc() != null) {
            plugin.getNpcManager().setUpgradeNpc(color, markers.upgradeNpc());
        }
        if (markers.itemNpc() != null) {
            plugin.getNpcManager().setItemNpc(color, markers.itemNpc());
        }
        // Emplacements du gabarit qui doivent rester VIDES.
        for (Location voidSlot : markers.voidSlots()) {
            base.addVoidSlot(voidSlot);
        }
        // Bannières blanches : couleur de l'équipe.
        int recolored = plugin.getMarkerManager()
                .recolorBanners(markers.banners(), color);
        if (recolored > 0) {
            plugin.getLogger().info("[Bases] " + color.getColoredName()
                    + " §7: " + recolored + " bannière(s) recolorée(s).");
        }

        // Spawn intérieur (laine VIOLETTE) : plus précis que l'estimation.
        if (markers.spawn() != null) {
            Location safe = findSafeSpawn(slot.anchor.getWorld(),
                    markers.spawn());
            if (safe != null) {
                base.setSpawn(safe);
                GameTeam team = plugin.getTeamManager().getTeam(color);
                if (team != null) {
                    team.setSpawn(safe);
                }
            }
        }

        slot.markersApplied = true;
    }

    /**
     * Enregistre les cristaux (coeur + tours) de l'emplacement pour une
     * équipe donnée (si ils ont été retrouvés).
     */
    private void applyCrystals(TeamColor color, BaseSlot slot) {
        if (slot.coreCrystal == null || slot.towers.size() < 4) {
            return;
        }
        if (plugin.getCoreService().hasBase(color)) {
            return; // déjà enregistré (ne pas ré-armer les tours)
        }
        plugin.getCoreService().registerBaseCrystals(color, slot.coreCrystal,
                slot.towers, slot.chest);
        plugin.getLogger().info("[Bases] " + color.name().toLowerCase()
                + " : 5 cristaux + coffre de dépôt enregistrés.");
    }

    /**
     * Applique les repères d'un emplacement à l'équipe qui lePossède
     * (appelé quand la pose asynchrone se termine après l'attribution).
     */
    private void applyMarkersToAssignedTeam(BaseSlot slot) {
        for (Map.Entry<TeamColor, BaseSlot> entry : assignment.entrySet()) {
            if (entry.getValue() == slot) {
                applyMarkers(entry.getKey(), slot);
                applyCrystals(entry.getKey(), slot);
            }
        }
    }

    /**
     * Vérifications différées : la pose de la structure est asynchrone,
     * les repères (spawn, bannières) et les cristaux peuvent arriver après
     * l'attribution. On réessaie quelques fois.
     */
    private void scheduleLateRefresh() {
        if (lateRefreshTask != null) {
            lateRefreshTask.cancel();
            lateRefreshTask = null;
        }
        lateRefreshTask = new org.bukkit.scheduler.BukkitRunnable() {
            private int attempts = 0;

            @Override
            public void run() {
                boolean pending = false;
                for (BaseSlot slot : slots) {
                    if (slot.removed) {
                        continue;
                    }
                    if (slot.markers == null || !slot.markersApplied
                            || slot.coreCrystal == null) {
                        pending = true;
                        applyMarkersToAssignedTeam(slot);
                    }
                }
                attempts++;
                if (!pending || attempts >= LATE_REFRESH_ATTEMPTS) {
                    cancel();
                    lateRefreshTask = null;
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Retire un emplacement du monde : la structure disparaît et le
     * terrain d'origine est restauré.
     */
    private void removeSlot(BaseSlot slot) {
        if (slot.removed) {
            return;
        }
        slot.removed = true;
        if (slot.anchor != null && slot.anchor.getWorld() != null) {
            plugin.getStructureManager().removeBasePlacement(slot.anchor);
            restoreTerrainSnapshot(slot);
            plugin.getLogger().info("[Bases] Emplacement "
                    + slot.label() + " §7retiré (aucune équipe attribuée) : "
                    + "zone régénérée.");
        }
    }

    // -----------------------------------------------------------------
    // Instantané de terrain (déspawn / "régénération" de l'empreinte)
    // -----------------------------------------------------------------

    /**
     * Mémorise l'état de TOUS les blocs de l'empreinte de la base (35x35,
     * marge +1) avant sa pose : c'est ce qui permet de rendre le terrain
     * d'origine si l'emplacement doit être retiré.
     *
     * L'instantané n'est pris qu'UNE FOIS par emplacement (le terrain
     * d'origine ne doit pas être écrasé par la structure lors d'une
     * reconstruction).
     */
    private void captureTerrainSnapshot(BaseSlot slot, Location anchor) {
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        if (terrainSnapshots.containsKey(slot.index)) {
            return; // déjà mémorisé
        }
        World world = anchor.getWorld();
        int ax = anchor.getBlockX();
        int ay = anchor.getBlockY();
        int az = anchor.getBlockZ();
        int minY = Math.max(world.getMinHeight(), ay - 8);
        int maxY = Math.min(world.getMaxHeight() - 1, ay + 48);

        Map<Long, String> snapshot = new HashMap<>();
        for (int x = ax - 1; x <= ax + GameBase.SIZE; x++) {
            for (int z = az - 1; z <= az + GameBase.SIZE; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    snapshot.put(packBlockKey(x, y, z),
                            block.getBlockData().getAsString());
                }
            }
        }
        terrainSnapshots.put(slot.index, snapshot);
        plugin.getLogger().info("[Bases] Instantané terrain "
                + slot.label() + " §7: " + snapshot.size()
                + " bloc(s) mémorisé(s).");
    }

    /**
     * Restaure le terrain d'origine de l'empreinte à partir de l'instantané
     * (remet chaque bloc mémorisé dans son état initial). Sans instantané
     * (base posée avant une mise à jour, ou serveur redémarré), on se
     * contente d'un nettoyage best-effort des blocs au-dessus du sol.
     */
    private void restoreTerrainSnapshot(BaseSlot slot) {
        Location anchor = slot.anchor;
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        Map<Long, String> snapshot = terrainSnapshots.get(slot.index);
        int restored = 0;
        if (snapshot != null && !snapshot.isEmpty()) {
            for (Map.Entry<Long, String> entry : snapshot.entrySet()) {
                long key = entry.getKey();
                int x = unpackX(key);
                int y = unpackY(key);
                int z = unpackZ(key);
                Block block = anchor.getWorld().getBlockAt(x, y, z);
                try {
                    block.setBlockData(
                            org.bukkit.Bukkit.createBlockData(entry.getValue()),
                            false);
                    restored++;
                } catch (IllegalArgumentException ignored) {
                    // Donnée de bloc inconnue (version différente) : on
                    // laisse le bloc tel quel plutôt que de crasher.
                }
            }
        }
        plugin.getLogger().info("[Bases] Terrain restauré "
                + slot.label() + " §7: " + restored + " bloc(s).");
    }

    /** Empaquette des coordonnées de bloc (x,y,z) dans un long. */
    private static long packBlockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFF);
    }

    private static int unpackX(long key) {
        int x = (int) (key >> 38);
        // Restaure le signe (encodage sur 26 bits).
        return (x << 6) >> 6;
    }

    private static int unpackY(long key) {
        int y = (int) ((key >> 26) & 0xFFF);
        return (y << 20) >> 20;
    }

    private static int unpackZ(long key) {
        int z = (int) (key & 0x3FFFFFF);
        return (z << 6) >> 6;
    }

    // -----------------------------------------------------------------
    // Spawn
    // -----------------------------------------------------------------

    /**
     * Spawn de l'équipe : le repère VIOLET (à l'intérieur de la base) s'il
     * est connu, sinon le repli "devant la façade" (ancien comportement).
     */
    private Location resolveSpawn(BaseSlot slot) {
        World world = slot.anchor.getWorld();
        BaseMarkers markers = slot.markers;
        if (markers != null && markers.spawn() != null) {
            Location safe = findSafeSpawn(world, markers.spawn());
            if (safe != null) {
                return safe;
            }
            plugin.getLogger().warning("[Bases] Repère de spawn (laine "
                    + "violette) unsafe : recherche d'un emplacement sûr "
                    + "à proximité.");
        }

        // Repli : spawn devant la FAÇADE (côté du centre de la map),
        // jamais au-dessus de l'eau.
        Location wanted = facadeSpawnInFront(slot.anchor, slot.rotation);
        Location spawn = resolveDrySpawnNear(world, wanted, slot.anchor);
        spawn.setY(surfaceY(world, spawn.getBlockX(), spawn.getBlockZ()));
        return spawn;
    }

    /**
     * Cherche un point de spawn SÛR autour du repère : le bloc doit être
     * de l'AIR, le bloc du dessous doit être SOLIDE (le joueur ne tombe
     * pas dans le vide) et la tête doit être libre.
     *
     * @return le spawn sûr, ou null si aucun trouvé.
     */
    private Location findSafeSpawn(World world, Location wanted) {
        if (world == null || wanted == null) {
            return null;
        }
        int bx = wanted.getBlockX();
        int by = wanted.getBlockY();
        int bz = wanted.getBlockZ();
        for (int radius = 0; radius <= SAFE_SPAWN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue; // périmètre uniquement
                    }
                    int x = bx + dx;
                    int z = bz + dz;
                    for (int y = by; y >= by - 2; y--) {
                        if (isSafeSpawn(world, x, y, z)) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Un emplacement est-il un spawn sûr (pieds dans l'air, sol dessous) ? */
    private boolean isSafeSpawn(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!feet.getType().isAir() || !head.getType().isAir()) {
            return false;
        }
        return ground.getType().isSolid();
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

    // -----------------------------------------------------------------
    // Niveau du sol (forêt) et comblement des trous
    // -----------------------------------------------------------------

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
     * VRAIE surface du terrain d'une colonne : on IGNORE la végétation
     * (feuilles, troncs, herbes, fleurs) et on descend jusqu'à trouver un
     * bloc de SOL (herbe, terre, pierre...).
     *
     * RÈGLE UTILISATEUR : sans ce correctif, une base posée en FORÊT
     * reposait sur la CANOPÉE (le bloc le plus haut était une feuille) :
     * la base flottait dans les arbres.
     */
    private int groundSurfaceY(World world, int x, int z) {
        int maxY = world.getHighestBlockYAt(x, z);
        int minY = world.getMinHeight();
        for (int y = maxY; y > minY; y--) {
            if (GROUND_MATERIALS.contains(world.getBlockAt(x, y, z).getType())) {
                return y;
            }
        }
        return maxY;
    }

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
                    int ground = groundSurfaceY(world, sx, sz) + 1;
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
            majority = groundSurfaceY(world, x, z) + 1;
        }
        return majority;
    }

    /**
     * Hauteur du sol (+1) pour une base, calculée comme le NIVEAU
     * MAJORITAIRE du relief dans le rayon de la base.
     *
     * RÈGLE UTILISATEUR : la base (et son spawn) doit reposer AU SOL
     * et non flotter. On abaisse de 9 blocs la position d'ancrage
     * calculée (les troncs/feuilles du footprint sont nettoyés à la
     * pose, voir RawTemplatePlacer.clearTreeIntersections).
     */
    private double worldGroundY(World world, int x, int z) {
        // L'échantillonnage se fait sur le CENTRE de l'empreinte
        // (l'ancre est le coin de la boîte 35x35) : c'est le relief
        // SOUS LA BASE qui compte.
        return majorityGroundY(world, x + GameBase.HALF, z + GameBase.HALF) - 9.0;
    }

    /**
     * COMBLEMENT DES TROUS sous le niveau du sol d'une base.
     *
     * RÈGLE UTILISATEUR : sous le niveau du sol de la base, s'il y a de
     * la terre (grass_block / dirt), les trous (air, herbes, neige) sont
     * remplacés par de la DIRT : la base ne "flotte" plus au-dessus du
     * terrain. Si la colonne ne repose sur AUCUNE terre, on ne comble
     * rien (le gabarit reste ce qu'il est).
     *
     * TERRAIN "TERRE" : le gravier et le sable comptent aussi (plage,
     * désert, fond de rivière) : sinon les trous sous la base n'étaient
     * jamais comblés sur ces terrains.
     *
     * EMPLACEMENTS VIDES : les cellules marquées "espace vide" par le
     * gabarit (structure_void / BARRIER) sont IGNORÉES : elles doivent
     * rester sans AUCUN bloc naturel.
     */
    private void fillGroundHoles(Location anchor, int floorY,
                                 List<Location> voidSlots) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        // Index des cellules "espace vide" (lookup O(1)).
        Set<Long> voidKeys = new HashSet<>();
        if (voidSlots != null) {
            for (Location location : voidSlots) {
                if (location == null) {
                    continue;
                }
                voidKeys.add(packBlockKey(location.getBlockX(),
                        location.getBlockY(), location.getBlockZ()));
            }
        }
        int filled = 0;
        for (int x = ax; x <= ax + GameBase.SIZE - 1; x++) {
            for (int z = az; z <= az + GameBase.SIZE - 1; z++) {
                // 1. La colonne doit reposer sur de la terre.
                boolean earth = false;
                for (int y = floorY - 1; y >= floorY - 1 - GROUND_FILL_DEPTH;
                     y--) {
                    if (y < world.getMinHeight()) {
                        break;
                    }
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (isEarthMaterial(type)) {
                        earth = true;
                        break;
                    }
                    if (!isFillableHole(type)) {
                        break; // un bloc plein arrête la descente
                    }
                }
                if (!earth) {
                    continue;
                }
                // 2. Comblement des trous (de la surface vers le bas).
                for (int y = floorY - 1; y >= floorY - 1 - GROUND_FILL_DEPTH;
                     y--) {
                    if (y < world.getMinHeight()) {
                        break;
                    }
                    if (voidKeys.contains(packBlockKey(x, y, z))) {
                        continue; // emplacement "espace vide" : aucun bloc
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (!isFillableHole(block.getType())) {
                        break;
                    }
                    block.setType(Material.DIRT, false);
                    filled++;
                }
            }
        }
        if (filled > 0) {
            plugin.getLogger().info("[Bases] Trous sous la base comblés : "
                    + filled + " bloc(s) de terre.");
        }
    }

    /**
     * REMBLAI DE LA MARGE autour de la base.
     *
     * RÈGLE UTILISATEUR : devant l'entrée et sur les côtés de la base, il
     * faut des BLOCS : sinon l'entrée mène sur un fossé et les côtés de
     * la base sont à côté du vide. On comble donc la MARGE (configurable)
     * autour de l'empreinte 35x35, jusqu'au niveau du plancher de la base.
     *
     * Règles :
     * - on ne remplit QUE ce qui manque (le terrain est plus bas que le
     *   plancher de la base) et on n'écrase jamais un bloc déjà présent ;
     * - les cellules "espace vide" du gabarit restent vides ;
     * - un écart trop important (falaise) n'est pas comblé : ce n'est
     *   pas un fossé ;
     * - applyPhysics = true : le moteur d'éclairage du serveur doit
     *   recalculer la lumière de la plateforme.
     */
    private void fillGroundMargin(Location anchor, int floorY,
                                  List<Location> voidSlots) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int margin = plugin.getConfigManager().getBasesGroundMargin();
        if (margin <= 0) {
            return;
        }
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        // Index des cellules "espace vide" (lookup O(1)).
        Set<Long> voidKeys = new HashSet<>();
        if (voidSlots != null) {
            for (Location location : voidSlots) {
                if (location == null) {
                    continue;
                }
                voidKeys.add(packBlockKey(location.getBlockX(),
                        location.getBlockY(), location.getBlockZ()));
            }
        }
        int filled = 0;
        for (int x = ax - margin; x <= ax + GameBase.SIZE - 1 + margin; x++) {
            for (int z = az - margin; z <= az + GameBase.SIZE - 1 + margin;
                 z++) {
                if (x >= ax && x <= ax + GameBase.SIZE - 1
                        && z >= az && z <= az + GameBase.SIZE - 1) {
                    continue; // intérieur : fillGroundHoles s'en charge
                }
                // 1. Niveau du terrain naturel autour de la base.
                int groundY = groundLevelY(world, x, z, floorY);
                if (groundY < 0 || groundY >= floorY) {
                    continue; // déjà au niveau (ou plus haut) : rien à faire
                }
                if (floorY - groundY > GROUND_MARGIN_MAX_DEPTH) {
                    continue; // falaise : ce n'est pas un fossé
                }
                Material groundType = world.getBlockAt(x, groundY, z).getType();
                Material top = marginTopMaterial(groundType);
                Material fill = top == Material.GRASS_BLOCK
                        ? Material.DIRT : top;
                // 2. Remplissage jusqu'au niveau du plancher de la base.
                for (int y = groundY + 1; y <= floorY; y++) {
                    if (voidKeys.contains(packBlockKey(x, y, z))) {
                        break; // emplacement "espace vide" : colonne intacte
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (!isFillableHole(block.getType())) {
                        break; // un bloc plein arrête le remblai
                    }
                    block.setType(y == floorY ? top : fill, true);
                    filled++;
                }
            }
        }
        if (filled > 0) {
            plugin.getLogger().info("[Bases] Marge remblayée autour de la "
                    + "base : " + filled + " bloc(s).");
        }
    }

    /**
     * Y du SOL NATUREL autour de la base (le bloc de terrain le plus haut
     * en dessous du plancher), en ignorant les plantes et les arbres.
     *
     * @return le Y du sol, ou -1 si aucun sol n'est trouvé dans la
     *         profondeur max.
     */
    private int groundLevelY(World world, int x, int z, int floorY) {
        for (int y = floorY; y >= floorY - GROUND_MARGIN_MAX_DEPTH; y--) {
            if (y < world.getMinHeight()) {
                break;
            }
            Material type = world.getBlockAt(x, y, z).getType();
            if (GROUND_MATERIALS.contains(type)) {
                return y; // le vrai sol
            }
            if (!isFillableHole(type)) {
                continue; // arbre, roche, buisson : on descend encore
            }
        }
        return -1;
    }

    /**
     * Matériau de la COUCHE DE SURFACE du remblai de marge (on suit le
     * sol naturel : herbe sur la terre, sable sur le sable, etc.).
     */
    private static Material marginTopMaterial(Material groundType) {
        return switch (groundType) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL ->
                    Material.GRASS_BLOCK;
            default -> groundType;
        };
    }

    /**
     * RAFRAÎCHIT L'ÉCLAIRAGE des chunks couverts par une base.
     *
     * RÈGLE UTILISATEUR (éclairage) : après la pose d'une base (et de son
     * remblai), les chunks concernés doivent être RENVOYÉS aux clients avec
     * leur éclairage à jour. Sans ce "refresh", le serveur conserve un
     * éclairage obsolète : l'ombre reste figée à l'ancien état du terrain
     * et des zones sombres apparaissent là où il ne devrait pas y en avoir.
     *
     * Le moteur d'éclairage applique ses mises à jour de façon DIFFÉRÉE :
     * on rafraîchit donc le chunk après un court délai.
     */
    private void scheduleLightRefresh(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int margin = plugin.getConfigManager().getBasesGroundMargin();
        int minX = anchor.getBlockX() - margin;
        int maxX = anchor.getBlockX() + GameBase.SIZE - 1 + margin;
        int minZ = anchor.getBlockZ() - margin;
        int maxZ = anchor.getBlockZ() + GameBase.SIZE - 1 + margin;
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    try {
                        // refreshChunk attend des COORDONNÉES DE CHUNK.
                        world.refreshChunk(cx, cz);
                    } catch (Throwable ignored) {
                        // chunk déchargé : sans conséquence
                    }
                }
            }
        }, LIGHT_REFRESH_DELAY_TICKS);
    }

    /**
     * Rafraîchit l'éclairage de TOUTES les bases encore en place.
     *
     * Appelé au lancement de la partie, APRÈS la téléportation des
     * joueurs : les chunks sont alors tickés, donc l'éclairage du serveur
     * est réellement à jour quand il est renvoyé aux clients.
     */
    public void refreshBaseLighting() {
        for (BaseSlot slot : slots) {
            if (slot.anchor == null || slot.removed) {
                continue;
            }
            scheduleLightRefresh(slot.anchor);
        }
    }

    /**
     * Matériaux considérés comme de la "TERRE" (support du comblement des
     * trous sous la base). Le gravier et le sable sont inclus : une base
     * posée sur une plage ou dans un désert doit être remblayée elle aussi.
     */
    private static boolean isEarthMaterial(Material type) {
        return type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.PODZOL
                || type == Material.MUD
                || type == Material.SAND
                || type == Material.SANDSTONE
                || type == Material.RED_SAND
                || type == Material.RED_SANDSTONE
                || type == Material.GRAVEL
                || type == Material.CLAY
                || type == Material.TERRACOTTA
                || type == Material.SNOW_BLOCK;
    }

    /** Ce bloc peut-il être remplacé par de la terre (trou) ? */
    private static boolean isFillableHole(Material type) {
        return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.LARGE_FERN
                || type == Material.DEAD_BUSH
                || type == Material.SNOW
                || type == Material.LIGHT;
    }

    // -----------------------------------------------------------------
    // Position de la base le long de son axe
    // -----------------------------------------------------------------

    /**
     * Résout la position de la base le long de son axe cardinal :
     * distance idéale d'abord, puis décalages vers l'EXTÉRIEUR (préserve
     * l'espacement inter-bases) puis vers l'INTÉRIEUR, jusqu'à la
     * première surface sèche. L'ancre est posée au sol (Y réel).
     */
    private Location resolveDryAnchor(BaseSlot slot, World world,
                                      Location center) {
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
                int x = centerX + slot.unitX * dist;
                int z = centerZ + slot.unitZ * dist;
                if (zone != null && !zone.contains(
                        new Location(world, x, center.getY(), z))) {
                    continue; // on ne sort jamais de la zone
                }
                if (LocationUtil.isDryColumn(world, x, z)) {
                    if (delta != 0) {
                        plugin.getLogger().info("[Bases] Emplacement "
                                + slot.label() + " §7décalé de "
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
        plugin.getLogger().severe("[Bases] AUCUNE surface sèche trouvée pour "
                + "l'emplacement " + slot.label() + " : base posée sur la "
                + "position idéale malgré l'eau.");
        int x = centerX + slot.unitX * radius;
        int z = centerZ + slot.unitZ * radius;
        return new Location(world, x + 0.5, worldGroundY(world, x, z), z + 0.5);
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
    /** Tentatives de scan différées (placement bascule async). */
    private static final int CRYSTAL_SCAN_RETRIES = 6;

    /**
     * Localise les 5 cristaux (sea_lantern) et le coffre de dépôt de
     * l'emplacement puis les enregistre dans le CoreService.
     *
     * La structure fournit ses propres cristaux ; le centre est identifié
     * comme le cristal le plus proche du centroïde des lanternes trouvées
     * (les 4 tours étant symétriques autour du centre).
     *
     * Si le scan ne trouve rien (placement asynchrone du poseur de
     * secours), on réessaie un peu plus tard.
     */
    private void tryScanCrystals(BaseSlot slot, int attempt) {
        Location anchor = slot.anchor;
        World world = anchor == null ? null : anchor.getWorld();
        if (world == null) {
            return;
        }

        List<Location> lanterns = new ArrayList<>();
        List<Location> chests = new ArrayList<>();
        scanBaseRegion(anchor, lanterns, chests);

        // RÈGLE UTILISATEUR : les cristaux sont repérés par la laine NOIRE
        // (cœur) et les laines MARRON (tours) du gabarit. Le gabarit
        // actuel ne contient PAS de coffre : celui-ci est donc OPTIONNEL
        // (il ne doit pas empêcher la détection des cristaux, sinon la
        // base n'aurait ni cœur ni tours → le verre violet deviendrait
        // cassable par les adversaires dès le combat).
        if (lanterns.size() >= 5) {
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
                collectCrystals(slot, unique, chests);
                return;
            }
            // Moins de 5 colonnes distinctes => placement pas encore en
            // place (ou structure trop endommagée) : on réessaie plus tard.
        }

        if (attempt < CRYSTAL_SCAN_RETRIES) {
            // La pose de secours (RawTemplatePlacer) est asynchrone :
            // on rescanne un peu plus tard.
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> tryScanCrystals(slot, attempt + 1), 20L);
            return;
        }
        plugin.getLogger().severe("[Bases] Cristaux introuvables pour "
                + "l'emplacement " + slot.label() + " §7(" + lanterns.size()
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
    private void collectCrystals(BaseSlot slot, List<Location> unique,
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

        // Le coffre de dépôt = le coffre le plus proche du centre
        // (OPTIONNEL : le gabarit actuel n'en contient pas).
        Location chest = null;
        if (!chests.isEmpty()) {
            chest = chests.get(0);
            double bestChest = Double.MAX_VALUE;
            for (Location candidate : chests) {
                double d = candidate.distanceSquared(center);
                if (d < bestChest) {
                    bestChest = d;
                    chest = candidate;
                }
            }
        }

        // RÈGLE UTILISATEUR : le cœur et les tours repérés par des laines
        // (laine NOIRE = cœur, laines MARRON = tours) sont PHYSIQUEMENT
        // convertis en sea_lantern, pour qu'on les reconnaisse au premier
        // coup d'œil (même rendu que les cristaux des anciens gabarits).
        setCrystalBlock(center);
        for (Location tower : towers) {
            setCrystalBlock(tower);
        }

        slot.coreCrystal = center;
        slot.towers.clear();
        slot.towers.addAll(towers);
        slot.chest = chest;

        // Si l'attribution est déjà faite, on enregistre tout de suite.
        applyMarkersToAssignedTeam(slot);
    }

    /** Pose un bloc cristal (sea_lantern) dans le monde, à cette position. */
    private void setCrystalBlock(Location location) {
        if (location != null && location.getWorld() != null) {
            location.getBlock().setType(
                    com.mceteams.xii.service.CoreService.CRYSTAL_MATERIAL,
                    false);
        }
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
                    // Candidats cristaux : le sea_lantern posé directement
                    // dans le .nbt OU les laines NOIRE (cœur) / MARRON
                    // (tours) des nouveaux gabarits - converties en
                    // sea_lantern par collectCrystals.
                    if (type == com.mceteams.xii.service.CoreService.CRYSTAL_MATERIAL
                            || type == Material.BLACK_WOOL
                            || type == Material.BROWN_WOOL) {
                        lanterns.add(block.getLocation());
                    } else if (type == Material.CHEST) {
                        chests.add(block.getLocation());
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Accès
    // -----------------------------------------------------------------

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
        if (lateRefreshTask != null) {
            lateRefreshTask.cancel();
            lateRefreshTask = null;
        }
        bases.clear();
        assignment.clear();
        slots.clear();
        terrainSnapshots.clear();
    }
}
