package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.gui.ClassSelectionGUI;
import com.mceteams.xii.gui.ConfirmGUI;
import com.mceteams.xii.gui.TeamManagementGUI;
import com.mceteams.xii.gui.TeamMembersGUI;
import com.mceteams.xii.gui.AdminGUI;
import com.mceteams.xii.gui.TeamOptionsGUI;
import com.mceteams.xii.gui.TeamSelectionGUI;
import com.mceteams.xii.service.ClassService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Surveillance de l'inventaire (spec §12/§18).
 *
 * Responsabilités (délégation, pas de logique métier) :
 * - protéger les items spéciaux contre déplacement/drop/duplication ;
 * - router les clics dans nos GUIs (pattern InventoryHolder) ;
 * - verrouiller la rangée du bas pour le Mineur (ClassService).
 */
public class InventoryListener implements Listener {

    private final XiiPlugin plugin;

    public InventoryListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système actif ? (spec §33 : jamais de test de phase ici) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isInventoryListenerEnabled();
    }

    // -----------------------------------------------------------------
    // Clics
    // -----------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 1. Routage vers nos GUIs (elles gèrent elles-mêmes l'annulation).
        if (routeGuiClick(event)) {
            return;
        }

        // 1bis. GUI d'ouverture de colis : AUCUN clic autorisé pendant
        // l'animation spirale (les vitres ne sont pas volables !).
        if (event.getView().getTopInventory().getHolder()
                instanceof com.mceteams.xii.service.PackageService.OpeningHolder) {
            event.setCancelled(true);
            return;
        }

        // 2. Protection des items spéciaux : interdits de bouger, de
        // sortir, d'être dupliqués via hotbar swap, etc.
        if (involvesSpecialItem(event)) {
            event.setCancelled(true);
            return;
        }

        // 3. Rangée verrouillée du Mineur : clics directs sur les slots
        // 27..35 de SON inventaire interdits.
        var data = plugin.getPlayerManager().getData(player);
        if (data.getPlayerClass() == com.mceteams.xii.enums.PlayerClass.MINER
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && plugin.getClassService().isMinerLockedSlot(event.getSlot())) {
            event.setCancelled(true);
        }
    }

    /**
     * @return true si le clic a été traité par une GUI du plugin.
     */
    private boolean routeGuiClick(InventoryClickEvent event) {
        var holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof TeamSelectionGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof AdminGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof TeamManagementGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof TeamMembersGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof TeamOptionsGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof ClassSelectionGUI gui) {
            gui.handleClick(event);
            return true;
        }
        if (holder instanceof ConfirmGUI gui) {
            gui.handleClick(event);
            return true;
        }
        return false;
    }

    /**
     * Le clic concerne-t-il un item spécial ?
     * On vérifie : item cliqué, item sous curseur, et les swaps hotbar.
     */
    private boolean involvesSpecialItem(InventoryClickEvent event) {
        if (com.mceteams.xii.util.ItemUtil.isSpecialItem(event.getCurrentItem())) {
            return true;
        }
        if (com.mceteams.xii.util.ItemUtil.isSpecialItem(event.getCursor())) {
            return true;
        }
        // Clic number-key (hotbar swap) : on vérifie l'item visé de la hotbar.
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && event.getWhoClicked() instanceof Player player) {
            if (com.mceteams.xii.util.ItemUtil
                    .isSpecialItem(player.getInventory().getItem(hotbarButton))) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Glisser-déposer (drag) : protège aussi les items spéciaux
    // -----------------------------------------------------------------

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // GUI d'ouverture de colis : aucun drag autorisé.
        if (event.getView().getTopInventory().getHolder()
                instanceof com.mceteams.xii.service.PackageService.OpeningHolder) {
            event.setCancelled(true);
            return;
        }
        for (org.bukkit.inventory.ItemStack item : event.getNewItems().values()) {
            if (com.mceteams.xii.util.ItemUtil.isSpecialItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
        // Rangée verrouillée du Mineur : aucun drag ne peut y déposer.
        if (event.getWhoClicked() instanceof Player player
                && plugin.getPlayerManager().getData(player).getPlayerClass()
                == com.mceteams.xii.enums.PlayerClass.MINER) {
            // Les slots bruts du bas commencent après l'inventaire du haut.
            int bottomStart = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= bottomStart
                        && plugin.getClassService()
                        .isMinerLockedSlot(rawSlot - bottomStart)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Fermeture d'inventaire : passe anti-doublons des items spéciaux
    // -----------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Sécurité anti-duplication : au plus UN exemplaire par type interne.
        deduplicateSpecialItems(player);
    }

    /**
     * Supprime les exemplaires en trop des items spéciaux du joueur :
     * il ne doit exister qu'un seul bouton de chaque type (spec §12).
     */
    private void deduplicateSpecialItems(Player player) {
        var inventory = player.getInventory();
        java.util.Set<String> seenTypes = new java.util.HashSet<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            String type = com.mceteams.xii.util.ItemUtil
                    .getInternalType(inventory.getItem(slot));
            if (type == null) {
                continue;
            }
            if (!seenTypes.add(type)) {
                inventory.setItem(slot, null); // doublon => supprimé
            }
        }
        player.updateInventory();
    }
}
