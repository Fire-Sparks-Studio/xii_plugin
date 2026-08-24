package com.mceteams.xii.enums;

/**
 * Identifiants des six tables de loot du jeu (spec : ne pas mélanger
 * les tables Package et Dungeon).
 *
 * Chaque table définit aussi son nombre de TIRAGES pondérés effectués
 * lorsqu'on génère son contenu.
 */
public enum LootTableId {

    // --- PACKAGES -----------------------------------------------------
    PACKAGE_NORMAL("Colis normal", 4),
    PACKAGE_UPGRADE("Colis amélioré", 3),
    PACKAGE_CURSED("Colis maudit", 4),

    // --- DONJONS --------------------------------------------------------
    DUNGEON_NORMAL("Donjon normal", 5),
    DUNGEON_RARE("Donjon rare", 5),
    DUNGEON_CURSED("Donjon maudit", 5);

    private final String displayName;
    private final int rolls;

    LootTableId(String displayName, int rolls) {
        this.displayName = displayName;
        this.rolls = rolls;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Nombre de tirages pondérés pour remplir un conteneur. */
    public int getRolls() {
        return rolls;
    }
}
