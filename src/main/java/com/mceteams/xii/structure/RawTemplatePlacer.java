package com.mceteams.xii.structure;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * rotation/axis remappées). Les entités du template sont ignorées.
 */
public class RawTemplatePlacer {

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
     * @param callback reçoit la liste des blocs POSÉS dans le monde
     *                 (liste vide = échec) - permet un éventuel "undo".
     */
    public void placeAsync(Path nbtFile, StructureLocation location,
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
                    Runnable write = () -> callback.accept(writeBlocks(blocks, location));
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
            try {
                Location target = new Location(location.getWorld(),
                        location.getX() + block.x(),
                        location.getY() + block.y(),
                        location.getZ() + block.z());
                // Jamais d'écrasement d'un BEACON : les coeurs provisoires
                // des bases peuvent être posés pendant l'application async.
                if (target.getBlock().getType()
                        == org.bukkit.Material.BEACON) {
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
                                        rotation));
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
                                 StructureRotation rotation) {
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
            default -> {
                return value;
            }
        }
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
