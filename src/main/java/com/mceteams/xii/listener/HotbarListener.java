package com.mceteams.xii.listener;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.ChatInputManager;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.HotbarManager;
import com.mceteams.xii.manager.SetupManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.ui.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class HotbarListener implements Listener {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final SetupManager setupManager;
    private final ChatInputManager chatInputManager;
    private final TeamSelectorGUI teamSelectorGUI;

    public HotbarListener(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager, SetupManager setupManager, ChatInputManager chatInputManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.setupManager = setupManager;
        this.chatInputManager = chatInputManager;
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

        if (slot == 4) {
            player.openInventory(teamSelectorGUI.create(player));
        }

        if (slot == 8) {
            LanguageGUI langGUI = new LanguageGUI();
            player.openInventory(langGUI.create(player));
        }
    }

    // Chat input handler
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (chatInputManager.hasPending(player)) {
            event.setCancelled(true);
            String message = event.getMessage();
            if (message.equalsIgnoreCase("cancel")) {
                chatInputManager.cancel(player);
                player.sendMessage("§cAction annulée.");
            } else {
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("XII-Days"),
                        () -> chatInputManager.submit(player, message)
                );
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (gameManager.getState() != GameState.WAITING) return;

        String title = event.getView().getTitle();

        // ===== Team Selector =====
        if (title.equals("§6Choisir une équipe")) {
            event.setCancelled(true);
            handleTeamSelectorClick(player, event);
            return;
        }

        // ===== Admin GUI =====
        if (title.equals("§6§lAdmin GUI")) {
            event.setCancelled(true);
            handleAdminGUIClick(player, event);
            return;
        }

        // ===== Team Management =====
        if (title.equals("§6§lTeam Management")) {
            event.setCancelled(true);
            handleTeamManagementClick(player, event);
            return;
        }

        // ===== Team Options =====
        if (event.getView().getTitle().startsWith("§6§l") && !title.equals("§6§lAdmin GUI")
                && !title.equals("§6§lTeam Management") && !title.equals("§e§lGame Management")
                && !title.equals("§6§lItem Management")) {
            // Check if it's a team options title (color name)
            for (TeamColor c : TeamColor.values()) {
                if (title.equals("§6§l" + c.getName(Lang.FR))) {
                    event.setCancelled(true);
                    handleTeamOptionsClick(player, event, title);
                    return;
                }
            }
        }

        // ===== Game Management =====
        if (title.equals("§e§lGame Management")) {
            event.setCancelled(true);
            handleGameManagementClick(player, event);
            return;
        }

        // ===== Item Management =====
        if (title.equals("§6§lItem Management")) {
            event.setCancelled(true);
            handleItemManagementClick(player, event);
            return;
        }

        // ===== Language =====
        if (title.equals("§6§lLanguage")) {
            event.setCancelled(true);
            handleLanguageClick(player, event);
            return;
        }

        // ===== Team Create =====
        if (title.equals("§a§lCréer une équipe")) {
            event.setCancelled(true);
            handleTeamCreateClick(player, event);
            return;
        }

        // ===== Team Player Select =====
        if (title.equals("§a§lAjouter un joueur") || title.equals("§c§lRetirer un joueur")) {
            event.setCancelled(true);
            handleTeamPlayerSelectClick(player, event, title);
            return;
        }

        // ===== Hotbar =====
        if (event.getClickedInventory() == player.getInventory()) {
            int rawSlot = event.getRawSlot();
            if (rawSlot >= 0 && rawSlot <= 8) {
                event.setCancelled(true);
            }
        }

        // ===== Hotbar (depuis un GUI ouvert) =====
        if (event.getClickedInventory() == player.getInventory()) {
            int rawSlot = event.getRawSlot();
            event.setCancelled(true);

            if (rawSlot == 0) {
                if (player.hasPermission("xii.admin")) {
                    AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
                    player.openInventory(adminGUI.create());
                }
            } else if (rawSlot == 4) {
                player.openInventory(teamSelectorGUI.create(player));
            } else if (rawSlot == 8) {
                LanguageGUI langGUI = new LanguageGUI();
                player.openInventory(langGUI.create(player));
            }
        }
    }

    private void handleTeamSelectorClick(Player player, InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        // Barrier = leave team
        if (event.getRawSlot() == 4 && current.getType() == Material.BARRIER) {
            GameTeam team = teamManager.getTeam(player.getUniqueId());
            if (team != null) {
                teamManager.removePlayer(player.getUniqueId());
                player.sendMessage("§aTu as quitté l'équipe.");
                hotbarManager.refreshTeamItem(player);
                player.openInventory(teamSelectorGUI.create(player));
            }
            return;
        }

        TeamColor color = null;
        for (TeamColor c : TeamColor.values()) {
            if (current.getType() == c.getMaterial()) {
                color = c;
                break;
            }
        }
        if (color == null) return;

        GameTeam team = teamManager.getTeam(color);
        if (team != null && teamManager.addPlayer(player.getUniqueId(), team)) {
            player.sendMessage("§aTu as rejoint l'équipe " + color.getName(Lang.FR) + " !");
            player.closeInventory();
            hotbarManager.refreshTeamItem(player);
        } else {
            player.sendMessage("§cL'équipe est complète !");
        }
    }

    private void handleAdminGUIClick(Player player, InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case 10 -> {
                TeamManagementGUI gui = new TeamManagementGUI(teamManager);
                player.openInventory(gui.create());
            }

            case 13 -> {
                GameManagementGUI gui = new GameManagementGUI(gameManager);
                player.openInventory(gui.create());
            }

            case 19 -> { // Allow Join toggle
                gameManager.setJoinEnabled(!gameManager.isJoinEnabled());
                boolean enabled = gameManager.isJoinEnabled();
                player.sendMessage("§aJoin " + (enabled ? "activé" : "désactivé") + " !");
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
                player.openInventory(adminGUI.create());
            }

            case 20 -> { // Allow Leave toggle
                gameManager.setLeaveEnabled(!gameManager.isLeaveEnabled());
                boolean enabled = gameManager.isLeaveEnabled();
                player.sendMessage("§aLeave " + (enabled ? "activé" : "désactivé") + " !");
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
                player.openInventory(adminGUI.create());
            }
        }
    }

    private void handleTeamManagementClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int size = event.getInventory().getSize();

        // Back button
        if (rawSlot == size - 1) {
            AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
            player.openInventory(adminGUI.create());
            return;
        }

        // Create team button
        if (rawSlot == size - 5 && item.getType() == Material.LIME_STAINED_GLASS_PANE) {
            TeamCreateGUI createGUI = new TeamCreateGUI(teamManager);
            player.openInventory(createGUI.create());
            return;
        }

        // Team slots
        for (GameTeam team : teamManager.getTeams()) {
            if (item.getType() == team.getColor().getMaterial()) {
                TeamOptionsGUI gui = new TeamOptionsGUI(gameManager);
                player.openInventory(gui.create(team));
                return;
            }
        }
    }

    private void handleTeamOptionsClick(Player player, InventoryClickEvent event, String title) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Find team from title
        GameTeam team = null;
        for (GameTeam t : teamManager.getTeams()) {
            if (title.equals("§6§l" + t.getColor().getName(Lang.FR))) {
                team = t;
                break;
            }
        }
        if (team == null) return;

        final GameTeam finalTeam = team;
        final String teamName = team.getColor().getName(Lang.FR);

        switch (event.getRawSlot()) {
            case 0 -> { // Destroy Heart
                teamManager.destroyHeart(team);
                player.sendMessage("§aCœur de l'équipe " + teamName + " détruit !");
                player.closeInventory();
            }

            case 1 -> { // Restore Heart
                team.setHeartAlive(true);
                player.sendMessage("§aCœur de l'équipe " + teamName + " restauré !");
                player.closeInventory();
            }

            case 2 -> { // Eliminate
                team.destroyHeart();
                for (java.util.UUID uuid : team.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setGameMode(GameMode.SPECTATOR);
                        p.sendMessage("§cVous avez été éliminé !");
                    }
                }
                player.sendMessage("§aÉquipe " + teamName + " éliminée !");
                player.closeInventory();
            }

            case 3 -> { // Revive
                team.setHeartAlive(true);
                for (java.util.UUID uuid : team.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setGameMode(GameMode.SURVIVAL);
                    }
                }
                player.sendMessage("§aÉquipe " + teamName + " réanimée !");
                player.closeInventory();
            }

            case 5 -> { // TP Base
                if (team.getSpawn() == null) {
                    player.sendMessage("§cLe spawn de cette équipe n'est pas défini !");
                    return;
                }
                for (java.util.UUID uuid : team.getPlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(team.getSpawn());
                    }
                }
                player.sendMessage("§aJoueurs de l'équipe " + teamName + " téléportés !");
            }

            case 6 -> { // Set Spawn
                team.setSpawn(player.getLocation());
                player.sendMessage("§aSpawn défini !");
            }

            case 7 -> { // Set Heart
                team.setHeartLocation(player.getLocation());
                player.sendMessage("§aCœur défini !");
            }

            case 8 -> {
                chatInputManager.requestInput(player, "§eÉcris le nouveau nombre maximum de joueurs:", input -> {
                    try {
                        int max = Integer.parseInt(input);
                        finalTeam.setMaxPlayers(max);
                        player.sendMessage("§aLimite mise à " + max + " !");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cNombre invalide !");
                    }
                    TeamOptionsGUI gui = new TeamOptionsGUI(gameManager);
                    player.openInventory(gui.create(finalTeam));
                });
                player.closeInventory();
            }

            case 10 -> { // Add Player
                TeamPlayerSelectGUI addGUI = new TeamPlayerSelectGUI(teamManager);
                player.openInventory(addGUI.createForAdd(finalTeam));
            }

            case 11 -> { // Remove Player
                TeamPlayerSelectGUI removeGUI = new TeamPlayerSelectGUI(teamManager);
                player.openInventory(removeGUI.createForRemove(finalTeam));
            }

            case 15 -> { // Delete Team
                teamManager.deleteTeam(finalTeam);
                player.sendMessage("§aÉquipe " + teamName + " supprimée !");
                TeamManagementGUI gui = new TeamManagementGUI(teamManager);
                player.openInventory(gui.create());
            }

            case 22 -> { // Back
                TeamManagementGUI gui = new TeamManagementGUI(teamManager);
                player.openInventory(gui.create());
            }
        }
    }

    private void handleGameManagementClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        switch (rawSlot) {
            case 10 -> { // Start/Stop
                if (gameManager.getState() == GameState.WAITING) {
                    if (teamManager.getTeamCount() < 2) {
                        player.sendMessage("§cIl faut au moins 2 équipes !");
                        return;
                    }
                    gameManager.startGame();
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("\n§6§lXII DAYS §7a commencé !\n"));
                } else {
                    gameManager.endGame();
                    Bukkit.broadcast(net.kyori.adventure.text.Component.text("\n§c§lXII DAYS §7a été arrêté !\n"));
                }
                player.closeInventory();
            }

            case 12 -> {
                chatInputManager.requestInput(player, "§eÉcris le jour (1-12):", input -> {
                    try {
                        int day = Integer.parseInt(input);
                        if (day < 1 || day > 12) {
                            player.sendMessage("§cLe jour doit être entre 1 et 12 !");
                        } else {
                            gameManager.getDayManager().setDay(day);
                            Bukkit.broadcast(net.kyori.adventure.text.Component.text("\n§6§lJour §c§l" + day + " §6§l!\n"));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cNombre invalide !");
                    }
                    GameManagementGUI gui = new GameManagementGUI(gameManager);
                    player.openInventory(gui.create());
                });
                player.closeInventory();
            }

            case 22 -> { // Back
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
                player.openInventory(adminGUI.create());
            }
        }
    }

    private void handleItemManagementClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        switch (rawSlot) {
            case 10 -> { // Blacklist Item
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne tiens aucun item !");
                    return;
                }
                gameManager.getBlacklistedItems().add(hand.getType());
                player.sendMessage("§c" + hand.getType().name() + " ajouté à la blacklist !");
            }
            case 12 -> { // Blacklist Block
                Block target = player.getTargetBlockExact(5);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne vises aucun bloc !");
                    return;
                }
                gameManager.getBlacklistedItems().add(target.getType());
                player.sendMessage("§c" + target.getType().name() + " ajouté à la blacklist !");
            }
            case 14 -> { // Whitelist Item
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne tiens aucun item !");
                    return;
                }
                gameManager.getBlacklistedItems().remove(hand.getType());
                player.sendMessage("§a" + hand.getType().name() + " retiré de la blacklist !");
            }
            case 16 -> { // Whitelist Block
                Block target = player.getTargetBlockExact(5);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne vises aucun bloc !");
                    return;
                }
                gameManager.getBlacklistedItems().remove(target.getType());
                player.sendMessage("§a" + target.getType().name() + " retiré de la blacklist !");
            }
            case 22 -> { // Back
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager);
                player.openInventory(adminGUI.create());
            }
        }
    }

    private void handleLanguageClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        switch (rawSlot) {
            case 3 -> {
                player.sendMessage("§aLangue changée en Français !");
                player.closeInventory();
            }
            case 5 -> {
                player.sendMessage("§aLanguage changed to English!");
                player.closeInventory();
            }
        }
    }

    private void handleTeamCreateClick(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();

        // Back button
        if (rawSlot == 49) {
            TeamManagementGUI gui = new TeamManagementGUI(teamManager);
            player.openInventory(gui.create());
            return;
        }

        // Find color from wool material
        for (TeamColor color : TeamColor.values()) {
            if (item.getType() == color.getMaterial()) {
                if (teamManager.getTeam(color) != null) {
                    player.sendMessage("§cCette équipe existe déjà !");
                    return;
                }
                teamManager.createTeam(color);
                player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " créée !");
                // Refresh the create GUI
                TeamCreateGUI createGUI = new TeamCreateGUI(teamManager);
                player.openInventory(createGUI.create());
                return;
            }
        }
    }

    private void handleTeamPlayerSelectClick(Player player, InventoryClickEvent event, String title) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();
        int size = event.getInventory().getSize();

        // Find team from title
        GameTeam team = null;
        for (GameTeam t : teamManager.getTeams()) {
            if (title.contains(t.getColor().getName(Lang.FR))) {
                team = t;
                break;
            }
        }
        if (team == null) return;

        // Back button
        if (rawSlot == size - 5) {
            TeamOptionsGUI gui = new TeamOptionsGUI(gameManager);
            player.openInventory(gui.create(team));
            return;
        }

        if (item.getType() != Material.PLAYER_HEAD) return;
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null || meta.getOwner() == null) return;

        String targetName = meta.getOwner();
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable !");
            return;
        }

        if (title.startsWith("§a§lAjouter")) {
            if (teamManager.addPlayer(target.getUniqueId(), team)) {
                player.sendMessage("§a" + target.getName() + " ajouté à l'équipe !");
            } else {
                player.sendMessage("§cL'équipe est complète !");
            }
            TeamPlayerSelectGUI gui = new TeamPlayerSelectGUI(teamManager);
            player.openInventory(gui.createForAdd(team));
        } else {
            teamManager.removePlayer(target.getUniqueId());
            player.sendMessage("§a" + target.getName() + " retiré !");
            TeamPlayerSelectGUI gui = new TeamPlayerSelectGUI(teamManager);
            player.openInventory(gui.createForRemove(team));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (gameManager.getState() != GameState.WAITING) return;
        if (setupManager.isSetup()) {
            hotbarManager.giveHotbar(player);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getState() != GameState.WAITING) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.getState() != GameState.WAITING) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (gameManager.getState() != GameState.WAITING) return;
        event.setCancelled(true);
    }
}
