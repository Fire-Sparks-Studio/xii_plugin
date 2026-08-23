package com.mceteams.xii.model;

import com.mceteams.xii.enums.DeathCause;
import com.mceteams.xii.enums.PlayerClass;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private final PlayerScore score;

    private UUID teamId;
    private PlayerClass playerClass;

    private boolean alive;
    private boolean eliminated;

    private DeathCause deathCause;

    private UUID lastDamager;
    private long lastDamageTime;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.score = new PlayerScore();

        this.alive = true;
        this.eliminated = false;

        this.deathCause = null;

        this.lastDamager = null;
        this.lastDamageTime = 0L;
    }

    public UUID getUuid() {
        return uuid;
    }

    public PlayerScore getScore() {
        return score;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public boolean hasTeam() {
        return teamId != null;
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(PlayerClass playerClass) {
        this.playerClass = playerClass;
    }

    public boolean hasClass() {
        return playerClass != null;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public DeathCause getDeathCause() {
        return deathCause;
    }

    public void setDeathCause(DeathCause deathCause) {
        this.deathCause = deathCause;
    }

    public UUID getLastDamager() {
        return lastDamager;
    }

    public void setLastDamager(UUID lastDamager) {
        this.lastDamager = lastDamager;
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long lastDamageTime) {
        this.lastDamageTime = lastDamageTime;
    }

    public boolean wasRecentlyDamagedByPlayer(long currentTime, long durationMillis) {
        return lastDamager != null
                && currentTime - lastDamageTime <= durationMillis;
    }

    public void clearLastDamage() {
        this.lastDamager = null;
        this.lastDamageTime = 0L;
    }
}