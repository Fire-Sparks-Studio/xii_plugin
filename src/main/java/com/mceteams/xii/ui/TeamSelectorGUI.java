package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TeamSelectorGUI {
    private final TeamManager teamManager;

    public TeamSelectorGUI(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public Inventory create(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§6Choisir une équipe");

        int slot = 0;
        for (TeamColor color : TeamColor.values()) {
            if (slot >= 9) break;
            if (slot == 4) slot++;

            GameTeam team = teamManager.getTeam(color);
            if (team == null) continue;

            if (team.getPlayers().size() >= team.getMaxPlayers()) {
                inv.setItem(slot, ItemBuilder.create(color.getGlassPane(), color.getName(Lang.FR), "§c§lCOMPLET"));
            } else {
                inv.setItem(slot, ItemBuilder.create(color.getMaterial(), color.getName(Lang.FR), "§7" + team.getPlayers().size() + "/" + team.getMaxPlayers() + " joueurs"));
            }
            slot++;
        }

        GameTeam myTeam = teamManager.getTeam(player.getUniqueId());

        inv.setItem(4, ItemBuilder.create(
                Material.BARRIER,
                "§7Mode Spectateur"
        ));


        return inv;
    }
}