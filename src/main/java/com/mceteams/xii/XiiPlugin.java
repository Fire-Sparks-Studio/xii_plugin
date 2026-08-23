package com.mceteams.xii;

import com.mceteams.xii.commands.XCommands;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.listener.GuiListener;
import com.mceteams.xii.listener.GameplayListener;
import com.mceteams.xii.listener.MiningListener;
import com.mceteams.xii.listener.PlaceListener;
import com.mceteams.xii.manager.*;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerScore;
import com.mceteams.xii.model.TeamScore;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import com.mceteams.xii.service.TeamAdminService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class XiiPlugin extends JavaPlugin implements Listener {
    private DayManager dayManager = new DayManager();
    private SetupManager setupManager;
    private HotbarManager hotbarManager;
    private GameManager gameManager;
    private TeamManager teamManager;
    private PointService pointService;
    private PlayerDataManager playerDataManager;
    private SoundService soundService;
    private TeamAdminService teamAdminService;
    private RestrictionManager restrictionManager;
    private SpectatorManager spectatorManager;
    private RespawnManager respawnManager;
    private DynamicBarManager dynamicBarManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        getLogger().info("===[ENABLING]===");

        getLogger().info(": Registering Managers & Services");
        teamManager = new TeamManager();
        pointService = new PointService();
        soundService = new SoundService();
        playerDataManager = new PlayerDataManager(this);
        hotbarManager = new HotbarManager(teamManager);
        gameManager = new GameManager(teamManager, dayManager, hotbarManager, pointService, soundService, playerDataManager);

        configManager = new ConfigManager(this);
        gameManager.setConfigManager(configManager);

        spectatorManager = new SpectatorManager(teamManager, playerDataManager, soundService);
        respawnManager = new RespawnManager(teamManager, playerDataManager, spectatorManager, soundService, gameManager);
        dynamicBarManager = new DynamicBarManager(gameManager, playerDataManager);
        restrictionManager = new RestrictionManager(gameManager);

        gameManager.setSpectatorManager(spectatorManager);
        gameManager.setRespawnManager(respawnManager);
        gameManager.setDynamicBarManager(dynamicBarManager);
        gameManager.setRestrictionManager(restrictionManager);

        setupManager = new SetupManager(teamManager, gameManager, hotbarManager, playerDataManager, soundService);
        gameManager.setSetupManager(setupManager);

        teamAdminService = new TeamAdminService(teamManager, gameManager, setupManager, playerDataManager, soundService);
        setupManager.startHotbarTask(this);

        ChatInputManager chatInputManager = new ChatInputManager();

        getLogger().info(": Managers & Services Registered!");

        getLogger().info(": Registering Listeners");
        GuiListener guiListener = new GuiListener(teamManager, gameManager, hotbarManager, setupManager, chatInputManager, playerDataManager, soundService, teamAdminService);
        MiningListener miningListener = new MiningListener(pointService, teamManager);
        PlaceListener placeListener = new PlaceListener(miningListener);

        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(miningListener, this);
        getServer().getPluginManager().registerEvents(placeListener, this);
        GameplayListener gameplayListener = new GameplayListener(gameManager);
        getServer().getPluginManager().registerEvents(gameplayListener, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(": Listeners Registered!");

        getLogger().info(": Registering Commands");
        XCommands commands = new XCommands(teamManager, gameManager, pointService, setupManager, playerDataManager, soundService, teamAdminService);

        Objects.requireNonNull(getCommand("xii")).setExecutor(commands);
        Objects.requireNonNull(getCommand("join")).setExecutor(commands);
        Objects.requireNonNull(getCommand("leave")).setExecutor(commands);
        Objects.requireNonNull(getCommand("admin")).setExecutor(commands);
        getLogger().info(": Commands Registered!");

        getLogger().info(": Getting Data And Game Ready");
        loadGameData();
        setupManager.onServerStart(this);
        getLogger().info(": Data And Game Ready!");

        getLogger().info("===[READY]===");
    }

    @Override
    public void onDisable() {
        saveGameData();
        getLogger().info("===[DISABLED]===");
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    public void saveGameData() {
        getConfig().set("setup", setupManager.isSetup());
        getConfig().set("joinEnabled", gameManager.isJoinEnabled());
        getConfig().set("leaveEnabled", gameManager.isLeaveEnabled());
        getConfig().set("day", gameManager.getDayManager().getCurrentDay());

        for (GameTeam team : teamManager.getTeams()) {
            String path = "teams." + team.getColor().name();
            getConfig().set(path + ".players", team.getPlayers().stream().map(UUID::toString).toList());
            getConfig().set(path + ".heartAlive", team.isHeartAlive());
            getConfig().set(path + ".maxPlayers", team.getMaxPlayers());
            if (team.getSpawn() != null) {
                getConfig().set(path + ".spawn.world", team.getSpawn().getWorld().getName());
                getConfig().set(path + ".spawn.x", team.getSpawn().getX());
                getConfig().set(path + ".spawn.y", team.getSpawn().getY());
                getConfig().set(path + ".spawn.z", team.getSpawn().getZ());
            }
            if (team.getHeartLocation() != null) {
                getConfig().set(path + ".heart.world", team.getHeartLocation().getWorld().getName());
                getConfig().set(path + ".heart.x", team.getHeartLocation().getX());
                getConfig().set(path + ".heart.y", team.getHeartLocation().getY());
                getConfig().set(path + ".heart.z", team.getHeartLocation().getZ());
            }
        }

        for (PlayerScore score : pointService.getAllPlayerScores()) {
            String path = "scores.players." + score.getPlayerUUID();
            for (PointCategory cat : PointCategory.values()) {
                getConfig().set(path + "." + cat.name(), score.getPoints(cat));
            }
        }

        for (GameTeam team : teamManager.getTeams()) {
            String path = "scores.teams." + team.getColor().name();
            TeamScore score = pointService.getTeamScore(team);
            for (PointCategory cat : PointCategory.values()) {
                getConfig().set(path + "." + cat.name(), score.getPoints(cat));
            }
        }
        saveConfig();
    }

    public void loadGameData() {
        if (getConfig().contains("setup")) {
            setupManager.setSetup(getConfig().getBoolean("setup"));
        }

        if (getConfig().contains("joinEnabled")) {
            gameManager.setJoinEnabled(getConfig().getBoolean("joinEnabled"));
        }

        if (getConfig().contains("leaveEnabled")) {
            gameManager.setLeaveEnabled(getConfig().getBoolean("leaveEnabled"));
        }

        if (getConfig().contains("day")) {
            gameManager.getDayManager().setDay(getConfig().getInt("day"));
        }

        if (getConfig().contains("teams")) {
            for (String colorName : Objects.requireNonNull(getConfig().getConfigurationSection("teams")).getKeys(false)) {
                TeamColor color = TeamColor.valueOf(colorName);
                GameTeam team = teamManager.createTeam(color);
                List<String> playerStrings = getConfig().getStringList("teams." + colorName + ".players");
                for (String uuid : playerStrings) {
                    team.addPlayer(UUID.fromString(uuid));
                    teamManager.addPlayerToMap(UUID.fromString(uuid), team);
                }
                team.setHeartAlive(getConfig().getBoolean("teams." + colorName + ".heartAlive"));
                team.setMaxPlayers(getConfig().getInt("teams." + colorName + ".maxPlayers"));
            }
        }

        if (getConfig().contains("scores.players")) {
            for (String uuid : Objects.requireNonNull(getConfig().getConfigurationSection("scores.players")).getKeys(false)) {
                PlayerScore score = pointService.getPlayerScore(UUID.fromString(uuid));
                for (PointCategory cat : PointCategory.values()) {
                    String path = "scores.players." + uuid + "." + cat.name();
                    if (getConfig().contains(path)) {
                        score.addPoints(cat, getConfig().getInt(path));
                    }
                }
            }
        }

        if (getConfig().contains("scores.teams")) {
            for (String colorName : Objects.requireNonNull(getConfig().getConfigurationSection("scores.teams")).getKeys(false)) {
                GameTeam team = teamManager.getTeam(TeamColor.valueOf(colorName));
                if (team == null) continue;
                TeamScore score = pointService.getTeamScore(team);
                for (PointCategory cat : PointCategory.values()) {
                    String path = "scores.teams." + colorName + "." + cat.name();
                    if (getConfig().contains(path)) {
                        score.addPoints(cat, getConfig().getInt(path));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    }
}
