package com.mceteams.xii.util;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Utilitaire de sons : centralise les sons du jeu (countdown, kill,
 * changement de phase...) pour éviter les valeurs dispersées.
 */
public final class SoundUtil {

    private SoundUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /** Joue un son à un joueur avec volume/pitch. */
    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || !player.isOnline() || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** Joue un son à tous les joueurs connectés. */
    public static void broadcast(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            play(player, sound, volume, pitch);
        }
    }

    /** "Clic" de countdown (5..1) : pling GRAVE (pitch 0.5). */
    public static void playCountdownTick(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
    }

    /** Growl de dragon : signal le DÉBUT officiel du lancement. */
    public static void playDragonGrowl(Player player) {
        play(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
    }

    /** Son final du countdown / début de phase. */
    public static void playPhaseStart(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

    /** Son joué quand un joueur gagne des points. */
    public static void playPointGain(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    /** Son joué à la mort d'un joueur. */
    public static void playDeath(Player player) {
        play(player, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
    }
}
