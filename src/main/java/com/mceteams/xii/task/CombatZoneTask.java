package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de la ZONE DE COMBAT (1 tick / seconde).
 *
 * Elle ne fait qu'un chose : demander au {@link
 * com.mceteams.xii.manager.CombatZoneManager} d'avancer la réduction de
 * la zone (recalcul du rayon depuis l'horloge des phases) et d'appliquer
 * les dégâts aux joueurs hors zone.
 *
 * La tâche est démarrée à l'entrée dans la phase COMBAT et arrêtée à la
 * fin de la partie (cf. GameManager).
 */
public class CombatZoneTask extends BukkitRunnable {

    private final XiiPlugin plugin;

    public CombatZoneTask(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getCombatZoneManager().tickSecond();
    }
}
