package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class TeamPlayerSelectGUI {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;

    public TeamPlayerSelectGUI(TeamManager teamManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory createForAdd(Player player, GameTeam team) {
        var lang = playerDataManager.getLang(player);
        int size = Math.max(54, (int) (Math.ceil((Bukkit.getOnlinePlayers().size() + 1) / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(null, size, Messages.GUI_ADD_PLAYER_TITLE.get(lang) + " §7| §f" + team.getColor().getName(lang));

        for (int i = 0; i < size; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= size - 9) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwner(online.getName());

            GameTeam playerTeam = teamManager.getTeam(online.getUniqueId());
            if (playerTeam != null) {
                meta.displayName(Component.text("§e" + online.getName()));
                meta.lore(List.of(
                        Component.text(Messages.ALREADY_IN_TEAM.get(lang, playerTeam.getColor().getName(lang)))
                ));
            } else {
                meta.displayName(Component.text("§a" + online.getName()));
                meta.lore(List.of(
                        Component.text(Messages.GUI_CLICK_TO_ADD.get(lang))
                ));
            }

            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        inv.setItem(size - 5, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }

    public Inventory createForRemove(Player player, GameTeam team) {
        var lang = playerDataManager.getLang(player);
        int size = Math.max(27, (int) (Math.ceil((team.getPlayers().size() + 1) / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(null, size, Messages.GUI_REMOVE_PLAYER_TITLE.get(lang) + " §7| §f" + team.getColor().getName(lang));

        for (int i = 0; i < size; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (java.util.UUID uuid : team.getPlayers()) {
            if (slot >= size - 9) break;

            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwner(name);
            meta.displayName(Component.text("§c" + name));
            meta.lore(List.of(
                    Component.text(Messages.GUI_CLICK_TO_REMOVE.get(lang))
            ));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        inv.setItem(size - 5, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
