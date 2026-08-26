package com.mceteams.xii.manager;

import com.mceteams.xii.enums.ItemRarity;
import com.mceteams.xii.enums.LootTableId;
import com.mceteams.xii.enums.LootType;
import com.mceteams.xii.enums.PreparationSubPhase;
import com.mceteams.xii.model.LootEntry;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Détient et sélectionne les TABLES DE LOOT (spec : aucune logique
 * Bukkit ici - les coffres/colis restent dans leurs services).
 *
 * Tables (ne jamais mélanger Package / Dungeon) :
 *   PACKAGE_NORMAL · PACKAGE_UPGRADE · PACKAGE_CURSED
 *   DUNGEON_NORMAL · DUNGEON_RARE  · DUNGEON_CURSED
 *
 * SÉLECTION selon la progression (§16) :
 *   Packages : START/PACKAGES/DUNGEON_RESTOCK -> NORMAL,
 *              POINT_UPGRADES/PACKAGE_UPGRADE -> UPGRADE,
 *              + chance fixe de tomber sur CURSED.
 *   Donjons  : NORMAL par défaut, RARE après un restock,
 *              + chance fixe de CURSED.
 *
 * GARANTIE DU TOTEM (§15) :
 *   - resurrectionTotemGenerated appartient à L'ÉTAT DE LA PARTIE ;
 *   - chaque tirage sans Totem augmente la dry-streak ;
 *   - le poids effectif du Totem monte progressivement ;
 *   - après GUARANTEE_AFTER tirages sans Totem, il est FORCÉ ;
 *   - aucun plafond global : plusieurs Totems peuvent sortir.
 */
public class LootManager {

    /** Tirages sans Totem avant garantie absolue. */
    private static final int GUARANTEE_AFTER = 45;
    /** Multiplication du poids du Totem par palier de malchance. */
    private static final double TOTEM_RAMP = 0.25;

    private final Random random = new Random();

    /** Tables enregistrées. */
    private final Map<LootTableId, List<LootEntry>> tables =
            new EnumMap<>(LootTableId.class);

    /** État de partie : un Totem a-t-il déjà été généré ? */
    private boolean resurrectionTotemGenerated = false;
    /** Nombre de tirages consécutifs sans Totem. */
    private int totemDryStreak = 0;

    public LootManager() {
        registerDefaultTables();
    }

    // -----------------------------------------------------------------
    // Enregistrement des tables (poids modifiables ici, équilibrage plus tard)
    // -----------------------------------------------------------------

