package com.mceteams.xii.item;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Gère la distribution/retirée des items de lobby (spec §5/§12).
 *
 * Règles :
 * - WAITING : tout le monde reçoit le sélecteur d'équipe,
 *   les opérateurs reçoivent aussi l'item admin ;
 * - dès le COUNTDOWN : les deux items sont retirés (spec §13) ;
 * - les items sont protégés contre move/drop/duplication via
 *   InventoryListener + une passe de nettoyage ici.
 *
 * Anti-duplication : à chaque distribution on RETIRE d'abord les
 * exemplaires existants puis on n'en pose qu'UN SEUL par type
 * (spec §12 : "un seul exemplaire de chaque bouton requis").
 */
public class LobbyItemManager {

    /** Slot fixe du sélecteur d'équipe dans la hotbar. */
    public static final int TEAM_SELECTOR_SLOT = 0;
    /** Slot fixe de l'item admin dans la hotbar. */
    public static final int ADMIN_SLOT = 8;

    private final XiiPlugin plugin;

    public LobbyItemManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Donne les items de lobby adaptés au joueur selon l'état courant.
     * Idempotent : peut être rappelé sans créer de doublons.
     */
    public void giveLobbyItems(Player player) {
        removeAll(player); // purge anti-doublon

        if (plugin.getGameManager().getState() == GameState.WAITING) {
            var data = plugin.getPlayerManager().getData(player);
            var selectedTeam = plugin.getTeamManager().getTeamOf(player.getUniqueId());
            ItemStack selector = TeamSelectorItem.build(
                    selectedTeam == null ? null : selectedTeam.getColor());
            player.getInventory().setItem(TEAM_SELECTOR_SLOT, selector);

            if (player.isOp()) {
                player.getInventory().setItem(ADMIN_SLOT, AdminItem.build());
            }
        }
        player.updateInventory();
    }

    /**
     * Retire TOUS les items spéciaux du joueur (countdown, début de
     * partie, /zone delete...).
     */
    public void removeAll(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (ItemUtil.isSpecialItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        // Le curseur et l'armure ne devraient jamais en contenir,
        // mais on nettoie par sécurité.
        player.setItemOnCursor(null);
        player.updateInventory();
    }

    /**
     * Resynchronise les items de tous les joueurs en ligne.
     * Appelé par SystemController.refresh().
     */
    public void refreshAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveLobbyItems(player);
        }
    }

    /**
     * Retire les items de tous les joueurs (fin de lobby, zone delete).
     */
    public void clearAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAll(player);
        }
    }
}
