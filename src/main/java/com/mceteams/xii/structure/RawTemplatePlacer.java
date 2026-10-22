package com.mceteams.xii.structure;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application MANUELLE d'un template .nbt : lecture directe du format
 * vanilla (size/palette/blocks) + pose des blocs via BlockData.
 *
 * Solution de SECOURS à StructureManager.loadStructure(), dont le
 * comportement varie selon les versions de Paper (retours null
 * silencieux malgré un fichier valide et présent). Ici, aucune API de
 * structure n'est utilisée : seulement des blocs.
 *
 * Supporte la rotation (positions transformées + propriétés facing/
 * rotation/axis + connexions north/south/east/west remappées). Les
 * entités du template sont ignorées.
 */
public class RawTemplatePlacer {

    /** Bloc "vide" du template : gabarit (posé pour les bases, voir
     * writeBlocks / MarkerManager : converti en air + "espace à laisser
     * vide"). */
    public static final String STRUCTURE_VOID = "minecraft:structure_void";
    /** Épaisseur max du remblai herbeux sous une base (style village). */
    private static final int GRASS_FILL_DEPTH = 4;

    private final XiiPlugin plugin;

    public RawTemplatePlacer(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Bloc décodé du template (position locale + BlockData cible). */
    private record TemplateBlock(int x, int y, int z, String dataString) {
    }

    /** Bloc "critique pour la lumière" : posé en 2e passe, avec la
     *  physique (pour que le moteur d'éclairage recalcule l'ombre). */
    private record PendingBlock(TemplateBlock block, BlockData data,
                                Location target) {
    }

    /**
     * Place le template sur le thread principal (chunks chargés async au
     * préalable).
     *
     * @param baseGrassFill true pour remblayer de la terre/grass sous le
     *                      plancher de la structure (style villages)
     * @param callback      reçoit la liste des blocs POSÉS dans le monde
     *                      (liste vide = échec) - permet un éventuel "undo".
     */
    public void placeAsync(Path nbtFile, StructureLocation location,
                           boolean baseGrassFill,
                           java.util.function.Consumer<List<Location>> callback) {
        List<TemplateBlock> blocks = decode(nbtFile, location.getRotation());
        if (blocks.isEmpty()) {
            callback.accept(List.of());
            return;
        }

        // Préchargement ASYNC de tous les chunks touchés avant écriture
        // (poser un bloc dans un chunk non généré gèlerait le serveur).
        Set<Long> chunkKeys = new LinkedHashSet<>();
        var world = location.getWorld();
        for (TemplateBlock block : blocks) {
            int x = location.getX() + block.x();
            int z = location.getZ() + block.z();
            chunkKeys.add(com.mceteams.xii.util.LocationUtil.chunkKey(
                    x >> 4, z >> 4));
        }
        List<java.util.concurrent.CompletableFuture<org.bukkit.Chunk>>
                futures = new ArrayList<>();
        for (long key : chunkKeys) {
            futures.add(world.getChunkAtAsync(
                    (int) (key >> 32), (int) key));
        }
        java.util.concurrent.CompletableFuture
                .allOf(futures.toArray(
                        new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> {
                    Runnable write = () -> {
                        // RÈGLE UTILISATEUR : si un arbre occupe l'empreinte
                        // de la base, on le NETTOIE (troncs + feuilles) AVANT
                        // de poser la structure, pour que celle-ci repose sur
                        // le sol et ne soit pas posée SUR la canopée.
                        if (baseGrassFill) {
                            clearTreeIntersections(location, blocks);
                        }
                        List<Location> written = writeBlocks(blocks, location,
                                baseGrassFill);
                        if (baseGrassFill) {
                            fillGrassUnder(location, blocks);
                        }
                        callback.accept(written);
                    };
                    if (Bukkit.isPrimaryThread()) {
                        write.run();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, write);
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Structures] Erreur raw-place : "
                            + throwable);
                    throwable.printStackTrace();
                    callback.accept(List.of());
                    return null;
                });
    }