    private void registerDefaultTables() {
        // ---------------- PACKAGE NORMAL : progression -----------------
        // (poids rares/légendaires volontairement relevés)
        tables.put(LootTableId.PACKAGE_NORMAL, List.of(
                LootEntry.resource(Material.COAL, 10, 4, 12),
                LootEntry.resource(Material.IRON_INGOT, 10, 3, 8),
                LootEntry.resource(Material.COPPER_INGOT, 8, 4, 12),
                LootEntry.resource(Material.GOLD_INGOT, 8, 2, 6),
                LootEntry.resource(Material.LAPIS_LAZULI, 7, 4, 12),
                LootEntry.resource(Material.REDSTONE, 7, 4, 12),
                LootEntry.resource(Material.DIAMOND, 6, 1, 2),
                LootEntry.resource(Material.EMERALD, 6, 1, 2),
                LootEntry.resource(Material.QUARTZ, 6, 3, 8),
                LootEntry.resource(Material.EXPERIENCE_BOTTLE, 6, 1, 3),
                LootEntry.resource(Material.ARROW, 6, 8, 24),
                LootEntry.equipment("iron_sword", 2),
                LootEntry.equipment("iron_pickaxe", 2),
                LootEntry.equipment("bow", 2),
                LootEntry.equipment("iron_helmet", 1),
                LootEntry.equipment("iron_chestplate", 1),
                LootEntry.equipment("iron_leggings", 1),
                LootEntry.equipment("iron_boots", 1),
                LootEntry.upgrade(ItemRarity.COMMON, 3),
                LootEntry.upgrade(ItemRarity.RARE, 1),
                LootEntry.upgrade(ItemRarity.EPIC, 3),
                LootEntry.totem(2)
        ));

        // ---------------- PACKAGE UPGRADE : upgrades dominantes --------
        // (rare plus rare que légendaire/épique : inversion demandée)
        tables.put(LootTableId.PACKAGE_UPGRADE, List.of(
                LootEntry.upgrade(ItemRarity.COMMON, 30),
                LootEntry.upgrade(ItemRarity.RARE, 22),
                LootEntry.upgrade(ItemRarity.EPIC, 28),
                LootEntry.totem(6),
                LootEntry.resource(Material.DIAMOND, 5, 1, 2),
                LootEntry.resource(Material.EMERALD, 5, 1, 2),
                LootEntry.resource(Material.EXPERIENCE_BOTTLE, 6, 2, 4)
        ));

        // ---------------- PACKAGE CURSED : junk + jackpot ---------------
        tables.put(LootTableId.PACKAGE_CURSED, List.of(
                LootEntry.resource(Material.COBWEB, 10, 16, 48),
                LootEntry.resource(Material.STRING, 9, 8, 24),
                LootEntry.resource(Material.ROTTEN_FLESH, 9, 8, 24),
                LootEntry.resource(Material.BONE, 8, 8, 20),
                LootEntry.resource(Material.WHEAT_SEEDS, 7, 4, 16),
                LootEntry.resource(Material.GRAVEL, 8, 8, 24),
                LootEntry.resource(Material.DIRT, 8, 8, 24),
                // Jackpot exceptionnel (risque/récompense) :
                LootEntry.upgrade(ItemRarity.COMMON, 2),
                LootEntry.upgrade(ItemRarity.RARE, 1),
                LootEntry.upgrade(ItemRarity.EPIC, 4),
                LootEntry.totem(3)
        ));

        // ---------------- DUNGEON NORMAL --------------------------------
        tables.put(LootTableId.DUNGEON_NORMAL, List.of(
                LootEntry.resource(Material.IRON_INGOT, 10, 6, 14),
                LootEntry.resource(Material.GOLD_INGOT, 9, 4, 10),
                LootEntry.resource(Material.LAPIS_LAZULI, 7, 6, 14),
                LootEntry.resource(Material.REDSTONE, 7, 6, 14),
                LootEntry.resource(Material.DIAMOND, 8, 1, 3),
                LootEntry.resource(Material.EMERALD, 7, 1, 3),
                LootEntry.resource(Material.QUARTZ, 6, 4, 10),
                LootEntry.resource(Material.EXPERIENCE_BOTTLE, 7, 2, 5),
                LootEntry.resource(Material.ARROW, 6, 16, 32),
                LootEntry.equipment("diamond_sword", 2),
                LootEntry.equipment("diamond_pickaxe", 2),
                LootEntry.equipment("bow", 3),
                LootEntry.equipment("diamond_helmet", 1),
                LootEntry.equipment("diamond_chestplate", 1),
                LootEntry.equipment("diamond_leggings", 1),
                LootEntry.equipment("diamond_boots", 1),
                LootEntry.equipment("strong_iron_helmet", 1),
                LootEntry.equipment("strong_iron_chestplate", 1),
                LootEntry.equipment("strong_iron_leggings", 1),
                LootEntry.equipment("strong_iron_boots", 1),
                LootEntry.upgrade(ItemRarity.RARE, 3),
                LootEntry.upgrade(ItemRarity.EPIC, 5),
                LootEntry.totem(2)
        ));

        // ---------------- DUNGEON RARE : nettement meilleur --------------
        tables.put(LootTableId.DUNGEON_RARE, List.of(
                LootEntry.resource(Material.DIAMOND, 10, 2, 6),
                LootEntry.resource(Material.EMERALD, 9, 2, 5),
                LootEntry.resource(Material.GOLD_INGOT, 9, 8, 20),
                LootEntry.resource(Material.IRON_INGOT, 9, 10, 24),
                LootEntry.resource(Material.EXPERIENCE_BOTTLE, 10, 8, 20),
                LootEntry.equipment("diamond_sword", 3),
                LootEntry.equipment("diamond_pickaxe", 3),
                LootEntry.equipment("bow", 2),
                LootEntry.equipment("diamond_helmet", 2),
                LootEntry.equipment("diamond_chestplate", 2),
                LootEntry.equipment("diamond_leggings", 2),
                LootEntry.equipment("diamond_boots", 2),
                LootEntry.upgrade(ItemRarity.RARE, 10),
                LootEntry.upgrade(ItemRarity.EPIC, 12),
                LootEntry.totem(9)
        ));

        // ---------------- DUNGEON CURSED : déception OU jackpot ----------
        tables.put(LootTableId.DUNGEON_CURSED, List.of(
                LootEntry.resource(Material.COBWEB, 10, 24, 64),
                LootEntry.resource(Material.STRING, 9, 12, 32),
                LootEntry.resource(Material.BONE, 9, 12, 28),
                LootEntry.resource(Material.ROTTEN_FLESH, 8, 12, 32),
                LootEntry.resource(Material.GRAVEL, 8, 12, 32),
                LootEntry.resource(Material.DIRT, 8, 12, 32),
                LootEntry.resource(Material.WHEAT_SEEDS, 7, 8, 24),
                // Jackpot PLUS fréquent que le package maudit :
                LootEntry.upgrade(ItemRarity.COMMON, 3),
                LootEntry.upgrade(ItemRarity.RARE, 2),
                LootEntry.upgrade(ItemRarity.EPIC, 5),
                LootEntry.totem(5)
        ));
    }

