package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SetupManager {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final PointService pointService;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;
    private boolean isSetup = false;

    public SetupManager(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager, PointService pointService, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.pointService = pointService;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public void setup() {
        if (isSetup) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            hotbarManager.giveHotbar(player);
        }

        isSetup = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_START);
            player.sendMessage(Messages.SETUP_INITIALIZED.get(playerDataManager.getLang(player)));
        }
    }

    public void quit() {
        if (!isSetup) return;
        if (gameManager.getState() != GameState.WAITING) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            hotbarManager.clearHotbar(player);
        }

        teamManager.reset();
        pointService.reset();
        isSetup = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_STOP);
            player.sendMessage(Messages.SETUP_RESET.get(playerDataManager.getLang(player)));
        }
    }

    public void startHotbarTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isSetup) return;
            if (gameManager.getState() != GameState.WAITING) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                hotbarManager.giveHotbar(player);
            }
        }, 0L, 40L); // 40 ticks = 2 secondes
    }

    public boolean isSetup() {
        return isSetup;
    }

    public void setSetup(boolean setup) {
        this.isSetup = setup;
    }
}
