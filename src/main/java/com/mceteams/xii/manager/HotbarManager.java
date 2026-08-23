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

    public static final int SLOT_ADMIN = 0;
    public static final int SLOT_TEAM = 4;
    public static final int SLOT_LANG = 8;

    // Globe/Earth texture (mc-heads.com)
    private static final String PLANET_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFkZDRmZTRhNDI5YWJkNjY1ZGZkYjNlMjEzMjFkNmVmYTZhNmI1ZTdiOTU2ZGI5YzVkNTljOWVmYWIyNSJ9fX0=";

    public HotbarManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void giveHotbar(Player player) {
        PlayerInventory inv = player.getInventory();

        if (player.hasPermission("xii.admin")) {
            inv.setItem(SLOT_ADMIN, ItemBuilder.create(Material.TRIPWIRE_HOOK, "§6§lAdmin", "§7Clic pour ouvrir le menu admin"));
        }

        inv.setItem(SLOT_TEAM, getTeamItem(player));

        inv.setItem(SLOT_LANG, ItemBuilder.createSkull(PLANET_TEXTURE, "§6§lLanguage", "§7Click to change language."));
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
        player.getInventory().setItem(SLOT_TEAM, getTeamItem(player));
    }
}