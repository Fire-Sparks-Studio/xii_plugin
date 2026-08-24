package com.mceteams.xii;

import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.enums.PreparationSubPhase;
import com.mceteams.xii.manager.PhaseManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du PhaseManager (spec §37).
 * La classe est volontairement sans Bukkit : cycle des 12 sous-phases
 * vérifié sans serveur.
 */
class PhaseManagerTest {

    private static final int SUB_PHASE_DURATION = 600; // 10 minutes

    @Test
    void startsInactive() {
        PhaseManager manager = new PhaseManager();
        assertEquals(GamePhase.NONE, manager.getPhase());
        assertEquals(0, manager.currentDay());
        assertEquals(PhaseManager.AdvanceResult.INACTIVE,
                manager.tickSecond(SUB_PHASE_DURATION));
    }

    @Test
    void preparationAdvancesThroughSixSubPhases() {
        PhaseManager manager = new PhaseManager();
        manager.startPreparation();

        assertEquals(GamePhase.PREPARATION, manager.getPhase());
        assertEquals(PreparationSubPhase.START, manager.getPreparationSubPhase());
        assertEquals(1, manager.currentDay());

        // 5 avances pour passer de START à DUNGEON_RESTOCK (6e sous-phase).
        for (int i = 0; i < 5; i++) {
            assertEquals(PhaseManager.AdvanceResult.ADVANCED_SUB_PHASE,
                    manager.advance());
        }
        assertEquals(PreparationSubPhase.DUNGEON_RESTOCK,
                manager.getPreparationSubPhase());
        assertEquals(6, manager.currentDay());
    }

    @Test
    void preparationEndsIntoCombat() {
        PhaseManager manager = new PhaseManager();
        manager.startPreparation();

        // Épuise les 6 sous-phases de préparation.
        for (int i = 0; i < 6; i++) {
            manager.advance();
        }
        // La 6e avance déclenche l'entrée en COMBAT.
        assertEquals(GamePhase.COMBAT, manager.getPhase());
        assertEquals(CombatSubPhase.START, manager.getCombatSubPhase());
        assertEquals(7, manager.currentDay());
    }

    @Test
    void combatEndsIntoGameOver() {
        PhaseManager manager = new PhaseManager();
        manager.startCombat();

        // 5 avances => SUDDEN_DEATH (12e jour).
        for (int i = 0; i < 5; i++) {
            assertEquals(PhaseManager.AdvanceResult.ADVANCED_SUB_PHASE,
                    manager.advance());
        }
        assertEquals(CombatSubPhase.SUDDEN_DEATH, manager.getCombatSubPhase());
        assertEquals(12, manager.currentDay());

        // Fin de SUDDEN_DEATH => fin automatique de partie (spec §26).
        assertEquals(PhaseManager.AdvanceResult.GAME_OVER, manager.advance());
    }

    @Test
    void tickSecondCountsThenAdvances() {
        PhaseManager manager = new PhaseManager();
        manager.startCombat();

        // Durée - 1 ticks : on reste dans la sous-phase.
        for (int i = 0; i < SUB_PHASE_DURATION - 1; i++) {
            assertEquals(PhaseManager.AdvanceResult.CONTINUING,
                    manager.tickSecond(SUB_PHASE_DURATION));
            assertTrue(manager.getRemainingSeconds(SUB_PHASE_DURATION) > 0);
        }
        // Le dernier tick fait basculer sur la sous-phase suivante.
        assertEquals(PhaseManager.AdvanceResult.ADVANCED_SUB_PHASE,
                manager.tickSecond(SUB_PHASE_DURATION));
        assertEquals(CombatSubPhase.METEORITES, manager.getCombatSubPhase());
        assertEquals(0, manager.getElapsedInSubPhase(), "compteur remis à zéro");
    }

    @Test
    void skipToDayJumpsAnywhere() {
        PhaseManager manager = new PhaseManager();
        manager.startPreparation();

        assertFalse(manager.skipToDay(0), "jour invalide refusé");
        assertFalse(manager.skipToDay(13), "jour invalide refusé");

        // Mapping jour -> sous-phase : jour 1 = START, 2 = PACKAGES,
        // 3 = DUNGEONS, 4 = POINT_UPGRADES...
        assertTrue(manager.skipToDay(3));
        assertEquals(GamePhase.PREPARATION, manager.getPhase());
        assertEquals(PreparationSubPhase.DUNGEONS,
                manager.getPreparationSubPhase());

        assertTrue(manager.skipToDay(4));
        assertEquals(PreparationSubPhase.POINT_UPGRADES,
                manager.getPreparationSubPhase());

        // Jour 12 = SUDDEN_DEATH en combat.
        assertTrue(manager.skipToDay(12));
        assertEquals(GamePhase.COMBAT, manager.getPhase());
        assertEquals(CombatSubPhase.SUDDEN_DEATH, manager.getCombatSubPhase());
    }

    @Test
    void subPhaseStartHookIsCalled() {
        PhaseManager manager = new PhaseManager();
        int[] hookCalls = {0};
        manager.setSubPhaseStartHook(sub -> hookCalls[0]++);

        manager.startPreparation();          // hook START
        manager.advance();                   // hook PACKAGES
        assertEquals(2, hookCalls[0]);
    }
}
