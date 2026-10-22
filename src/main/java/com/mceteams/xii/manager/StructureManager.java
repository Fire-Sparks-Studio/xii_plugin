package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.structure.StructureLocation;
import com.mceteams.xii.structure.StructurePlacer;
import com.mceteams.xii.structure.StructureLoader;
import org.bukkit.Location;
import org.bukkit.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** Template UNIQUE partagé par les 4 bases d'équipe (bases.nbt). */
    private static final String BASES_PATH = "structures/bases/bases.nbt";
    private static final String BASES_NAME = "bases";

    private final XiiPlugin plugin;
    private final StructureLoader loader;
    private final StructurePlacer placer;
    /** Pose de secours indépendante du StructureManager. */
    private final com.mceteams.xii.structure.RawTemplatePlacer rawPlacer;

    /** Placements effectués (pour une éventuelle régénération). */
    private final List<PlacementRecord> placements = new ArrayList<>();
    /** Clés uniques des placements (anti-croissance de l'historique). */
    private final Set<String> placementKeys = new LinkedHashSet<>();

    /**
     * Positions de TOUS les blocs posés manuellement (poseur de secours) :
     * permet à /zone delete de retirer les structures du monde.
     */
    private final Set<Location> placedBlocks = new LinkedHashSet<>();

    /**
     * Blocs du LOBBY uniquement : retirés quand la partie se lance,
     * reposés quand tout le monde est téléporté de retour (fin/stop).
     */
    private final Set<Location> lobbyBlocks = new LinkedHashSet<>();

    /**
     * Clés "monde:x:y:z" de TOUS les blocs de structure posés : sert à la
     * protection (blocs de structure incassables/irremplaçables, spec §18).
     */
    private final Set<String> structureKeys = new LinkedHashSet<>();

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
     * Place une base d'équipe à l'ancrage donné, avec la rotation voulue.
     *
     * Les 4 emplacements de base partagent LE MÊME template (bases.nbt) :
     * seule la rotation diffère (la base regarde vers le centre de map).
     *
     * NB : le remblai herbeux (fill grass sous le plancher, style
     * village) est appliqué par le poseur de secours, de même que
     * l'enregistrement des blocs de structure (protection).
     *
     * RÈGLE UTILISATEUR : les REPÈRES (lainages) sont appliqués APRÈS
     * l'écriture de la structure (sinon la pose des blocs du .nbt les
     * écraserait). Ils sont NEUTRALISÉS d'équipe : c'est
     * BaseManager#assignBasesToTeams (au LANCEMENT de la partie) qui
     * rattachera les repères à l'équipe propriétaire de l'emplacement.
     *
     * @param anchor   point d'ancrage calculé par BaseManager
     * @param rotation rotation de la structure
     * @param onWritten reçoit les repères une fois la structure écrite
     */
    public boolean placeBase(Location anchor,
                             com.mceteams.xii.structure.StructureRotation rotation,
                             java.util.function.Consumer<
                                     com.mceteams.xii.model.BaseMarkers>
                                     onWritten) {
        var location = StructureLocation.of(anchor, rotation);
        java.io.File cached = loader.ensureCached(BASES_PATH, BASES_NAME);
        return place(BASES_PATH, BASES_NAME, location, true, written -> {
                    structureKeys.addAll(keys(written));
                    // RÈGLE UTILISATEUR : les cellules VIDES du gabarit
                    // (AIR du template) sont remises à AIR dans le monde,
                    // pour que rien (arbre, butte) ne dépasse à l'intérieur
                    // de la base. Les structure_void posés par la structure
                    // sont ensuite convertis en air + mémorisés par
                    // MarkerManager (l'espace vide du gabarit reste vide).
                    if (cached != null) {
                        rawPlacer.clearTemplateEmptyCells(cached.toPath(),
                                location);
                    }
                    // RÈGLE UTILISATEUR : applique les REPÈRES (lainages)
                    // posés par le développeur dans l'empreinte de la base
                    // (sol rouge → herbe, portillons bleu ciel → spruce,
                    // PNJ vert/jaune → emplacements, laine violette → spawn,
                    // bannières blanches → à recolorer). On traite APRÈS
                    // l'écriture de la structure pour ne pas être écrasé
                    // par la pose des blocs du .nbt.
                    com.mceteams.xii.model.BaseMarkers markers =
                            plugin.getMarkerManager().processBase(anchor,
                                    rotation);
                    if (onWritten != null) {
                        onWritten.accept(markers);
                    }
                },
                true);
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
                StructureLocation.of(anchor, rotation),
                true,
                written -> structureKeys.addAll(keys(written)),
                false);
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
        return place(resourcePath, structureName, location, true, null, false);
    }

    /**
     * @param track false lors d'une RÉGÉNÉRATION : on rejoue un placement
     *              déjà enregistré sans l'ajouter une 2e fois à
     *              l'historique (sinon croissance infinie + CME).
     */
    private boolean place(String resourcePath, String structureName,
                          StructureLocation location, boolean track) {
        return place(resourcePath, structureName, location, track, null, false);
    }

    /**
     * @param onWritten reçoit les blocs POSÉS une fois le placement
     *                  effectif (permet d'enregistrer les blocs de
     *                  structure pour la protection).
     * @param baseGrassFill remblai de terre/grass sous le plancher des
     *                  bases (style villages).
     */
    private boolean place(String resourcePath, String structureName,
                          StructureLocation location, boolean track,
                          java.util.function.Consumer<List<Location>> onWritten,
                          boolean baseGrassFill) {
        Structure structure = loader.load(resourcePath, structureName);
        if (structure != null && placer.place(structure, location)) {
            trackPlacement(track, resourcePath, structureName, location);
            if (onWritten != null) {
                onWritten.accept(List.of());
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
        rawPlacer.placeAsync(cached.toPath(), location, baseGrassFill, written -> {
            placedBlocks.addAll(written);
            if (structureName.equalsIgnoreCase(LOBBY_NAME)) {
                lobbyBlocks.addAll(written);
            }
            if (onWritten != null) {
                onWritten.accept(written);
            }
        });
        trackPlacement(track, resourcePath, structureName, location);
        return true;
    }

    /** Enregistre un placement (dédoublonné : clé = fichier + position). */
    private void trackPlacement(boolean track, String resourcePath,
                                String structureName,
                                StructureLocation location) {
        if (!track) {
            return;
        }
        String key = placementKey(resourcePath, location);
        if (placementKeys.add(key)) {
            placements.add(new PlacementRecord(resourcePath, structureName, location));
        }
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
        structureKeys.clear();
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
        placementKeys.clear();
    }

    /**
     * Retire la STRUCTURE DE BASE d'un modèle : purge les blocs de
     * structure enregistrés dans son empreinte et OUBLIE son placement.
     *
     * La remise en état du terrain (blocs à AIR / restauration) est faite
     * par l'appelant ({@link com.mceteams.xii.manager.BaseManager}) à
     * partir de l'instantané de terrain pris avant la pose ; ici on ne
     * s'occupe que des traces internes de {@code StructureManager} pour
     * qu'une régénération de fin de partie ne repose pas la base.
     *
     * @param base modèle de base à oublier
     * @return nombre de clés de structure purgées
     */
    public int removeBasePlacement(GameBase base) {
        if (base == null) {
            return 0;
        }
        return removeBasePlacement(base.getAnchor());
    }

    /**
     * Retire la STRUCTURE DE BASE d'un ANCRE : purge les blocs de
     * structure enregistrés dans son empreinte et OUBLIE son placement.
     *
     * La remise en état du terrain (blocs à AIR / restauration) est faite
     * par l'appelant ({@link com.mceteams.xii.manager.BaseManager}) à
     * partir de l'instantané de terrain pris avant la pose ; ici on ne
     * s'occupe que des traces internes de {@code StructureManager} pour
     * qu'une régénération de fin de partie ne repose pas la base.
     *
     * @param anchor ancre de la base à oublier
     * @return nombre de clés de structure purgées
     */
    public int removeBasePlacement(Location anchor) {
        if (anchor == null
                || anchor.getWorld() == null) {
            return 0;
        }
        Location base = anchor;
        String worldName = base.getWorld().getName();
        int ax = base.getBlockX();
        int ay = base.getBlockY();
        int az = base.getBlockZ();
        // Emission verticale large (la base peut s'étendre sous/sur l'ancre).
        int minY = ay - 32;
        int maxY = ay + 96;

        // 1. Purge des clés de structure dans l'empreinte 35x35.
        // NB : le nom du monde peut lui-même contenir ':' (ex : "xii:monde") :
        // on parse RELATIVEMENT au préfixe connu plutôt qu'avec un
        // split(':') naïf (sinon la clé serait ignorée ou mal découpée).
        String prefix = worldName + ":";
        int purged = 0;
        java.util.Iterator<String> it = structureKeys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (!key.startsWith(prefix)) {
                continue;
            }
            int[] xyz = parseBlockKey(key, prefix.length());
            if (xyz == null) {
                continue;
            }
            if (xyz[0] >= ax - 1 && xyz[0] <= ax + GameBase.SIZE
                    && xyz[2] >= az - 1 && xyz[2] <= az + GameBase.SIZE
                    && xyz[1] >= minY && xyz[1] <= maxY) {
                it.remove();
                purged++;
            }
        }

        // 2. Blocs posés par le poseur de secours (mêmes coordonnées).
        placedBlocks.removeIf(location -> location.getWorld() != null
                && location.getWorld().getName().equals(worldName)
                && location.getBlockX() >= ax - 1
                && location.getBlockX() <= ax + GameBase.SIZE
                && location.getBlockZ() >= az - 1
                && location.getBlockZ() <= az + GameBase.SIZE
                && location.getBlockY() >= minY
                && location.getBlockY() <= maxY);

        // 3. Historique de placement : on oublie ce placement de base pour
        // que la régénération de fin de partie ne la repose pas.
        String placementPrefix = BASES_PATH + "|" + worldName
                + "|" + ax + "|" + ay + "|" + az + "|";
        placements.removeIf(record -> placementKey(record.resourcePath(),
                record.location()).startsWith(placementPrefix));
        placementKeys.removeIf(key -> key.startsWith(placementPrefix));

        plugin.getLogger().info("[Structures] Base oubliée à l'ancre ("
                + ax + ", " + ay + ", " + az + ") : " + purged
                + " clé(s) de structure purgée(s).");
        return purged;
    }

    // -----------------------------------------------------------------
    // Protection des blocs de structure
    // -----------------------------------------------------------------

    /** Clé canonique "monde:x:y:z" d'un bloc. */
    private String blockKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX()
                + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    /**
     * Décode une clé de bloc "monde:x:y:z" en {x, y, z}, en partant de la
     * fin de la chaîne (le nom du monde peut contenir ':').
     *
     * @param key    clé complète
     * @param offset index de départ de la partie "x:y:z" (longueur du
     *               préfixe monde + séparateur)
     * @return les coordonnées, ou null si la clé est mal formée
     */
    private static int[] parseBlockKey(String key, int offset) {
        String rest = key.substring(offset);
        String[] parts = rest.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Clé canonique d'un placement (fichier + ancre + rotation). */
    private String placementKey(String resourcePath, StructureLocation location) {
        return resourcePath + "|" + location.getWorld().getName()
                + "|" + location.getX() + "|" + location.getY()
                + "|" + location.getZ() + "|" + location.getRotation();
    }

    private java.util.Collection<String> keys(List<Location> written) {
        List<String> keys = new ArrayList<>(written.size());
        for (Location location : written) {
            if (location.getWorld() != null) {
                keys.add(blockKey(location));
            }
        }
        return keys;
    }

    /**
     * Ce bloc fait-il partie d'une STRUCTURE posée par le plugin ?
     * (blocs inviolables : incassables, irremplaçables, spec §18).
     */
    public boolean isStructureBlock(Location location) {
        return location != null && location.getWorld() != null
                && structureKeys.contains(blockKey(location));
    }

    /** Trace interne d'un placement effectué. */
    private record PlacementRecord(String resourcePath,
                                   String name,
                                   StructureLocation location) {
    }
}
