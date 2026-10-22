package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * ZONE DE COMBAT : rayon de sécurité qui se RÉDUIT pendant la phase de
 * combat.
 *
 * PRINCIPE
 * - Au DÉBUT de la phase de combat, la zone de sécurité couvre toute la
 *   carte (rayon = zone.size / 2, soit 1000 blocs depuis le centre 0,0).
 * - Elle se RÉDUIT LINÉAIREMENT jusqu'au rayon final (150 blocs) en
 *   {@code zone.combat-zone.shrink-seconds} secondes (900 = 15 min).
 * - Passé ce délai, le rayon reste bloqué à 150 blocs.
 * - Les bases sont posées à 100 blocs du centre : elles restent donc
 *   TOUJOURS accessibles.
 * - Un joueur situé AU-DELÀ du rayon subit des dégâts continue : il doit
 *   réintégrer le cercle.
 *
 * La progression est recalculée à partir de l'HORLOIRE DES PHASES
 * (PhaseManager) : un saut de jour (/party set) est donc pris en compte
 * automatiquement, la zone reste toujours cohérente avec le jour courant.
 *
 * Aucun mur de blocs n'est posé (trop coûteux à 1000 blocs de rayon) :
 * l'information passe par le scoreboard, la barre d'action et des
 * annonces générales.
 */
public class CombatZoneManager {

    private final XiiPlugin plugin;

    /** Rayon de sécurité courant (blocs depuis le centre de la zone). */
    private double currentRadius;

    /** Rayon de départ (au début du combat). */
    private double startRadius;

    /** Rayon final (atteint à la fin de la réduction). */
    private double endRadius;

    /** Durée totale de la réduction (secondes). */
    private int shrinkSeconds;

    /** La zone est-elle active (combat en cours) ? */
    private boolean active;

    /** Dernier palier d'annonce effectué (palier de 5 minutes). */
    private int lastAnnouncedMark = -1;

    public CombatZoneManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Cycle de vie
    // -----------------------------------------------------------------

    /**
     * Démarre la zone de combat (appelé à l'entrée dans la phase COMBAT).
     *
     * Le rayon de départ est réinitialisé : si le combat a été rejoint
     * par un saut de jour, la réduction reprend là où l'horloge des
     * phases l'indique (le rayon courant est alors recalculé
     * immédiatement).
     */
    public void start() {
        if (!plugin.getConfigManager().isCombatZoneEnabled()) {
            active = false;
            currentRadius = 0;
            return;
        }
        GameZone zone = plugin.getZoneManager().getZone();
        if (zone == null) {
            active = false;
            currentRadius = 0;
            return;
        }

        startRadius = resolveStartRadius(zone);
        endRadius = Math.min(plugin.getConfigManager().getCombatZoneEndRadius(),
                startRadius);
        shrinkSeconds = plugin.getConfigManager().getCombatZoneShrinkSeconds();
        active = true;
        lastAnnouncedMark = -1;

        // Recalage immédiat sur l'horloge des phases (saut de jour).
        updateRadius();

        MessageUtil.broadcast("§8§m                                        ");
        MessageUtil.broadcast("§c§lZONE DE COMBAT §7: la zone se réduit autour du centre !");
        MessageUtil.broadcast("§7Rayon final : §b" + (int) Math.ceil(endRadius)
                + " §7blocs dans "
                + formatDuration(shrinkSeconds) + ".");
        MessageUtil.broadcast("§c§lATTENTION §7: hors de la zone, vous subissez des dégâts.");
        MessageUtil.broadcast("§8§m                                        ");
    }

    /**
     * Démarre la zone de combat si elle ne l'est pas déjà, sinon se
     * recale sur l'horloge des phases (cas d'un saut de jour).
     */
    public void ensureStarted() {
        if (active) {
            updateRadius();
            return;
        }
        start();
    }

    /** Arrête la zone de combat (fin de partie, retour en attente...). */
    public void stop() {
        active = false;
        currentRadius = 0;
        lastAnnouncedMark = -1;
    }

    // -----------------------------------------------------------------
    // Boucle
    // -----------------------------------------------------------------

    /**
     * Tick called chaque seconde par {@link com.mceteams.xii.task.CombatZoneTask}.
     */
    public void tickSecond() {
        if (!active) {
            return;
        }
        if (plugin.getPhaseManager().getPhase() != GamePhase.COMBAT) {
            stop();
            return;
        }
        updateRadius();
        announceMilestones();
        applyOutsideDamage();
    }

    /** Recalcule le rayon courant à partir de l'horloge des phases. */
    private void updateRadius() {
        int elapsed = combatElapsedSeconds();
        double progress = Math.min(1.0D, (double) elapsed / shrinkSeconds);
        currentRadius = startRadius + (endRadius - startRadius) * progress;
    }

