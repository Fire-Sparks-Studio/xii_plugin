package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.DeathCause;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Détection des MORTS de joueurs (spec §19/§29).
 *
 * Le listener qualifie la cause (tueur ? environnement ?) puis délègue
 * TOUT à DeathService : enregistrement, points, streak, titre,
 * spectateur, respawn. Aucune logique ici.
 */
public class DeathListener implements Listener {

    private final XiiPlugin plugin;

    public DeathListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système mort actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isDeathListenerEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player victim = event.getEntity();

        // Qualification de la cause.
        DeathCause cause = DeathCause.OTHER;
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            // Le dernier attaquant enregistré correspond-il au tueur vanilla ?
            var data = plugin.getPlayerManager().getData(victim);
            if (killer.getUniqueId().equals(data.getLastDamager())) {
                cause = DeathCause.PLAYER;
            }
        }

        // Message de mort vanilla masqué : le plugin diffuse les siens.
        event.deathMessage(null);

        // Délégation complète du traitement métier.
        plugin.getDeathService().handleDeath(victim, cause, killer);
    }

    /**
     * Dégâts finaux joueur->joueur MONITOR : sert uniquement au debug
     * (aucun calcul, aucune modification).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFinalDamage(EntityDamageByEntityEvent event) {
        // Intentionnellement vide : point d'observation pour le debug.
    }
}
