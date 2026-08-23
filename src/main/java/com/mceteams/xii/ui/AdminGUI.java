package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class AdminGUI {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;

    public AdminGUI(TeamManager teamManager, GameManager gameManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 27, Messages.GUI_ADMIN.get(lang));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(10, ItemBuilder.create(
                Material.PURPLE_BANNER,
                Messages.GUI_TEAM_MGMT.get(lang),
                Messages.GUI_TEAM_MGMT_LORE.get(lang)
        ));

        inv.setItem(13, ItemBuilder.create(
                Material.CLOCK,
                Messages.GUI_GAME_MGMT.get(lang),
                Messages.GUI_GAME_MGMT_LORE.get(lang)
        ));

        boolean joinEnabled = gameManager.isJoinEnabled();
        inv.setItem(19, ItemBuilder.create(
                joinEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                Messages.GUI_ALLOW_JOIN.get(lang),
                Messages.GUI_ALLOW_JOIN_STATE.get(lang, joinEnabled ? Messages.GUI_ENABLED.get(lang) : Messages.GUI_DISABLED.get(lang)),
                "",
                Messages.GUI_TOGGLE.get(lang)
        ));

        boolean leaveEnabled = gameManager.isLeaveEnabled();
        inv.setItem(20, ItemBuilder.create(
                leaveEnabled ? Material.LIME_WOOL : Material.RED_WOOL,
                Messages.GUI_ALLOW_LEAVE.get(lang),
                Messages.GUI_ALLOW_LEAVE_STATE.get(lang, leaveEnabled ? Messages.GUI_ENABLED.get(lang) : Messages.GUI_DISABLED.get(lang)),
                "",
                Messages.GUI_TOGGLE.get(lang)
        ));

        return inv;
    }
}
