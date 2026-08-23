package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class LanguageGUI {
    private static final String FRANCE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjkwMzM0OWZhNDViZGQ4NzEyNmQ5Y2QzYzZjMGFiYmE3ZGJkNmY1NmZiOGQ3ODcwMTg3M2ExZTdjOGVlMzNjZiJ9fX0=";
    private static final String US_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmNiYzMyY2IyNGQ1N2ZjZGMwMzFlODUxMjM1ZGEyZGFhZDNlMTkxNGI4NzA0M2JkMDEyNjMzZTZmMzJjNyJ9fX0=";

    private final PlayerDataManager playerDataManager;

    public LanguageGUI(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player) {
        var lang = playerDataManager.getLang(player);
        Inventory inv = Bukkit.createInventory(null, 9, Messages.GUI_LANGUAGE.get(lang));

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(3, ItemBuilder.createSkull(
                FRANCE_TEXTURE,
                "§f§lFrançais",
                "§7Langue du jeu"
        ));

        inv.setItem(5, ItemBuilder.createSkull(
                US_TEXTURE,
                "§9§lEnglish",
                "§7Game language"
        ));

        return inv;
    }
}
