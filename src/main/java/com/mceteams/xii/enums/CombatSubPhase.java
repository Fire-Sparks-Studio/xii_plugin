package com.mceteams.xii.enums;

/**
 * Sous-phases du COMBAT (spécifications §20 à §26).
 * L'ordre de cette énumération EST l'ordre du jeu.
 * La fin de SUDDEN_DEATH déclenche automatiquement la fin de partie.
 */
public enum CombatSubPhase {
    /** Début du combat : le PvP devient global selon les règles. */
    START,
    /** Les météorites commencent à tomber sur la map. */
    METEORITES,
    /** Les dégâts infligés sont multipliés par deux. */
    MORE_DAMAGE,
    /** Tous les cœurs encore actifs sont détruits automatiquement. */
    ALL_CORE_DESTRUCTION,
    /** Météorites plus fréquentes + points des mécaniques concernées x2. */
    MORE_METEORITES,
    /** Mort subite : les dragons détruisent progressivement la map. */
    SUDDEN_DEATH
}
