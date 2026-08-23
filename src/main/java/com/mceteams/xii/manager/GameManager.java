package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameManager {
    private final TeamManager teamManager;
    private final DayManager dayManager;
    private final HotbarManager hotbarManager;
    private final PointService pointService;
    private final SoundService soundService;
    private final PlayerDataManager playerDataManager;

    private GameState state = GameState.WAITING;
    private boolean joinEnabled = true;
    private boolean leaveEnabled = true;
    private final Set<Material> blacklistedItems = new HashSet<>();
    private BukkitTask waitingActionBarTask;

    public GameManager(TeamManager teamManager, DayManager dayManager, HotbarManager hotbarManager, PointService pointService, SoundService soundService, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.dayManager = dayManager;
        this.hotbarManager = hotbarManager;
        this.pointService = pointService;
        this.soundService = soundService;
        this.playerDataManager = playerDataManager;
    }

    public void startGame() {
        cancelWaitingActionBar();
        this.state = GameState.PREPARATION;
        dayManager.startGame();

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGameMode(GameMode.SURVIVAL);
            online.sendTitle("", "", 0, 0, 0);
            hotbarManager.clearHotbar(online);
        }
    }

    public void startWaitingActionBar(JavaPlugin plugin) {
        cancelWaitingActionBar();
        waitingActionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != GameState.WAITING) return;
            Component bar = Component.text("§e§lWaiting for start...");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendActionBar(bar);
            }
        }, 0L, 20L);
    }

    public void cancelWaitingActionBar() {
        if (waitingActionBarTask != null) {
            waitingActionBarTask.cancel();
            waitingActionBarTask = null;
        }
    }

    public void endGame() {
        List<GameTeam> aliveTeams = new ArrayList<>();
        for (GameTeam team : teamManager.getTeams()) {
            if (team.isHeartAlive()) {
                aliveTeams.add(team);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            Lang lang = playerDataManager.getLang(online);

            if (aliveTeams.isEmpty()) {
                soundService.play(online, GameSound.DEFEAT);
                online.sendMessage(Messages.TIE_ANNOUNCE.get(lang));
            } else if (aliveTeams.size() == 1) {
                GameTeam winner = aliveTeams.get(0);
                boolean isWinner = winner.getPlayers().contains(online.getUniqueId());

                if (isWinner) {
                    soundService.play(online, GameSound.VICTORY);
                    online.sendMessage(Messages.VICTORY_ANNOUNCE.get(lang));
                } else {
                    soundService.play(online, GameSound.DEFEAT);
                    online.sendMessage(Messages.DEFEAT_ANNOUNCE.get(lang));
                }
            } else {
                soundService.play(online, GameSound.DEFEAT);
                online.sendMessage(Messages.TIE_ANNOUNCE.get(lang));
            }
        }

        this.state = GameState.WAITING;
        this.joinEnabled = true;
        this.leaveEnabled = true;
        dayManager.setDay(1);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGameMode(GameMode.ADVENTURE);
            hotbarManager.giveHotbar(online);
        }
    }

    public DayManager getDayManager() {
        return this.dayManager;
    }

    public GameState getState() {
        return this.state;
    }

    public Set<Material> getBlacklistedItems() {
        return this.blacklistedItems;
    }

    public boolean isJoinEnabled() {
        return this.joinEnabled;
    }

    public void setJoinEnabled(boolean enabled) {
        this.joinEnabled = enabled;
    }

    public boolean isLeaveEnabled() {
        return this.leaveEnabled;
    }

    public void setLeaveEnabled(boolean enabled) {
        this.leaveEnabled = enabled;
    }

    public void forceState(GameState state) {
        this.state = state;
    }

    public PointService getPointService() {
        return pointService;
    }
}
