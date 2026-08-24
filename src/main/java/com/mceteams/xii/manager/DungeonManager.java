package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.config.ConfigManager;
import com.mceteams.xii.model.Dungeon;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Gère les 4 donjons (spec §8).
 *
 * Positions FIXES (relatives au centre de la zone) :
 *   Donjon slot 1 : (+500 ; +500)
 *   Donjon slot 2 : (+500 ; -500)
 *   Donjon slot 3 : (-500 ; +500)
 *   Donjon slot 4 : (-500 ; -500)
 *
 * Les QUATRE structures dungeon_1..4.nbt sont attribuées ALÉATOIREMENT
 * aux quatre slots, sans doublon (mélange de Fisher-Yates).
 */
public class DungeonManager {

    /** Offset fixe des donjons par rapport au centre (spec §8). */
    public static final int DUNGEON_OFFSET = 500;
    /** Rayon de détection/restock des coffres autour d'un donjon. */
    private static final int LOOT_RADIUS = 48;

    private final XiiPlugin plugin;
    private final Random random = new Random();

    /** Donjons placés (par slot). */
    private final List<Dungeon> dungeons = new ArrayList<>();

    /**
     * Le loot des donjons est-il accessible ?
     * false au début de la préparation, true dès la sous-phase
     * DUNGEONS (et après un restock).
     */
    private boolean lootAccessible = false;

    public DungeonManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Génération
    // -----------------------------------------------------------------

    /**
     * Place les 4 donjons avec attribution aléatoire sans doublon.
     */
    public void buildDungeons(GameZone zone) {
        dungeons.clear();

        Location center = zone.getCenterLocation();
        if (center == null) {
            return;
        }

        // Mélange aléatoire des numéros de structures [1..4].
        List<Integer> structureNumbers = new ArrayList<>(List.of(1, 2, 3, 4));
        Collections.shuffle(structureNumbers, random);

        // Les 4 slots fixes (diagonales), spec §8.
        int[][] offsets = {
                {+DUNGEON_OFFSET, +DUNGEON_OFFSET},   // slot 1
                {+DUNGEON_OFFSET, -DUNGEON_OFFSET},   // slot 2
                {-DUNGEON_OFFSET, +DUNGEON_OFFSET},   // slot 3
                {-DUNGEON_OFFSET, -DUNGEON_OFFSET}    // slot 4
        };

        for (int slot = 1; slot <= 4; slot++) {
            int structureNumber = structureNumbers.get(slot - 1);
            int dx = offsets[slot - 1][0];
            int dz = offsets[slot - 1][1];

            Location anchor = new Location(
                    center.getWorld(),
                    center.getX() + dx,
                    center.getY(),
                    center.getZ() + dz);

            boolean placed = plugin.getStructureManager()
                    .placeDungeon(structureNumber, anchor, center);
            if (!placed) {
                plugin.getLogger().warning("[Donjons] Structure manquante : dungeon_"
                        + structureNumber + ".nbt");
            }

            Dungeon dungeon = new Dungeon(slot,
                    "dungeon_" + structureNumber, anchor, LOOT_RADIUS);
            dungeons.add(dungeon);

            // Détection des coffres de loot posés par la structure.
            scanLootChests(dungeon);
            fillChests(dungeon); // remplissage initial (verrouillé jusqu'à DUNGEONS)
        }
    }

    /**
     * Scanne les coffres dans le rayon du donjon et les enregistre.
     */
    private void scanLootChests(Dungeon dungeon) {
        dungeon.clearLootChests();
        var world = dungeon.getCenter().getWorld();
        if (world == null) {
            return;
        }
        Location c = dungeon.getCenter();
        for (int x = -LOOT_RADIUS; x <= LOOT_RADIUS; x += 8) {
            for (int z = -LOOT_RADIUS; z <= LOOT_RADIUS; z += 8) {
                for (int y = -20; y <= 40; y += 4) {
                    Block block = world.getBlockAt(
                            c.getBlockX() + x,
                            c.getBlockY() + y,
                            c.getBlockZ() + z);
                    if (block.getType() == Material.CHEST) {
                        dungeon.addLootChest(block);
                    }
                }
            }
        }
        plugin.getLogger().info("[Donjons] Slot " + dungeon.getSlot()
                + " (" + dungeon.getStructureName() + ") : "
                + dungeon.getLootChests().size() + " coffre(s).");
    }

    /**
     * Remplit (ou remplit à nouveau) les coffres d'un donjon.
     */
    public void fillChests(Dungeon dungeon) {
        ConfigManager config = plugin.getConfigManager();
        for (Location chestLocation : dungeon.getLootChests()) {
            Block block = chestLocation.getBlock();
            if (block.getType() != Material.CHEST) {
                continue; // coffre détruit pendant la partie
            }
            BlockState state = block.getState();
            if (!(state instanceof Chest chest)) {
                continue;
            }
            chest.getInventory().clear();
            for (ConfigManager.LootEntry entry : config.getLootTable()) {
                int amount = entry.randomAmount(random);
                if (amount > 0) {
                    chest.getInventory().setItem(
                            random.nextInt(chest.getInventory().getSize()),
                            new org.bukkit.inventory.ItemStack(entry.material(), amount));
                }
            }
            chest.update();
        }
    }

    // -----------------------------------------------------------------
    // Accès au loot & restock (spec §17 : DUNGEONS / DUNGEON_RESTOCK)
    // -----------------------------------------------------------------

    /** Débloque l'accès aux loots (début de la sous-phase DUNGEONS). */
    public void unlockLoot() {
        this.lootAccessible = true;
        MessageUtil.broadcast("§7Les loots des donjons sont §aaccessibles§7 !");
    }

    /** Restocke TOUS les donjons (sous-phase DUNGEON_RESTOCK). */
    public void restockAll() {
        for (Dungeon dungeon : dungeons) {
            // Re-scan léger : des coffres peuvent avoir été détruits.
            fillChests(dungeon);
        }
        MessageUtil.broadcast("§7Les donjons ont été §brestockés§7 !");
    }

    public boolean isLootAccessible() {
        return lootAccessible;
    }

    /** Réinitialise le verrou de loot (nouvelle partie). */
    public void resetAccess() {
        this.lootAccessible = false;
    }

    // -----------------------------------------------------------------
    // Lectures
    // -----------------------------------------------------------------

    public List<Dungeon> all() {
        return new ArrayList<>(dungeons);
    }

    /**
     * Ce bloc est-il un coffre de loot d'un donjon ?
     * Utilisé par InteractionListener pour verrouiller l'accès.
     */
    public boolean isLootChest(Block block) {
        for (Dungeon dungeon : dungeons) {
            if (dungeon.isLootChest(block)) {
                return true;
            }
        }
        return false;
    }

    /** Vide tout (retour WAITING / zone supprimée). */
    public void clearAll() {
        dungeons.clear();
        lootAccessible = false;
    }
}
