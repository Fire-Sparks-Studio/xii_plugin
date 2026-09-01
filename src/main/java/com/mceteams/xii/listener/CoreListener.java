package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Détection des attaques sur les CRISTAUX des bases (spec §28).
 *
 * Chaque base a 5 cristaux : 4 dans les tours (boucliers du coeur) et
 * 1 au centre (le coeur). Le coeur ne peut être détruit qu'une fois les
 * 4 tours tombées.
 *
 * Actif pendant TOUTE partie en cours (préparation + combat) :
 * - un membre ne peut JAMAIS casser les cristaux de sa propre équipe
 *   (message) ;
 * - en PRÉPARATION, les ennemis sont de toute façon bloqués par les
 *   règles de base (ProtectionListener LOW) => aucun accès ;
 * - en COMBAT, un ennemi qui casse déclenche la logique appropriée :
 *     . cristal de TOUR   => breakTowerCrystal (petits points) ;
 *     . cristal CENTRAL   => breakCore, SAUF si les tours sont debout
 *       (message "le coeur est protégé").
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

        // Le bloc visé est-il un cristal (tour ou centre) ?
        var coreService = plugin.getCoreService();
        GameTeam team = coreService.getTeamByCrystalBlock(event.getBlock());
        if (team == null) {
            return; // pas un cristal : rien à faire ici
        }

        // Le bloc n'est PAS cassé par la voie vanilla : le service
        // gère lui-même l'état et le retrait physique du bloc.
        event.setCancelled(true);

        // Un membre ne peut JAMAIS détruire les cristaux de son équipe.
        var breakerTeam = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (breakerTeam != null && breakerTeam == team) {
            MessageUtil.send(player,
                    "§c✘ Vous ne pouvez pas détruire les cristaux de votre équipe !");
            MessageUtil.sendActionBar(player,
                    "§c✘ Cristaux intouchables !");
            return;
        }

        // Cristal de TOUR : destruction individuelle (petits points).
        if (coreService.isTowerCrystal(event.getBlock())) {
            coreService.breakTowerCrystal(team, event.getBlock(), player);
            return;
        }

        // Cristal CENTRAL (coeur) : protégé tant que des tours sont debout.
        if (coreService.isHeartShielded(team.getColor())) {
            MessageUtil.send(player,
                    "§c✘ Le cœur est protégé : détruisez d'abord les 4 cristaux des tours !");
            MessageUtil.sendActionBar(player,
                    "§c✘ Tours non détruites !");
            return;
        }

        // Délégation complète : points + annonces + éventuelle élimination.
        coreService.breakCore(team, player, false, false);
    }
}