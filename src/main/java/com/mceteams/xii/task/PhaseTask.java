package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Horloge des PHASES (spec §17/§20).
 *
 * Chaque seconde :
 * 1. avance l'horloge de la sous-phase via PhaseManager ;
 * 2. GameManager traite les transitions (nouvelle sous-phase, combat,
 *    fin de partie) et les hooks associés ;
 * 3. les scoreboards sont rafraîchis ;
 * 4. la restriction Mineur (rangée verrouillée) est appliquée.
 */
public class PhaseTask extends BukkitRunnable {

    private final XiiPlugin plugin;

    public PhaseTask(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Toute l'orchestration est déléguée au GameManager (état global).
        plugin.getGameManager().onSecondTick();
    }
}
