package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Détection des attaques sur les COEURS d'équipe (spec §28).
 *
 * Le listener détecte la tentative de casse du bloc coeur et délègue
 * à CoreService (points, élimination, annonces). La destruction via
 * explosion est gérée par WorldListener.
 */
public class CoreListener implements Listener {

    private final XiiPlugin plugin;

    public CoreListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système coeur actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isCoreListenerEnabled();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player player = event.getPlayer();

        // Le bloc visé est-il un coeur ?
        var team = plugin.getCoreService().getTeamByCoreBlock(event.getBlock());
        if (team == null) {
            return; // pas un coeur : rien à faire ici
        }

        // Le bloc n'est PAS cassé par la voie vanilla : le service
        // gère lui-même l'état et le retrait physique du bloc.
        event.setCancelled(true);

        // Un membre ne peut pas détruire son propre coeur.
        var breakerTeam = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (breakerTeam != null && breakerTeam == team) {
            com.mceteams.xii.util.MessageUtil.send(player,
                    "§cVous ne pouvez pas détruire votre propre cœur !");
            return;
        }

        // Délégation complète : points + annonce + éventuelle élimination.
        plugin.getCoreService().breakCore(team, player, false);
    }

    /**
     * Sécurité MONITOR : si le coeur a été cassé par un autre chemin
     * (créatif op, glitch), on force la logique métier.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        if (!event.isCancelled() && systemEnabled()) {
            var team = plugin.getCoreService().getTeamByCoreBlock(event.getBlock());
            if (team != null) {
                // Cassé hors de notre contrôle : destruction attribuée au joueur.
                plugin.getCoreService().breakCore(team, event.getPlayer(), false);
            }
        }
    }
}