    /** Pose effective des blocs (thread principal, chunks chargés). */
    private List<Location> writeBlocks(List<TemplateBlock> blocks,
                                       StructureLocation location,
                                       boolean baseGrassFill) {
        List<Location> written = new ArrayList<>();
        int failed = 0;

        // Les données des blocs du gabarit ne sont créées QU'UNE fois
        // (le gabarit ne contient qu'une vingtaine de matériaux).
        Map<String, BlockData> dataCache = new HashMap<>();
        // Par colonne, le Y du bloc OPAQUE le plus haut (le "toit") :
        // ce sont ces blocs qui bouchent la lumière du ciel.
        Map<Long, Integer> topOpaqueY = new HashMap<>();
        for (TemplateBlock block : blocks) {
            if (isVoid(block.dataString())) {
                continue; // structure_void : traité plus bas
            }
            BlockData data = dataCache.get(block.dataString());
            if (data == null) {
                try {
                    data = Bukkit.createBlockData(block.dataString());
                } catch (Throwable ignored) {
                    continue; // l'erreur sera signalée à la pose
                }
                dataCache.put(block.dataString(), data);
            }
            if (!data.isOccluding()) {
                continue; // ne bouche pas la lumière
            }
            int x = location.getX() + block.x();
            int z = location.getZ() + block.z();
            int y = location.getY() + block.y();
            topOpaqueY.merge(columnKey(x, z), y, Math::max);
        }

        // RÈGLE UTILISATEUR (éclairage) : les blocs qui bouchent la
        // lumière du ciel (le toit de la base) et les sources de lumière
        // (torches, lanternes...) sont posés AVEC la physique, et EN
        // DERNIER : c'est ce qui force le moteur d'éclairage du serveur
        // à recalculer l'ombrage et à le redistribuer aux clients.
        // Sans cela, l'éclairage n'est jamais rafraîchi et l'ombre
        // reste figée à l'ancien état du terrain.
        List<PendingBlock> lightCritical = new ArrayList<>();

        for (TemplateBlock block : blocks) {
            if (isVoid(block.dataString())) {
                // structure_void : gabarit, jamais "posé" comme un bloc
                // de structure... SAUF pour une BASE (baseGrassFill) : on
                // pose tout de même le bloc pour que MarkerManager le
                // convertisse en air et mémorise l'emplacement ("l'espace
                // vide du gabarit doit rester vide", pose interdite).
                if (baseGrassFill) {
                    Location target = new Location(location.getWorld(),
                            location.getX() + block.x(),
                            location.getY() + block.y(),
                            location.getZ() + block.z());
                    if (target.getBlock().getType() != Material.BEACON) {
                        target.getBlock().setType(
                                Material.STRUCTURE_VOID, false);
                    }
                }
                continue;
            }
            try {
                Location target = new Location(location.getWorld(),
                        location.getX() + block.x(),
                        location.getY() + block.y(),
                        location.getZ() + block.z());
                // Jamais d'écrasement d'un BEACON : les coeurs provisoires
                // des bases peuvent être posés pendant l'application async.
                if (target.getBlock().getType() == Material.BEACON) {
                    continue;
                }
                BlockData data = dataCache.get(block.dataString());
                if (data == null) {
                    data = Bukkit.createBlockData(block.dataString());
                    dataCache.put(block.dataString(), data);
                }
                if (isLightCritical(block, data, location, topOpaqueY)) {
                    lightCritical.add(new PendingBlock(block, data, target));
                    continue; // posé plus bas, avec la physique
                }
                target.getBlock().setBlockData(data, false);
                // RÈGLE UTILISATEUR : les REPÈRES "espace vide" (bloc
                // alternatif au structure_void) sont posés (MarkerManager
                // les convertira en AIR) mais NE sont PAS enregistrés
                // comme blocs de structure : leur position doit rester
                // libre (ni protégée, ni "bloc de structure").
                if (!baseGrassFill || !isVoidMarker(block.dataString())) {
                    written.add(target);
                }
            } catch (Throwable throwable) {
                failed++;
                if (failed <= 5) { // pas de spam : 5 exemples suffisent
                    plugin.getLogger().warning("[Structures] Bloc ignoré ("
                            + block.dataString() + ") : " + throwable);
                }
            }
        }

        // 2e passe : les blocs "critiques pour la lumière" (avec physique).
        for (PendingBlock pending : lightCritical) {
            try {
                pending.target().getBlock().setBlockData(pending.data(), true);
                if (!baseGrassFill
                        || !isVoidMarker(pending.block().dataString())) {
                    written.add(pending.target());
                }
            } catch (Throwable throwable) {
                failed++;
                if (failed <= 5) {
                    plugin.getLogger().warning("[Structures] Bloc ignoré ("
                            + pending.block().dataString() + ") : "
                            + throwable);
                }
            }
        }

        plugin.getLogger().info("[Structures] Pose manuelle : " + written.size()
                + " bloc(s)" + (failed > 0 ? ", " + failed + " ignoré(s)" : "")
                + ".");
        return written;
    }

