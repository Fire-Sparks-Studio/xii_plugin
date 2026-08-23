package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SetupManager {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final HotbarManager hotbarManager;
    private final PointService pointService;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;
    private boolean isSetup = false;

    public static final int CENTER_X = 0;
    public static final int CENTER_Z = 0;
    public static final int FLOOR_Y = 100;
    public static final int PLATFORM_SIZE = 9;

    public SetupManager(TeamManager teamManager, GameManager gameManager, HotbarManager hotbarManager, PointService pointService, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.hotbarManager = hotbarManager;
        this.pointService = pointService;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public void setup() {
        if (isSetup) return;

        World world = Bukkit.getWorlds().get(0);
        buildWaitingPlatform(world);

        org.bukkit.Location spawn = new org.bukkit.Location(world, CENTER_X + 0.5, FLOOR_Y + 1, CENTER_Z + 0.5);
        world.setSpawnLocation(spawn);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawn);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            hotbarManager.giveHotbar(player);
        }

        isSetup = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_START);
            player.sendMessage(Messages.SETUP_INITIALIZED.get(playerDataManager.getLang(player)));
        }
    }

    public void clearWaitingPlatform(World world) {
        int half = PLATFORM_SIZE / 2;
        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int z = CENTER_Z - half; z <= CENTER_Z + half; z++) {
                for (int y = FLOOR_Y - 3; y <= FLOOR_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    private void buildWaitingPlatform(World world) {
        int half = PLATFORM_SIZE / 2;

        // Clear area
        for (int x = CENTER_X - half - 2; x <= CENTER_X + half + 2; x++) {
            for (int z = CENTER_Z - half - 2; z <= CENTER_Z + half + 2; z++) {
                for (int y = FLOOR_Y - 3; y <= FLOOR_Y + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // Floor (smooth stone)
        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int z = CENTER_Z - half; z <= CENTER_Z + half; z++) {
                world.getBlockAt(x, FLOOR_Y, z).setType(Material.SMOOTH_STONE);
            }
        }

        // Base layer (stone)
        for (int x = CENTER_X - half; x <= CENTER_X + half; x++) {
            for (int z = CENTER_Z - half; z <= CENTER_Z + half; z++) {
                for (int y = FLOOR_Y - 3; y < FLOOR_Y; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE);
                }
            }
        }

        // Walls (glass panes on edges, 3 blocks high)
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

    public void quit() {
        if (!isSetup) return;
        if (gameManager.getState() != GameState.WAITING) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            hotbarManager.clearHotbar(player);
        }

        teamManager.reset();
        pointService.reset();
        isSetup = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            soundService.play(player, GameSound.SETUP_STOP);
            player.sendMessage(Messages.SETUP_RESET.get(playerDataManager.getLang(player)));
        }
    }

    public void startHotbarTask(JavaPlugin plugin) {
        gameManager.startWaitingActionBar(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isSetup) return;
            if (gameManager.getState() != GameState.WAITING) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                hotbarManager.giveHotbar(player);
            }
        }, 0L, 40L); // 40 ticks = 2 secondes
    }

    public boolean isSetup() {
        return isSetup;
    }

    public void setSetup(boolean setup) {
        this.isSetup = setup;
    }
}
