package com.mceteams.xii.ui;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public class GameManagementGUI {
    private final GameManager gameManager;

    public GameManagementGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public Inventory create() {
        Inventory inv = Bukkit.createInventory(null, 27, "§e§lGame Management");

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        boolean isStarted = gameManager.getState() != GameState.WAITING;

        inv.setItem(10, ItemBuilder.create(
                isStarted ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                isStarted ? "§c§lStop" : "§a§lStart",
                isStarted ? "§7Arrêter la partie" : "§7Démarrer la partie"
        ));

        inv.setItem(12, ItemBuilder.create(
                Material.CLOCK,
                "§e§lSet Day",
                "§7Jour actuel: §c" + gameManager.getDayManager().getCurrentDay(),
                "§7Clic pour changer (1-12)"
        ));

        // Back button
        inv.setItem(22, ItemBuilder.create(
                Material.ARROW,
                "§c§lRetour"
        ));

        return inv;
    }
}
