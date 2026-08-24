package com.mceteams.xii.enums;

/**
 * État global de la partie (spécification §40 : ne jamais inventer
 * d'autres états).
 *
 * Cycle officiel :
 * NONE -> WAITING -> COUNTDOWN -> CLASS_SELECTION -> PREPARATION
 *      -> COMBAT -> ENDING -> (retour) WAITING.
 *
 * NONE est l'absence de partie : le serveur reste un serveur Minecraft
 * normal tant qu'aucune zone n'a été définie avec /zone set (spec §3).
 */
public enum GameState {
    /** Aucune zone définie : le plugin n'interfère pas avec le serveur. */
    NONE,
    /** Zone définie, les joueurs attendent dans la zone d'attente. */
    WAITING,
    /** Compte à rebours de 5 secondes après /party start. */
    COUNTDOWN,
    /** Sélection de classe pendant 30 secondes. */
    CLASS_SELECTION,
    /** Phase de préparation : 60 minutes, 6 sous-phases. */
    PREPARATION,
    /** Phase de combat : 60 minutes, 6 sous-phases. */
    COMBAT,
    /** Fin de partie : affichage des résultats. */
    ENDING
}
