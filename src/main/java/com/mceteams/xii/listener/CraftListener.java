package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Restrictions de CRAFT.
 *
 * RÈGLE UTILISATEUR : la TABLE D'ENCHANTEMENT ne peut PAS être craftée.
 * Elle (et la bibliothèque) n'existent que comme DÉCOR dans les bases ;
 * la partie n'accorde pas de moyen d'enchantement via la fabrication.
 */
public class CraftListener implements Listener {

    private final XiiPlugin plugin;

    public CraftListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getRecipe() == null
                ? null : event.getRecipe().getResult();
        if (result == null || result.getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        // Table d'enchantement impossible à fabriquer : le résultat est
        // vidé ET la grille est signalée. On informe chaque joueur qui
        // regarde cette grille.
        event.getInventory().setResult(null);
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player) {
                MessageUtil.sendActionBar(player,
                        "§c✘ La table d'enchantement ne peut pas être fabriquée.");
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe() == null
                ? null : event.getRecipe().getResult();
        if (result != null && result.getType() == Material.ENCHANTING_TABLE) {
            event.setCancelled(true);
        }
    }
}