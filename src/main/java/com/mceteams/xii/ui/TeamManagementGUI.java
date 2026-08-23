package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class TeamManagementGUI {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;

    public TeamManagementGUI(TeamManager teamManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        List<GameTeam> teams = teamManager.getTeams();
        int size = Math.max(27, (int) (Math.ceil((teams.size() + 1) / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(null, size, Messages.GUI_TEAM_MANAGEMENT.get(lang));

        for (int i = 0; i < size; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (GameTeam team : teams) {
            if (slot >= size - 9) break;
            var c = team.getColor();
            String status = team.isHeartAlive() ? "§a❤" : "§c✖";
            inv.setItem(slot, ItemBuilder.create(
                    c.getMaterial(),
                    c.getChatColor() + "§l" + c.getName(lang),
                    status + " " + Messages.GUI_HEART_STATUS.get(lang),
                    "§7" + team.getPlayers().size() + "/" + team.getMaxPlayers() + " " + (lang == com.mceteams.xii.enums.Lang.FR ? "joueurs" : "players"),
                    "",
                    Messages.GUI_CLICK_TO_MANAGE.get(lang)
            ));
            slot++;
        }

        inv.setItem(size - 5, ItemBuilder.create(
                Material.LIME_STAINED_GLASS_PANE,
                Messages.GUI_CREATE_TEAM.get(lang)
        ));

        inv.setItem(size - 5 + 4, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
