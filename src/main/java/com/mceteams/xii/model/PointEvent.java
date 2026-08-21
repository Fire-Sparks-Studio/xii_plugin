package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.UUID;

public class PointEvent {
    private UUID player;
    private GameTeam team;
    private PointCategory category;
    private int amount;
    private String source;
    private String sourceUID;

    public PointEvent(UUID player, GameTeam team, PointCategory category, int amount, String source, String sourceUID) {
        this.player = player;
        this.team = team;
        this.category = category;
        this.amount = amount;
        this.source = source;
        this.sourceUID = sourceUID;
    }

    public UUID getPlayer() {
        return this.player;
    }

    public GameTeam getTeam() {
        return this.team;
    }

    public PointCategory getCategory() {
        return this.category;
    }

    public int getAmount() {
        return this.amount;
    }

    public String getSource() {return this.source;}

    public String getSourceUID() {
        return this.sourceUID;
    }
}
