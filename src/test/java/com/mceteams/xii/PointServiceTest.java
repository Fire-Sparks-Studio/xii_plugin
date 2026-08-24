package com.mceteams.xii;

import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.service.PointService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires du PointService (spec §37).
 * On teste le calcul PUR des multiplicateurs : aucun serveur requis.
 */
class PointServiceTest {

    @Test
    void baseMultiplierIsOne() {
        assertEquals(1.0, PointService.computeMultiplier(
                PointCategory.MINING, false, false, false));
    }

    @Test
    void workerBonusMultipliesBy125() {
        // Travailleur : +25% de points pour l'équipe (spec §31).
        assertEquals(1.25, PointService.computeMultiplier(
                PointCategory.MINING, true, false, false), 1e-9);
    }

    @Test
    void pointUpgradesDoublesEverything() {
        // POINT_UPGRADES : x2 sur TOUTES les catégories (spec §17).
        for (PointCategory category : PointCategory.values()) {
            assertEquals(2.0, PointService.computeMultiplier(
                    category, false, true, false), 1e-9,
                    "Catégorie : " + category);
        }
    }

    @Test
    void moreMeteoritesOnlyDoublesTerrainCategories() {
        // MORE_METEORITES : x2 UNIQUEMENT minage/exploration (spec §25).
        assertEquals(2.0, PointService.computeMultiplier(
                PointCategory.MINING, false, false, true), 1e-9);
        assertEquals(2.0, PointService.computeMultiplier(
                PointCategory.EXPLORATION, false, false, true), 1e-9);
        // Le combat n'est pas une mécanique "terrain" : inchangé.
        assertEquals(1.0, PointService.computeMultiplier(
                PointCategory.KILL, false, false, true), 1e-9);
        assertEquals(1.0, PointService.computeMultiplier(
                PointCategory.CORE, false, false, true), 1e-9);
    }

    @Test
    void allMultipliersStack() {
        // POINT_UPGRADES (x2) + Travailleur (+25%) => x2.5.
        assertEquals(2.5, PointService.computeMultiplier(
                PointCategory.EXPLORATION, true, true, false), 1e-9);

        // POINT_UPGRADES + MORE_METEORITES + Travailleur sur du minage
        // => 2 * 2 * 1.25 = 5.0.
        assertEquals(5.0, PointService.computeMultiplier(
                PointCategory.MINING, true, true, true), 1e-9);
    }
}
