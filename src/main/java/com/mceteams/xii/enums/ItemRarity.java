package com.mceteams.xii.enums;

/**
 * Les 4 niveaux de rareté des objets du jeu (upgrades & légendaires).
 *
 * IMPORTANT : le NIVEAU d'une upgrade n'est PAS une rareté - un item
 * Vitalité reste Commun même utilisé trois fois (Vitalité III).
 */
public enum ItemRarity {
    COMMON("Commun", "§a"),
    RARE("Rare", "§9"),
    EPIC("Épique", "§5"),
    LEGENDARY("Légendaire", "§6");

    /** Nom français de la rareté. */
    private final String displayName;
    /** Couleur associée (nom de l'item, lore...). */
    private final String colorCode;

    ItemRarity(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    /** Nom coloré prêt à afficher, ex : "§9Rare". */
    public String getColoredName() {
        return colorCode + displayName;
    }
}