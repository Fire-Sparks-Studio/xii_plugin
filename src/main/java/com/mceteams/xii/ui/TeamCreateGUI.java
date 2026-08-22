package com.mceteams.xii.ui;

import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public class TeamCreateGUI {
    private final TeamManager teamManager;

    public TeamCreateGUI(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public Inventory create() {
        Inventory inv = Bukkit.createInventory(null, 54, "§a§lCréer une équipe");

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (TeamColor color : TeamColor.values()) {
            if (teamManager.getTeam(color) != null) {
                inv.setItem(slot, ItemBuilder.create(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "§7" + color.getName(com.mceteams.xii.enums.Lang.FR),
                        "§cDéjà créée"
                ));
            } else {
                inv.setItem(slot, ItemBuilder.create(
                        color.getMaterial(),
                        color.getChatColor() + "§l" + color.getName(com.mceteams.xii.enums.Lang.FR),
                        "§aClic pour créer"
                ));
            }
            slot++;
        }

        inv.setItem(49, ItemBuilder.create(
                Material.ARROW,
                "§c§lRetour"
        ));

        return inv;
    }
}