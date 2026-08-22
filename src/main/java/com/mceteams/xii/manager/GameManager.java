package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.service.PointService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class GameManager {
    private final DayManager dayManager;
    private final HotbarManager hotbarManager;
    private final PointService pointService;

    private GameState state = GameState.WAITING;
    private boolean joinEnabled = true;
    private boolean leaveEnabled = true;
    private final Set<Material> blacklistedItems = new HashSet<>();

    public GameManager(TeamManager teamManager, DayManager dayManager, HotbarManager hotbarManager, PointService pointService) {
        this.dayManager = dayManager;
        this.hotbarManager = hotbarManager;
        this.pointService = pointService;
    }

    public void startGame() {
        this.state = GameState.PREPARATION;
        dayManager.startGame();

        for (Player online : Bukkit.getOnlinePlayers()) {
            hotbarManager.clearHotbar(online);
        }
    }

    public DayManager getDayManager() {
        return this.dayManager;
    }

    public void endGame() {
        this.state = GameState.ENDED;
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
