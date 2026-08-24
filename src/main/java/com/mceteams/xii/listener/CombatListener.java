package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Surveillance du COMBAT (spec §18).
 *
 * Le listener détecte les coups joueur->joueur, délègue le calcul à
 * CombatService (classes + MORE_DAMAGE) puis applique les dégâts finaux.
 * Aucun calcul ici : extraction + délégation (spec §2).
 */
public class CombatListener implements Listener {

    private final XiiPlugin plugin;

    public CombatListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système combat actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isCombatListenerEnabled();
    }

    /**
     * Coup joueur -> joueur : calcul des dégâts via CombatService.
     * Priorité HIGH : après ProtectionListener/TeamListener qui ont déjà
     * annulé les coups illégaux.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // Un spectateur ne peut PAS frapper (invisible mais parfois
        // en survie : on bloque à la source).
        if (event.getDamager() instanceof Player damagerPlayer
                && plugin.getProtectionService().isSpectator(damagerPlayer)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        // Délégation complète du calcul au service.
        boolean cancel =
                plugin.getCombatService().handleDamage(attacker, victim, event.getDamage());
        if (cancel) {
            event.setCancelled(true);
            return;
        }

        // Application des dégâts recalculés (classe + sous-phase).
        double computed = plugin.getCombatService().consumePendingDamage(victim);
        if (computed > 0) {
            event.setDamage(computed);
        }
    }

    /**
     * Fall damage : annulé pour la classe AGILE (spec §31).
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getClassService().shouldCancelFallDamage(player)) {
            event.setCancelled(true); // Agile : aucun fall damage
        }
    }
}
