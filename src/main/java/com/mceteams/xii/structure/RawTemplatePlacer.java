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

    /** Bloc "vide" du template : jamais posé, sert de gabarit. */
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
                        List<Location> written = writeBlocks(blocks, location);
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
                                       StructureLocation location) {
        List<Location> written = new ArrayList<>();
        int failed = 0;
        for (TemplateBlock block : blocks) {
            if (isVoid(block.dataString())) {
                continue; // structure_void : gabarit, jamais posé
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
                target.getBlock().setBlockData(
                        Bukkit.createBlockData(block.dataString()), false);
                written.add(target);
            } catch (Throwable throwable) {
                failed++;
                if (failed <= 5) { // pas de spam : 5 exemples suffisent
                    plugin.getLogger().warning("[Structures] Bloc ignoré ("
                            + block.dataString() + ") : " + throwable);
                }
            }
        }
        plugin.getLogger().info("[Structures] Pose manuelle : " + written.size()
                + " bloc(s)" + (failed > 0 ? ", " + failed + " ignoré(s)" : "")
                + ".");
        return written;
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
            if (isVoid(block.dataString())) {
                voidColumns.add(key);
            } else {
                floorByColumn.merge(key, y, Math::min);
            }
        }

        int filled = 0;
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
                if (type != Material.AIR && type != Material.STRUCTURE_VOID) {
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

    /** true si la chaîne BlockData représente un structure_void. */
    private boolean isVoid(String dataString) {
        return dataString != null
                && dataString.startsWith(STRUCTURE_VOID);
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
                if (dataString.equals("minecraft:air")) {
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
        List<String> cycle = List.of("north", "east", "south", "west");
        int index = cycle.indexOf(facing);
        if (index < 0) {
            return facing;
        }
        int delta = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        return cycle.get((index + delta) % 4);
    }

    private String stripNamespace(String name) {
        if (name == null) {
            return "minecraft:air";
        }
        return name.startsWith("minecraft:") ? name : "minecraft:" + name;
    }
}
