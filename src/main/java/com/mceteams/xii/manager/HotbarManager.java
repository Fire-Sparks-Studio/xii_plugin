package com.mceteams.xii.manager;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.ui.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;

public class HotbarManager {
    private final TeamManager teamManager;

    public HotbarManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void giveHotbar(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();

        if (player.hasPermission("xii.admin")) {
            ItemStack adminItem = ItemBuilder.create(Material.TRIPWIRE_HOOK, "§6§lAdmin", "§7Clic pour ouvrir le menu admin");
            adminItem.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
            inv.setItem(0, adminItem);
        }

        inv.setItem(4, getTeamBanner(player));

        inv.setItem(8, ItemBuilder.create(Material.BARRIER, "§cQuitter l'équipe", "§7Clic pour leave"));
    }

    public ItemStack getTeamBanner(Player player) {
        GameTeam team = teamManager.getTeam(player.getUniqueId());
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();

        if (team == null) {
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.CROSS));
            meta.displayName(Component.text("§cAucune équipe"));
        } else {
            meta.addPattern(new Pattern(team.getColor().getDyeColor(), PatternType.BASE));
            meta.displayName(Component.text(team.getColor().getName(Lang.FR)));
        }
        banner.setItemMeta(meta);
        return banner;
    }

    public void clearHotbar(Player player) {
        player.getInventory().clear();
    }
}