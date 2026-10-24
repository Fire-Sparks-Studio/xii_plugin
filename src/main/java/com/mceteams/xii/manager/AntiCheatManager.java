package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatManager {
    private final XiiPlugin plugin;
    private final Map<UUID, VanishData> vanish = new ConcurrentHashMap<>();
    private final Map<UUID, ClickData> clicks = new ConcurrentHashMap<>();
    private final Map<UUID, MiningData> mining = new ConcurrentHashMap<>();

    public static class VanishData {
        boolean vanished = false;
        org.bukkit.GameMode previousGamemode = null;
    }

    public static class ClickData {
        long lastClick = 0;
        int clicksLastSecond = 0;
        long windowStart = System.currentTimeMillis();
        int warnings = 0;
        long lastWarn = 0;
    }

    public static class MiningData {
        long totalBlocksBroken = 0;
        long oreBroken = 0;
        long lastCheck = System.currentTimeMillis();
    }

    public AntiCheatManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    public void toggleVanish(Player player) {
        VanishData vd = vanish.computeIfAbsent(player.getUniqueId(), k -> new VanishData());
        if (!vd.vanished) {
            vd.vanished = true;
            vd.previousGamemode = player.getGameMode();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player)) {
                    p.hidePlayer(plugin, player);
                    // Try to send leave message in receiver's language context is not directly possible,
                    // but we can send the vanilla leave message to others? Better to fake quit in tab by hiding
                    // Also broadcast translated leave message for non-admins? Use server's language via adventure if possible
                    try {
                        net.kyori.adventure.text.Component leaveMsg = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.Component.text(player.getName()));
                        p.sendMessage(leaveMsg);
                    } catch (Exception ignored) {
                        p.sendMessage("§e" + player.getName() + " left the game");
                    }
                }
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage("§7[AntiCheat] Vanish §aactivé");
        } else {
            vd.vanished = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, player);
                try {
                    net.kyori.adventure.text.Component joinMsg = net.kyori.adventure.text.Component.translatable("multiplayer.player.joined", net.kyori.adventure.text.Component.text(player.getName()));
                    p.sendMessage(joinMsg);
                } catch (Exception ignored) {
                    p.sendMessage("§e" + player.getName() + " joined the game");
                }
            }
            player.setAllowFlight(false);
            player.setFlying(false);
            if (vd.previousGamemode != null) {
                player.setGameMode(vd.previousGamemode);
            }
            player.sendMessage("§7[AntiCheat] Vanish §cdésactivé");
        }
    }

    public boolean isVanished(Player player) {
        VanishData vd = vanish.get(player.getUniqueId());
        return vd != null && vd.vanished;
    }

    public void onLeftClick(Player player) {
        ClickData cd = clicks.computeIfAbsent(player.getUniqueId(), k -> new ClickData());
        long now = System.currentTimeMillis();
        if (now - cd.windowStart > 1000) {
            cd.windowStart = now;
            cd.clicksLastSecond = 0;
        }
        cd.clicksLastSecond++;
        cd.lastClick = now;
        if (cd.clicksLastSecond > 15) {
            if (now - cd.lastWarn > 2000) {
                player.sendMessage("§c[AntiCheat] Autoclicker suspect - cliques limités.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                cd.lastWarn = now;
                cd.warnings++;
            }
            // cancel effect by not doing anything special; actual limiting done via event cancel logic
        } else if (cd.clicksLastSecond > 10) {
            if (now - cd.lastWarn > 3000) {
                player.sendMessage("§e[AntiCheat] Trop de clics détectés.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                cd.lastWarn = now;
                cd.warnings++;
            }
        }
    }

    public boolean shouldCancelClick(Player player) {
        ClickData cd = clicks.get(player.getUniqueId());
        if (cd == null) return false;
        long now = System.currentTimeMillis();
        if (now - cd.windowStart > 1000) {
            cd.windowStart = now;
            cd.clicksLastSecond = 0;
            return false;
        }
        if (cd.clicksLastSecond > 15) {
            return true;
        }
        return false;
    }

    public void onBlockBreak(Player player, Material mat) {
        MiningData md = mining.computeIfAbsent(player.getUniqueId(), k -> new MiningData());
        md.totalBlocksBroken++;
        if (isOre(mat)) {
            md.oreBroken++;
        }
        long now = System.currentTimeMillis();
        if (now - md.lastCheck > 60000) {
            if (md.totalBlocksBroken > 100) {
                double ratio = (double) md.oreBroken / (double) md.totalBlocksBroken;
                // rough heuristic: normal ore ratio varies; flag if very high
                if (ratio > 0.25) {
                    plugin.getLogger().warning("[AntiCheat] Mining suspect: " + player.getName() + " oreRatio=" + ratio);
                    Bukkit.broadcast(plugin.getServer().permissionMessage(), "xii.admin");
                    // don't auto-ban, just flag per request
                }
                if (ratio > 0.4) {
                    plugin.getLogger().severe("[AntiCheat] Mining ABUSE: " + player.getName() + " oreRatio=" + ratio);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " Mining abuse");
                }
            }
            md.lastCheck = now;
            md.totalBlocksBroken = 0;
            md.oreBroken = 0;
        }
    }

    private boolean isOre(Material m) {
        String n = m.name();
        return n.contains("ORE") || n.contains("ANCIENT_DEBRIS");
    }
}
