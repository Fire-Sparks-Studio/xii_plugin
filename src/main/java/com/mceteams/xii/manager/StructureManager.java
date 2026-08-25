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

    private static final String LOBBY_PATH = "structures/waiting/waiting_lobby.nbt";
    private static final String LOBBY_NAME = "waiting_lobby";

    private final XiiPlugin plugin;
    private final StructureLoader loader;
    private final StructurePlacer placer;
    /** Pose de secours indépendante du StructureManager. */
    private final com.mceteams.xii.structure.RawTemplatePlacer rawPlacer;

    /** Placements effectués (pour une éventuelle régénération). */
    private final List<PlacementRecord> placements = new ArrayList<>();

    /**
     * Positions de TOUS les blocs posés manuellement (poseur de secours) :
     * permet à /zone delete de retirer les structures du monde.
     */
    private final List<Location> placedBlocks = new ArrayList<>();

    /**
     * Blocs du LOBBY uniquement : retirés quand la partie se lance,
     * reposés quand tout le monde est téléporté de retour (fin/stop).
     */
    private final List<Location> lobbyBlocks = new ArrayList<>();

    public StructureManager(XiiPlugin plugin) {
        this.plugin = plugin;
        this.loader = new StructureLoader(plugin);
        this.placer = new StructurePlacer();
        this.rawPlacer = new com.mceteams.xii.structure.RawTemplatePlacer(plugin);
    }

    /** Ancre BRUTE (config uniquement, sans auto-centrage). */
    private Location baseAnchor(GameZone zone) {
        Location center = zone.getCenterLocation();
        if (center == null) {
            return null;
        }
        return new Location(
                center.getWorld(),
                center.getBlockX() + plugin.getConfigManager().getLobbyAnchorX(),
                center.getBlockY() + plugin.getConfigManager().getWaitingLobbyHeight()
                        + plugin.getConfigManager().getLobbyAnchorY(),
                center.getBlockZ() + plugin.getConfigManager().getLobbyAnchorZ());
    }

    /**
     * Ancre du lobby : base + AUTO-CENTRAGE - l'ancre est décalée pour que
     * le CENTRE GÉOMÉTRIQUE des blocs du template tombe sur la colonne du
     * centre de la zone (l'origine d'un .nbt est rarement le centre visuel
     * du bâtiment). Désactivable via zone.waiting-lobby-auto-center: false.
     */
    public Location lobbyAnchor(GameZone zone) {
        Location anchor = baseAnchor(zone);
        if (anchor == null) {
            return null;
        }

        if (plugin.getConfigManager().isLobbyAutoCenter()) {
            java.io.File cached = loader.ensureCached(LOBBY_PATH, LOBBY_NAME);
            if (cached != null) {
                double[] localCenter =
                        rawPlacer.localCenterXZ(cached.toPath());
                if (localCenter != null) {
                    anchor.setX(anchor.getX() - localCenter[0]);
                    anchor.setZ(anchor.getZ() - localCenter[1]);
                }
            }
        }
        return anchor;
    }

    /**
     * Point d'apparition des joueurs dans le lobby :
     * - auto-center ON : colonne CENTRALE de la zone (le milieu du
     *   bâtiment), Y posé automatiquement sur le plancher (offset Y = 0) ;
     * - auto-center OFF : ancien comportement ancre + offsets.
     */
    public Location lobbySpawn(GameZone zone) {
        Location center = zone.getCenterLocation();
        if (center == null) {
            return null;
        }
        var cfg = plugin.getConfigManager();

        double x;
        double z;
        if (cfg.isLobbyAutoCenter()) {
            // Le bâtiment est centré => le spawn est la colonne centrale.
            x = center.getBlockX() + 0.5 + cfg.getLobbySpawnOffsetX();
            z = center.getBlockZ() + 0.5 + cfg.getLobbySpawnOffsetZ();
        } else {
            Location anchor = lobbyAnchor(zone);
            if (anchor == null) {
                return null;
            }
            x = anchor.getX() + cfg.getLobbySpawnOffsetX() + 0.5;
            z = anchor.getZ() + cfg.getLobbySpawnOffsetZ() + 0.5;
        }

        // Y : plancher automatique (plus bas bloc non-air + 1), puis les
        // offsets configurables s'AJOUTENT par-dessus (fine-tuning).
        double yBase = baseAnchor(zone).getY();
        if (cfg.isLobbyAutoCenter()) {
            java.io.File cached = loader.ensureCached(LOBBY_PATH, LOBBY_NAME);
            if (cached != null) {
                Integer floorY = rawPlacer.localFloorY(cached.toPath());
                if (floorY != null) {
                    yBase += floorY + 1.0;
                }
            }
        }
        double y = yBase + cfg.getLobbySpawnOffsetY();
        return new Location(center.getWorld(), x, y, z);
    }

    /**
     * Place la zone d'attente au-dessus du centre de la zone (spec §4),
     * auto-centrée sur la colonne centrale.
     *
     * @return true si la structure a été placée.
     */
    public boolean placeWaitingLobby(GameZone zone) {
        Location anchor = lobbyAnchor(zone);
        if (anchor == null) {
            return false;
        }

        // L'ancrage est posé en hauteur, sans rotation.
        return place(LOBBY_PATH,
                LOBBY_NAME,
                new StructureLocation(anchor.getWorld(),
                        anchor.getBlockX(),
                        anchor.getBlockY(),
                        anchor.getBlockZ(),
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
     *
     * STRATÉGIE EN DEUX TEMPS :
     * 1. API officielle StructureManager (comportement variable selon les
     *    versions de Paper) ;
     * 2. SECOURS : pose MANUELLE du .nbt brut via RawTemplatePlacer
     *    (lecture du format vanilla + setBlockData) - indépendante de
     *    l'API et donc toujours fonctionnelle.
     */
    private boolean place(String resourcePath, String structureName,
                          StructureLocation location) {
        return place(resourcePath, structureName, location, true);
    }

    /**
     * @param track false lors d'une RÉGÉNÉRATION : on rejoue un placement
     *              déjà enregistré sans l'ajouter une 2e fois à
     *              l'historique (sinon croissance infinie + CME).
     */
    private boolean place(String resourcePath, String structureName,
                          StructureLocation location, boolean track) {
        Structure structure = loader.load(resourcePath, structureName);
        if (structure != null && placer.place(structure, location)) {
            if (track) {
                placements.add(new PlacementRecord(resourcePath, structureName, location));
            }
            plugin.getLogger().info("[Structures] Placée : " + structureName
                    + " -> " + location);
            return true;
        }

        // Secours : application directe du .nbt (asynchrone).
        java.io.File cached =
                loader.ensureCached(resourcePath, structureName);
        if (cached == null) {
            return false; // ressource absente : non bloquant (fournie par le dev)
        }
        rawPlacer.placeAsync(cached.toPath(), location, written -> {
            placedBlocks.addAll(written);
            if (structureName.equalsIgnoreCase(LOBBY_NAME)) {
                lobbyBlocks.addAll(written);
            }
        });
        if (track) {
            placements.add(new PlacementRecord(resourcePath, structureName, location));
        }
        return true;
    }

    /**
     * Retire UNIQUEMENT le lobby d'attente (appelé au lancement de la
     * partie : les joueurs sont téléportés aux bases). Les bases et
     * donjons restent en place.
     */
    public void removeLobbyBlocks() {
        if (lobbyBlocks.isEmpty()) {
            return;
        }
        int removed = 0;
        for (Location location : lobbyBlocks) {
            var block = location.getBlock();
            if (!block.getType().isAir()) {
                block.setType(org.bukkit.Material.AIR, false);
                removed++;
            }
        }
        lobbyBlocks.clear();
        plugin.getLogger().info("[Structures] Lobby retiré (" + removed
                + " bloc(s)).");
    }

    /**
     * Retire du monde TOUS les blocs posés manuellement (poseur de
     * secours) puis vide l'historique : utilisé par /zone delete.
     *
     * NB best-effort : un bloc détruit/modifié pendant la partie est
     * simplement remis à AIR à sa position d'origine.
     */
    public void removeAllPlacedBlocks() {
        int removed = 0;
        for (Location location : placedBlocks) {
            var block = location.getBlock();
            if (!block.getType().isAir()) {
                block.setType(org.bukkit.Material.AIR, false);
                removed++;
            }
        }
        placedBlocks.clear();
        if (removed > 0) {
            plugin.getLogger().info("[Structures] " + removed
                    + " bloc(s) de structure retiré(s).");
        }
    }

    /**
     * Régénère tous les placements connus (nettoyage après partie,
     * spec §35 : on repose les structures sur leurs empreintes).
     *
     * NB : cela ne restaure pas les blocs détruits HORS des empreintes
     * (météorites, dragons) ; c'est un nettoyage best-effort documenté.
     */
    public void regenerateAll() {
        // COPIE obligatoire : place() modifie sinon la liste pendant
        // l'itération (ConcurrentModificationException) - et on rejoue
        // chaque placement avec track=false pour ne pas dupliquer
        // l'historique à chaque fin de partie.
        List<PlacementRecord> snapshot = new ArrayList<>(placements);
        for (PlacementRecord record : snapshot) {
            place(record.resourcePath(), record.name(), record.location(), false);
        }
        plugin.getLogger().info("[Structures] Régénération de "
                + snapshot.size() + " placement(s).");
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
