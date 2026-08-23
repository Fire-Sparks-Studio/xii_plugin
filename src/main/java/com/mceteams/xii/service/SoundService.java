package com.mceteams.xii.service;

import com.mceteams.xii.enums.GameSound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class SoundService {

    public void play(Player player, GameSound gameSound) {
        if (player == null || !player.isOnline()) return;
        player.playSound(player.getLocation(), gameSound.getSound(), gameSound.getVolume(), gameSound.getPitch());
    }

    public void playToAll(GameSound gameSound) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            play(player, gameSound);
        }
    }

    public void playSequence(Player player, GameSound... sounds) {
        if (player == null || !player.isOnline()) return;
        long delay = 0;
        for (GameSound sound : sounds) {
            long currentDelay = delay;
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("XII-Days"),
                    () -> play(player, sound),
                    currentDelay
            );
            delay += 20L;
        }
    }
}
