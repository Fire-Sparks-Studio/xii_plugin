package com.mceteams.xii.enums;

/**
 * Sous-phases de la PRÉPARATION (spécification §17).
 * L'ordre de cette énumération EST l'ordre du jeu.
 */
public enum PreparationSubPhase {
    /** Début de la préparation. */
    START,
    /** Les packages (colis) commencent à apparaître. */
    PACKAGES,
    /** Les loots des donjons deviennent accessibles. */
    DUNGEONS,
    /** Les points gagnés sont multipliés par deux. */
    POINT_UPGRADES,
    /** Davantage de packages apparaissent. */
    PACKAGE_UPGRADE,
    /** Les donjons sont restockés. */
    DUNGEON_RESTOCK
}
