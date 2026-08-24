package com.mceteams.xii.model;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Données d'une équipe du jeu (spec §38).
 *
 * IMPORTANT : cette classe est la SOURCE DE VÉRITÉ du gameplay
 * (points, coeur, élimination, spawn...). L'équipe Bukkit créée en
 * parallèle par TeamManager ne sert qu'au friendly fire, au préfixe,
 * aux collisions et aux mécaniques vanilla (spec §6).
 */
public class GameTeam {

    /** Couleur unique de l'équipe (identité fonctionnelle). */
    private final TeamColor color;
    /** Joueurs membres (UUID uniquement, jamais de Player Bukkit). */
    private final Set<UUID> players;
    /** Score collectif de l'équipe. */
    private final TeamScore score;

    /** Taille maximale de l'équipe. */
    private int maxPlayers;

    /** Le coeur de l'équipe est-il encore vivant ? */
    private boolean heartAlive;
    /** L'équipe est-elle éliminée ? */
    private boolean eliminated;

    /** Série de kills en cours de l'équipe (reset quand un membre meurt). */
    private int killStreak;

    /** Point de spawn de l'équipe (base). Clone à la lecture pour éviter les mutations. */
    private Location spawn;

    public GameTeam(TeamColor color, int maxPlayers) {
        this.color = color;
        this.maxPlayers = maxPlayers;

        this.players = new HashSet<>();
        this.score = new TeamScore();

        this.heartAlive = true;
        this.eliminated = false;
        this.killStreak = 0;
    }

    public TeamColor getColor() {
        return color;
    }

    /**
     * Copie défensive : l'appelant ne peut pas modifier la liste interne.
     */
    public Set<UUID> getPlayers() {
        return Set.copyOf(players);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public TeamScore getScore() {
        return score;
    }

    public boolean isHeartAlive() {
        return heartAlive;
    }

    public void setHeartAlive(boolean heartAlive) {
        this.heartAlive = heartAlive;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public int getKillStreak() {
        return killStreak;
    }

    public void setKillStreak(int killStreak) {
        this.killStreak = killStreak;
    }

    public void resetKillStreak() {
        this.killStreak = 0;
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn == null ? null : spawn.clone();
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void clearPlayers() {
        players.clear();
    }
}
