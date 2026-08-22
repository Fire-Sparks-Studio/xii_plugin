package com.mceteams.xii.ui;

import com.mceteams.xii.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public class ItemManagementGUI {
    private final GameManager gameManager;

    public ItemManagementGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public Inventory create() {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lItem Management");

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(10, ItemBuilder.create(
                Material.DIAMOND,
                "§b§lBlacklist Item",
                "§7Tiens un item en main",
                "§7Puis clic ici pour blacklister"
        ));

        inv.setItem(12, ItemBuilder.create(
                Material.STONE,
                "§7§lBlacklist Block",
                "§7Regarde un bloc",
                "§7Puis clic ici pour blacklister"
        ));

        inv.setItem(14, ItemBuilder.create(
                Material.LIME_DYE,
                "§a§lWhitelist Item",
                "§7Tiens un item en main",
                "§7Puis clic ici pour retirer de la blacklist"
        ));

        inv.setItem(16, ItemBuilder.create(
                Material.LIME_WOOL,
                "§a§lWhitelist Block",
                "§7Regarde un bloc",
                "§7Puis clic ici pour retirer de la blacklist"
        ));

        // Back button
        inv.setItem(22, ItemBuilder.create(
                Material.ARROW,
                "§c§lRetour"
        ));

        return inv;
    }
}