    /**
     * Ce bloc doit-il être posé AVEC la physique (donc recalculer
     * l'éclairage du serveur) ?
     *
     * - les SOURCES de lumière (torche, lanterne, feu de camp...) : sans
     *   elles, le bloc n'éclaire rien ;
     * - le bloc OPAQUE le plus haut de sa colonne (le toit) : c'est lui
     *   qui projette l'ombre de la base.
     */
    private boolean isLightCritical(TemplateBlock block, BlockData data,
                                    StructureLocation location,
                                    Map<Long, Integer> topOpaqueY) {
        if (data.getLightEmission() > 0) {
            return true;
        }
        if (!data.isOccluding()) {
            return false;
        }
        int x = location.getX() + block.x();
        int z = location.getZ() + block.z();
        int y = location.getY() + block.y();
        Integer top = topOpaqueY.get(columnKey(x, z));
        return top != null && y >= top;
    }

    /**
     * Remblai sous le plancher de la structure : grass sur la couche
     * supérieure (juste sous le sol), terre en dessous, comme les
     * villages vanilla.
     *
     * RÈGLE UTILISATEUR (anti "herbe dans la base") :
     * - Seuls les blocs {@code structure_void} du gabarit AUTORISENT le
     *   remblai : une colonne n'est remplie QUE si le template a un bloc
     *   structure_void directement SOUS son plancher (y < plancher).
     * - Les colonnes dont le template a une structure pleine (sol/mur)
     *   ne sont PAS remblayées => plus d'herbe/terre dans l'intérieur.
     * - Les colonnes du COULOIR d'entrée (structure_void au niveau du
     *   sol d'accès, PAS sous un plancher) ne sont PAS remblayées : on
     *   laisse le monde tel quel, rien ne pousse devant les portillons.
     * - On ne touche JAMAIS un bloc posé (autre qu'air ou structure_void)
     *   et on ne creuse jamais sous le relief d'origine.
     */
    private void fillGrassUnder(StructureLocation location,
                                List<TemplateBlock> blocks) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        // Plancher par colonne : le plus bas bloc NON-void du template
        // (un build aux sols étagés garde un remblai localisé).
        Map<Long, Integer> floorByColumn = new HashMap<>();
        // Colonnes contenant au moins un structure_void dans le template.
        Set<Long> voidColumns = new java.util.HashSet<>();
        for (TemplateBlock block : blocks) {
            int x = location.getX() + block.x();
            int z = location.getZ() + block.z();
            int y = location.getY() + block.y();
            long key = columnKey(x, z);
            if (isVoidMarker(block.dataString())) {
                voidColumns.add(key);
            } else {
                floorByColumn.merge(key, y, Math::min);
            }
        }

