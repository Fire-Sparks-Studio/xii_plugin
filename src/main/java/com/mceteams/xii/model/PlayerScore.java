package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.Map;
import java.util.UUID;

public class PlayerScore {
    private final UUID player;
    private Map<PointCategory, Integer> points;

    public PlayerScore(UUID player) {
        this.player = player;
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
