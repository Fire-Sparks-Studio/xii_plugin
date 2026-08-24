package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Apparition périodique des COLIS (spec §17).
 *
 * Chaque seconde, un compteur décrémente ; à zéro :
 * - PackageService fait apparaître un colis à un point aléatoire ;
 * - un nouvel intervalle aléatoire est tiré (réduit pendant
 *   PACKAGE_UPGRADE via le facteur de config).
 *
 * La task s'auto-désactive si la mécanique n'est plus active
 * (fin de préparation, arrêt de partie).
 */
public class PackageTask extends BukkitRunnable {

    private final XiiPlugin plugin;
    /** Secondes avant le prochain spawn. */
    private int secondsUntilSpawn;

    public PackageTask(XiiPlugin plugin) {
        this.plugin = plugin;
        // Premier colis rapide : entre 10 et 30 secondes.
        this.secondsUntilSpawn = 10 + java.util.concurrent.ThreadLocalRandom
                .current().nextInt(20);
    }

    @Override
    public void run() {
        // Auto-arrêt : le système colis doit être actif (sous-phases prep).
        if (!plugin.getGameSystems().isPackageListenerEnabled()) {
            this.cancel();
            return;
        }

        if (--secondsUntilSpawn > 0) {
            return;
        }

        // Spawn d'un colis + nouvel intervalle aléatoire.
        plugin.getPackageService().spawnRandomPackage();
        secondsUntilSpawn = plugin.getPackageService().nextSpawnDelaySeconds();
    }
}
