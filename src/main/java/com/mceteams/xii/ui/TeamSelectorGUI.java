package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TeamSelectorGUI {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;

    public TeamSelectorGUI(TeamManager teamManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 9, Messages.GUI_TEAM_SELECTOR.get(lang));

        int slot = 0;
        for (TeamColor color : TeamColor.values()) {
            if (slot >= 9) break;
            if (slot == 4) slot++;

            GameTeam team = teamManager.getTeam(color);
            if (team == null) continue;

            if (team.getPlayers().size() >= team.getMaxPlayers()) {
                inv.setItem(slot, ItemBuilder.create(color.getGlassPane(), color.getName(lang), Messages.GUI_FULL.get(lang)));
            } else {
                inv.setItem(slot, ItemBuilder.create(color.getMaterial(), color.getName(lang), "§7" + team.getPlayers().size() + "/" + team.getMaxPlayers() + " " + (lang == Lang.FR ? "joueurs" : "players")));
            }
            slot++;
        }

        inv.setItem(4, ItemBuilder.create(
                Material.BARRIER,
                Messages.GUI_SPECTATOR.get(lang)
        ));

        return inv;
    }
}
