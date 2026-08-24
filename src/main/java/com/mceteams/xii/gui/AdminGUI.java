package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.util.ItemUtil;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * GUI d'ADMINISTRATION (spec §5).
 *
 * Ouverte via l'item TRIPWIRE (opérateurs, pendant l'attente).
 * Permet notamment de gérer :
 * - le lancement de la partie (/party start) ;
 * - l'annulation du countdown / l'arrêt de la partie (/party stop) ;
 * - la gestion des équipes (TeamManagementGUI).
 *
 * Le saut de jour reste une commande : /party set <jour> (spec §34).
 */
public class AdminGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private Inventory inventory;

    public AdminGUI(XiiPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "§cAdministration");
        fillButtons();
        player.openInventory(inventory);
    }

    /** Boutons selon l'état courant du jeu. */
    private void fillButtons() {
        GameState state = plugin.getGameManager().getState();

        // Lancement : uniquement en WAITING.
        inventory.setItem(10, ItemUtil.buildNamedItem(
                Material.EMERALD,
                "§aLancer la partie",
                List.of(state == GameState.WAITING
                        ? "§7Démarre le compte à rebours."
                        : "§cIndisponible maintenant.")));

        // Annulation / arrêt.
        String stopName = state == GameState.COUNTDOWN
                ? "§eAnnuler le compte à rebours"
                : "§cArrêter la partie";
        inventory.setItem(12, ItemUtil.buildNamedItem(
                Material.REDSTONE_BLOCK,
                stopName,
                List.of("§7Équivalent /party stop")));

        // Gestion des équipes.
        inventory.setItem(14, ItemUtil.buildNamedItem(
                Material.NAME_TAG,
                "§bGérer les équipes",
                List.of("§7Créer, supprimer, ajuster...")));

        // Fermer.
        inventory.setItem(16, ItemUtil.buildNamedItem(
                Material.BARRIER, "§7Fermer", null));
    }

    /** Routage des clics (appelé par InventoryListener). */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        int slot = event.getSlot();

        switch (slot) {
            case 10 -> {
                // Lancer la partie (validation centralisée : zone + équipes).
                String error = plugin.getGameManager().startParty();
                if (error != null) {
                    MessageUtil.send(player, "§c" + error);
                    // La GUI reste ouverte avec ses boutons actualisables.
                    fillButtons();
                    player.updateInventory();
                } else {
                    player.closeInventory();
                }
            }
            case 12 -> {
                // Stop / annulation.
                player.closeInventory();
                if (plugin.getGameManager().getState() == GameState.COUNTDOWN) {
                    plugin.getGameManager().cancelCountdown();
                } else if (plugin.getGameManager().isRunning()) {
                    plugin.getGameManager().stopParty();
                } else {
                    MessageUtil.send(player, "§cAucune partie à arrêter.");
                }
            }
            case 14 -> {
                // Ouvre la gestion des équipes au tick suivant
                // (on ne modifie pas un inventaire pendant son clic).
                Bukkit.getScheduler().runTask(plugin,
                        () -> new TeamManagementGUI(plugin, player).open());
            }
            case 16 -> player.closeInventory();
            default -> { /* case vide : rien */ }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
