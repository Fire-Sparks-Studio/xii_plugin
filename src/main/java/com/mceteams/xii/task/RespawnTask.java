package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Traitement des respawn arrivés à échéance (spec §19/§29).
 *
 * Appelle RespawnManager.processDue() chaque seconde. Tout le
 * calendrier et les règles (élimination définitive si coeur mort...)
 * restent centralisés dans RespawnManager.
 */
public class RespawnTask extends BukkitRunnable {

    private final XiiPlugin plugin;

    public RespawnTask(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getRespawnManager().processDue();
    }
}
