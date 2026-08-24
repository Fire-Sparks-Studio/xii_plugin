package com.mceteams.xii.model;

import com.mceteams.xii.enums.ItemRarity;
import com.mceteams.xii.enums.LootType;
import org.bukkit.Material;

/**
 * Entrée DÉCLARATIVE d'une loot table (spec : pas de logique de
 * génération ici, seule la description).
 *
 * - RESOURCE : material + quantité min/max ;
 * - EQUIPMENT : variant (clé de construction dans LootService), qty=1 ;
 * - UPGRADE  : rarityFilter = rareté tirée parmi les upgrades correspondantes
 *              (jamais le Totem, qui a son propre type) ;
 * - TOTEM    : légendaire sans niveau ni limite globale.
 */
public class LootEntry {

    private final LootType type;
    /** Matériau pour RESOURCE. */
    private final Material material;
    /** Clé de variante pour EQUIPMENT (ex : "iron_sword"). */
    private final String variant;
    /** Filtre de rareté pour UPGRADE (null sinon). */
    private final ItemRarity rarityFilter;
    /** Poids relatif dans le tirage pondéré. */
    private final int weight;
    /** Quantité minimale (RESOURCE uniquement). */
    private final int min;
    /** Quantité maximale (RESOURCE uniquement). */
    private final int max;

    private LootEntry(LootType type, Material material, String variant,
                      ItemRarity rarityFilter, int weight, int min, int max) {
        this.type = type;
        this.material = material;
        this.variant = variant;
        this.rarityFilter = rarityFilter;
        this.weight = Math.max(1, weight);
        this.min = min;
        this.max = max;
    }

    // --- Fabriques lisibles ------------------------------------------------

    public static LootEntry resource(Material material, int weight, int min, int max) {
        return new LootEntry(LootType.RESOURCE, material, null, null,
                weight, min, max);
    }

    public static LootEntry equipment(String variant, int weight) {
        return new LootEntry(LootType.EQUIPMENT, null, variant, null,
                weight, 1, 1);
    }

    public static LootEntry upgrade(ItemRarity rarityFilter, int weight) {
        return new LootEntry(LootType.UPGRADE, null, null, rarityFilter,
                weight, 1, 1);
    }

    public static LootEntry totem(int weight) {
        return new LootEntry(LootType.TOTEM, null, null, null,
                weight, 1, 1);
    }

    // --- Getters -------------------------------------------------------------

    public LootType getType() {
        return type;
    }

    public Material getMaterial() {
        return material;
    }

    public String getVariant() {
        return variant;
    }

    public ItemRarity getRarityFilter() {
        return rarityFilter;
    }

    public int getWeight() {
        return weight;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}
