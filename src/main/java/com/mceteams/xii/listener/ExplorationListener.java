package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Détection de l'EXPLORATION (spec §18).
 *
 * Le listener détecte les changements de chunk et délègue à
 * ExplorationService (première visite => points).
 */
public class ExplorationListener implements Listener {

    private final XiiPlugin plugin;

    public ExplorationListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système exploration actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isExplorationListenerEnabled();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // Optimisation : on ignore les mouvements sans changement de bloc.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        var data = plugin.getPlayerManager().getData(player);
        // Seuls les joueurs vivants explorent (les morts sont invisibles/tp).
        if (!data.isAlive() || data.isSpectator()) {
            return;
        }

        // Délégation : le service décide si ce chunk est nouveau et attribue.
        plugin.getExplorationService().handleMove(
                player,
                event.getFrom().getBlockX() >> 4, event.getFrom().getBlockZ() >> 4,
                event.getTo().getBlockX() >> 4, event.getTo().getBlockZ() >> 4);
    }
}
