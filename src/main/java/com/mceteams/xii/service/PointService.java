package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PointEvent;
import com.mceteams.xii.model.PlayerData;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Attribution centralisée des points (spec §32).
 *
 * RÈGLE CLÉ : les multiplicateurs sont centralisés ICI et nulle part
 * ailleurs (spec §32 : "Ne pas coder les multiplicateurs directement
 * dans tous les services").
 *
 * Chaîne appliquée :
 *   points de base
 *     x 2 si POINT_UPGRADES (toutes catégories, spec §17)
 *     x 2 si MORE_METEORITES (catégories "terrain" : minage/exploration,
 *       spec §25 - hypothèse documentée : les mécaniques concernées par
 *       les météorites sont le terrain, donc minage + exploration)
 *     x 1.25 si l'attributaire est TRAVAILLEUR (bonus équipe, spec §31)
 *
 * Les points sont écrits à la fois sur PlayerScore ET TeamScore.
 */
public class PointService {

    private final XiiPlugin plugin;
    /** Historique des derniers événements (debug / future extension). */
    private final List<PointEvent> recentEvents = new ArrayList<>();

    public PointService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Calcul PUR des multiplicateurs (testable sans serveur - spec §37)
    // -----------------------------------------------------------------

    /**
     * Multiplicateur final pour une catégorie donnée.
     *
     * @param category            catégorie de points visée
     * @param workerBonus         true si l'attributaire est Travailleur
     * @param doublePointsEvent   true si sous-phase POINT_UPGRADES
     * @param moreMeteoritesEvent true si sous-phase MORE_METEORITES
     */
    public static double computeMultiplier(PointCategory category,
                                           boolean workerBonus,
                                           boolean doublePointsEvent,
                                           boolean moreMeteoritesEvent) {
        double multiplier = 1.0;

        if (doublePointsEvent) {
            multiplier *= 2.0;      // POINT_UPGRADES : tout x2 (§17)
        }
        if (moreMeteoritesEvent
                && (category == PointCategory.MINING
                || category == PointCategory.EXPLORATION)) {
            multiplier *= 2.0;      // MORE_METEORITES : terrain x2 (§25)
        }
        if (workerBonus) {
            multiplier *= 1.25;     // Travailleur : +25% (§31)
        }
        return multiplier;
    }

    // -----------------------------------------------------------------
    // Attribution
    // -----------------------------------------------------------------

    /**
     * Attribue des points à un joueur ET à son équipe.
     *
     * @param player joueur attributaire (peut être null : points d'équipe
     *               uniquement, ex destruction de coeur automatique)
     * @param baseAmount montant de base AVANT multiplicateurs
     */
    public void award(Player player, PointCategory category,
                      int baseAmount, String reason) {
        if (baseAmount <= 0) {
            return;
        }

        PlayerData data = player != null
                ? plugin.getPlayerManager().getData(player) : null;
        GameTeam team = data != null && data.hasTeam()
                ? plugin.getTeamManager().getTeamOf(data.getUuid()) : null;

        int finalAmount = computeFinalAmount(data, team, category, baseAmount);

        // Écriture joueur.
        if (data != null) {
            data.getScore().add(category, finalAmount);
        }
        // Écriture équipe (le Travailleur booste "les points pour
        // l'équipe" : le bonus s'applique donc aux deux niveaux).
        if (team != null) {
            team.getScore().add(category, finalAmount);
        }

        recordEvent(player, team, category, finalAmount, reason);

        // Feedback discret en action bar (+N points).
        if (player != null && finalAmount > 0) {
            MessageUtil.sendActionBar(player,
                    "§a+" + finalAmount + " pts §7(" + reason + ")");
        }
    }

    /**
     * Retire des points (pénalité de mort, spec §19 point 3).
     * La pénalité est enregistrée sur le joueur ET son équipe.
     */
    public void remove(Player player, int amount, String reason) {
        if (player == null || amount <= 0) {
            return;
        }
        var data = plugin.getPlayerManager().getData(player);
        data.getScore().addPenalty(amount);

        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (team != null) {
            team.getScore().addPenalty(amount);
        }
        recordEvent(player, team, null, -amount, reason);
    }

    /**
     * Calcule le montant FINAL en résolvant tous les multiplicateurs.
     * C'est le seul endroit du plugin qui connaît la chaîne complète.
     */
    private int computeFinalAmount(PlayerData data, GameTeam team,
                                   PointCategory category, int baseAmount) {
        boolean workerBonus = isWorkerActive(team);
        boolean doublePoints =
                plugin.getPhaseManager().getPhase()
                        == com.mceteams.xii.enums.GamePhase.PREPARATION
                        && plugin.getPhaseManager().getPreparationSubPhase()
                        == com.mceteams.xii.enums.PreparationSubPhase.POINT_UPGRADES;
        boolean moreMeteorites =
                plugin.getPhaseManager().getCombatSubPhase()
                        == CombatSubPhase.MORE_METEORITES;

        double multiplier = computeMultiplier(
                category, workerBonus, doublePoints, moreMeteorites);

        return Math.max(1, (int) Math.round(baseAmount * multiplier));
    }

    /**
     * Le bonus Travailleur est-il actif ? Le bonus profite à l'ÉQUIPE :
     * il suffit qu'UN membre de l'équipe soit Travailleur.
     */
    private boolean isWorkerActive(GameTeam team) {
        if (team == null) {
            return false;
        }
        for (var memberUuid : team.getPlayers()) {
            PlayerData member = plugin.getPlayerManager().getData(memberUuid);
            if (member.getPlayerClass() == PlayerClass.WORKER) {
                return true;
            }
        }
        return false;
    }

    /** Journalise l'événement (logs + historique borné). */
    private void recordEvent(Player player, GameTeam team,
                             PointCategory category, int amount, String reason) {
        try {
            recentEvents.add(new PointEvent(
                    player != null ? player.getUniqueId() : null,
                    team != null ? teamColorKey(team) : null,
                    category, amount, reason));
            if (recentEvents.size() > 200) {
                recentEvents.remove(0); // borne l'historique mémoire
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.FINE,
                    "PointService: événement non journalisé", exception);
        }
    }

    /** Clé stable d'une équipe (pour PointEvent). */
    private java.util.UUID teamColorKey(GameTeam team) {
        return java.util.UUID.nameUUIDFromBytes(
                team.getColor().name().getBytes());
    }

    /** Vide l'historique (nouvelle partie). */
    public void resetMatchState() {
        recentEvents.clear();
    }
}