    /**
     * Secondes écoulées depuis le DÉBUT de la phase de combat.
     *
     * Calculé depuis l'horloge des phases : un saut de jour (/party set 9)
     * est donc immédiatement pris en compte.
     */
    private int combatElapsedSeconds() {
        var phaseManager = plugin.getPhaseManager();
        if (phaseManager.getPhase() != GamePhase.COMBAT) {
            return 0;
        }
        CombatSubPhase sub = phaseManager.getCombatSubPhase();
        int duration = plugin.getConfigManager().getSubPhaseDurationSeconds();
        int ordinal = sub == null ? 0 : sub.ordinal();
        return ordinal * duration + phaseManager.getElapsedInSubPhase();
    }

    /**
     * Annonces générales aux paliers (toutes les 5 minutes de combat) et
     * à la fin de la réduction.
     */
    private void announceMilestones() {
        int elapsed = combatElapsedSeconds();
        if (elapsed >= shrinkSeconds) {
            if (lastAnnouncedMark != -2) {
                lastAnnouncedMark = -2;
                MessageUtil.broadcast("§8§m                                        ");
                MessageUtil.broadcast("§c§lZONE DE COMBAT §7: la zone est maintenant fixée à §b"
                        + (int) Math.ceil(endRadius) + " §7blocs.");
                MessageUtil.broadcast("§8§m                                        ");
            }
            return;
        }
        int remaining = shrinkSeconds - elapsed;
        // Palier de 5 minutes : 300, 600, ... secondes restantes.
        int mark = remaining / 300;
        if (mark <= 0 || mark == lastAnnouncedMark) {
            return;
        }
        lastAnnouncedMark = mark;
        MessageUtil.broadcast("§8§m                                        ");
        MessageUtil.broadcast("§7La zone se réduit : §b"
                + (int) Math.ceil(currentRadius) + " §7blocs"
                + " (§c" + formatDuration(remaining) + " §7restants).");
        MessageUtil.broadcast("§8§m                                        ");
    }

    /**
     * Applique les dégâts aux joueurs situés hors de la zone et affiche
     * une alerte dans leur barre d'action.
     */
    private void applyOutsideDamage() {
        GameZone zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        double damage = plugin.getConfigManager().getCombatZoneDamage();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != zone.getWorld()) {
                continue;
            }
            var data = plugin.getPlayerManager().getData(player);
            if (data == null || !data.isAlive() || data.isEliminated()
                    || data.isSpectator()) {
                continue;
            }
            if (isInside(player.getLocation())) {
                continue;
            }
            if (damage > 0) {
                player.damage(damage);
            }
            MessageUtil.sendActionBar(player,
                    "§c§lHORS DE LA ZONE §7rejoignez le centre !");
        }
    }

    // -----------------------------------------------------------------
    // Accesseurs
    // -----------------------------------------------------------------

    /** Rayon de sécurité courant (en blocs). */
    public double getCurrentRadius() {
        return currentRadius;
    }

    /** Rayon de départ (au début du combat). */
    public double getStartRadius() {
        return startRadius;
    }

    /** Rayon final de la zone. */
    public double getEndRadius() {
        return endRadius;
    }

    /** La zone de combat est-elle active ? */
    public boolean isActive() {
        return active;
    }

    /** Seconds restantes avant d'atteindre le rayon final (0 si fini). */
    public int getRemainingShrinkSeconds() {
        if (!active) {
            return 0;
        }
        return Math.max(0, shrinkSeconds - combatElapsedSeconds());
    }

    /**
     * Le joueur est-il à l'intérieur de la zone de sécurité ?
     *
     * Test en X/Z uniquement (la hauteur n'entre pas en compte).
     */
    public boolean isInside(Location location) {
        if (!active || location == null || location.getWorld() == null) {
            return true;
        }
        GameZone zone = plugin.getZoneManager().getZone();
        if (zone == null) {
            return true;
        }
        if (!location.getWorld().getName().equals(zone.getWorldName())) {
            return true; // autre monde : hors du périmètre de la zone
        }
        double dx = location.getX() - zone.getCenterX();
        double dz = location.getZ() - zone.getCenterZ();
        return (dx * dx + dz * dz) <= currentRadius * currentRadius;
    }

    // -----------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------

    /** Rayon de départ : celui de la config, ou la moitié de la zone. */
    private double resolveStartRadius(GameZone zone) {
        int configured = plugin.getConfigManager().getCombatZoneStartRadius();
        if (configured > 0) {
            return configured;
        }
        return zone.getSize() / 2.0D;
    }

    /** Formate une durée en secondes : "12:34" ou "1:05:30". */
    private String formatDuration(int seconds) {
        int s = Math.max(0, seconds);
        int hours = s / 3600;
        int minutes = (s % 3600) / 60;
        int secs = s % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }
}
