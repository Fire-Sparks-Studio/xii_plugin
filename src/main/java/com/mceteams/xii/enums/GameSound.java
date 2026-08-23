package com.mceteams.xii.enums;

import org.bukkit.Sound;

public enum GameSound {

    // ===== UI (Niveau 1 — Interaction) =====
    CLICK(Sound.UI_BUTTON_CLICK, 0.15f, 1.0f),
    SELECT(Sound.UI_BUTTON_CLICK, 0.15f, 1.0f),
    BACK(Sound.UI_BUTTON_CLICK, 0.15f, 1.0f),
    CHANGE(Sound.UI_BUTTON_CLICK, 0.15f, 1.0f),
    LANG_SELECT(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f),

    // ===== Feedback (Niveau 2) =====
    SUCCESS(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f),
    SUCCESS_HIGH(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 2.0f),
    ERROR(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.0f),
    TEAM_JOIN(Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.2f),
    TEAM_LEAVE(Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f),
    PLAYER_ADDED(Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.3f),
    PLAYER_REMOVED(Sound.ENTITY_ITEM_BREAK, 0.4f, 0.9f),

    // ===== Création / suppression d'équipe =====
    TEAM_CREATED(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f),
    TEAM_DELETED(Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.5f),

    // ===== Progression (Niveau 3) =====
    POINTS_GAINED(Sound.BLOCK_NOTE_BLOCK_PLING, 0.15f, 1.5f),
    POINTS_MILESTONE(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 2.0f),
    RARE_RESOURCE(Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f),
    DAY_CHANGE(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f),

    // ===== Combat (Niveau 4) =====
    HEART_DESTROYED(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f),
    HEART_RESTORED(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f),
    PLAYER_ELIMINATED(Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f),
    PLAYER_REVIVED(Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f),
    FIRST_BLOOD(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f),

    // ===== Équipe (Niveau 5) =====
    TEAM_ELIMINATED(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f),
    TEAM_REVIVED(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f),

    // ===== Partie (Niveau 6) =====
    COUNTDOWN(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.8f),
    COUNTDOWN_FINAL(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.3f),
    COUNTDOWN_GO(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f),
    GAME_START(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f),
    GAME_STOP(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f),
    PREPARATION_START(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.0f),
    COMBAT_START(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f),
    SUDDEN_DEATH(Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f),
    VICTORY(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f),
    DEFEAT(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f),
    MVP(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f),

    // ===== Événements spéciaux =====
    METEOR(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.3f),
    DRAGON(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f),

    // ===== TP =====
    TELEPORT(Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.0f, 1.5f),

    // ===== Spectateur =====
    SPECTATOR(Sound.ENTITY_GHAST_SHOOT, 0.4f, 1.5f),

    // ===== Setup =====
    SETUP_START(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f),
    SETUP_STOP(Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.5f);

    private final Sound sound;
    private final float volume;
    private final float pitch;

    GameSound(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public Sound getSound() {
        return sound;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}
