package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.*;

public class SpectatorManager {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;

    private final Set<UUID> permanentSpectators = new HashSet<>();
    private final Set<UUID> temporarySpectators = new HashSet<>();

    public SpectatorManager(TeamManager teamManager, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public void makePermanentSpectator(Player player) {
        permanentSpectators.add(player.getUniqueId());
        temporarySpectators.remove(player.getUniqueId());
        applySpectatorState(player);
        Lang lang = playerDataManager.getLang(player);
        player.sendMessage(Messages.SPECTATOR_NO_TEAM.get(lang));
    }

    public void makeTemporarySpectator(Player player) {
        temporarySpectators.add(player.getUniqueId());
        permanentSpectators.remove(player.getUniqueId());
        applySpectatorState(player);
    }

    public void removeSpectator(Player player) {
        permanentSpectators.remove(player.getUniqueId());
        temporarySpectators.remove(player.getUniqueId());
        removeSpectatorState(player);
    }

    public boolean isPermanentSpectator(Player player) {
        return permanentSpectators.contains(player.getUniqueId());
    }

    public boolean isTemporarySpectator(Player player) {
        return temporarySpectators.contains(player.getUniqueId());
    }

    public boolean isSpectating(Player player) {
        return permanentSpectators.contains(player.getUniqueId()) || temporarySpectators.contains(player.getUniqueId());
    }

    public void removeSpectatorByUUID(UUID uuid) {
        permanentSpectators.remove(uuid);
        temporarySpectators.remove(uuid);
    }

    private void applySpectatorState(Player player) {
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setFlying(true);
        player.setAllowFlight(true);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    private void removeSpectatorState(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setNoDamageTicks(0);
    }

    public void teleportToNearestPlayer(Player spectator) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(spectator)) continue;
            if (isSpectating(online)) continue;

            double dist = spectator.getLocation().distanceSquared(online.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = online;
            }
        }

        if (nearest != null) {
            spectator.teleport(nearest.getLocation());
        }
    }

    public void cycleTarget(Player spectator) {
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(spectator)) continue;
            if (isSpectating(online)) continue;
            targets.add(online);
        }

        if (targets.isEmpty()) return;

        if (spectator.getSpectatorTarget() != null && spectator.getSpectatorTarget() instanceof Player current) {
            int idx = targets.indexOf(current);
            if (idx >= 0) {
                int next = (idx + 1) % targets.size();
                spectator.setSpectatorTarget(targets.get(next));
                return;
            }
        }

        if (!targets.isEmpty()) {
            spectator.setSpectatorTarget(targets.get(0));
        }
    }

    public void cleanup() {
        for (UUID uuid : permanentSpectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectatorState(player);
            }
        }
        for (UUID uuid : temporarySpectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeSpectatorState(player);
            }
        }
        permanentSpectators.clear();
        temporarySpectators.clear();
    }

    public void cleanupByUUID(UUID uuid) {
        permanentSpectators.remove(uuid);
        temporarySpectators.remove(uuid);
    }
}
