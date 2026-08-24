package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Compte à rebours générique et réutilisable.
 *
 * Utilisé deux fois :
 * - countdown de lancement : 5..1 puis début de la sélection de classe
 *   (spec §13) ;
 * - sélection de classe : 30 secondes puis début de la préparation
 *   (spec §14).
 *
 * Peut être annulé proprement par /party stop ou le GUI admin
 * (cancelExternally => le onFinish n'est PAS exécuté).
 */
public class CountdownTask extends BukkitRunnable {

    private final XiiPlugin plugin;
    /** Secondes restantes avant la fin. */
    private int secondsLeft;
    /** Action exécutée à zéro (si non annulé). */
    private final Runnable onFinish;
    /** Annulation externe (stop/arrêt). */
    private boolean cancelledExternally = false;

    public CountdownTask(XiiPlugin plugin, int seconds, Runnable onFinish) {
        this.plugin = plugin;
        this.secondsLeft = seconds;
        this.onFinish = onFinish;
    }

    @Override
    public void run() {
        if (cancelledExternally) {
            this.cancel();
            return;
        }

        if (secondsLeft <= 0) {
            // Fin normale : on exécute l'action différée d'un tick pour
            // ne jamais muter le monde depuis le scheduler interne.
            this.cancel();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!cancelledExternally && onFinish != null) {
                    onFinish.run();
                }
            });
            return;
        }

        // Annonce du chiffre courant (toutes les secondes).
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendTitle(player,
                    "§e" + secondsLeft,
                    null,
                    5, 25, 5);
            SoundUtil.playCountdownTick(player);
        }
        secondsLeft--;
    }

    /**
     * Annule le compte à rebours SANS exécuter l'action finale
     * (utilisé par GameManager.cancelCountdown / stopParty).
     */
    public void cancelExternally() {
        cancelledExternally = true;
        try {
            this.cancel();
        } catch (IllegalStateException ignored) {
            // pas encore schedulée : rien à faire
        }
    }
}
