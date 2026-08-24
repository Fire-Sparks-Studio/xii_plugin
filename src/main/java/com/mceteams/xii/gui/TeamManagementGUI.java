package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUI de GESTION DES ÉQUIPES (spec §5 : "paramètres d'équipe").
 *
 * Liste les équipes existantes ; un clic ouvre TeamMembersGUI puis
 * TeamOptionsGUI (taille, suppression). La création d'équipe reste
 * une commande admin : /teams create <couleur>.
 */
public class TeamManagementGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public TeamManagementGUI(XiiPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "§bGestion des équipes");
        fillTeams();
        player.openInventory(inventory);
    }

    /** Une laine par équipe existante + rappel de la commande de création. */
    private void fillTeams() {
        int slot = 10;
        for (TeamColor color : TeamColor.values()) {
            var team = plugin.getTeamManager().getTeam(color);
            if (team != null) {
                inventory.setItem(slot, ItemUtil.buildNamedItem(
                        com.mceteams.xii.util.TeamUtil.woolOf(color),
                        color.getColoredName() + " §7(" + team.getPlayerCount()
                                + "/" + team.getMaxPlayers() + ")",
                        java.util.List.of("§7Clique pour gérer.")));
            }
            slot += 2;
        }
        // Rappel : création via commande.
        inventory.setItem(16, ItemUtil.buildNamedItem(
                Material.PAPER,
                "§7/teams create <couleur>",
                java.util.List.of("§8Création via commande admin.")));
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }

        for (TeamColor color : TeamColor.values()) {
            if (clicked.getType() == com.mceteams.xii.util.TeamUtil.woolOf(color)) {
                // Ouverture différée au tick suivant.
                var membersGui = new TeamMembersGUI(plugin, player, color);
                Bukkit.getScheduler().runTask(plugin, membersGui::open);
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
