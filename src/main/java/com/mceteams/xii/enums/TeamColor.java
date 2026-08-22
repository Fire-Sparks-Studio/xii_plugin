package com.mceteams.xii.enums;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {

    WHITE(DyeColor.WHITE, ChatColor.WHITE, "Blanc", "White"),
    ORANGE(DyeColor.ORANGE, ChatColor.GOLD, "Orange", "Orange"),
    MAGENTA(DyeColor.MAGENTA, ChatColor.LIGHT_PURPLE, "Magenta", "Magenta"),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, ChatColor.AQUA, "Bleu Clair", "Light Blue"),
    YELLOW(DyeColor.YELLOW, ChatColor.YELLOW, "Jaune", "Yellow"),
    LIME(DyeColor.LIME, ChatColor.GREEN, "Vert Clair", "Lime"),
    PINK(DyeColor.PINK, ChatColor.LIGHT_PURPLE, "Rose", "Pink"),
    GRAY(DyeColor.GRAY, ChatColor.DARK_GRAY, "Gris", "Gray"),
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, ChatColor.GRAY, "Gris Clair", "Light Gray"),
    CYAN(DyeColor.CYAN, ChatColor.DARK_AQUA, "Cyan", "Cyan"),
    PURPLE(DyeColor.PURPLE, ChatColor.DARK_PURPLE, "Violet", "Purple"),
    BLUE(DyeColor.BLUE, ChatColor.DARK_BLUE, "Bleu", "Blue"),
    BROWN(DyeColor.BROWN, ChatColor.GOLD, "Marron", "Brown"),
    GREEN(DyeColor.GREEN, ChatColor.DARK_GREEN, "Vert", "Green"),
    RED(DyeColor.RED, ChatColor.RED, "Rouge", "Red"),
    BLACK(DyeColor.BLACK, ChatColor.BLACK, "Noir", "Black");

    private final DyeColor dyeColor;
    private final ChatColor chatColor;
    private final String nameFR;
    private final String nameEN;

    TeamColor(DyeColor dyeColor, ChatColor chatColor, String nameFR, String nameEN) {
        this.dyeColor = dyeColor;
        this.chatColor = chatColor;
        this.nameFR = nameFR;
        this.nameEN = nameEN;
    }

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public String getName(Lang lang) {
        if (Lang.FR.equals(lang)) return nameFR;

        return nameEN;
    }

    public Material getMaterial() {
        return Material.valueOf(this.name() + "_WOOL");
    }

    public Material getGlassPane() {
        return Material.valueOf(this.name() + "_STAINED_GLASS_PANE");
    }

    public String getFormattedName() {
        return this.name().charAt(0) + this.name().substring(1).toLowerCase();
    }
}
