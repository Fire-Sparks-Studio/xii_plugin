package com.mceteams.xii.listener;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.HotbarManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.ui.AdminGUI;
import com.mceteams.xii.ui.TeamSelectorGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class HotbarListener implements Listener {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final TeamSelectorGUI teamSelectorGUI;

    public HotbarListener(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.teamSelectorGUI = new TeamSelectorGUI(teamManager);
    }

    @EventHandler
    public void onHotbarClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getState() != GameState.WAITING) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        int slot = player.getInventory().getHeldItemSlot();

        if (slot == 0 && item.getType() == Material.TRIPWIRE_HOOK) {
            AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
            player.openInventory(adminGUI.create());
        }

        if (slot == 4 && item.getType() == Material.WHITE_BANNER) {
            player.openInventory(teamSelectorGUI.create(player));
        }

        if (slot == 8 && item.getType() == Material.BARRIER) {
            if (!gameManager.isLeaveEnabled()) {
                player.sendMessage("§cLe leave est désactivé !");
                return;
            }
            GameTeam team = teamManager.getTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage("§cTu n'es dans aucune équipe !");
                return;
            }
            teamManager.removePlayer(player.getUniqueId());
            player.sendMessage("§aTu as quitté l'équipe.");
            hotbarManager.giveHotbar(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        if (title.equals("§6Choisir une équipe")) {
            event.setCancelled(true);
            if (gameManager.getState() != GameState.WAITING) {
                player.closeInventory();
                return;
            }

            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;
            if (event.getRawSlot() == 4) return;

            TeamColor color = null;
            for (TeamColor c : TeamColor.values()) {
                if (item.getType() == c.getMaterial()) {
                    color = c;
                    break;
                }
            }
            if (color == null) return;

            GameTeam team = teamManager.getTeam(color);
            if (team == null) {
                team = teamManager.createTeam(color);
            }

            if (teamManager.addPlayer(player.getUniqueId(), team)) {
                player.sendMessage("§aTu as rejoint l'équipe " + color.getName(Lang.FR) + " !");
                player.closeInventory();
                hotbarManager.giveHotbar(player);
            } else {
                player.sendMessage("§cL'équipe est complète !");
            }
        }

        // Admin
        if (title.equals("§6§lAdmin GUI")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) return;

            switch (event.getRawSlot()) {
                case 10 -> {
                    player.sendMessage("§dTeam Management — bientôt !");
                }
                case 13 -> {
                    player.sendMessage("§eGame Management — bientôt !");
                }
                case 16 -> {
                    player.sendMessage("§6Item Management — bientôt !");
                }
            }
        }
    }
}