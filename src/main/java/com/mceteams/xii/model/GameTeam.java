package com.mceteams.xii.model;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameTeam {
    private final int Id;
    private final TeamColor color;
    private final List<UUID> players = new ArrayList<>();
    private boolean heartAlive;
    private Location spawn;
    private Location heartLocation;
    private int maxPlayers = 10;

    public GameTeam(int Id, TeamColor color) {
        this.Id = Id;
        this.color = color;
        this.heartAlive = true;
    }

    public int getId() {
        return this.Id;
    }
    public TeamColor getColor() {
        return this.color;
    }

    public String getDisplayName(Lang lang) {
        return this.color.getChatColor() + color.getName(lang);
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public Location getSpawn() {
        return this.spawn;
    }

    public void setHeartAlive(boolean alive) { this.heartAlive = alive; }

    public int getMaxPlayers() { return this.maxPlayers; }

    public void setMaxPlayers(int max) { this.maxPlayers = max; }

    public void setHeartLocation(Location heartLocation) {
        this.heartLocation = heartLocation;
    }

    public Location getHeartLocation() {
        return this.heartLocation;
    }

    public boolean addPlayer(UUID playerUUID) {
        return this.players.add(playerUUID);
    }

    public boolean removePlayer(UUID playerUUID) {
        return this.players.remove(playerUUID);
    }

    public boolean hasPlayer(UUID playerUUID) {
        return this.players.contains(playerUUID);
    }

    public boolean isHeartAlive() {
        return this.heartAlive;
    }

    public void destroyHeart() {
        this.heartAlive = false;
    }

    public boolean isAlive() {
        return !this.players.isEmpty();
    }

    public List<UUID> getPlayers() {
        return this.players;
    }
}
