package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RespawnManager {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;
    private final SpectatorManager spectatorManager;
    private final SoundService soundService;
    private final GameManager gameManager;

    private int respawnTimeSeconds = 10;
    private final Map<UUID, BukkitTask> respawnTimers = new HashMap<>();
    private final Map<UUID, Integer> respawnCountdowns = new HashMap<>();

    public RespawnManager(TeamManager teamManager, PlayerDataManager playerDataManager, SpectatorManager spectatorManager, SoundService soundService, GameManager gameManager) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
        this.spectatorManager = spectatorManager;
        this.soundService = soundService;
        this.gameManager = gameManager;
    }

    public void handleDeath(Player player) {
        Lang lang = playerDataManager.getLang(player);

        player.showTitle(Title.title(
                Component.text(Messages.YOU_DIED.get(lang)),
                Component.text(Messages.RESPAWN_IN.get(lang, respawnTimeSeconds)),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        ));

        spectatorManager.makeTemporarySpectator(player);

        World gameWorld = gameManager.getGameWorld();
        if (gameWorld != null) {
            Location center = new Location(gameWorld, 0, gameWorld.getHighestBlockYAt(0, 0) + 1, 0);
            player.teleport(center);
        }

        spectatorManager.teleportToNearestPlayer(player);
        startRespawnCountdown(player);
    }

    private void startRespawnCountdown(Player player) {
        cancelRespawnTimer(player.getUniqueId());

        respawnCountdowns.put(player.getUniqueId(), respawnTimeSeconds);
        Lang lang = playerDataManager.getLang(player);

        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("XII-Days");

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            UUID uuid = player.getUniqueId();
            int remaining = respawnCountdowns.getOrDefault(uuid, 0);

            if (remaining <= 0) {
                completeRespawn(player);
                return;
            }

            player.showTitle(Title.title(
                    Component.text(Messages.YOU_DIED.get(lang)),
                    Component.text(Messages.RESPAWN_IN.get(lang, remaining)),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
            ));

            respawnCountdowns.put(uuid, remaining - 1);
        }, 20L, 20L);

        respawnTimers.put(player.getUniqueId(), task);
    }

    public void completeRespawn(Player player) {
        cancelRespawnTimer(player.getUniqueId());
        respawnCountdowns.remove(player.getUniqueId());

        spectatorManager.removeSpectator(player);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team != null && team.getSpawn() != null) {
            player.teleport(team.getSpawn());
        } else {
            World gameWorld = gameManager.getGameWorld();
            if (gameWorld != null) {
                Location center = new Location(gameWorld, 0, gameWorld.getHighestBlockYAt(0, 0) + 1, 0);
                player.teleport(center);
            }
        }

        player.sendTitle("", "", 0, 0, 0);
        soundService.play(player, GameSound.PLAYER_REVIVED);
    }

    public void handleDisconnect(Player player) {
        cancelRespawnTimer(player.getUniqueId());
        respawnCountdowns.remove(player.getUniqueId());
    }

    public void handleReconnect(Player player) {
        if (teamManager.getTeam(player.getUniqueId()) != null) {
            handleDeath(player);
            Lang lang = playerDataManager.getLang(player);
            for (Player member : Bukkit.getOnlinePlayers()) {
                GameTeam memberTeam = teamManager.getTeam(member.getUniqueId());
                if (memberTeam != null && memberTeam.equals(teamManager.getTeam(player.getUniqueId()))) {
                    member.sendMessage(Messages.PLAYER_RECONNECTED.get(lang, player.getName()));
                }
            }
        }
    }

    public void cancelRespawnTimer(UUID uuid) {
        BukkitTask task = respawnTimers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void cleanup() {
        for (BukkitTask task : respawnTimers.values()) {
            task.cancel();
        }
        respawnTimers.clear();
        respawnCountdowns.clear();
    }

    public int getRespawnTimeSeconds() {
        return respawnTimeSeconds;
    }

    public void setRespawnTimeSeconds(int seconds) {
        this.respawnTimeSeconds = seconds;
    }
}
