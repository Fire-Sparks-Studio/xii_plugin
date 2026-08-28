package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

/**
 * Masque les ANNONCES d'achievements (progrès) UNIQUEMENT pendant une
 * partie en cours : le chat reste propre en PREPARATION / COMBAT
 * (pas de "a fait le progrès [...]"). Hors partie, comportement vanilla.
 */
public class AchievementsListener implements Listener {

    private final XiiPlugin plugin;

    public AchievementsListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        // Uniquement quand une partie est en cours (préparation ou combat).
        if (plugin.getGameManager().isRunning()) {
            event.message(null);
        }
    }
}