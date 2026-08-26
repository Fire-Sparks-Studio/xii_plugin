package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Réclamation des objets RARES/LÉGENDAIRES au RAMASSAGE AU SOL.
 *
 * Un objet rare taggué "non réclamé" (rare_claimed = 0) qui finit au
 * sol (interruption de transfert, coffre détruit...) doit être annoncé
 * quand un joueur le ramasse, exactement comme via le transfert des
 * colis. Le tag passe à 1 => jamais d'annonce en double.
 */
public class RarePickupListener implements Listener {

    private final XiiPlugin plugin;

    public RarePickupListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!com.mceteams.xii.util.ItemUtil.isUnclaimedRare(stack)) {
            return;
        }
        // Marque AVANT l'ajout à l'inventaire (le tag persiste sur
        // l'entité item même si l'événement est modifié plus tard).
        com.mceteams.xii.util.ItemUtil.markClaimed(stack);
        event.getItem().setItemStack(stack);
        plugin.getPackageService().handleRareRetrieval(player, stack);
    }
}
