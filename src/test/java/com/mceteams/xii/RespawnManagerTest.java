package com.mceteams.xii;

import com.mceteams.xii.manager.RespawnManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires du RespawnManager (spec §37).
 * Vérification du calcul PUR du délai (spec §19) :
 *   1ère mort = 5 s, +5 s par mort, maximum 30 s.
 */
class RespawnManagerTest {

    private static final int STEP = 5;
    private static final int MAX = 30;

    @Test
    void firstDeathIsFiveSeconds() {
        assertEquals(5, RespawnManager.computeDelaySeconds(1, STEP, MAX));
    }

    @Test
    void eachDeathAddsFiveSeconds() {
        assertEquals(10, RespawnManager.computeDelaySeconds(2, STEP, MAX));
        assertEquals(15, RespawnManager.computeDelaySeconds(3, STEP, MAX));
        assertEquals(20, RespawnManager.computeDelaySeconds(4, STEP, MAX));
        assertEquals(25, RespawnManager.computeDelaySeconds(5, STEP, MAX));
    }

    @Test
    void sixthDeathReachesThirtySecondCap() {
        assertEquals(30, RespawnManager.computeDelaySeconds(6, STEP, MAX));
    }

    @Test
    void capNeverExceeded() {
        assertEquals(30, RespawnManager.computeDelaySeconds(7, STEP, MAX));
        assertEquals(30, RespawnManager.computeDelaySeconds(50, STEP, MAX));
    }

    @Test
    void customValuesAreRespected() {
        // Valeurs de config alternatives (respawn.step/max dans config.yml).
        assertEquals(8, RespawnManager.computeDelaySeconds(2, 4, 40));
        assertEquals(40, RespawnManager.computeDelaySeconds(11, 4, 40));
    }

    @Test
    void zeroOrNegativeCountFallsBackToStep() {
        assertEquals(STEP, RespawnManager.computeDelaySeconds(0, STEP, MAX));
        assertEquals(STEP, RespawnManager.computeDelaySeconds(-3, STEP, MAX));
    }
}
