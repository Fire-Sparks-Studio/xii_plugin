package com.mceteams.xii.model;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Un donjon placé dans le monde (spec §8).
 *
 * Il existe exactement 4 emplacements de donjons fixes aux diagonales
 * (+/-500 ; +/-500) autour du centre de la zone. Les 4 structures
 * dungeon_1..4.nbt y sont attribuées ALÉATOIREMENT sans doublon.
 */
public class Dungeon {

    /** Index de l'emplacement fixe (1 à 4). */
    private final int slot;
    /** Nom de la structure .nbt placée (dungeon_1..4, attribution aléatoire). */
    private final String structureName;
    /** Centre du donjon dans le monde. */
    private final Location center;
    /** Rayon dans lequel les coffres de loot sont détectés/restockés. */
    private final int lootRadius;

    /** Coffres de loot détectés après placement de la structure. */
    private final List<Location> lootChests = new ArrayList<>();

    public Dungeon(int slot, String structureName, Location center, int lootRadius) {
        this.slot = slot;
        this.structureName = structureName;
        this.center = center.clone();
        this.lootRadius = lootRadius;
    }

    public int getSlot() {
        return slot;
    }

    public String getStructureName() {
        return structureName;
    }

    public Location getCenter() {
        return center.clone();
    }

    public int getLootRadius() {
        return lootRadius;
    }

    public List<Location> getLootChests() {
        return new ArrayList<>(lootChests);
    }

    /**
     * Enregistre un coffre découvert dans la zone du donjon.
     */
    public void addLootChest(Block chestBlock) {
        if (!isLootChest(chestBlock)) {
            lootChests.add(chestBlock.getLocation());
        }
    }

    /**
     * Ce bloc est-il l'un des coffres de loot enregistrés ?
     */
    public boolean isLootChest(Block block) {
        return lootChests.stream().anyMatch(loc ->
                loc.getBlockX() == block.getX()
                        && loc.getBlockY() == block.getY()
                        && loc.getBlockZ() == block.getZ());
    }

    /**
     * Vide la liste (appelé avant un re-scan ou lors d'un nettoyage).
     */
    public void clearLootChests() {
        lootChests.clear();
    }
}
