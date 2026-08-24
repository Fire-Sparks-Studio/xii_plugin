package com.mceteams.xii.manager;

import com.mceteams.xii.model.PlayerData;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les données de gameplay des joueurs (spec §2).
 *
 * Règle d'or (spec §40) : on stocke des UUID, jamais de Player Bukkit.
 * Le Player est récupéré uniquement au moment où on en a besoin.
 */
public class PlayerManager {

    /** Données par UUID. ConcurrentHashMap : accès depuis les tasks. */
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    /**
     * Récupère (ou crée à la volée) les données d'un joueur.
     */
    public PlayerData getData(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerData::new);
    }

    /**
     * Données d'un joueur Bukkit (raccourci).
     */
    public PlayerData getData(org.bukkit.entity.Player player) {
        return getData(player.getUniqueId());
    }

    /**
     * Toutes les données connues (y compris joueurs hors ligne dont le
     * respawn est en attente).
     */
    public Collection<PlayerData> all() {
        return players.values();
    }

    /**
     * @return le Player Bukkit s'il est en ligne, sinon null.
     */
    public org.bukkit.entity.Player getPlayer(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    /**
     * Oublie les données d'un joueur (appelé uniquement lors du
     * nettoyage global de fin de partie / retour WAITING).
     * On ne supprime PAS à la déconnexion : le respawn doit pouvoir
     * se produire même si le joueur est hors ligne.
     */
    public void forget(UUID uuid) {
        players.remove(uuid);
    }

    /** Vide tout le cache (reset complet). */
    public void clearAll() {
        players.clear();
    }
}
