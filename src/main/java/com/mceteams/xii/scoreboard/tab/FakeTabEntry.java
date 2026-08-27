package com.mceteams.xii.scoreboard.tab;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/**
 * Fausse entrée du TAB : un UUID déterministe, un nom de profil (jamais
 * affiché directement, utilisé comme repli de tri côté client), le nom
 * affiché (legacy §x), son ordre de liste et UN PROFIL "tête" optionnel.
 *
 * Quand {@code profile} est non nul, c'est lui qui est servi au client
 * (il porte une texture de peau) : on l'utilise pour afficher un BLOC
 * GRIS à la place des têtes de joueur par défaut sur les lignes vides.
 *
 * Servie au client via le paquet réseau "player_info / update".
 */
public record FakeTabEntry(UUID uuid, String profileName, String displayName,
                           int listOrder, GameProfile profile) {

    public FakeTabEntry(UUID uuid, String profileName, String displayName,
                        int listOrder) {
        this(uuid, profileName, displayName, listOrder, null);
    }
}