        int filled = 0;
        java.util.List<Material> voidMarkers =
                plugin.getConfigManager().getVoidMarkerMaterials();
        for (long key : voidColumns) {
            // Un void n'autorise le remblai QUE s'il se trouve sous le
            // plancher de la colonne (sinon : couloir / vide au sol → rien).
            if (!floorByColumn.containsKey(key)) {
                continue; // colonne sans plancher (ex. couloir) : rien
            }
            int x = (int) (key >> 32);
            int z = (int) (long) key;
            int floorY = floorByColumn.get(key);
            int groundY = world.getHighestBlockYAt(x, z);
            int topY = floorY - 1;
            // Jamais au-dessus du sol réel, jamais plus profond que
            // GRASS_FILL_DEPTH sous le plancher.
            int bottomY = Math.max(groundY, topY - GRASS_FILL_DEPTH);
            for (int y = topY; y >= bottomY; y--) {
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type == Material.STRUCTURE_VOID
                        || voidMarkers.contains(type)) {
                    continue; // emplacement "espace vide" : AUCUN bloc
                }
                if (type != Material.AIR) {
                    continue; // ne jamais écraser un bloc de structure existant
                }
                block.setBlockData(Bukkit.createBlockData(
                        y == topY ? "minecraft:grass_block" : "minecraft:dirt"),
                        false);
                filled++;
            }
        }
        if (filled > 0) {
            plugin.getLogger().info("[Structures] Remblai herbeux sous la base : "
                    + filled + " bloc(s).");
        }
    }

    /**
     * RÈGLE UTILISATEUR : remet à AIR les cellules VIDES du gabarit
     * (template AIR) d'une BASE, pour que l'intérieur soit dégagé :
     * aucun arbre, aucune butte, aucun vestige de terrain ne doit
     * dépasser "dedans".
     *
     * writeBlocks IGNORE l'air du gabarit (l'air n'écrase pas le
     * terrain, pour le lobby/donjons) ; pour les bases, on vide donc
     * EXPLICITEMENT ces cellules. Seule la portion comprise ENTRE le
     * plancher et le TOIT de la colonne est vidée :
     * - on ne creuse jamais sous la structure (le relief enterré reste
     *   intact) ;
     * - on ne purge rien AU-DESSUS du toit (les cellules air du
     *   gabarit laissées telles quelles : le monde garde ses blocs).
     *
     * Les {@code structure_void}, eux, sont posés par writeBlocks
     * (baseGrassFill) : MarkerManager les convertit en air et mémorise
     * l'emplacement comme "à laisser vide de blocs NATURELS".
     *
     * Appelé par StructureManager.placeBase pour les DEUX voies de pose
     * (API et poseur de secours), après l'écriture de la structure.
     */
    public void clearTemplateEmptyCells(Path nbtFile,
                                        StructureLocation location) {
        List<TemplateBlock> blocks = decode(nbtFile,
                location.getRotation(), true);
        if (blocks.isEmpty()) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Sol ET toit par colonne = plus bas / plus haut bloc NI vide du
        // gabarit ; repli : sol global du gabarit (borné à l'ancre pour
        // ne jamais creuser de sous-sol sur une colonne entièrement vide).
        Map<Long, Integer> floorByColumn = new HashMap<>();
        Map<Long, Integer> topByColumn = new HashMap<>();
        int globalFloor = location.getY();
        int globalTop = location.getY();
        for (TemplateBlock block : blocks) {
            String data = block.dataString();
            if (isVoidMarker(data) || data.equals("minecraft:air")) {
                continue;
            }
            int x = location.getX() + block.x();
            int z = location.getZ() + block.z();
            int y = location.getY() + block.y();
            long key = columnKey(x, z);
            floorByColumn.merge(key, y, Math::min);
            topByColumn.merge(key, y, Math::max);
            globalFloor = Math.min(globalFloor, y);
            globalTop = Math.max(globalTop, y);
        }

        int cleared = 0;
        for (TemplateBlock block : blocks) {
            if (!block.dataString().equals("minecraft:air")) {
                continue;
            }
            int x = location.getX() + block.x();
            int y = location.getY() + block.y();
            int z = location.getZ() + block.z();
            long key = columnKey(x, z);
            int floor = floorByColumn.getOrDefault(key, globalFloor);
            int top = topByColumn.getOrDefault(key, globalTop);
            if (y < floor) {
                continue; // sous le plancher de la colonne : on ne creuse pas
            }
            if (y > top) {
                continue; // au-dessus du toit : les blocs du monde restent
            }
            Block target = world.getBlockAt(x, y, z);
            if (!target.getType().isAir()) {
                target.setType(Material.AIR, false);
                cleared++;
            }
        }
        if (cleared > 0) {
            plugin.getLogger().info("[Structures] Intérieur de base dégagé : "
                    + cleared + " cellule(s) vide(s) du gabarit en AIR.");
        }
    }

    /** true si la chaîne BlockData représente un structure_void. */
    private boolean isVoid(String dataString) {
        return dataString != null
                && dataString.startsWith(STRUCTURE_VOID);
    }

    /**
     * true si la chaîne BlockData représente un REPÈRE "ESPACE VIDE".
     *
     * RÈGLE UTILISATEUR : le repère canonique est le structure_void ;
     * si l'outil de sauvegarde du .nbt ne le conserve pas, le
     * développeur le remplace par un bloc spécifique (BARRIER, LIGHT...)
     * listé en configuration ({@code bases.void-marker-blocks}).
     *
     * Ces blocs sont posés puis convertis en AIR par le MarkerManager,
     * et leur emplacement reste vide (pose interdite).
     */
    private boolean isVoidMarker(String dataString) {
        if (dataString == null) {
            return false;
        }
        String name = dataString;
        int bracket = name.indexOf('[');
        if (bracket >= 0) {
            name = name.substring(0, bracket);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        Material material = Material.matchMaterial(name);
        if (material == null) {
            return false;
        }
        if (material == Material.STRUCTURE_VOID) {
            return true;
        }
        return plugin.getConfigManager().getVoidMarkerMaterials()
                .contains(material);
    }

    /**
     * RÈGLE UTILISATEUR : retire les ARBRES qui occupent l'empreinte d'une
     * base AVANT la pose de la structure. Sans cela, une base posée où pousse
     * un arbre se retrouvait flottant SUR la canopée au lieu de reposer au
     * sol. On supprime troncs (logs), feuilles et souches sur toute la hauteur
     * du template, dans la boîte X/Z qu'il recouvre.
     *
     * Ne touche JAMAIS aux blocs de la structure elle-même (appelée AVANT
     * writeBlocks) ni à l'eau/lave (on ne vide pas un étang).
     */
    private void clearTreeIntersections(StructureLocation location,
                                        List<TemplateBlock> blocks) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        // Bornes de l'empreinte du template (tous les blocs, void inclus).
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (TemplateBlock block : blocks) {
            int x = location.getX() + block.x();
            int z = location.getZ() + block.z();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        if (minX > maxX || minZ > maxZ) {
            return;
        }

        // Hauteur scrutée : du niveau de l'ancre jusqu'au sommet du template.
        int maxY = location.getY();
        for (TemplateBlock block : blocks) {
            maxY = Math.max(maxY, location.getY() + block.y());
        }

        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Sol local : on peut partir du bas de la structure.
                int minY = Math.max(1, minAnchorY(location, blocks, x, z));
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isTreeBlock(block.getType())) {
                        block.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        if (cleared > 0) {
            plugin.getLogger().info("[Structures] Arbres retiré(s) (" + cleared
                    + " bloc(s)) sur l'empreinte d'une base.");
        }
    }

    /** Plus bas Y du template pour une colonne (x,z) de l'empreinte. */
    private int minAnchorY(StructureLocation location,
                           List<TemplateBlock> blocks, int x, int z) {
        int minY = location.getY() - 4; // petite marge sous l'ancre
        for (TemplateBlock block : blocks) {
            int bx = location.getX() + block.x();
            int bz = location.getZ() + block.z();
            if (bx == x && bz == z) {
                minY = Math.min(minY, location.getY() + block.y());
            }
        }
        return minY;
    }

    /** Un bloc constitutif d'un arbre ? */
    private boolean isTreeBlock(Material type) {
        return switch (type) {
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG,
                    ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG,
                    CRIMSON_STEM, WARPED_STEM,
                    OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                    ACACIA_LEAVES, DARK_OAK_LEAVES, MANGROVE_LEAVES,
                    CHERRY_LEAVES, AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES,
                    OAK_WOOD, SPRUCE_WOOD, BIRCH_WOOD, JUNGLE_WOOD,
                    ACACIA_WOOD, DARK_OAK_WOOD, MANGROVE_WOOD, CHERRY_WOOD,
                    STRIPPED_OAK_LOG, STRIPPED_SPRUCE_LOG, STRIPPED_BIRCH_LOG,
                    STRIPPED_JUNGLE_LOG, STRIPPED_ACACIA_LOG, STRIPPED_DARK_OAK_LOG
                    -> true;
            default -> false;
        };
    }

    /** Clé compacte d'une colonne (x,z). */
    private long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * Bornes des blocs NON-AIR du template : {minX,maxX,minY,maxY,minZ,maxZ},
     * ou null si illisible/vide. Rotation NONE = coordonnées locales brutes.
     */
    public int[] localBounds(Path nbtFile) {
        List<TemplateBlock> blocks = decode(nbtFile, StructureRotation.NONE);
        if (blocks.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE,
                minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE,
                maxZ = Integer.MIN_VALUE;
        for (TemplateBlock block : blocks) {
            minX = Math.min(minX, block.x());
            maxX = Math.max(maxX, block.x());
            minY = Math.min(minY, block.y());
            maxY = Math.max(maxY, block.y());
            minZ = Math.min(minZ, block.z());
            maxZ = Math.max(maxZ, block.z());
        }
        return new int[]{minX, maxX, minY, maxY, minZ, maxZ};
    }

    /**
     * Centre X/Z de la BOÎTE du template : {(sizeX-1)/2 ; (sizeZ-1)/2},
     * ou null si illisible.
     *
     * NB : basé sur la taille déclarée du .nbt (et PAS sur les blocs
     * non-air) => pour une boîte impaire (ex : 45), le centre est un
     * bloc ENTIER (index 22) qui peut être posé pile sur la colonne
     * centrale d'une zone.
     */
    public double[] localCenterXZ(Path nbtFile) {
        try {
            SimpleNbt.Compound root = SimpleNbt.read(
                    Files.readAllBytes(nbtFile));
            int[] size = root.getIntList("size");
            if (size.length != 3 || size[0] <= 0 || size[2] <= 0) {
                return null;
            }
            return new double[]{(size[0] - 1) / 2.0, (size[2] - 1) / 2.0};
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Structures] Lecture taille impossible "
                    + "(" + nbtFile + ") : " + throwable);
            return null;
        }
    }

    /** Y du PLANCHER (plus bas bloc non-air), ou null. Les pieds du
     * joueur doivent être posés à floor + 1. */
    public Integer localFloorY(Path nbtFile) {
        int[] b = localBounds(nbtFile);
        return b == null ? null : b[2];
    }

    /**
     * Décode le fichier en blocs transformés selon la rotation.
     * Format template : size[w,h,l], palette[{Name,Properties}],
     * blocks[{pos[x,y,z], state}].
     */
    private List<TemplateBlock> decode(Path nbtFile,
                                       StructureRotation rotation) {
        return decode(nbtFile, rotation, false);
    }

    /**
     * @param includeAir true pour inclure les cellules AIR du gabarit
     *                   dans la liste (elles ne le sont PAS par défaut :
     *                   l'air du template n'écrase pas le terrain).
     */
    private List<TemplateBlock> decode(Path nbtFile,
                                       StructureRotation rotation,
                                       boolean includeAir) {
        try {
            SimpleNbt.Compound root = SimpleNbt.read(
                    Files.readAllBytes(nbtFile));
            int[] size = root.getIntList("size");
            if (size.length != 3) {
                plugin.getLogger().severe("[Structures] Template sans taille "
                        + "valide : " + nbtFile);
                return List.of();
            }
            int sizeX = size[0];
            int sizeZ = size[2];

            // Palette -> chaînes BlockData ("minecraft:xx[prop=v,...]").
            List<String> palette = new ArrayList<>();
            for (Object entry : root.getList("palette")) {
                if (!(entry instanceof SimpleNbt.Compound state)) {
                    palette.add("minecraft:air");
                    continue;
                }
                StringBuilder sb = new StringBuilder(
                        stripNamespace(state.getString("Name")));
                SimpleNbt.Compound properties =
                        state.getCompound("Properties");
                if (!properties.values().isEmpty()) {
                    sb.append('[');
                    boolean first = true;
                    for (Map.Entry<String, Object> prop
                            : properties.values().entrySet()) {
                        if (!first) {
                            sb.append(',');
                        }
                        first = false;
                        sb.append(prop.getKey()).append('=')
                                .append(remapProperty(prop.getKey(),
                                        String.valueOf(prop.getValue()),
                                        rotation, properties));
                    }
                    sb.append(']');
                }
                palette.add(sb.toString());
            }

            // Blocks -> positions transformées par la rotation.
            List<TemplateBlock> blocks = new ArrayList<>();
            for (Object entry : root.getList("blocks")) {
                if (!(entry instanceof SimpleNbt.Compound block)) {
                    continue;
                }
                int[] pos = block.getIntList("pos");
                if (pos.length != 3) {
                    continue;
                }
                int index = block.getInt("state");
                if (index < 0 || index >= palette.size()) {
                    continue;
                }
                String dataString = palette.get(index);
                if (dataString.equals("minecraft:air") && !includeAir) {
                    continue; // l'air du template n'écrase pas le terrain
                }
                int[] transformed = transform(pos[0], pos[1], pos[2],
                        sizeX, sizeZ, rotation);
                blocks.add(new TemplateBlock(transformed[0], transformed[1],
                        transformed[2], dataString));
            }
            return blocks;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("[Structures] Décodage impossible ("
                    + nbtFile + ") : " + throwable);
            throwable.printStackTrace();
            return List.of();
        }
    }

    /**
     * Transformation de position pour une structure tournée autour de son
     * origine (convention : CW90 vu de dessus => north->east).
     */
    private int[] transform(int x, int y, int z,
                            int sizeX, int sizeZ, StructureRotation rotation) {
        return switch (rotation) {
            case NONE -> new int[]{x, y, z};
            case CLOCKWISE_90 -> new int[]{sizeZ - 1 - z, y, x};
            case CLOCKWISE_180 -> new int[]{sizeX - 1 - x, y, sizeZ - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, y, sizeX - 1 - x};
        };
    }

    /** Remappe les propriétés directionnelles selon la rotation. */
    private String remapProperty(String key, String value,
                                 StructureRotation rotation,
                                 SimpleNbt.Compound props) {
        if (rotation == StructureRotation.NONE || value == null) {
            return value;
        }
        switch (key) {
            case "facing" -> {
                return switch (value) {
                    case "north" -> rotateFacing("north", rotation);
                    case "south" -> rotateFacing("south", rotation);
                    case "east" -> rotateFacing("east", rotation);
                    case "west" -> rotateFacing("west", rotation);
                    default -> value; // up/down inchangés
                };
            }
            case "rotation" -> {
                try {
                    int steps = Integer.parseInt(value);
                    steps = switch (rotation) {
                        case CLOCKWISE_90 -> steps + 4;
                        case CLOCKWISE_180 -> steps + 8;
                        case COUNTERCLOCKWISE_90 -> steps + 12;
                        default -> steps;
                    };
                    return String.valueOf(steps % 16);
                } catch (NumberFormatException ignored) {
                    return value;
                }
            }
            case "axis" -> {
                if (rotation == StructureRotation.CLOCKWISE_90
                        || rotation == StructureRotation.COUNTERCLOCKWISE_90) {
                    if ("x".equals(value)) {
                        return "z";
                    }
                    if ("z".equals(value)) {
                        return "x";
                    }
                }
                return value;
            }
            // Murs, murets, barrières en fer : connexions booléennes par
            // côté (north/east/south/west). Elles décrivent un RÉSEAU
            // horizontal : la structure tournée doit conserver SES
            // connexions physiques, donc on lit les propriétés SOEURS.
            case "north", "east", "south", "west" -> {
                return remapConnection(key, props, rotation);
            }
            default -> {
                return value;
            }
        }
    }

    /**
     * Remappe une connexion booléenne en lisant le côté SOURCE qui, une
     * fois la structure tournée, occupera la place du côté {@code side}.
     *  CW90  : north<-west  east<-north  south<-east  west<-south
     *  CW180 : north<-south e<-w         south<-north west<-east
     *  CCW90 : north<-east  east<-south  south<-west  west<-north
     */
    private String remapConnection(String side, SimpleNbt.Compound props,
                                   StructureRotation rotation) {
        String source = switch (rotation) {
            case CLOCKWISE_90 -> switch (side) {
                case "north" -> "west";
                case "east" -> "north";
                case "south" -> "east";
                case "west" -> "south";
                default -> side;
            };
            case CLOCKWISE_180 -> switch (side) {
                case "north" -> "south";
                case "east" -> "west";
                case "south" -> "north";
                case "west" -> "east";
                default -> side;
            };
            case COUNTERCLOCKWISE_90 -> switch (side) {
                case "north" -> "east";
                case "east" -> "south";
                case "south" -> "west";
                case "west" -> "north";
                default -> side;
            };
            default -> side;
        };
        Object raw = props.get(source);
        return raw == null ? "false" : String.valueOf(raw);
    }

    /** Cycle des directions cardinales pour chaque rotation. */
    private String rotateFacing(String facing, StructureRotation rotation) {
        return rotation.rotateDirection(facing);
    }

    private String stripNamespace(String name) {
        if (name == null) {
            return "minecraft:air";
        }
        return name.startsWith("minecraft:") ? name : "minecraft:" + name;
    }
}
