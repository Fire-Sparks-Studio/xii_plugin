package com.mceteams.xii.enums;

/**
 * Cause d'une mort de joueur (spécifications §19, §29, §30).
 *
 * Attention (spec §24) : la destruction d'un coeur n'est PAS une mort
 * de joueur ; elle est traitée par CoreService sans DeathCause.
 *
 * PLAYER     : tué par un joueur (kill attribué).
 * DISCONNECT : déconnexion pendant la fenêtre de combat (15 s après
 *              avoir reçu un coup) - traitée comme une mort.
 * OTHER      : toute autre cause (chute, météorite, dragon...).
 */
public enum DeathCause {
    PLAYER,
    DISCONNECT,
    OTHER
}
