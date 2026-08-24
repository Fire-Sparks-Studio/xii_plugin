package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUI de SÉLECTION DE CLASSE (spec §14/§31).
 *
 * Présente les 5 classes avec avantage/malus. Un clic enregistre le
 * choix via ClassManager. Si aucun choix : classe aléatoire à la fin
 * des 30 secondes (géré par GameManager/ClassManager).
 */
public class ClassSelectionGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public ClassSelectionGUI(XiiPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                "§eChoisissez votre classe");
        fillClasses();
        player.openInventory(inventory);
    }

    /** Une case par classe : matériau symbolique + lore avantage/malus. */
    private void fillClasses() {
        for (PlayerClass playerClass : PlayerClass.values()) {
            int slot = 9 + playerClass.ordinal(); // rangée du milieu
            ItemStack item = ItemUtil.buildNamedItem(
                    materialOf(playerClass),
                    playerClass.getColoredName(),
                    java.util.List.of(
                            playerClass.getAdvantageLine(),
                            playerClass.getMalusLine(),
                            "",
                            "§7Clique pour choisir."));
            inventory.setItem(slot, item);
        }
    }

    /** Matériau illustrant chaque classe. */
    private Material materialOf(PlayerClass playerClass) {
        return switch (playerClass) {
            case MINER -> Material.IRON_PICKAXE;   // Mineur
            case WORKER -> Material.GOLDEN_SHOVEL; // Travailleur
            case TANK -> Material.SHIELD;          // Robuste
            case AGILE -> Material.FEATHER;        // Agile
            case WARRIOR -> Material.IRON_SWORD;   // Guerrier
        };
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        // Les classes sont posées sur les slots 9..13.
        int index = event.getSlot() - 9;
        PlayerClass[] classes = PlayerClass.values();
        if (index < 0 || index >= classes.length) {
            return; // clic hors zone : annulé, rien de plus
        }
        // Enregistrement + fermeture.
        plugin.getClassManager().select(player, classes[index]);
        player.closeInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