    // -----------------------------------------------------------------
    // Sélection de table selon la progression (§16/§17 : pas de if phase
    // dans LootService - c'est ICI que la décision est prise).
    // -----------------------------------------------------------------

    /**
     * Table utilisée pour un colis qui apparaît, selon la sous-phase.
     * Chance fixe de colis maudit (risque/récompense).
     */
    public LootTableId getPackageTable(PreparationSubPhase subPhase) {
        if (random.nextDouble() < 0.12) {
            return LootTableId.PACKAGE_CURSED;
        }
        if (subPhase == PreparationSubPhase.POINT_UPGRADES
                || subPhase == PreparationSubPhase.PACKAGE_UPGRADE) {
            return LootTableId.PACKAGE_UPGRADE;
        }
        return LootTableId.PACKAGE_NORMAL;
    }

    /**
     * Table pour un coffre de donjon : RARE après restock, sinon NORMAL ;
     * chance fixe de donjon maudit.
     */
    public LootTableId getDungeonTable(boolean restockedRareTier) {
        if (random.nextDouble() < 0.10) {
            return LootTableId.DUNGEON_CURSED;
        }
        return restockedRareTier ? LootTableId.DUNGEON_RARE
                : LootTableId.DUNGEON_NORMAL;
    }

    /** @return la liste des entrées d'une table (non modifiable). */
    public List<LootEntry> getEntries(LootTableId id) {
        return tables.get(id);
    }

    /** Aléa partagé pour les quantités (même source que la sélection). */
    public Random getRandom() {
        return random;
    }

    // -----------------------------------------------------------------
    // Garantie du Totem (§15)
    // -----------------------------------------------------------------

    /** Un Totem a-t-il déjà été généré pendant cette partie ? */
    public boolean isResurrectionTotemGenerated() {
        return resurrectionTotemGenerated;
    }

    /**
     * Multiplicateur appliqué au poids du Totem : croît avec la
     * malchance accumulée (aucun effet une fois un Totem obtenu).
     */
    public double getTotemWeightMultiplier() {
        return resurrectionTotemGenerated ? 1.0
                : 1.0 + TOTEM_RAMP * totemDryStreak;
    }

    /** La garantie absolue est-elle active ? (fin de fenêtre de loot) */
    public boolean mustGuaranteeTotem() {
        return !resurrectionTotemGenerated && totemDryStreak >= GUARANTEE_AFTER;
    }

    /** Appelé quand un TOTEM sort d'une table. */
    public void notifyTotemGenerated() {
        resurrectionTotemGenerated = true;
        totemDryStreak = 0; // le multiplicateur redevient neutre
    }

    /** Appelé à chaque tirage (table contenant un Totem) sans Totem. */
    public void notifyNonTotemRoll() {
        if (!resurrectionTotemGenerated) {
            totemDryStreak++;
        }
    }

    /** Remise à zéro au lancement d'une nouvelle partie. */
    public void resetForNewGame() {
        resurrectionTotemGenerated = false;
        totemDryStreak = 0;
    }

    /** Compteur exposé pour debug/affichage. */
    public int getTotemDryStreak() {
        return totemDryStreak;
    }

    /** Type utilitaire exposé pour les services (LootType.TOTEM etc.). */
    public static LootType totemType() {
        return LootType.TOTEM;
    }
}
