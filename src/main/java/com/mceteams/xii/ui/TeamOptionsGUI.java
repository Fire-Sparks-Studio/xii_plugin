package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class TeamOptionsGUI {
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;

    public TeamOptionsGUI(GameManager gameManager, PlayerDataManager playerDataManager) {
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player, GameTeam team) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 27,
                "§6§l" + team.getColor().getName(lang));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(0, ItemBuilder.create(
                Material.RED_DYE,
                Messages.GUI_DESTROY_HEART.get(lang),
                Messages.GUI_DESTROY_HEART_LORE.get(lang)
        ));

        inv.setItem(1, ItemBuilder.create(
                Material.GREEN_DYE,
                Messages.GUI_RESTORE_HEART.get(lang),
                Messages.GUI_RESTORE_HEART_LORE.get(lang)
        ));

        inv.setItem(2, ItemBuilder.create(
                Material.BONE,
                Messages.GUI_ELIMINATE.get(lang),
                Messages.GUI_ELIMINATE_LORE.get(lang)
        ));

        inv.setItem(3, ItemBuilder.create(
                Material.TOTEM_OF_UNDYING,
                Messages.GUI_REVIVE.get(lang),
                Messages.GUI_REVIVE_LORE.get(lang)
        ));

        inv.setItem(5, ItemBuilder.create(
                Material.ENDER_PEARL,
                Messages.GUI_TP_BASE.get(lang),
                Messages.GUI_TP_BASE_LORE.get(lang)
        ));

        inv.setItem(8, ItemBuilder.create(
                Material.PAPER,
                Messages.GUI_MAX_MEMBERS.get(lang),
                Messages.GUI_MAX_MEMBERS_LORE.get(lang, team.getMaxPlayers()),
                Messages.GUI_CLICK_TO_CHANGE2.get(lang)
        ));

        inv.setItem(10, ItemBuilder.create(
                Material.LIME_WOOL,
                Messages.GUI_ADD_PLAYER.get(lang),
                Messages.GUI_ADD_PLAYER_LORE.get(lang)
        ));

        inv.setItem(11, ItemBuilder.create(
                Material.RED_WOOL,
                Messages.GUI_REMOVE_PLAYER.get(lang),
                Messages.GUI_REMOVE_PLAYER_LORE.get(lang)
        ));

        inv.setItem(15, ItemBuilder.create(
                Material.BARRIER,
                Messages.GUI_DELETE_TEAM.get(lang),
                Messages.GUI_DELETE_TEAM_LORE.get(lang)
        ));

        inv.setItem(22, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
