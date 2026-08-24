package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.util.ItemUtil;
import com.mceteams.xii.util.MessageUtil;
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
 * Affiche TOUJOURS les 4 couleurs :
 * - équipe existante  : laine cliquable -> TeamMembersGUI ;
 * - équipe inexistante: barrière cliquable -> CRÉATION immédiate.
 *
 * La création par commande /teams create reste bien sûr disponible.
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

    /**
     * Les 4 couleurs sont TOUJOURS affichées :
     * slots 10, 12, 14, 16 (une case sur deux, centrées).
     */
    private void fillTeams() {
        int slot = 10;
        for (TeamColor color : TeamColor.values()) {
            var team = plugin.getTeamManager().getTeam(color);
            ItemStack item;
            if (team != null) {
                item = ItemUtil.buildNamedItem(
                        com.mceteams.xii.util.TeamUtil.woolOf(color),
                        color.getColoredName() + " §7(" + team.getPlayerCount()
                                + "/" + team.getMaxPlayers() + ")",
                        java.util.List.of("§7Clique pour gérer."));
            } else {
                // Non créée : barrière + proposition de création.
                item = ItemUtil.buildNamedItem(
                        Material.GRAY_DYE,
                        "§7" + color.getDisplayName() + " §8(non créée)",
                        java.util.List.of(
                                "§aClique pour CRÉER cette équipe.",
                                "§8(équivalent /teams create)"));
            }
            inventory.setItem(slot, item);
            slot += 2;
        }
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }

        // Identification par POSITION (slots 10, 12, 14, 16 dans l'ordre
        // de TeamColor.values()) et non par matériau : sinon tous les
        // boutons "créer" (teinture grise) correspondraient à la même
        // première couleur de la boucle !
        int offset = event.getSlot() - 10;
        if (offset < 0 || offset % 2 != 0) {
            return; // hors des cases d'équipe
        }
        TeamColor[] colors = TeamColor.values();
        int index = offset / 2;
        if (index >= 0 && index < colors.length) {
            handleTeamSlot(colors[index]);
        }
    }

    /** Ouvre la gestion de l'équipe ou la crée si elle n'existe pas. */
    private void handleTeamSlot(TeamColor color) {
        var team = plugin.getTeamManager().getTeam(color);
        if (team != null) {
            // Ouverture différée au tick suivant.
            var membersGui = new TeamMembersGUI(plugin, player, color);
            Bukkit.getScheduler().runTask(plugin, membersGui::open);
            return;
        }

        // Création depuis la GUI.
        boolean created = plugin.getTeamManager().createTeam(color);
        if (created) {
            MessageUtil.send(player,
                    "§a✔ Équipe créée : " + color.getColoredName());
        } else {
            MessageUtil.send(player, "§cImpossible de créer l'équipe.");
        }
        open(); // rafraîchit l'affichage (la barrière devient une laine)
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
