package com.mceteams.xii.ui;

import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public class AdminGUI {
    private final TeamManager teamManager;
    private final GameManager gameManager;

    public AdminGUI(TeamManager teamManager, GameManager gameManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
    }

    public Inventory create() {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lAdmin GUI");

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(10, ItemBuilder.create(
                Material.PURPLE_BANNER,
                "§d§lTeam Management",
                "§7Créer, supprimer, gérer les équipes"
        ));

        inv.setItem(13, ItemBuilder.create(
                Material.CLOCK,
                "§e§lGame Management",
                "§7Start, stop, set day, etc."
        ));

        inv.setItem(16, ItemBuilder.create(
                Material.CHEST,
                "§6§lItem Management",
                "§7Blacklist, give items"
        ));

        return inv;
    }
}