package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.HashMap;
import java.util.Map;

public class TeamScore {
    GameTeam team;
    Map<PointCategory, Integer> points = new HashMap<>();

    public TeamScore(GameTeam team) {
        this.team = team;
    }

    public void addPoints(PointCategory category, int amount) {
        this.points.merge(category, Math.abs(amount), Integer::sum);
    }

    public void removePoints(PointCategory category, int amount) {
        this.points.merge(category, -Math.abs(amount), Integer::sum);
    }

    public int getPoints(PointCategory category) {
        return this.points.getOrDefault(category, 0);
    }

    public int getTotal() {
        int total = 0;
        for (int score : this.points.values()) {
            total += score;
        }
        return total;
    }

    public void setTotal(int total) {
        this.points.put(PointCategory.OTHER, total);
    }

    public void reset() {
        this.points.clear();
    }
}
