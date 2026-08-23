package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashSet;
import java.util.Set;

public class RestrictionManager {
    private final GameManager gameManager;
    private final Set<Material> blacklistedItems = new HashSet<>();

    public RestrictionManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public boolean isBlockBreakAllowed(BlockBreakEvent event) {
        GameState state = gameManager.getState();
        Player player = event.getPlayer();

        if (state == GameState.NON_SETUP) return true;
        if (state == GameState.COMBAT) return true;

        if (state == GameState.WAITING || state == GameState.PREPARATION) {
            return false;
        }

        return true;
    }

    public boolean isBlockPlaceAllowed(BlockPlaceEvent event) {
        GameState state = gameManager.getState();
        Player player = event.getPlayer();

        if (state == GameState.NON_SETUP) return true;
        if (state == GameState.COMBAT) return true;

        if (state == GameState.WAITING) {
            return false;
        }

        if (state == GameState.PREPARATION) {
            return true;
        }

        return false;
    }

    public boolean isItemDropAllowed(PlayerDropItemEvent event) {
        GameState state = gameManager.getState();

        if (state == GameState.NON_SETUP) return true;
        if (state == GameState.COMBAT) return true;

        if (state == GameState.WAITING) return false;
        if (state == GameState.PREPARATION) return false;

        return false;
    }

    public boolean isItemDragAllowed(InventoryDragEvent event) {
        GameState state = gameManager.getState();

        if (state == GameState.NON_SETUP) return true;
        if (state == GameState.COMBAT) return true;

        return false;
    }

    public boolean isPvPAllowed(EntityDamageByEntityEvent event) {
        GameState state = gameManager.getState();

        if (state == GameState.COMBAT) return true;

        if (state == GameState.WAITING) return false;
        if (state == GameState.PREPARATION) return false;

        return false;
    }

    public boolean isFoodLevelChangeAllowed(FoodLevelChangeEvent event) {
        GameState state = gameManager.getState();

        if (state == GameState.WAITING) return false;
        if (state == GameState.PREPARATION) return false;

        return true;
    }

    public boolean isHotbarItemRestricted(Material material) {
        return blacklistedItems.contains(material);
    }

    public boolean isInteractAllowed(PlayerInteractEvent event) {
        GameState state = gameManager.getState();

        if (state == GameState.NON_SETUP) return true;
        if (state == GameState.COMBAT) return true;

        if (state == GameState.WAITING) {
            Player player = event.getPlayer();
            org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) return true;
            if (item.getType() == Material.TRIPWIRE_HOOK && player.hasPermission("xii.admin")) return true;
            if (player.getInventory().getHeldItemSlot() == 4) return true;
            if (player.getInventory().getHeldItemSlot() == 8) return true;
            return false;
        }

        return true;
    }

    public Set<Material> getBlacklistedItems() {
        return blacklistedItems;
    }

    public void blacklistItem(Material material) {
        blacklistedItems.add(material);
    }

    public void whitelistItem(Material material) {
        blacklistedItems.remove(material);
    }

    public void clearBlacklist() {
        blacklistedItems.clear();
    }
}
