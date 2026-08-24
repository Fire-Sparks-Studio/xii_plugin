package com.mceteams.xii.enums;

/**
 * Catégories de points (spécification §32).
 * Chaque catégorie correspond à une mécanique qui rapporte des points,
 * gérés de manière centralisée par PointService.
 */
public enum PointCategory {
    /** Minage de minerais. */
    MINING("Minage"),
    /** Kill d'un joueur adverse. */
    KILL("Kill"),
    /** Premier kill de la partie. */
    FIRST_KILL("Premier kill"),
    /** Exploration de nouveaux chunks. */
    EXPLORATION("Exploration"),
    /** Ouverture d'un colis (package). */
    PACKAGE("Colis"),
    /** Découverte d'un objet rare dans un colis. */
    RARE_ITEM("Objet rare"),
    /** Destruction du coeur d'une équipe. */
    CORE("Coeur");

    private final String displayName;

    PointCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
