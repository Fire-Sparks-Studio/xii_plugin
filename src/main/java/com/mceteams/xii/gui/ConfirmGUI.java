package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * GUI de CONFIRMATION générique.
 *
 * Affiche une question + deux boutons (confirmer / annuler) et exécute
 * le Runnable correspondant. Réutilisée par les actions destructives
 * (ex : suppression d'une équipe).
 */
public class ConfirmGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private final String question;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private Inventory inventory;

    public ConfirmGUI(XiiPlugin plugin,
                      Player player,
                      String question,
                      Runnable onConfirm,
                      Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.question = question;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "§8Confirmation");
        inventory.setItem(11, ItemUtil.buildNamedItem(
                Material.LIME_WOOL, "§aConfirmer", null));
        inventory.setItem(15, ItemUtil.buildNamedItem(
                Material.RED_WOOL, "§cAnnuler", null));
        inventory.setItem(13, ItemUtil.buildNamedItem(
                Material.PAPER,
                question == null ? "§7Confirmer ?" : question,
                null));
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        int slot = event.getSlot();
        if (slot == 11) {
            player.closeInventory();
            if (onConfirm != null) {
                // Exécution différée : jamais pendant le traitement du clic.
                Bukkit.getScheduler().runTask(plugin, onConfirm);
            }
        } else if (slot == 15) {
            player.closeInventory();
            if (onCancel != null) {
                Bukkit.getScheduler().runTask(plugin, onCancel);
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
