package com.mceteams.xii.model;

import com.mceteams.xii.enums.DeathCause;
import com.mceteams.xii.enums.PlayerClass;

import java.util.UUID;

/**
 * Données de gameplay d'un joueur (spec §38 : un model ne contient pas
 * de logique Bukkit complexe, on stocke les UUID et jamais le Player).
 */
public class PlayerData {

    /** UUID du joueur : seule identité persistée. */
    private final UUID uuid;
    /** Score personnel (points par catégorie). */
    private final PlayerScore score;

    /** Équipe du joueur (null = sans équipe => spectateur au lancement). */
    private UUID teamId;
    /** Classe choisie ou attribuée aléatoirement (null tant que non choisi). */
    private PlayerClass playerClass;

    /** Le joueur est-il vivant ? */
    private boolean alive;
    /** Le joueur est-il éliminé définitivement (plus aucun respawn possible) ? */
    private boolean eliminated;
    /** Le joueur est-il actuellement en mode spectateur (custom) ? */
    private boolean spectator;
    /** Le joueur est-il déconnecté en pleine partie ? */
    private boolean disconnected;

    /** Cause de la dernière mort (spec §19 étape 2). */
    private DeathCause deathCause;

    /**
     * Dernier attaquant + horodatage : alimente la "fenêtre de combat"
     * de 15 secondes utilisée pour qualifier les déconnexions (§30).
     */
    private UUID lastDamager;
    private long lastDamageTime;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.score = new PlayerScore();

        this.alive = true;
        this.eliminated = false;
        this.spectator = false;
        this.disconnected = false;

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

    public boolean isSpectator() {
        return spectator;
    }

    public void setSpectator(boolean spectator) {
        this.spectator = spectator;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
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

    /**
     * Le joueur a-t-il reçu un coup d'un adversaire il y a moins de
     * {@code durationMillis} ? Utilisé par CombatService pour décider
     * si une déconnexion compte comme une mort (fenêtre de 15 s).
     */
    public boolean wasRecentlyDamagedByPlayer(long currentTime, long durationMillis) {
        return lastDamager != null
                && currentTime - lastDamageTime <= durationMillis;
    }

    /** Remet à zéro la fenêtre de combat (appelé après respawn, etc.). */
    public void clearLastDamage() {
        this.lastDamager = null;
        this.lastDamageTime = 0L;
    }
}
