package com.mceteams.xii.enums;

/**
 * Les quatre couleurs d'équipe possibles (spec §6).
 * Aucune logique Bukkit ici : la correspondance vers les matériaux
 * (laines) et les ChatColor est faite par util/TeamUtil.
 */
public enum TeamColor {

    BLUE("Bleu", "§9"),
    YELLOW("Jaune", "§e"),
    RED("Rouge", "§c"),
    GREEN("Vert", "§a");

    /** Nom français de l'équipe (affichage chat / GUI). */
    private final String displayName;
    /** Code couleur (§x) utilisé pour le chat et le scoreboard. */
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

    /**
     * Nom coloré prêt à afficher, ex : "§9Bleu".
     */
    public String getColoredName() {
        return colorCode + displayName;
    }

    /**
     * Lettre unique en majuscule (B/J/R/V) : préfixe du TAB en partie.
     */
    public String getLetter() {
        return displayName.substring(0, 1).toUpperCase();
    }
}
