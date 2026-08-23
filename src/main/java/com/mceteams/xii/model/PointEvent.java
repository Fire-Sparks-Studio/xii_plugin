package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

public class PointEvent {

    private final PointCategory category;
    private final int points;

    public PointEvent(PointCategory category, int points) {
        this.category = category;
        this.points = points;
    }

    public PointCategory getCategory() {
        return category;
    }

    public int getPoints() {
        return points;
    }
}