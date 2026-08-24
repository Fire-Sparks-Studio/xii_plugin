package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.item.LobbyItemManager;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUI de SÉLECTION D'ÉQUIPE (spec §5).
 *
 * Ouverte via l'item sélecteur du lobby. Affiche les 4 équipes avec
 * leur effectif ; un clic fait rejoindre l'équipe (si non pleine).
 * L'item sélecteur du joueur est mis à jour après le choix.
 */
public class TeamSelectionGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public TeamSelectionGUI(XiiPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Construit puis ouvre l'inventaire pour le joueur. */
    public void open() {
        this.inventory = Bukkit.createInventory(this, 27,
                "§8Sélection d'équipe");
        fillTeams();
        player.openInventory(inventory);
    }

    /** Remplit les cases des équipes selon l'état courant. */
    private void fillTeams() {
        int slot = 10;
        for (TeamColor color : TeamColor.values()) {
            var team = plugin.getTeamManager().getTeam(color);
            boolean exists = team != null;

            String name = exists
                    ? color.getColoredName() + " §7(" + team.getPlayerCount()
                    + "/" + team.getMaxPlayers() + ")"
                    : "§7" + color.getDisplayName() + " §8(non créée)";

            java.util.List<String> lore = exists
                    ? java.util.List.of(
                    team.isFull() ? "§cÉquipe pleine !" : "§7Clique pour rejoindre.")
                    : java.util.List.of("§7Demandez à un admin de la créer.");

            // Laine couleur si dispo, sinon teinture grise.
            ItemStack item = com.mceteams.xii.util.ItemUtil.buildNamedItem(
                    exists ? com.mceteams.xii.util.TeamUtil.woolOf(color)
                            : Material.GRAY_DYE,
                    name, lore);
            inventory.setItem(slot, item);
            slot += 2;
        }
    }

    /**
     * Gère un clic dans cette GUI (appelé par InventoryListener).
     * Le clic est TOUJOURS annulé : les GUIs ne perdent pas d'items.
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        // Identification de l'équipe par le matériau de la laine.
        for (TeamColor color : TeamColor.values()) {
            if (clicked.getType() == com.mceteams.xii.util.TeamUtil.woolOf(color)) {
                tryJoin(color);
                return;
            }
        }
    }

    /** Tentative de rejoindre une équipe + feedback. */
    private void tryJoin(TeamColor color) {
        var result = plugin.getTeamManager().addPlayer(player.getUniqueId(), color);
        switch (result) {
            case OK -> {
                MessageUtil.send(player, "§7Équipe rejointe : "
                        + color.getColoredName());
                // Mise à jour de l'item sélecteur dans la hotbar.
                player.closeInventory();
                plugin.getLobbyItemManager().giveLobbyItems(player);
            }
            case FULL -> {
                MessageUtil.send(player, "§cCette équipe est pleine !");
                open(); // rafraîchit les compteurs
            }
            case ALREADY_IN_TEAM ->
                    MessageUtil.send(player, "§7Vous êtes déjà dans cette équipe.");
            case TEAM_NOT_FOUND ->
                    MessageUtil.send(player, "§cÉquipe introuvable.");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
