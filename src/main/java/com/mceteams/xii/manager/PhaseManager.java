package com.mceteams.xii.manager;

import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.enums.PreparationSubPhase;

import java.util.function.Consumer;

/**
 * Gère la phase courante et les sous-phases (spec §17/§20).
 *
 * Cette classe est VOLONTAIREMENT sans dépendance Bukkit : elle ne fait
 * qu'avancer un curseur temporel et prévenir un "hook" à chaque début
 * de sous-phase. GameManager s'abonne au hook pour déclencher les
 * mécaniques (colis, météorites, restock...).
 *
 * Découpage officiel :
 * - PRÉPARATION : 6 sous-phases de 10 minutes (START..DUNGEON_RESTOCK)
 * - COMBAT      : 6 sous-phases de 10 minutes (START..SUDDEN_DEATH)
 *
 * Les 12 sous-phases forment les "12 jours" de XII Days :
 * jour N = sous-phase N (1..6 préparation, 7..12 combat).
 */
public class PhaseManager {

    /** Phase courante (NONE en dehors du gameplay). */
    private GamePhase phase = GamePhase.NONE;
    /** Sous-phase de préparation courante (si phase = PREPARATION). */
    private PreparationSubPhase preparationSubPhase;
    /** Sous-phase de combat courante (si phase = COMBAT). */
    private CombatSubPhase combatSubPhase;
    /** Secondes écoulées dans la sous-phase courante. */
    private int elapsedInSubPhase = 0;

    /**
     * Hook appelé à chaque DÉBUT d'une sous-phase.
     * L'objet passé est PreparationSubPhase ou CombatSubPhase.
     */
    private Consumer<Object> subPhaseStartHook;

    // -----------------------------------------------------------------
    // Cycle de vie
    // -----------------------------------------------------------------

    /** Remet le curseur à zéro (retour WAITING / arrêt de partie). */
    public void reset() {
        phase = GamePhase.NONE;
        preparationSubPhase = null;
        combatSubPhase = null;
        elapsedInSubPhase = 0;
    }

    /** Démarre officiellement la PRÉPARATION sur sa première sous-phase. */
    public void startPreparation() {
        phase = GamePhase.PREPARATION;
        preparationSubPhase = PreparationSubPhase.START;
        combatSubPhase = null;
        elapsedInSubPhase = 0;
        fireSubPhaseStart(preparationSubPhase);
    }

    /** Passe en COMBAT (déclenché quand les 6 sous-phases prep finies). */
    public void startCombat() {
        phase = GamePhase.COMBAT;
        preparationSubPhase = null;
        combatSubPhase = CombatSubPhase.START;
        elapsedInSubPhase = 0;
        fireSubPhaseStart(combatSubPhase);
    }

    /**
     * Horloge : à appeler UNE FOIS PAR SECONDE par la PhaseTask.
     *
     * @param subPhaseDurationSeconds durée d'une sous-phase (600 par défaut)
     * @return le résultat du tick.
     */
    public TickResult tickSecond(int subPhaseDurationSeconds) {
        if (phase != GamePhase.PREPARATION && phase != GamePhase.COMBAT) {
            return TickResult.INACTIVE;
        }
        elapsedInSubPhase++;
        if (elapsedInSubPhase < subPhaseDurationSeconds) {
            return TickResult.CONTINUING;
        }
        return advance();
    }

    /**
     * Avance d'une sous-phase (logique pure, testable sans serveur).
     */
    public AdvanceResult advance() {
        if (phase == GamePhase.PREPARATION) {
            PreparationSubPhase next = nextOf(preparationSubPhase);
            if (next != null) {
                preparationSubPhase = next;
                elapsedInSubPhase = 0;
                fireSubPhaseStart(next);
                return AdvanceResult.ADVANCED_SUB_PHASE;
            }
            // Fin des 6 sous-phases de préparation => COMBAT.
            startCombat();
            return AdvanceResult.ENTERED_COMBAT;
        }
        if (phase == GamePhase.COMBAT) {
            CombatSubPhase next = nextOf(combatSubPhase);
            if (next != null) {
                combatSubPhase = next;
                elapsedInSubPhase = 0;
                fireSubPhaseStart(next);
                return AdvanceResult.ADVANCED_SUB_PHASE;
            }
            // Fin de SUDDEN_DEATH => fin automatique de partie (§26/§27).
            return AdvanceResult.GAME_OVER;
        }
        return AdvanceResult.INACTIVE;
    }

