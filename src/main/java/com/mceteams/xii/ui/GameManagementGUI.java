package com.mceteams.xii.ui;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class GameManagementGUI {
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;

    public GameManagementGUI(GameManager gameManager, PlayerDataManager playerDataManager) {
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 27, Messages.GUI_GAME_MANAGEMENT.get(lang));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        boolean isStarted = gameManager.getState() != GameState.WAITING;

        inv.setItem(10, ItemBuilder.create(
                isStarted ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                isStarted ? Messages.GUI_STOP.get(lang) : Messages.GUI_START.get(lang),
                isStarted ? Messages.GUI_STOP_LORE.get(lang) : Messages.GUI_START_LORE.get(lang)
        ));

        inv.setItem(12, ItemBuilder.create(
                Material.CLOCK,
                Messages.GUI_SET_DAY.get(lang),
                Messages.GUI_CURRENT_DAY.get(lang, gameManager.getDayManager().getCurrentDay()),
                Messages.GUI_CLICK_TO_CHANGE2.get(lang)
        ));

        inv.setItem(22, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
