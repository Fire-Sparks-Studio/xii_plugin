package com.mceteams.xii.ui;

import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TeamManagementGUI {
    private final TeamManager teamManager;

    public TeamManagementGUI(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public Inventory create() {
        List<GameTeam> teams = teamManager.getTeams();
        int size = Math.max(27, (int) (Math.ceil((teams.size() + 1) / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(null, size, "§6§lTeam Management");

        for (int i = 0; i < size; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // Team slots: rows 0-2 = slots 0-26, teams go from slot 0
        int slot = 0;
        for (GameTeam team : teams) {
            if (slot >= size - 9) break;
            TeamColor c = team.getColor();
            String status = team.isHeartAlive() ? "§a❤" : "§c✖";
            inv.setItem(slot, ItemBuilder.create(
                    c.getMaterial(),
                    c.getChatColor() + "§l" + c.getName(com.mceteams.xii.enums.Lang.FR),
                    status + " §7Cœur",
                    "§7" + team.getPlayers().size() + "/" + team.getMaxPlayers() + " joueurs",
                    "",
                    "§e§lClic pour gérer"
            ));
            slot++;
        }

        // Create team button
        inv.setItem(size - 5, ItemBuilder.create(
                Material.LIME_STAINED_GLASS_PANE,
                "§a§l+ Créer une équipe"
        ));

        // Back button
        inv.setItem(size - 5 + 4, ItemBuilder.create(
                Material.ARROW,
                "§c§lRetour"
        ));

        return inv;
    }
}
