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
                "§7Gérer les équipes, créer, supprimer, etc."
        ));

        inv.setItem(13, ItemBuilder.create(
                Material.CLOCK,
                "§e§lGame Management",
                "§7Start, stop, set day"
        ));

        boolean joinEnabled = gameManager.isJoinEnabled();
        inv.setItem(19, ItemBuilder.create(
                joinEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                "§e§lAllow Join",
                "§7État: " + (joinEnabled ? "§aActivé" : "§cDésactivé"),
                "",
                "§7Clic pour basculer"
        ));

        boolean leaveEnabled = gameManager.isLeaveEnabled();
        inv.setItem(20 , ItemBuilder.create(
                leaveEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                "§e§lAllow Leave",
                "§7État: " + (leaveEnabled ? "§aActivé" : "§cDésactivé"),
                "",
                "§7Clic pour basculer"
        ));

        return inv;
    }
}
