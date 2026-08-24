package com.mceteams.xii.enums;

/**
 * Type d'une entrée de loot table. Détermine comment LootService
 * construit la récompense concrète à partir de l'entrée.
 */
public enum LootType {
    /** Ressource simple empilable (minerais, junk, flèches, XP...). */
    RESOURCE,
    /** Équipement enchanté (variant décrit dans l'entrée). */
    EQUIPMENT,
    /** Upgrade consommable tirée selon une rareté (jamais le Totem ici). */
    UPGRADE,
    /** Totem de Résurrection : légendaire sans niveau, sans limite. */
    TOTEM
}
