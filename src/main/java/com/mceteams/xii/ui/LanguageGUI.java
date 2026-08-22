package com.mceteams.xii.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class LanguageGUI {
    public Inventory create(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§6§lLanguage");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // Tête FR
        ItemStack headFR = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skullFR = (org.bukkit.inventory.meta.SkullMeta) headFR.getItemMeta();
        skullFR.displayName(Component.text("§f§lFrançais"));
        skullFR.lore(List.of(Component.text("§7Langue du jeu")));
        skullFR.setOwner("MHF_FR");
        headFR.setItemMeta(skullFR);
        inv.setItem(3, headFR);

        // Tête EN
        ItemStack headEN = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skullEN = (org.bukkit.inventory.meta.SkullMeta) headEN.getItemMeta();
        skullEN.displayName(Component.text("§9§lEnglish"));
        skullEN.lore(List.of(Component.text("§7Game language")));
        skullEN.setOwner("MHF_GB");
        headEN.setItemMeta(skullEN);
        inv.setItem(5, headEN);

        return inv;
    }
}
