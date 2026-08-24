package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Restrictions "lobby" et règles de protection (spec §12/§18).
 *
 * Ce listener est actif quand gameSystems.isProtectionListenerEnabled()
 * vaut true (états WAITING / COUNTDOWN / CLASS_SELECTION / ENDING) :
 * il bloque tout ce qui doit rester impossible pendant l'attente.
 *
 * Priorité LOW : s'exécute avant les listeners de gameplay
 * (MiningListener/BlockPlaceListener) pour court-circuiter proprement.
 */
public class ProtectionListener implements Listener {

    private final XiiPlugin plugin;

    public ProtectionListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système actif ? (pattern officiel, spec §33 : jamais de test de phase ici) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isProtectionListenerEnabled();
    }

    // -----------------------------------------------------------------
    // Casser / poser
    // -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getProtectionService().shouldBlockWorldInteraction(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getProtectionService().shouldBlockWorldInteraction(player)) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // PvP
    // -----------------------------------------------------------------

    /**
     * Blocage du PvP dans les états protégés. Pendant PREPARATION/COMBAT,
     * la décision fine appartient à CombatService via CombatListener.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return; // seulement joueur -> joueur ici
        }
        if (plugin.getGameManager().getState() == com.mceteams.xii.enums.GameState.PREPARATION
                || plugin.getGameManager().getState() == com.mceteams.xii.enums.GameState.COMBAT) {
            return; // gameplay : géré par CombatListener
        }
        // Lobby : PvP interdit.
        if (!plugin.getProtectionService().isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Ramasser / jeter
    // -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getProtectionService().shouldBlockPickup(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (plugin.getProtectionService().shouldBlockDrop(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Faim gelée pendant toute partie active (confort compétitif)
    // -----------------------------------------------------------------

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true); // barre de faim verrouillée au max
        }
    }
}
