package com.mceteams.xii.manager;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.ui.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class HotbarManager {
    private final TeamManager teamManager;

    public HotbarManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void giveHotbar(Player player) {
        PlayerInventory inv = player.getInventory();

        if (player.hasPermission("xii.admin") && inv.getItem(0) == null) {
            ItemStack adminItem = ItemBuilder.create(Material.TRIPWIRE_HOOK, "§6§lAdmin", "§7Clic pour ouvrir le menu admin");
            adminItem.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
            inv.setItem(0, adminItem);
        }

        if (inv.getItem(4) == null) {
            inv.setItem(4, getTeamItem(player));
        }

        if (inv.getItem(8) == null) {
            inv.setItem(8, ItemBuilder.createSkull("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA2NWI0OTU3NzdiYTg0MmQyMmRjYTY2M2U5Zjc5NjE3ZDhiZDliOTgzZDQyNGUzNmRkN2Q5OTMwNGJhMjUwOCJ9fX0=", "§6§lLanguage", "§7Click to change of Language."));
        }
    }

    public ItemStack getTeamItem(Player player) {
        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            return ItemBuilder.create(Material.BARRIER, "§cAucune équipe", "§7Clic pour choisir une team");
        }
        return ItemBuilder.create(team.getColor().getMaterial(), "§aTeam : " + team.getColor().getName(Lang.FR));
    }

    public void clearHotbar(Player player) {
        player.getInventory().clear();
    }

    public void refreshTeamItem(Player player) {
        player.getInventory().setItem(4, getTeamItem(player));
    }
}