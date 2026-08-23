package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TeamCreateGUI {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;

    public TeamCreateGUI(TeamManager teamManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 54, Messages.GUI_TEAM_CREATE.get(lang));

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (TeamColor color : TeamColor.values()) {
            if (teamManager.getTeam(color) != null) {
                inv.setItem(slot, ItemBuilder.create(
                        Material.GRAY_STAINED_GLASS_PANE,
                        "§7" + color.getName(lang),
                        Messages.GUI_ALREADY_CREATED.get(lang)
                ));
            } else {
                inv.setItem(slot, ItemBuilder.create(
                        color.getMaterial(),
                        color.getChatColor() + "§l" + color.getName(lang),
                        Messages.GUI_CLICK_TO_CREATE.get(lang)
                ));
            }
            slot++;
        }

        inv.setItem(49, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
