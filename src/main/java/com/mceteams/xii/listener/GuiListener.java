package com.mceteams.xii.listener;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.ChatInputManager;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.HotbarManager;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.SetupManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.SoundService;
import com.mceteams.xii.service.TeamAdminService;
import com.mceteams.xii.ui.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Objects;

public class GuiListener implements Listener {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final SetupManager setupManager;
    private final ChatInputManager chatInputManager;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;
    private final TeamAdminService teamAdminService;
    private final TeamSelectorGUI teamSelectorGUI;

    public GuiListener(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager, SetupManager setupManager, ChatInputManager chatInputManager, PlayerDataManager playerDataManager, SoundService soundService, TeamAdminService teamAdminService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.setupManager = setupManager;
        this.chatInputManager = chatInputManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
        this.teamAdminService = teamAdminService;
        this.teamSelectorGUI = new TeamSelectorGUI(teamManager, playerDataManager);
    }

    // ========== Hotbar Click ==========

    @EventHandler
    public void onHotbarClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (gameManager.getState() == GameState.NON_SETUP) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        int slot = player.getInventory().getHeldItemSlot();

        if (slot == 0 && item.getType() == Material.TRIPWIRE_HOOK) {
            if (!setupManager.isSetup()) {
                soundService.play(player, GameSound.ERROR);
                return;
            }
            AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
            player.openInventory(adminGUI.create(player));
            return;
        }

        if (gameManager.getState() != GameState.WAITING) return;

        if (slot == 4) {
            player.openInventory(teamSelectorGUI.create(player));
        }

