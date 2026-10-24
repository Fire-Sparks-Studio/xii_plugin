package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * GUI d'inspection d'inventaire (admin).
 *
 * Double coffre (54 slots) :
 *  - slots 0..3 : armure (casque, plastron, jambières, bottes)
 *  - slot 4     : main secondaire (bouclier)
 *  - slots 9..44 : inventaire principal (36 slots)
 *
 * Les modifications sont synchronisées vers le joueur cible à la fermeture.
 */
public class InvseeGUI implements InventoryHolder {
    private final XiiPlugin plugin;
    private final UUID targetUuid;
    private Inventory inventory;

    public InvseeGUI(XiiPlugin plugin, Player target) {
        this.plugin = plugin;
        this.targetUuid = target.getUniqueId();
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void open(Player admin) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            return;
        }
        inventory = Bukkit.createInventory(this, 54, "§cInvsee: " + target.getName());
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta gm = glass.getItemMeta();
        if (gm != null) {
            gm.displayName(net.kyori.adventure.text.Component.text(" "));
            glass.setItemMeta(gm);
        }
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass.clone());
        }
        // Armure: ligne 2 colonnes 1-4
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null && armor.length >= 4) {
            inventory.setItem(9, armor[3]);
            inventory.setItem(10, armor[2]);
            inventory.setItem(11, armor[1]);
            inventory.setItem(12, armor[0]);
        }
        // Bouclier colonne 4 ligne 4
        inventory.setItem(30, target.getInventory().getItemInOffHand());
        // Inventaire principal lignes 7-10? map to slots 36-44? better use 27-44 for inv + 45-53 hotbar
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < 27; i++) {
            inventory.setItem(18 + i, contents[i]);
        }
        for (int i = 0; i < 9; i++) {
            inventory.setItem(45 + i, target.getInventory().getItem(i));
        }
        admin.openInventory(inventory);
    }

    public void syncBack() {
        syncToTarget();
    }

    public void syncToTarget() {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || inventory == null) {
            return;
        }
        ItemStack[] armor = new ItemStack[4];
        armor[3] = inventory.getItem(9);
        armor[2] = inventory.getItem(10);
        armor[1] = inventory.getItem(11);
        armor[0] = inventory.getItem(12);
        target.getInventory().setArmorContents(armor);
        target.getInventory().setItemInOffHand(inventory.getItem(30));
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 27; i++) {
            contents[i] = inventory.getItem(18 + i);
        }
        for (int i = 0; i < 9; i++) {
            contents[27 + i] = inventory.getItem(45 + i);
        }
        target.getInventory().setStorageContents(contents);
        target.updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
