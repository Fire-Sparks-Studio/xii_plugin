package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.item.AdminItem;
import com.mceteams.xii.item.TeamSelectorItem;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Interactions des joueurs avec les ITEMS spéciaux (spec §5/§12).
 *
 * - clic droit sur le sélecteur d'équipe => TeamSelectionGUI ;
 * - clic droit sur l'item admin => AdminGUI ;
 * - boussole spectateur => joueur suivant/précédent ;
 * - toute autre interaction en état protégé est bloquée.
 */
public class InteractionListener implements Listener {

    private final XiiPlugin plugin;

    public InteractionListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        // --- Items spéciaux : clic droit uniquement -------------------
        if (item != null && (action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK)) {

            // UPGRADE consommable : vérification de l'état de partie avant
            // usage. Les upgrades ne sont utilisables qu'en Préparation ou
            // Combat (jamais au lobby, en countdown ni en fin de partie).
            String itemData = com.mceteams.xii.util.ItemUtil.getItemData(item);
            if (itemData != null && itemData.startsWith("upgrade:")) {
                var state = plugin.getGameManager().getState();
                if (state != GameState.PREPARATION && state != GameState.COMBAT) {
                    MessageUtil.send(player,
                            "§cLes upgrades ne sont utilisables qu'en préparation ou en combat.");
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                plugin.getUpgradeService().handleUse(player, item,
                        itemData.substring("upgrade:".length()));
                return;
            }

            // Sélecteur d'équipe.
            if (com.mceteams.xii.util.ItemUtil
                    .isType(item, TeamSelectorItem.INTERNAL_TYPE)) {
                event.setCancelled(true);
                if (plugin.getGameSystems().isTeamItemsEnabled()) {
                    new com.mceteams.xii.gui.TeamSelectionGUI(plugin, player).open();
                }
                return;
            }

            // Item admin (réservé aux opérateurs).
            if (com.mceteams.xii.util.ItemUtil.isType(item, AdminItem.INTERNAL_TYPE)) {
                event.setCancelled(true);
                if (plugin.getGameSystems().isAdminItemsEnabled() && player.isOp()) {
                    new com.mceteams.xii.gui.AdminGUI(plugin, player).open();
                }
                return;
            }

            // Boussole spectateur : cycle de ciblage.
            if (plugin.getSpectatorService().isSpectatorCompass(item)) {
                event.setCancelled(true);
                plugin.getSpectatorService().cycleTarget(player, true);
                return;
            }
        }

        // Clic gauche avec la boussole spectateur : cible précédente.
        if (item != null && (action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK)
                && plugin.getSpectatorService().isSpectatorCompass(item)) {
            event.setCancelled(true);
            plugin.getSpectatorService().cycleTarget(player, false);
            return;
        }

        // --- SPECTATEUR : blocage TOTAL de tout le reste --------------
        // (casser, poser, frapper, manger, ouvrir, utiliser...) La
        // boussole a déjà été traitée ci-dessus.
        if (plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }

        // --- Blocage générique des interactions "monde" ---------------
        // En état protégé (lobby...) : rien à faire sur le monde.
        boolean protectedState =
                plugin.getProtectionService().shouldBlockWorldInteraction(player);
        if (protectedState) {
            if (action == Action.RIGHT_CLICK_BLOCK
                    || action == Action.LEFT_CLICK_BLOCK
                    || action == Action.PHYSICAL) {
                event.setCancelled(true);
            }
        }
    }
}
