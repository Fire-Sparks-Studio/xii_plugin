package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.model.GameZone;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logique métier d'EXPLORATION (spec §18/§32).
 *
 * Règle simple : la première visite d'un chunk situé dans la zone de
 * jeu rapporte des points EXPLORATION. Les chunks déjà visités ne
 * rapportent plus rien.
 */
public class ExplorationService {

    private final XiiPlugin plugin;

    /** Chunks visités par joueur : uuid -> ensemble de clés de chunk. */
    private final Map<UUID, java.util.Set<Long>> visitedChunks =
            new ConcurrentHashMap<>();

    public ExplorationService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Traite un déplacement de joueur. Appelé par ExplorationListener
     * sur PlayerMoveEvent (optimisé : on ne travaille que si le chunk
     * a changé).
     *
     * @return true si de nouveaux points ont été attribués.
     */
    public boolean handleMove(Player player, int fromChunkX, int fromChunkZ,
                              int toChunkX, int toChunkZ) {
        if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) {
            return false; // même chunk => rien à faire
        }

        GameZone zone = plugin.getZoneManager().getZone();
        if (zone == null || player.getLocation() == null
                || !zone.contains(player.getLocation())) {
            return false; // hors zone de jeu : pas d'exploration comptée
        }

        long chunkKey = com.mceteams.xii.util.LocationUtil.chunkKey(toChunkX, toChunkZ);
        java.util.Set<Long> visited = visitedChunks.computeIfAbsent(
                player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        // Première visite de ce chunk => points.
        if (visited.add(chunkKey)) {
            plugin.getPointService().award(player,
                    PointCategory.EXPLORATION,
                    plugin.getConfigManager().getExplorationPoints(),
                    "chunk découvert");
            return true;
        }
        return false;
    }

    /** Nouvelle partie : on oublie les chunks explorés. */
    public void resetMatchState() {
        visitedChunks.clear();
    }
}
