package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUI des MEMBRES d'une équipe (spec §5 : "les joueurs").
 *
 * Affiche la liste des membres (têtes) + bouton d'options d'équipe.
 */
public class TeamMembersGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private final TeamColor color;
    private Inventory inventory;

    public TeamMembersGUI(XiiPlugin plugin, Player player, TeamColor color) {
        this.plugin = plugin;
        this.player = player;
        this.color = color;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 54,
                color.getColoredName() + " §8- membres");
        fillMembers();
        player.openInventory(inventory);
    }

    /** Têtes des membres + bouton options. */
    private void fillMembers() {
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team == null) {
            player.closeInventory();
            return;
        }
        int slot = 0;
        for (var memberUuid : team.getPlayers()) {
            var offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
            String name = offlinePlayer.getName() != null
                    ? offlinePlayer.getName()
                    : memberUuid.toString().substring(0, 8);

            ItemStack head = ItemUtil.buildNamedItem(
                    Material.PLAYER_HEAD,
                    "§f" + name,
                    java.util.List.of(
                            "§7Vivant : " + (plugin.getPlayerManager()
                                    .getData(memberUuid).isAlive()
                                    ? "§aoui" : "§cnon")));
            inventory.setItem(slot++, head);
        }

        // Options de l'équipe (taille max, suppression...).
        inventory.setItem(48, ItemUtil.buildNamedItem(
                Material.COMPARATOR,
                "§bOptions",
                java.util.List.of("§7Taille, suppression...")));

        // Retour à la gestion des équipes.
        inventory.setItem(49, ItemUtil.buildNamedItem(
                Material.ARROW, "§7Retour", null));
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        int slot = event.getSlot();

        if (slot == 49) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> new TeamManagementGUI(plugin, player).open());
            return;
        }
        if (slot == 48) {
            var optionsGui = new TeamOptionsGUI(plugin, player, color);
            Bukkit.getScheduler().runTask(plugin, optionsGui::open);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
