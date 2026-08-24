package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Détection du MINAGE (spec §18).
 *
 * Le listener détecte la casse d'un minerai et délègue TOUT le calcul
 * (points, fonte Mineur, anti-duplication) à MiningService.
 */
public class MiningListener implements Listener {

    private final XiiPlugin plugin;

    public MiningListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système minage actif ? (pattern officiel, spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isMiningListenerEnabled();
    }

    /**
     * Priorité HIGH : laisse ProtectionListener (LOW) annuler d'abord
     * les casses interdites en lobby.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!systemEnabled() || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();

        // Les spectateurs ne minent pas.
        if (plugin.getProtectionService().isSpectator(player)) {
            return;
        }

        // Délégation complète au service métier.
        plugin.getMiningService().handleBlockBreak(player, event.getBlock(), event);
    }
}
