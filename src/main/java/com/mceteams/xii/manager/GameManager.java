package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import com.mceteams.xii.world.FlatChunkGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GameManager {
    private final TeamManager teamManager;
    private final DayManager dayManager;
    private final HotbarManager hotbarManager;
    private final PointService pointService;
    private final SoundService soundService;
    private final PlayerDataManager playerDataManager;

    private GameState state = GameState.NON_SETUP;
    private boolean joinEnabled = true;
    private boolean leaveEnabled = true;
    private World gameWorld;
    private BukkitTask countdownTask;

    private SetupManager setupManager;
    private RestrictionManager restrictionManager;
    private SpectatorManager spectatorManager;
    private RespawnManager respawnManager;
    private DynamicBarManager dynamicBarManager;
    private ConfigManager configManager;

    public GameManager(TeamManager teamManager, DayManager dayManager, HotbarManager hotbarManager, PointService pointService, SoundService soundService, PlayerDataManager playerDataManager) {
        this.teamManager = teamManager;
        this.dayManager = dayManager;
        this.hotbarManager = hotbarManager;
        this.pointService = pointService;
        this.soundService = soundService;
        this.playerDataManager = playerDataManager;
    }

    public void setSetupManager(SetupManager setupManager) {
        this.setupManager = setupManager;
    }

    public void setRestrictionManager(RestrictionManager restrictionManager) {
        this.restrictionManager = restrictionManager;
    }

    public void setSpectatorManager(SpectatorManager spectatorManager) {
        this.spectatorManager = spectatorManager;
    }

    public void setRespawnManager(RespawnManager respawnManager) {
        this.respawnManager = respawnManager;
    }

    public void setDynamicBarManager(DynamicBarManager dynamicBarManager) {
        this.dynamicBarManager = dynamicBarManager;
    }

    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public World createGameWorld() {
        String worldName = "xii_world_" + System.currentTimeMillis();
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generator(new FlatChunkGenerator());
        creator.generateStructures(false);
        creator.seed(new java.util.Random().nextLong());

        this.gameWorld = creator.createWorld();

        if (gameWorld != null) {
            gameWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            gameWorld.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            gameWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            gameWorld.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
            gameWorld.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        }

        return gameWorld;
    }

    public void deleteGameWorld() {
        if (gameWorld == null) return;

        for (Player player : gameWorld.getPlayers()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }

        String worldName = gameWorld.getName();
        Bukkit.unloadWorld(gameWorld, false);

        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        deleteFolder(worldFolder);

        this.gameWorld = null;
    }

    private void deleteFolder(File folder) {
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteFolder(file);
                    } else {
                        file.delete();
                    }
                }
            }
            folder.delete();
        }
    }

    public void startWaitingPhase(JavaPlugin plugin) {
        this.state = GameState.WAITING;
        this.joinEnabled = true;
        this.leaveEnabled = true;

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGameMode(GameMode.ADVENTURE);
            online.setNoDamageTicks(0);
            online.teleport(getLobbySpawn());
            hotbarManager.giveHotbar(online);
        }

        dynamicBarManager.startWaitingBar(plugin);
    }

    public void startPreparationPhase(JavaPlugin plugin) {
        this.state = GameState.PREPARATION;
        dayManager.startGame();

        dynamicBarManager.startPreparationBar(plugin);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGameMode(GameMode.SURVIVAL);
            online.setNoDamageTicks(0);
            hotbarManager.clearHotbar(online);

            GameTeam team = teamManager.getTeam(online.getUniqueId());
            if (team != null && team.getSpawn() != null) {
                online.teleport(team.getSpawn());
            } else {
                spectatorManager.makePermanentSpectator(online);
            }
        }

        if (gameWorld != null) {
            clearWaitingPlatform(gameWorld);
        }
    }

    public void startCombatPhase(JavaPlugin plugin) {
        this.state = GameState.COMBAT;
        dynamicBarManager.startCombatBar(plugin);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (spectatorManager.isPermanentSpectator(online)) continue;
            if (spectatorManager.isTemporarySpectator(online)) continue;
            online.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void endGame() {
        cancelCountdown();
        dynamicBarManager.cancelActionBar();

        List<GameTeam> aliveTeams = new ArrayList<>();
        for (GameTeam team : teamManager.getTeams()) {
            if (team.isHeartAlive()) {
                aliveTeams.add(team);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            Lang lang = playerDataManager.getLang(online);

            if (aliveTeams.isEmpty()) {
                soundService.play(online, GameSound.DEFEAT);
                online.sendMessage(Messages.TIE_ANNOUNCE.get(lang));
            } else if (aliveTeams.size() == 1) {
                GameTeam winner = aliveTeams.get(0);
                boolean isWinner = winner.getPlayers().contains(online.getUniqueId());

                if (isWinner) {
                    soundService.play(online, GameSound.VICTORY);
                    online.sendMessage(Messages.VICTORY_ANNOUNCE.get(lang));
                } else {
                    soundService.play(online, GameSound.DEFEAT);
                    online.sendMessage(Messages.DEFEAT_ANNOUNCE.get(lang));
                }
            } else {
                soundService.play(online, GameSound.DEFEAT);
                online.sendMessage(Messages.TIE_ANNOUNCE.get(lang));
            }
        }

        spectatorManager.cleanup();
        respawnManager.cleanup();
        deleteGameWorld();

        if (configManager != null) {
            configManager.clearGameConfig();
        }

        this.state = GameState.NON_SETUP;
        this.joinEnabled = true;
        this.leaveEnabled = true;
        dayManager.setDay(1);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGameMode(GameMode.SURVIVAL);
            online.setNoDamageTicks(0);
            online.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            online.sendTitle("", "", 0, 0, 0);
            online.sendActionBar(Component.empty());
            online.getInventory().clear();
            online.getInventory().setArmorContents(null);
        }

        teamManager.reset();
        pointService.reset();
    }

    public void stopAndReset() {
        cancelCountdown();
        dynamicBarManager.cancelActionBar();

        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setNoDamageTicks(0);
            player.teleport(mainWorld.getSpawnLocation());
            player.sendActionBar(Component.empty());
            player.sendTitle("", "", 0, 0, 0);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setFireTicks(0);
            player.setExp(0);
            player.setLevel(0);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.closeInventory();
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setInvulnerable(false);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        }

        spectatorManager.cleanup();
        respawnManager.cleanup();
        deleteGameWorld();

        if (configManager != null) {
            configManager.clearGameConfig();
        }

        this.state = GameState.NON_SETUP;
        this.joinEnabled = true;
        this.leaveEnabled = true;
        dayManager.setDay(1);

        teamManager.reset();
        pointService.reset();

        if (restrictionManager != null) {
            restrictionManager.clearBlacklist();
        }

        Bukkit.getScheduler().runTaskLater(
            (JavaPlugin) Bukkit.getPluginManager().getPlugin("XII-Days"),
            () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setNoDamageTicks(0);
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }, 5L
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_STOP);
            player.sendMessage(Messages.SETUP_RESET.get(playerDataManager.getLang(player)));
        }
    }

    public void clearWaitingPlatform(World world) {
        int centerX = SetupManager.CENTER_X;
        int centerZ = SetupManager.CENTER_Z;
        int floorY = SetupManager.FLOOR_Y;
        int platformSize = SetupManager.PLATFORM_SIZE;
        int half = platformSize / 2;

        for (int x = centerX - half - 2; x <= centerX + half + 2; x++) {
            for (int z = centerZ - half - 2; z <= centerZ + half + 2; z++) {
                for (int y = floorY - 3; y <= floorY + 5; y++) {
                    world.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                }
            }
        }
    }

    public Location getLobbySpawn() {
        if (gameWorld != null) {
            return new Location(gameWorld, SetupManager.CENTER_X + 0.5, SetupManager.FLOOR_Y + 1, SetupManager.CENTER_Z + 0.5);
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void setCountdownTask(BukkitTask task) {
        this.countdownTask = task;
    }

    public void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    public World getGameWorld() {
        return gameWorld;
    }

    public void setGameWorld(World world) {
        this.gameWorld = world;
    }

    public DayManager getDayManager() {
        return this.dayManager;
    }

    public GameState getState() {
        return this.state;
    }

    public void forceState(GameState state) {
        this.state = state;
    }

    public boolean isJoinEnabled() {
        return this.joinEnabled;
    }

    public void setJoinEnabled(boolean enabled) {
        this.joinEnabled = enabled;
    }

    public boolean isLeaveEnabled() {
        return this.leaveEnabled;
    }

    public void setLeaveEnabled(boolean enabled) {
        this.leaveEnabled = enabled;
    }

    public PointService getPointService() {
        return pointService;
    }

    public RestrictionManager getRestrictionManager() {
        return restrictionManager;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public RespawnManager getRespawnManager() {
        return respawnManager;
    }

    public DynamicBarManager getDynamicBarManager() {
        return dynamicBarManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
