package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Chute périodique des MÉTÉORITES (spec §22/§25).
 *
 * Chaque seconde, un compteur décrémente ; à zéro :
 * - MeteoriteService lance une météorite vers un point aléatoire ;
 * - un nouvel intervalle est tiré (réduit pendant MORE_METEORITES).
 *
 * La task s'auto-désactive si le système météorites n'est plus actif.
 */
public class MeteoriteTask extends BukkitRunnable {

    private final XiiPlugin plugin;
    /** Secondes avant la prochaine chute. */
    private int secondsUntilStrike;

    public MeteoriteTask(XiiPlugin plugin) {
        this.plugin = plugin;
        // Première chute rapide : entre 5 et 15 secondes.
        this.secondsUntilStrike = 5 + java.util.concurrent.ThreadLocalRandom
                .current().nextInt(10);
    }

    @Override
    public void run() {
        // Auto-arrêt : système météorites actif uniquement en combat.
        if (!plugin.getGameSystems().isMeteoriteListenerEnabled()) {
            this.cancel();
            return;
        }

        if (--secondsUntilStrike > 0) {
            return;
        }

        // Impact + nouvel intervalle (fréquence x2 en MORE_METEORITES).
        plugin.getMeteoriteService().strike();
        secondsUntilStrike = plugin.getMeteoriteService().nextStrikeDelaySeconds();
    }
}
