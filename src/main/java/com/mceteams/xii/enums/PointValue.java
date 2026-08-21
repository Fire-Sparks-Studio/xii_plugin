package com.mceteams.xii.enums;

public enum PointValue {
    // Combat
    KILL_PLAYER(200, "PLAYER_KILL"),
    FIRST_BLOOD(500, "FIRST_BLOOD"),
    KILL_MOB(10, "MOB_KILL"),
    KILL_STREAK(5, "KILL_STREAK"),
    HEART_DESTROY(2000, "HEART_DESTROY"),

    // Mining
    MINING_DIAMOND(50, "MINING_DIAMOND"),
    MINING_EMERALD(50, "MINING_EMERALD"),
    MINING_GOLD(20, "MINING_GOLD"),
    MINING_IRON(15, "MINING_IRON"),
    MINING_LAPIS(10, "MINING_LAPIS"),
    MINING_REDSTONE(10, "MINING_REDSTONE"),
    MINING_COPPER(10, "MINING_COPPER"),
    MINING_COAL(5, "MINING_COAL");

    private final int value;
    private final String source;

    PointValue(int value, String source) {
        this.value = value;
        this.source = source;
    }

    public int getValue() {
        return this.value;
    }

    public String getSource() {
        return this.source;
    }
}
