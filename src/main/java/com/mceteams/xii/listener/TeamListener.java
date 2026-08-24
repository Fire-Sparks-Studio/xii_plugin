package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Filet de sécurité "équipe" (spec §6).
 *
 * L'équipe Bukkit désactive déjà le friendly fire, mais ce listener
 * garantit en dernier recours qu'aucun dégât joueur->coéquipier ne
 * passe (ex : projectiles indirects). La décision fine du PvP est
 * déléguée à ProtectionService/CombatService.
 */
public class TeamListener implements Listener {

    private final XiiPlugin plugin;

    public TeamListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système combat actif ? Sinon, ProtectionListener gère déjà tout. */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isCombatListenerEnabled();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        // Double vérification friendly fire : si ProtectionService
        // interdit ce coup, on annule aussi ici.
        if (!plugin.getProtectionService().isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
        }
    }
}
