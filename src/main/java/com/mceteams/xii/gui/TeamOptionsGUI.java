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
 * GUI d'OPTIONS d'une équipe (spec §5 : "paramètres / état").
 *
 * - taille maximale +/- ;
 * - suppression de l'équipe (avec confirmation via ConfirmGUI).
 */
public class TeamOptionsGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private final TeamColor color;
    private Inventory inventory;

    public TeamOptionsGUI(XiiPlugin plugin, Player player, TeamColor color) {
        this.plugin = plugin;
        this.player = player;
        this.color = color;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                color.getColoredName() + " §8- options");
        fillOptions();
        player.openInventory(inventory);
    }

    /** Boutons : taille -, taille +, supprimer, retour. */
    private void fillOptions() {
        var team = plugin.getTeamManager().getTeam(color);
        if (team == null) {
            player.closeInventory();
            return;
        }

        inventory.setItem(10, ItemUtil.buildNamedItem(
                Material.REDSTONE, "§c-1 joueur",
                java.util.List.of("§7Taille max actuelle : §f"
                        + team.getMaxPlayers())));

        inventory.setItem(12, ItemUtil.buildNamedItem(
                Material.GLOWSTONE_DUST, "§a+1 joueur",
                java.util.List.of("§7Taille max actuelle : §f"
                        + team.getMaxPlayers())));

        inventory.setItem(14, ItemUtil.buildNamedItem(
                Material.TNT, "§cSupprimer l'équipe",
                java.util.List.of("§7Demande une confirmation.")));

        inventory.setItem(16, ItemUtil.buildNamedItem(
                Material.ARROW, "§7Retour", null));
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
        int slot = event.getSlot();

        switch (slot) {
            case 10 -> { // -1
                var team = plugin.getTeamManager().getTeam(color);
                if (team != null && team.getMaxPlayers() > team.getPlayerCount()) {
                    team.setMaxPlayers(team.getMaxPlayers() - 1);
                    open(); // rafraîchit l'affichage
                } else {
                    MessageUtil.send(player,
                            "§cImpossible : des membres occupent la place.");
                }
            }
            case 12 -> { // +1
                var team = plugin.getTeamManager().getTeam(color);
                if (team != null && team.getMaxPlayers() < 64) {
                    team.setMaxPlayers(team.getMaxPlayers() + 1);
                    open();
                }
            }
            case 14 -> { // suppression avec confirmation
                Bukkit.getScheduler().runTask(plugin, () -> new ConfirmGUI(
                        plugin,
                        player,
                        "§cSupprimer l'équipe " + color.getColoredName() + "§c ?",
                        () -> {
                            boolean removed =
                                    plugin.getTeamManager().removeTeam(color);
                            MessageUtil.send(player, removed
                                    ? "§7Équipe supprimée."
                                    : "§cÉquipe introuvable.");
                            player.closeInventory();
                        },
                        this::open
                ).open());
            }
            case 16 -> {
                var membersGui = new TeamMembersGUI(plugin, player, color);
                Bukkit.getScheduler().runTask(plugin, membersGui::open);
            }
            default -> { /* rien */ }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
