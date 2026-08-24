package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Compte à rebours générique et réutilisable.
 *
 * DEUX MODES D'AFFICHAGE :
 * - TITLES (countdown de lancement, 5 s) : gros chiffre à l'écran +
 *   un "pling" GRAVE (pitch 0.5) chaque seconde ;
 * - ACTION BAR (sélection de classe, 30 s) : temps affiché dans la
 *   barre au-dessus de la hotbar, AUCUN son.
 *
 * SON DE FIN : optionnel. Pour le lancement, un GROWL DE DRAGON
 * (pitch 1) retentit quand le countdown se termine.
 *
 * Peut être annulé proprement par /party stop ou le GUI admin
 * (cancelExternally => ni son ni onFinish).
 */
public class CountdownTask extends BukkitRunnable {

    private final XiiPlugin plugin;
    /** Secondes restantes avant la fin. */
    private int secondsLeft;
    /** Action exécutée à zéro (si non annulé). */
    private final Runnable onFinish;

    // --- Affichage -----------------------------------------------------
    /** true = barre d'action ; false = titres plein écran. */
    private final boolean actionBarMode;
    /** Texte affiché en action bar avec "%s" remplacé par les secondes. */
    private final String actionBarFormat;

    // --- Sons ------------------------------------------------------------
    /** Son joué à CHAQUE seconde (null = aucun). */
    private final Sound tickSound;
    private final float tickPitch;
    /** Son joué UNE FOIS à la fin (null = aucun). */
    private final Sound finishSound;
    private final float finishPitch;

    /** Annulation externe (stop/arrêt). */
    private boolean cancelledExternally = false;

    /**
     * Constructeur complet.
     *
     * @param actionBarMode true => action bar (sans title), false => titles
     * @param actionBarFormat format utilisé si actionBarMode, ex :
     *                        "§7Classe : §e%s s"
     * @param tickSound       son de tick (null pour silence)
     * @param tickPitch       hauteur du son de tick
     * @param finishSound     son de fin (null pour silence)
     * @param finishPitch     hauteur du son de fin
     */
    public CountdownTask(XiiPlugin plugin,
                         int seconds,
                         Runnable onFinish,
                         boolean actionBarMode,
                         String actionBarFormat,
                         Sound tickSound, float tickPitch,
                         Sound finishSound, float finishPitch) {
        this.plugin = plugin;
        this.secondsLeft = seconds;
        this.onFinish = onFinish;
        this.actionBarMode = actionBarMode;
        this.actionBarFormat = actionBarFormat;
        this.tickSound = tickSound;
        this.tickPitch = tickPitch;
        this.finishSound = finishSound;
        this.finishPitch = finishPitch;
    }

    @Override
    public void run() {
        if (cancelledExternally) {
            this.cancel();
            return;
        }

        if (secondsLeft <= 0) {
            // Fin normale : son de fin puis action différée d'un tick
            // pour ne jamais muter le monde depuis le scheduler interne.
            this.cancel();
            playFinishSound();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!cancelledExternally && onFinish != null) {
                    onFinish.run();
                }
            });
            return;
        }

        displayTime();
        secondsLeft--;
    }

    /**
     * Affiche le temps restant selon le mode choisi.
     */
    private void displayTime() {
        if (actionBarMode) {
            // Barre au-dessus de la hotbar : "Choisissez votre classe (27 s)".
            String text = String.format(actionBarFormat, secondsLeft);
            for (Player player : Bukkit.getOnlinePlayers()) {
                MessageUtil.sendActionBar(player, text);
            }
            return; // pas de son en mode action bar (spec : 30 s silencieuses)
        }

        // Mode titles : gros chiffre + pling grave (pitch 0.5).
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendTitle(player,
                    "§e" + secondsLeft,
                    null,
                    5, 25, 5);
            if (tickSound != null) {
                com.mceteams.xii.util.SoundUtil.play(player, tickSound, 1.0f, tickPitch);
            }
        }
    }

    /** Son unique de fin de countdown (ex : growl de dragon). */
    private void playFinishSound() {
        if (finishSound == null) {
            return;
        }
        com.mceteams.xii.util.SoundUtil.broadcast(finishSound, 1.0f, finishPitch);
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