        if (slot == 8) {
            LanguageGUI langGUI = new LanguageGUI(playerDataManager);
            player.openInventory(langGUI.create(player));
        }
    }

    // ========== Chat ==========

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Lang lang = playerDataManager.getLang(player);
        if (chatInputManager.hasPending(player)) {
            event.setCancelled(true);
            String message = event.getMessage();
            if (message.equalsIgnoreCase("cancel")) {
                chatInputManager.cancel(player);
                soundService.play(player, GameSound.CHANGE);
                player.sendMessage(Messages.CANCELLED.get(lang));
            } else {
                Bukkit.getScheduler().runTask(
                        Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("XII-Days")),
                        () -> chatInputManager.submit(player, message)
                );
            }
        }
    }

    // ========== Inventory Click ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Lang lang = playerDataManager.getLang(player);

        String title = event.getView().getTitle();

        if (title.equals(Messages.GUI_ADMIN.get(lang))) {
            if (!setupManager.isSetup()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            handleAdminGUIClick(player, event, lang);
            return;
        }

        if (title.equals(Messages.GUI_TEAM_SELECTOR.get(lang))) {
            event.setCancelled(true);
            handleTeamSelectorClick(player, event, lang);
            return;
        }

        if (title.equals(Messages.GUI_TEAM_MANAGEMENT.get(lang))) {
            event.setCancelled(true);
            handleTeamManagementClick(player, event, lang);
            return;
        }

        if (event.getView().getTitle().startsWith("§6§l") && !title.equals(Messages.GUI_ADMIN.get(lang))
                && !title.equals(Messages.GUI_TEAM_MANAGEMENT.get(lang)) && !title.equals(Messages.GUI_GAME_MANAGEMENT.get(lang))) {
            for (TeamColor c : TeamColor.values()) {
                if (title.equals("§6§l" + c.getName(lang))) {
                    event.setCancelled(true);
                    handleTeamOptionsClick(player, event, title, lang);
                    return;
                }
            }
        }

        if (title.equals(Messages.GUI_GAME_MANAGEMENT.get(lang))) {
            event.setCancelled(true);
            handleGameManagementClick(player, event, lang);
            return;
        }

        if (title.equals(Messages.GUI_LANGUAGE.get(lang))) {
            event.setCancelled(true);
            handleLanguageClick(player, event);
            return;
        }

        if (title.equals(Messages.GUI_TEAM_CREATE.get(lang))) {
            event.setCancelled(true);
            handleTeamCreateClick(player, event, lang);
            return;
        }

        if (title.startsWith(Messages.GUI_ADD_PLAYER_TITLE.get(lang)) || title.startsWith(Messages.GUI_REMOVE_PLAYER_TITLE.get(lang))) {
            event.setCancelled(true);
            handleTeamPlayerSelectClick(player, event, title, lang);
            return;
        }

        if (title.startsWith(Messages.GUI_TP_BASE.get(lang))) {
            event.setCancelled(true);
            handleTpBaseClick(player, event, title, lang);
            return;
        }

        if (event.getClickedInventory() == player.getInventory()) {
            int rawSlot = event.getRawSlot();
            event.setCancelled(true);

            if (rawSlot == 0 && player.hasPermission("xii.admin") && setupManager.isSetup()) {
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
                player.openInventory(adminGUI.create(player));
            } else if (rawSlot == 4) {
                player.openInventory(teamSelectorGUI.create(player));
            } else if (rawSlot == 8) {
                LanguageGUI langGUI = new LanguageGUI(playerDataManager);
                player.openInventory(langGUI.create(player));
            }
        }
    }

    private void handleTeamSelectorClick(Player player, InventoryClickEvent event, Lang lang) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        if (event.getRawSlot() == 4 && current.getType() == Material.BARRIER) {
            GameTeam team = teamManager.getTeam(player.getUniqueId());
            if (team != null) {
                teamAdminService.leaveTeam(player);
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
        if (team != null) {
            teamAdminService.joinTeam(player, team);
            player.closeInventory();
            hotbarManager.refreshTeamItem(player);
        }
    }

    private void handleAdminGUIClick(Player player, InventoryClickEvent event, Lang lang) {
        switch (event.getRawSlot()) {
            case 10 -> {
                soundService.play(player, GameSound.CLICK);
                TeamManagementGUI gui = new TeamManagementGUI(teamManager, playerDataManager);
                player.openInventory(gui.create(player));
            }

            case 13 -> {
                soundService.play(player, GameSound.CLICK);
                GameManagementGUI gui = new GameManagementGUI(gameManager, playerDataManager);
                player.openInventory(gui.create(player));
            }

            case 19 -> {
                teamAdminService.toggleJoin(player, !gameManager.isJoinEnabled());
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
                player.openInventory(adminGUI.create(player));
            }

            case 20 -> {
                teamAdminService.toggleLeave(player, !gameManager.isLeaveEnabled());
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
                player.openInventory(adminGUI.create(player));
            }
        }
    }

    private void handleTeamManagementClick(Player player, InventoryClickEvent event, Lang lang) {
        int rawSlot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int size = event.getInventory().getSize();

        if (rawSlot == size - 1) {
            soundService.play(player, GameSound.BACK);
            AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
            player.openInventory(adminGUI.create(player));
            return;
        }

        if (rawSlot == size - 5 && item.getType() == Material.LIME_STAINED_GLASS_PANE) {
            soundService.play(player, GameSound.CLICK);
            TeamCreateGUI createGUI = new TeamCreateGUI(teamManager, playerDataManager);
            player.openInventory(createGUI.create(player));
            return;
        }

        for (GameTeam team : teamManager.getTeams()) {
            if (item.getType() == team.getColor().getMaterial()) {
                soundService.play(player, GameSound.SELECT);
                TeamOptionsGUI gui = new TeamOptionsGUI(gameManager, playerDataManager);
                player.openInventory(gui.create(player, team));
                return;
            }
        }
    }

    private void handleTeamOptionsClick(Player player, InventoryClickEvent event, String title, Lang lang) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        GameTeam team = null;
        for (GameTeam t : teamManager.getTeams()) {
            if (title.equals("§6§l" + t.getColor().getName(lang))) {
                team = t;
                break;
            }
        }
        if (team == null) return;

        final GameTeam finalTeam = team;

        switch (event.getRawSlot()) {
            case 0 -> {
                teamAdminService.destroyHeart(player, team);
                player.closeInventory();
            }

            case 1 -> {
                teamAdminService.restoreHeart(player, team);
                player.closeInventory();
            }

            case 2 -> {
                teamAdminService.eliminateTeam(player, team);
                player.closeInventory();
            }

            case 3 -> {
                teamAdminService.reviveTeam(player, team);
                player.closeInventory();
            }

            case 5 -> {
                soundService.play(player, GameSound.CLICK);
                TpBaseGUI tpBaseGUI = new TpBaseGUI(teamManager, playerDataManager);
                player.openInventory(tpBaseGUI.create(player, finalTeam));
            }

            case 8 -> {
                chatInputManager.requestInput(player, Messages.OPTIONS_MAX_MEMBERS_USAGE.get(lang, team.getColor().getName(lang)), input -> {
                    Lang lang2 = playerDataManager.getLang(player);
                    try {
                        int max = Integer.parseInt(input);
                        teamAdminService.setMaxMembers(player, finalTeam, max);
                    } catch (NumberFormatException e) {
                        soundService.play(player, GameSound.ERROR);
                        player.sendMessage(Messages.INVALID_NUMBER.get(lang2));
                    }
                    TeamOptionsGUI gui = new TeamOptionsGUI(gameManager, playerDataManager);
                    player.openInventory(gui.create(player, finalTeam));
                });
                player.closeInventory();
            }

            case 10 -> {
                soundService.play(player, GameSound.CLICK);
                TeamPlayerSelectGUI addGUI = new TeamPlayerSelectGUI(teamManager, playerDataManager);
                player.openInventory(addGUI.createForAdd(player, finalTeam));
            }

            case 11 -> {
                soundService.play(player, GameSound.CLICK);
                TeamPlayerSelectGUI removeGUI = new TeamPlayerSelectGUI(teamManager, playerDataManager);
                player.openInventory(removeGUI.createForRemove(player, finalTeam));
            }

            case 15 -> {
                teamAdminService.deleteTeam(player, finalTeam);
                TeamManagementGUI gui = new TeamManagementGUI(teamManager, playerDataManager);
                player.openInventory(gui.create(player));
            }

            case 22 -> {
                soundService.play(player, GameSound.BACK);
                TeamManagementGUI gui = new TeamManagementGUI(teamManager, playerDataManager);
                player.openInventory(gui.create(player));
            }
        }
    }

    private void handleGameManagementClick(Player player, InventoryClickEvent event, Lang lang) {
        int rawSlot = event.getRawSlot();

        switch (rawSlot) {
            case 10 -> {
                if (gameManager.getState() == GameState.WAITING) {
                    teamAdminService.startGame(player);
                } else {
                    teamAdminService.stopGame(player);
                }
                player.closeInventory();
            }

            case 12 -> {
                chatInputManager.requestInput(player, "§eÉcris le jour (1-12):", input -> {
                    Lang lang2 = playerDataManager.getLang(player);
                    try {
                        int day = Integer.parseInt(input);
                        teamAdminService.setDay(player, day);
                    } catch (NumberFormatException e) {
                        soundService.play(player, GameSound.ERROR);
                        player.sendMessage(Messages.INVALID_NUMBER.get(lang2));
                    }
                    GameManagementGUI gui = new GameManagementGUI(gameManager, playerDataManager);
                    player.openInventory(gui.create(player));
                });
                player.closeInventory();
            }

            case 22 -> {
                soundService.play(player, GameSound.BACK);
                AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
                player.openInventory(adminGUI.create(player));
            }
        }
    }

    private void handleLanguageClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        switch (rawSlot) {
            case 3 -> {
                playerDataManager.setLang(player, Lang.FR);
                hotbarManager.giveHotbar(player);
                soundService.play(player, GameSound.LANG_SELECT);
                player.sendMessage(Messages.LANG_CHANGED_FR.get(Lang.FR));
                player.closeInventory();
            }
            case 5 -> {
                playerDataManager.setLang(player, Lang.EN);
                hotbarManager.giveHotbar(player);
                soundService.play(player, GameSound.LANG_SELECT);
                player.sendMessage(Messages.LANG_CHANGED_EN.get(Lang.EN));
                player.closeInventory();
            }
        }
    }

    private void handleTeamCreateClick(Player player, InventoryClickEvent event, Lang lang) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();

        if (rawSlot == 49) {
            soundService.play(player, GameSound.BACK);
            TeamManagementGUI gui = new TeamManagementGUI(teamManager, playerDataManager);
            player.openInventory(gui.create(player));
            return;
        }

        for (TeamColor color : TeamColor.values()) {
            if (item.getType() == color.getMaterial()) {
                teamAdminService.createTeam(player, color);
                TeamCreateGUI createGUI = new TeamCreateGUI(teamManager, playerDataManager);
                player.openInventory(createGUI.create(player));
                return;
            }
        }
    }

    private void handleTeamPlayerSelectClick(Player player, InventoryClickEvent event, String title, Lang lang) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();
        int size = event.getInventory().getSize();

        GameTeam team = null;
        for (GameTeam t : teamManager.getTeams()) {
            if (title.contains(t.getColor().getName(lang))) {
                team = t;
                break;
            }
        }
        if (team == null) return;

        if (rawSlot == size - 5) {
            soundService.play(player, GameSound.BACK);
            TeamOptionsGUI gui = new TeamOptionsGUI(gameManager, playerDataManager);
            player.openInventory(gui.create(player, team));
            return;
        }

        if (item.getType() != Material.PLAYER_HEAD) return;
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null || meta.getOwner() == null) return;

        String targetName = meta.getOwner();
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.PLAYER_NOT_FOUND_SHORT.get(lang));
            return;
        }

        if (title.startsWith(Messages.GUI_ADD_PLAYER_TITLE.get(lang))) {
            teamAdminService.addPlayer(player, target, team);
            TeamPlayerSelectGUI gui = new TeamPlayerSelectGUI(teamManager, playerDataManager);
            player.openInventory(gui.createForAdd(player, team));
        } else {
            teamAdminService.removePlayer(player, target);
            TeamPlayerSelectGUI gui = new TeamPlayerSelectGUI(teamManager, playerDataManager);
            player.openInventory(gui.createForRemove(player, team));
        }
    }

    private void handleTpBaseClick(Player player, InventoryClickEvent event, String title, Lang lang) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();
        int size = event.getInventory().getSize();

        GameTeam team = null;
        for (GameTeam t : teamManager.getTeams()) {
            if (title.contains(t.getColor().getName(lang))) {
                team = t;
                break;
            }
        }
        if (team == null) return;

        if (rawSlot == size - 1) {
            soundService.play(player, GameSound.BACK);
            TeamOptionsGUI gui = new TeamOptionsGUI(gameManager, playerDataManager);
            player.openInventory(gui.create(player, team));
            return;
        }

        if (rawSlot == size - 5) {
            teamAdminService.tpBaseTeam(player, team);
            player.closeInventory();
            return;
        }

        if (item.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta == null || meta.getOwner() == null) return;
            Player target = Bukkit.getPlayer(meta.getOwner());
            if (target != null) {
                teamAdminService.tpBasePlayer(player, target);
            } else {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.PLAYER_NOT_FOUND_SHORT.get(lang));
            }
        }
    }

    // ========== Block Place / Drop / Drag ==========

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getState() == GameState.NON_SETUP) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gameManager.getState() == GameState.NON_SETUP) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (gameManager.getState() == GameState.NON_SETUP) return;
        event.setCancelled(true);
    }

    // ========== Block Break ==========

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameManager.getState() == GameState.NON_SETUP) return;
        if (gameManager.getState() == GameState.COMBAT) return;
        event.setCancelled(true);
    }

    // ========== Inventory Close ==========

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (gameManager.getState() != GameState.WAITING) return;
        if (setupManager.isSetup()) {
            hotbarManager.giveHotbar(player);
        }
    }

    // ========== Player Join ==========

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (gameManager.getState() == GameState.NON_SETUP) return;

        if (gameManager.getState() == GameState.WAITING) {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(gameManager.getLobbySpawn());
            hotbarManager.giveHotbar(player);
        }

        if (gameManager.getState() == GameState.PREPARATION || gameManager.getState() == GameState.COMBAT) {
            com.mceteams.xii.model.GameTeam team = teamManager.getTeam(player.getUniqueId());
            if (team == null) {
                gameManager.getSpectatorManager().makePermanentSpectator(player);
            } else {
                gameManager.getRespawnManager().handleReconnect(player);
            }
        }
    }

    // ========== Player Quit ==========

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (gameManager.getState() == GameState.PREPARATION || gameManager.getState() == GameState.COMBAT) {
            com.mceteams.xii.model.GameTeam team = teamManager.getTeam(player.getUniqueId());
            if (team != null) {
                Lang lang = playerDataManager.getLang(player);
                for (Player member : Bukkit.getOnlinePlayers()) {
                    com.mceteams.xii.model.GameTeam memberTeam = teamManager.getTeam(member.getUniqueId());
                    if (memberTeam != null && memberTeam.equals(team)) {
                        member.sendMessage(Messages.PLAYER_LEFT_GAME.get(playerDataManager.getLang(member), player.getName()));
                    }
                }
                gameManager.getRespawnManager().handleDisconnect(player);
            }
        }
    }

    // ========== Death Event ==========

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (gameManager.getState() == GameState.PREPARATION || gameManager.getState() == GameState.COMBAT) {
            event.setDeathMessage("");
            event.getDrops().clear();
            event.setDroppedExp(0);

            gameManager.getRespawnManager().handleDeath(player);
        }
    }
}
