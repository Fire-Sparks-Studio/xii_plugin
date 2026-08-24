package com.mceteams.xii.enums;

/**
 * Les cinq classes jouables (spécification §31).
 *
 * Chaque classe possède un nom français, une couleur d'affichage et
 * des lignes de lore décrivant son avantage / malus. Ces textes sont
 * utilisés par la ClassSelectionGUI.
 *
 * Règles :
 * - Mineur     : minerais automatiquement fondus / une ligne d'inventaire en moins
 * - Travailleur: +25% de points pour l'équipe / 5 coeurs (10 PV)
 * - Robuste    : 15 PV / -15% dégâts infligés, -15% vitesse
 * - Agile      : +20% vitesse, aucun fall damage / aucun malus
 * - Guerrier   : +25% dégâts infligés / 7 coeurs (14 PV)
 */
public enum PlayerClass {

    MINER("Mineur", "§6",
            "§7Avantage : §fMinerais automatiquement fondus",
            "§7Malus : §cUne ligne d'inventaire en moins"),

    WORKER("Travailleur", "§e",
            "§7Avantage : §f+25% de points pour l'équipe",
            "§7Malus : §c5 coeurs (10 PV)"),

    TANK("Robuste", "§a",
            "§7Avantage : §f15 coeurs (30 PV)",
            "§7Malus : §c-15% dégâts infligés, -15% vitesse"),

    AGILE("Agile", "§b",
            "§7Avantages : §f+20% vitesse, aucun fall damage",
            "§7Malus : §caucun"),

    WARRIOR("Guerrier", "§c",
            "§7Avantage : §f+25% dégâts infligés",
            "§7Malus : §c7 coeurs (14 PV)");

    /** Nom français affiché dans les GUIs et les messages. */
    private final String displayName;
    /** Code couleur (§x) utilisé pour le chat et les items. */
    private final String colorCode;
    /** Description de l'avantage de la classe. */
    private final String advantageLine;
    /** Description du malus de la classe. */
    private final String malusLine;

    PlayerClass(String displayName, String colorCode,
                String advantageLine, String malusLine) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.advantageLine = advantageLine;
        this.malusLine = malusLine;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getAdvantageLine() {
        return advantageLine;
    }

    public String getMalusLine() {
        return malusLine;
    }

    /**
     * Nom coloré prêt à afficher, ex : "§6Mineur".
     */
    public String getColoredName() {
        return colorCode + displayName;
    }
}
