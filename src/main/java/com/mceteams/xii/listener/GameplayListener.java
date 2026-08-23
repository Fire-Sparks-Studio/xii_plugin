package com.mceteams.xii.listener;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.manager.GameManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class GameplayListener implements Listener {
    private final GameManager gameManager;

    public GameplayListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (gameManager.getState() == GameState.WAITING) {
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (gameManager.getState() != GameState.WAITING) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (gameManager.getState() == GameState.WAITING) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (gameManager.getState() == GameState.WAITING) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setAmount(event.getAmount() * 0.3);
        }
    }
}
