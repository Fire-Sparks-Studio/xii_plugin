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
            return;
        }
        // Un spectateur ne casse JAMAIS de bloc, même en pleine partie.
        if (plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        // Règles de bases en pleine partie : seuls les propriétaires
        // modifient leur base (le coeur a sa propre logique).
        var state = plugin.getGameManager().getState();
        if ((state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT)
                && !plugin.getProtectionService()
                        .canModifyBlock(player, event.getBlock())) {
            event.setCancelled(true);
            com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                    "§c✘ Vous ne pouvez rien casser dans cette base.");
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
            return;
        }
        // Un spectateur ne pose JAMAIS de bloc.
        if (plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        var state = plugin.getGameManager().getState();
        if ((state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT)
                && !plugin.getProtectionService()
                        .canModifyBlock(player, event.getBlock())) {
            event.setCancelled(true);
            com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                    "§c✘ Vous ne pouvez rien poser dans cette base.");
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
        // Un spectateur ne ramasse rien, même en pleine partie.
        if (plugin.getProtectionService().shouldBlockPickup(player)
                || plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // Un spectateur ne jette rien.
        if (plugin.getProtectionService().shouldBlockDrop(event.getPlayer())
                || plugin.getProtectionService().isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Faim gelée + inventaires interdits aux spectateurs
    // -----------------------------------------------------------------

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);       // barre de faim verrouillée
            player.setSaturation(0f);       // mais SANS saturation
        }
    }

    /**
     * Un spectateur ne peut PAS ouvrir de conteneur (coffres, fours...).
     * Nos propres GUIs passent par openInventory et déclenchent aussi
     * cet événement : on laisse passer celles du plugin.
     */
    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.getProtectionService().isSpectator(player)) {
            return;
        }
        // Nos GUIs ont un holder plugin => autorisées.
        if (event.getInventory().getHolder() instanceof org.bukkit.inventory.InventoryHolder holder
                && holder.getClass().getName().startsWith("com.mceteams.xii")) {
            return;
        }
        event.setCancelled(true);
    }
}
