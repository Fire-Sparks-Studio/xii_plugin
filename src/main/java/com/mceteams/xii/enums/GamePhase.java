package com.mceteams.xii.enums;

/**
 * Grande phase de gameplay (spécification §40).
 * Contrairement à {@link GameState}, cette énumération ne décrit que
 * le "type de jeu" en cours : aucune phase, préparation ou combat.
 */
public enum GamePhase {
    /** Aucune phase active (attente, countdown, sélection, fin). */
    NONE,
    /** Phase de préparation de 60 minutes. */
    PREPARATION,
    /** Phase de combat de 60 minutes. */
    COMBAT
}
