package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;

public class DayManager {
    private int currentDay;
    private long dayStartTime;
    private long dayDuration;
    private GameState gameState;

    public void startGame() {
        this.currentDay = 1;
        this.dayStartTime = System.currentTimeMillis();
    }
    public void nextDay() {
        this.currentDay++;
        this.dayDuration = System.currentTimeMillis() - dayStartTime;
    }

    public void setDay(int day) {
        this.currentDay = day;
        this.dayStartTime = System.currentTimeMillis();
    }

    public int getCurrentDay() {
        return this.currentDay;
    }

    public boolean isPreparationPhase() {
        return this.currentDay <= 6;
    }

    public boolean isCombatPhase() {
        return this.currentDay >= 7;
    }
}
