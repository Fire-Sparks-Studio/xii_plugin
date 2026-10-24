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
        // Armure
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null && armor.length >= 4) {
            inventory.setItem(0, armor[3]); // casque
            inventory.setItem(1, armor[2]); // plastron
            inventory.setItem(2, armor[1]); // jambières
            inventory.setItem(3, armor[0]); // bottes
        }
        inventory.setItem(4, target.getInventory().getItemInOffHand());
        // Inventaire principal
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && i < 36; i++) {
            inventory.setItem(9 + i, contents[i]);
        }
        admin.openInventory(inventory);
    }

    public void syncBack() {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || inventory == null) {
            return;
        }
        ItemStack[] armor = new ItemStack[4];
        armor[3] = inventory.getItem(0); // casque
        armor[2] = inventory.getItem(1); // plastron
        armor[1] = inventory.getItem(2); // jambières
        armor[0] = inventory.getItem(3); // bottes
        target.getInventory().setArmorContents(armor);
        target.getInventory().setItemInOffHand(inventory.getItem(4));
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            contents[i] = inventory.getItem(9 + i);
        }
        target.getInventory().setStorageContents(contents);
        target.updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
