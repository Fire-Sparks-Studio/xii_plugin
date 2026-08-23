package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.service.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class SetupManager {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;

    public static final int CENTER_X = 0;
    public static final int CENTER_Z = 0;
    public static final int FLOOR_Y = 100;
    public static final int PLATFORM_SIZE = 9;

    private boolean isSetup = false;

    public SetupManager(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public void setup(JavaPlugin plugin, Player actor) {
        if (isSetup) {
            actor.sendMessage(Messages.SETUP_ALREADY_DONE.get(playerDataManager.getLang(actor)));
            return;
        }

        actor.sendMessage(Messages.SETUP_IN_PROGRESS.get(playerDataManager.getLang(actor)));

        World gameWorld = gameManager.createGameWorld();
        if (gameWorld == null) {
            actor.sendMessage(Messages.SETUP_FAILED.get(playerDataManager.getLang(actor)));
            return;
        }

        gameManager.setGameWorld(gameWorld);

        buildWaitingPlatform(gameWorld);

        Location spawn = new Location(gameWorld, CENTER_X + 0.5, FLOOR_Y + 1, CENTER_Z + 0.5);

        gameManager.getConfigManager().saveWorldName(gameWorld.getName());
        gameManager.getConfigManager().saveLobbyLocation(spawn);
        gameManager.getConfigManager().saveSetupState(true);
        gameManager.getConfigManager().forceState(GameState.WAITING);

        isSetup = true;
        gameManager.startWaitingPhase(plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_START);
            player.sendMessage(Messages.SETUP_INITIALIZED.get(playerDataManager.getLang(player)));
        }
    }

    public void quit(JavaPlugin plugin) {
        if (!isSetup) return;

        gameManager.stopAndReset();

        isSetup = false;
    }

    public void onServerStart(JavaPlugin plugin) {
        boolean savedSetup = gameManager.getConfigManager().loadSetupState();

        if (!savedSetup) {
            isSetup = false;
            gameManager.forceState(GameState.NON_SETUP);
            return;
        }

        String worldName = gameManager.getConfigManager().loadWorldName();
        if (worldName == null) {
            isSetup = false;
            gameManager.getConfigManager().clearGameConfig();
            gameManager.forceState(GameState.NON_SETUP);
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            isSetup = false;
            gameManager.getConfigManager().clearGameConfig();
            gameManager.forceState(GameState.NON_SETUP);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(Messages.WORLD_MISSING.get(playerDataManager.getLang(player)));
            }
            return;
        }

        gameManager.setGameWorld(world);
        isSetup = true;
        gameManager.startWaitingPhase(plugin);
    }

    public void buildWaitingPlatform(World world) {
        int half = PLATFORM_SIZE / 2;

        for (int x = CENTER_X - half - 2; x <= CENTER_X + half + 2; x++) {
            for (int z = CENTER_Z - half - 2; z <= CENTER_Z + half + 2; z++) {
                for (int y = FLOOR_Y - 3; y <= FLOOR_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int z = CENTER_Z - half; z <= CENTER_Z + half; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.GLASS);
            }
        }

        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int z = CENTER_Z - half; z <= CENTER_Z + half; z++) {
                for (int y = FLOOR_Y - 3; y < FLOOR_Y; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE);
                }
            }
        }

        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                world.getBlockAt(x, y, CENTER_Z - half).setType(Material.GLASS_PANE);
                world.getBlockAt(x, y, CENTER_Z + half).setType(Material.GLASS_PANE);
            }
        }
        for (int z = CENTER_Z - half + 1; z < CENTER_Z + half; z++) {
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
                world.getBlockAt(CENTER_X - half, y, z).setType(Material.GLASS_PANE);
                world.getBlockAt(CENTER_X + half, y, z).setType(Material.GLASS_PANE);
            }
        }
    }

    public void startHotbarTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isSetup) return;
            if (gameManager.getState() != GameState.WAITING) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                hotbarManager.giveHotbar(player);
            }
        }, 0L, 40L);
    }

    public boolean isSetup() {
        return isSetup;
    }

    public void setSetup(boolean setup) {
        this.isSetup = setup;
    }
}
