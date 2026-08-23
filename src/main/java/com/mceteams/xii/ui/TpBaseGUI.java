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
import java.util.UUID;

public class TpBaseGUI {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;

    public TpBaseGUI(TeamManager teamManager, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
    }

    public Inventory create(Player player, GameTeam team) {
        var lang = playerDataManager.getLang(player);
        int playerCount = team.getPlayers().size();
        int size = Math.max(27, (int) (Math.ceil((playerCount + 2) / 9.0) * 9));
        Inventory inv = Bukkit.createInventory(null, size,
                Messages.GUI_TP_BASE.get(lang) + " §7| §f" + team.getColor().getName(lang));

        for (int i = 0; i < size; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        int slot = 0;
        for (UUID uuid : team.getPlayers()) {
            if (slot >= size - 9) break;

            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwner(name);
            meta.displayName(Component.text("§a" + name));
            meta.lore(List.of(
                    Component.text(Messages.GUI_TP_CLICK.get(lang))
            ));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        inv.setItem(size - 5, ItemBuilder.create(
                Material.ENDER_EYE,
                Messages.GUI_WHOLE_TEAM.get(lang),
                Messages.GUI_WHOLE_TEAM_LORE.get(lang)
        ));

        inv.setItem(size - 1, ItemBuilder.create(
                Material.ARROW,
                Messages.GUI_BACK.get(lang)
        ));

        return inv;
    }
}
