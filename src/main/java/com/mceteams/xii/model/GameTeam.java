package com.mceteams.xii.model;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameTeam {

    private final TeamColor color;
    private final Set<UUID> players;
    private final TeamScore score;

    private int maxPlayers;

    private boolean heartAlive;
    private boolean eliminated;

    private int killStreak;

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