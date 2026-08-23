package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class DynamicBarManager {
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;
    private BukkitTask actionBarTask;

    public DynamicBarManager(GameManager gameManager, PlayerDataManager playerDataManager) {
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
    }

    public void startWaitingBar(JavaPlugin plugin) {
        cancelActionBar();
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            GameState state = gameManager.getState();
            if (state != GameState.WAITING) return;

            for (Player online : Bukkit.getOnlinePlayers()) {
                Lang lang = playerDataManager.getLang(online);
                online.sendActionBar(Component.text(Messages.WAITING_FOR_START.get(lang)));
            }
        }, 0L, 20L);
    }

    public void startPreparationBar(JavaPlugin plugin) {
        cancelActionBar();
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            GameState state = gameManager.getState();
            if (state != GameState.PREPARATION) return;

            for (Player online : Bukkit.getOnlinePlayers()) {
                Lang lang = playerDataManager.getLang(online);
                int day = gameManager.getDayManager().getCurrentDay();
                online.sendActionBar(Component.text(Messages.PREPARATION_INFO.get(lang, day)));
            }
        }, 0L, 20L);
    }

    public void startCombatBar(JavaPlugin plugin) {
        cancelActionBar();
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            GameState state = gameManager.getState();
            if (state != GameState.COMBAT) return;

            for (Player online : Bukkit.getOnlinePlayers()) {
                Lang lang = playerDataManager.getLang(online);
                int day = gameManager.getDayManager().getCurrentDay();
                online.sendActionBar(Component.text(Messages.COMBAT_INFO.get(lang, day)));
            }
        }, 0L, 20L);
    }

    public void cancelActionBar() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    public void clearActionBar(Player player) {
        player.sendActionBar(Component.empty());
    }

    public void clearAllActionBars() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendActionBar(Component.empty());
        }
    }
}
