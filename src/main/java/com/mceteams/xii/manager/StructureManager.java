package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.structure.StructureLocation;
import com.mceteams.xii.structure.StructurePlacer;
import com.mceteams.xii.structure.StructureLoader;
import org.bukkit.Location;
import org.bukkit.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade de gestion des structures .nbt (spec §36).
 *
 * Le plugin ne génère JAMAIS l'architecture lui-même : il charge et
 * place uniquement les structures fournies par le développeur dans
 * resources/structures/ (spec §4).
 *
 * Ce manager conserve la liste des placements effectués pour pouvoir
 * "régénérer/nettoyer" lors d'un /party stop (spec §35 point 8) :
 * on replacer simplement les structures par-dessus la zone jouée.
 */
public class StructureManager {

    private final XiiPlugin plugin;
    private final StructureLoader loader;
    private final StructurePlacer placer;

    /** Placements effectués (pour une éventuelle régénération). */
    private final List<PlacementRecord> placements = new ArrayList<>();

    public StructureManager(XiiPlugin plugin) {
        this.plugin = plugin;
        this.loader = new StructureLoader(plugin);
        this.placer = new StructurePlacer();
    }

    /**
     * Place la zone d'attente au-dessus du centre de la zone (spec §4).
     *
     * @return true si la structure a été placée.
     */
    public boolean placeWaitingLobby(GameZone zone) {
        Location center = zone.getCenterLocation();
        if (center == null) {
            return false;
        }
        int height = plugin.getConfigManager().getWaitingLobbyHeight();

        // L'ancrage est posé au centre, en hauteur, sans rotation.
        return place("structures/waiting/waiting_lobby.nbt",
                "waiting_lobby",
                new StructureLocation(center.getWorld(),
                        center.getBlockX(),
                        center.getBlockY() + height,
                        center.getBlockZ(),
                        com.mceteams.xii.structure.StructureRotation.NONE));
    }

    /**
     * Place une base d'équipe à l'ancrage donné, orientée vers le centre.
     *
     * @param colorName couleur en minuscules ("blue", "yellow"...)
     * @param anchor    point d'ancrage calculé par BaseManager
     * @param target    point que la base doit regarder (centre de map)
     */
    public boolean placeBase(String colorName,
                             Location anchor,
                             Location target) {
        var rotation = com.mceteams.xii.structure.StructureRotation
                .facingToward(anchor, target);
        return place("structures/bases/base_" + colorName + ".nbt",
                "base_" + colorName,
                StructureLocation.of(anchor, rotation));
    }

    /**
     * Place un donjon à l'ancrage donné, orienté vers le centre.
     *
     * @param dungeonNumber numéro de la structure (1..4)
     */
    public boolean placeDungeon(int dungeonNumber,
                                Location anchor,
                                Location target) {
        var rotation = com.mceteams.xii.structure.StructureRotation
                .facingToward(anchor, target);
        return place("structures/dungeons/dungeon_" + dungeonNumber + ".nbt",
                "dungeon_" + dungeonNumber,
                StructureLocation.of(anchor, rotation));
    }

    /**
     * Charge puis place une structure ; conserve une trace du placement.
     */
    private boolean place(String resourcePath, String structureName,
                          StructureLocation location) {
        Structure structure = loader.load(resourcePath, structureName);
        if (structure == null) {
            // Structure manquante : non bloquant (fournie par le dev).
            return false;
        }
        boolean placed = placer.place(structure, location);
        if (placed) {
            placements.add(new PlacementRecord(resourcePath, structureName, location));
            plugin.getLogger().info("[Structures] Placée : " + structureName
                    + " -> " + location);
        }
        return placed;
    }

    /**
     * Régénère tous les placements connus (nettoyage après partie,
     * spec §35 : on repose les structures sur leurs empreintes).
     *
     * NB : cela ne restaure pas les blocs détruits HORS des empreintes
     * (météorites, dragons) ; c'est un nettoyage best-effort documenté.
     */
    public void regenerateAll() {
        for (PlacementRecord record : placements) {
            Structure structure = loader.load(record.resourcePath(), record.name());
            if (structure != null) {
                placer.place(structure, record.location());
            }
        }
        plugin.getLogger().info("[Structures] Régénération de "
                + placements.size() + " placement(s).");
    }

    /** Vide l'historique des placements (après /zone delete). */
    public void clearHistory() {
        placements.clear();
    }

    /** Trace interne d'un placement effectué. */
    private record PlacementRecord(String resourcePath,
                                   String name,
                                   StructureLocation location) {
    }
}
