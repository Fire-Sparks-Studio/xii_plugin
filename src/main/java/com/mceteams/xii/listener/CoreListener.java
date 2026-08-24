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
 * Actif pendant TOUTE partie en cours (préparation + combat) :
 * - un membre ne peut JAMAIS casser son propre coeur (message) ;
 * - en PRÉPARATION, les ennemis sont de toute façon bloqués par les
 *   règles de base (ProtectionListener LOW) => aucun accès ;
 * - en COMBAT, un ennemi qui casse déclenche la destruction complète.
 *
 * La destruction via explosion est gérée par WorldListener.
 */
public class CoreListener implements Listener {

    private final XiiPlugin plugin;

    public CoreListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Actif dès qu'une partie existe (préparation ou combat). */
    private boolean systemEnabled() {
        var state = plugin.getGameManager().getState();
        return state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!systemEnabled() || event.isCancelled()) {
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

        // Un membre ne peut JAMAIS détruire son propre coeur.
        var breakerTeam = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (breakerTeam != null && breakerTeam == team) {
            com.mceteams.xii.util.MessageUtil.send(player,
                    "§c✘ Vous ne pouvez pas détruire votre propre coeur !");
            com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                    "§c✘ Coeur intouchable !");
            return;
        }

        // Délégation complète : points + annonces + éventuelle élimination.
        plugin.getCoreService().breakCore(team, player, false, false);
    }
}
