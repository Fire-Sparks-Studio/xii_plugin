package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.service.PointService;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

public class GameManager {
    private final TeamManager teamManager = new TeamManager();
    private final DayManager dayManager =  new DayManager();
    private final PointService pointService =  new PointService();
    private GameState state;
    private boolean joinEnabled = true;
    private boolean leaveEnabled = true;
    private final Set<Material> blacklistedItems = new HashSet<>();

    public void startGame() {
        this.state = GameState.PREPARATION;
        dayManager.startGame();
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
}
