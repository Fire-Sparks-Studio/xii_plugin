package com.mceteams.xii.enums;

public enum TeamColor {

    BLUE("Bleu", "§9"),
    YELLOW("Jaune", "§e"),
    RED("Rouge", "§c"),
    GREEN("Vert", "§a");

    private final String displayName;
    private final String colorCode;

    TeamColor(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }
}
