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
 * GUI d'OPTIONS d'une équipe (spec §5 + administration gameplay).
 *
 * - taille maximale +/- ;
 * - DESTRUCTION / RESTAURATION du coeur (via CoreService) ;
 * - ELIMINATION / REHABILITATION de l'équipe (via TeamManager) ;
 * - suppression de l'équipe (avec ConfirmGUI).
 *
 * Les actions destructrices (coeur, élimination, suppression) passent
 * par une confirmation ; les restaurations sont immédiates.
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
        this.inventory = Bukkit.createInventory(this, 45,
                color.getColoredName() + " §8- options");
        fillOptions();
        player.openInventory(inventory);
    }

    /** Boutons selon l'état courant de l'équipe et de son coeur. */
    private void fillOptions() {
        var team = plugin.getTeamManager().getTeam(color);
        if (team == null) {
            player.closeInventory();
            return;
        }

        // --- Rangée 2 : taille de l'équipe ---------------------------
        inventory.setItem(10, ItemUtil.buildNamedItem(
                Material.REDSTONE, "§c-1 joueur",
                java.util.List.of("§7Taille max actuelle : §f"
                        + team.getMaxPlayers())));

        inventory.setItem(12, ItemUtil.buildNamedItem(
                Material.GLOWSTONE_DUST, "§a+1 joueur",
                java.util.List.of("§7Taille max actuelle : §f"
                        + team.getMaxPlayers())));

        // --- Rangée 3 : administration gameplay ------------------------
        if (team.isHeartAlive()) {
            inventory.setItem(19, ItemUtil.buildNamedItem(
                    Material.WITHER_SKELETON_SKULL,
                    "§cDétruire le coeur",
                    java.util.List.of("§7Equivalent §f/teams heart destroy",
                            "§7Aucun point attribué.")));
        } else {
            inventory.setItem(19, ItemUtil.buildNamedItem(
                    Material.BEACON,
                    "§aRestaurer le coeur",
                    java.util.List.of("§7Equivalent §f/teams heart restore")));
        }

        if (!team.isEliminated()) {
            inventory.setItem(21, ItemUtil.buildNamedItem(
                    Material.SKELETON_SKULL,
                    "§cÉliminer l'équipe",
                    java.util.List.of("§7Membres => spectateurs.",
                            "§7Equivalent §f/teams eliminate")));
        } else {
            inventory.setItem(24, ItemUtil.buildNamedItem(
                    Material.TOTEM_OF_UNDYING,
                    "§aRéhabiliter l'équipe",
                    java.util.List.of("§7Membres => retour en jeu.",
                            "§7Equivalent §f/teams revive")));
        }

        // --- Rangée 5 : retour + suppression ----------------------------
        inventory.setItem(36, ItemUtil.buildNamedItem(
                Material.ARROW, "§7Retour aux membres", null));

        inventory.setItem(40, ItemUtil.buildNamedItem(
                Material.TNT, "§cSupprimer l'équipe",
                java.util.List.of("§7Demande une confirmation.")));
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
        var team = plugin.getTeamManager().getTeam(color);
        if (team == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();

        switch (slot) {
            case 10 -> { // -1
                if (team.getMaxPlayers() > team.getPlayerCount()) {
                    team.setMaxPlayers(team.getMaxPlayers() - 1);
                    rebuild();
                } else {
                    MessageUtil.send(player,
                            "§cImpossible : des membres occupent la place.");
                }
            }
            case 12 -> { // +1
                if (team.getMaxPlayers() < 64) {
                    team.setMaxPlayers(team.getMaxPlayers() + 1);
                    rebuild();
                }
            }
            case 19 -> { // coeur : détruire ou restaurer
                if (team.isHeartAlive()) {
                    confirm("§cDétruire le coeur de l'équipe "
                                    + color.getColoredName() + "§c ?",
                            () -> {
                                plugin.getCoreService()
                                        .breakCore(team, null, true);
                                MessageUtil.send(player,
                                        "§4✖ Coeur détruit.");
                                rebuild();
                            });
                } else {
                    plugin.getCoreService().restoreCore(color);
                    rebuild();
                }
            }
            case 21 -> { // éliminer
                confirm("§cÉliminer l'équipe " + color.getColoredName() + "§c ?",
                        () -> {
                            boolean ok =
                                    plugin.getTeamManager().forceEliminate(color);
                            MessageUtil.send(player, ok
                                    ? "§c✘ Équipe éliminée."
                                    : "§cAction impossible.");
                            rebuild();
                        });
            }
            case 24 -> { // réhabiliter
                boolean ok = plugin.getTeamManager().reviveTeam(color);
                MessageUtil.send(player, ok
                        ? "§a✔ Équipe réhabilitée."
                        : "§cAction impossible.");
                rebuild();
            }
            case 36 -> { // retour aux membres
                Bukkit.getScheduler().runTask(plugin,
                        () -> new TeamMembersGUI(plugin, player, color).open());
            }
            case 40 -> { // suppression avec confirmation
                confirm("§cSupprimer l'équipe " + color.getColoredName() + "§c ?",
                        () -> {
                            boolean removed =
                                    plugin.getTeamManager().removeTeam(color);
                            MessageUtil.send(player, removed
                                    ? "§7Équipe supprimée."
                                    : "§cÉquipe introuvable.");
                            player.closeInventory();
                        });
            }
            default -> { /* rien */ }
        }
    }

    /**
     * Ouvre une ConfirmGUI dont la validation exécute {@code action}.
     * L'annulation rouvre simplement cette GUI.
     */
    private void confirm(String question, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> new ConfirmGUI(
                plugin,
                player,
                question,
                action,
                this::open
        ).open());
    }

    /** Rafraîchit le contenu sans rouvrir (même inventaire). */
    private void rebuild() {
        fillOptions();
        player.updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
