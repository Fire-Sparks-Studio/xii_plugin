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
            boolean isFull = team != null && team.getPlayers().size() >= team.getMaxPlayers();

            if (isFull) {
                inv.setItem(slot, ItemBuilder.create(
                        color.getGlassPane(),
                        color.getName(Lang.FR),
                        "§c§lCOMPLET"
                ));
            } else {
                int count = team != null ? team.getPlayers().size() : 0;
                inv.setItem(slot, ItemBuilder.create(
                        color.getMaterial(),
                        color.getName(Lang.FR),
                        "§7" + count + "/10 joueurs"
                ));
            }
            slot++;
        }

        GameTeam myTeam = teamManager.getTeam(player.getUniqueId());
        if (myTeam != null) {
            inv.setItem(4, ItemBuilder.create(
                    myTeam.getColor().getMaterial(),
                    "§aTa team : " + myTeam.getColor().getName(Lang.FR)
            ));
        } else {
            inv.setItem(4, ItemBuilder.create(
                    Material.WHITE_STAINED_GLASS_PANE,
                    "§7Aucune team"
            ));
        }

        return inv;
    }
}