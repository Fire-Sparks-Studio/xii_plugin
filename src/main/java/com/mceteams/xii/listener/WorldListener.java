package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.block.BlockIgniteEvent;

/**
 * Événements du MONDE (spec §22/§26).
 *
 * Responsabilités :
 * - contrôler les spawns naturels (map propre pendant la partie) ;
 * - protéger la zone d'attente des explosions ;
 * - traiter les explosions de MÉTÉORITES : dégâts joueurs via
 *   MeteoriteService + éventuels coeurs détruits via CoreService ;
 * - bloquer la météo.
 */
public class WorldListener implements Listener {

    private final XiiPlugin plugin;

    public WorldListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Spawns de créatures
    // -----------------------------------------------------------------

    /**
     * Pendant une partie active, seuls nos spawns CUSTOM sont autorisés
     * (dragons de mort subite). Les mobs vanilla restent désactivés.
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        var state = plugin.getGameManager().getState();
        if (state == com.mceteams.xii.enums.GameState.NONE
                || state == com.mceteams.xii.enums.GameState.WAITING) {
            return; // serveur normal / lobby : comportement vanilla
        }
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Explosions
    // -----------------------------------------------------------------

    /**
     * Explosion d'entité : météorites (tag xii_meteorite) ou autres.
     */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        boolean isMeteorite =
                event.getEntity() != null
                        && event.getEntity().getScoreboardTags()
                        .contains(com.mceteams.xii.service.MeteoriteService.METEORITE_TAG);

        // 1. Protection de la zone d'attente : aucun bloc retiré là-haut.
        protectWaitingLobby(event.blockList());

        // 2. Météorite : dégâts joueurs spécifiques (35-50% vie max).
        if (isMeteorite && plugin.getGameSystems().isMeteoriteListenerEnabled()) {
            plugin.getMeteoriteService().applyImpactDamage(
                    event.getLocation(), event.getEntity());
        }

        // 3. Cœurs touchés par l'explosion : destruction contrôlée.
        handleCoresInExplosion(event.blockList());
    }

    /** Explosion de blocs (lit, réservoir...) : mêmes protections. */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        protectWaitingLobby(event.blockList());
        handleCoresInExplosion(event.blockList());
    }

    /**
     * Retire les blocs de la zone d'attente d'une liste d'explosion :
     * le lobby est intouchable depuis le sol.
     */
    private void protectWaitingLobby(java.util.List<org.bukkit.block.Block> blocks) {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        double centerX = zone.getCenterX();
        double centerZ = zone.getCenterZ();
        double lobbyY = zone.getCenterY()
                + plugin.getConfigManager().getWaitingLobbyHeight();
        double lobbyRadius = 40.0; // rayon approximatif de protection du lobby

        blocks.removeIf(block -> {
            double dx = block.getX() - centerX;
            double dz = block.getZ() - centerZ;
            double dy = block.getY() - lobbyY;
            return dx * dx + dz * dz + dy * dy <= lobbyRadius * lobbyRadius;
        });
    }

    /**
     * Si l'explosion détruirait un coeur : on retire le bloc de la liste
     * et on applique la logique métier CoreService (sans attributaire).
     */
    private void handleCoresInExplosion(java.util.List<org.bukkit.block.Block> blocks) {
        if (!plugin.getGameSystems().isCoreListenerEnabled()) {
            return;
        }
        for (var iterator = blocks.iterator(); iterator.hasNext(); ) {
            var block = iterator.next();
            var team = plugin.getCoreService().getTeamByCoreBlock(block);
            if (team != null) {
                iterator.remove();          // pas de drop vanilla du beacon
                plugin.getCoreService().breakCore(team, null, false, false);
            }
        }
    }

    // -----------------------------------------------------------------
    // Météo & feu
    // -----------------------------------------------------------------

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        // Météo figée au beau fixe dès qu'une partie existe.
        if (event.toWeatherState()
                && plugin.getGameManager().getState()
                != com.mceteams.xii.enums.GameState.NONE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        if (event.toThunderState()
                && plugin.getGameManager().getState()
                != com.mceteams.xii.enums.GameState.NONE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Pas de propagation de feu naturelle en partie (doFireTick off,
        // mais on coupe aussi les allumages externes).
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                && plugin.getGameManager().getState()
                != com.mceteams.xii.enums.GameState.NONE) {
            event.setCancelled(true);
        }
    }
}
