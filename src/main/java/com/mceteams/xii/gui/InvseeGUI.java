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
        inventory = Bukkit.createInventory(this, 99, "§cInvsee: " + target.getName());
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta gm = glass.getItemMeta();
        if (gm != null) {
            gm.displayName(net.kyori.adventure.text.Component.text(" "));
            glass.setItemMeta(gm);
        }
        for (int i = 0; i < 99; i++) {
            inventory.setItem(i, glass.clone());
        }
        // Ligne 2: colonnes 1-4 -> armure (slots 10,19,28,37)
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null && armor.length >= 4) {
            inventory.setItem(10, armor[3]); // casque
            inventory.setItem(19, armor[2]); // plastron
            inventory.setItem(28, armor[1]); // jambières
            inventory.setItem(37, armor[0]); // bottes
        }
        inventory.setItem(37 + 9, target.getInventory().getItemInOffHand()); // bouclier colonne 4 ligne 4 approx -> slot 46
        inventory.setItem(46, target.getInventory().getItemInOffHand());
        // Inventaire principal lignes 7-10 (slots 63-98)
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < 36; i++) {
            inventory.setItem(63 + i, contents[i]);
        }
        // Hotbar ligne 11 (slots 90-98)
        for (int i = 0; i < 9; i++) {
            inventory.setItem(90 + i, target.getInventory().getItem(i));
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
        armor[3] = inventory.getItem(10);
        armor[2] = inventory.getItem(19);
        armor[1] = inventory.getItem(28);
        armor[0] = inventory.getItem(37);
        target.getInventory().setArmorContents(armor);
        target.getInventory().setItemInOffHand(inventory.getItem(46));
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            contents[i] = inventory.getItem(63 + i);
        }
        target.getInventory().setStorageContents(contents);
        for (int i = 0; i < 9; i++) {
            target.getInventory().setItem(i, inventory.getItem(90 + i));
        }
        target.updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
