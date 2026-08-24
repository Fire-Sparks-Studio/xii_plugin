package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Surveillance du PLACEMENT de blocs (spec §18).
 *
 * Deux rôles :
 * 1. ProtectionListener (LOW) annule la pose interdite en lobby ;
 * 2. ICI (blockPlaceEnabled) : enregistrement des minerais POSÉS pour
 *    l'anti-duplication des points (silk touch => aucun point au break).
 */
public class BlockPlaceListener implements Listener {

    private final XiiPlugin plugin;

    public BlockPlaceListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système anti-abus actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isBlockPlaceListenerEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!systemEnabled() || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getProtectionService().isSpectator(player)) {
            return; // un spectateur ne devrait jamais poser
        }

        // Seuls les minerais suivis intéressent l'anti-duplication.
        if (plugin.getConfigManager().isTrackedOre(event.getBlock().getType())) {
            plugin.getMiningService().trackPlacedOre(event.getBlock());
        }
    }
}
