package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.EnumMap;
import java.util.Map;

public class TeamScore {

    private final Map<PointCategory, Integer> points;

    public TeamScore() {
        this.points = new EnumMap<>(PointCategory.class);

        for (PointCategory category : PointCategory.values()) {
            points.put(category, 0);
        }
    }

    public int get(PointCategory category) {
        return points.getOrDefault(category, 0);
    }

    public void set(PointCategory category, int value) {
        points.put(category, value);
    }

    public void add(PointCategory category, int value) {
        points.put(category, get(category) + value);
    }

    public int getTotal() {
        return points.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    public void reset() {
        for (PointCategory category : PointCategory.values()) {
            points.put(category, 0);
        }
    }

    public Map<PointCategory, Integer> getPoints() {
        return Map.copyOf(points);
    }
}