    /** Sous-phase de préparation suivante (null si terminée). */
    private PreparationSubPhase nextOf(PreparationSubPhase current) {
        PreparationSubPhase[] values = PreparationSubPhase.values();
        int index = current.ordinal();
        return index + 1 < values.length ? values[index + 1] : null;
    }

    /** Sous-phase de combat suivante (null si terminée). */
    private CombatSubPhase nextOf(CombatSubPhase current) {
        CombatSubPhase[] values = CombatSubPhase.values();
        int index = current.ordinal();
        return index + 1 < values.length ? values[index + 1] : null;
    }

    // -----------------------------------------------------------------
    // Saut direct (/party set <jour>, spec §34)
    // -----------------------------------------------------------------

    /**
     * Saute directement au "jour" donné (1..12) en déclenchant les hooks
     * normaux, comme si la partie y était arrivée naturellement.
     *
     * @return true si le saut a été effectué.
     */
    public boolean skipToDay(int day) {
        if (day < 1 || day > 12) {
            return false;
        }
        if (day <= 6) {
            phase = GamePhase.PREPARATION;
            preparationSubPhase = PreparationSubPhase.values()[day - 1];
            combatSubPhase = null;
        } else {
            phase = GamePhase.COMBAT;
            combatSubPhase = CombatSubPhase.values()[day - 7];
            preparationSubPhase = null;
        }
        elapsedInSubPhase = 0;
        fireSubPhaseStart(day <= 6 ? preparationSubPhase : combatSubPhase);
        return true;
    }

    /**
     * @return le jour courant (1..12), ou 0 hors gameplay.
     */
    public int currentDay() {
        if (phase == GamePhase.PREPARATION && preparationSubPhase != null) {
            return preparationSubPhase.ordinal() + 1;
        }
        if (phase == GamePhase.COMBAT && combatSubPhase != null) {
            return combatSubPhase.ordinal() + 7;
        }
        return 0;
    }

    // -----------------------------------------------------------------
    // Lectures
    // -----------------------------------------------------------------

    public GamePhase getPhase() {
        return phase;
    }

    public PreparationSubPhase getPreparationSubPhase() {
        return preparationSubPhase;
    }

    public CombatSubPhase getCombatSubPhase() {
        return combatSubPhase;
    }

    public int getElapsedInSubPhase() {
        return elapsedInSubPhase;
    }

    /**
     * Temps restant dans la sous-phase courante (pour l'affichage).
     */
    public int getRemainingSeconds(int subPhaseDurationSeconds) {
        return Math.max(0, subPhaseDurationSeconds - elapsedInSubPhase);
    }

    /** Enregistre le hook de début de sous-phase. */
    public void setSubPhaseStartHook(Consumer<Object> hook) {
        this.subPhaseStartHook = hook;
    }

    private void fireSubPhaseStart(Object subPhase) {
        if (subPhaseStartHook != null) {
            subPhaseStartHook.accept(subPhase);
        }
    }

    // -----------------------------------------------------------------
    // Types de résultat
    // -----------------------------------------------------------------

    /** Résultat d'un tick de l'horloge. */
    public enum TickResult {
        INACTIVE,
        CONTINUING
    }

    /** Résultat d'une avance de sous-phase. */
    public enum AdvanceResult {
        ADVANCED_SUB_PHASE,
        ENTERED_COMBAT,
        GAME_OVER,
        INACTIVE
    }
}
