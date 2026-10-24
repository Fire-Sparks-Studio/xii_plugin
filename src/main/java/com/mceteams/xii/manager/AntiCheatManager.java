package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
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
        final Deque<Long> clickTimes = new ArrayDeque<>();
        long lastAdded = 0;
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
                    try {
                        net.kyori.adventure.text.Component leaveMsg = net.kyori.adventure.text.Component.text("§e" + player.getName() + " left the game");
                        p.sendMessage(leaveMsg);
                    } catch (Exception ignored) {
                        p.sendMessage("§e" + player.getName() + " left the game");
                    }
                }
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            try {
                player.playerListName(net.kyori.adventure.text.Component.text("§o[VANISH] " + player.getName()));
            } catch (Exception ignored) {
                player.setPlayerListName("§o[VANISH] " + player.getName());
            }
            player.sendMessage("§7[VANISH] §aactivé");
        } else {
            vd.vanished = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, player);
                try {
                    net.kyori.adventure.text.Component joinMsg = net.kyori.adventure.text.Component.text("§e" + player.getName() + " joined the game");
                    p.sendMessage(joinMsg);
                } catch (Exception ignored) {
                    p.sendMessage("§e" + player.getName() + " joined the game");
                }
            }
            if (vd.previousGamemode != null && vd.previousGamemode == org.bukkit.GameMode.CREATIVE) {
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                player.setAllowFlight(true);
                player.setFlying(true);
            } else if (vd.previousGamemode != null && vd.previousGamemode == org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            } else {
                player.setAllowFlight(false);
                player.setFlying(false);
                if (vd.previousGamemode != null) {
                    player.setGameMode(vd.previousGamemode);
                }
            }
            try {
                player.playerListName(null);
            } catch (Exception ignored) {}
            player.sendMessage("§7[VANISH] §cdésactivé");
        }
    }

    public boolean isVanished(Player player) {
        VanishData vd = vanish.get(player.getUniqueId());
        return vd != null && vd.vanished;
    }

    public void onLeftClick(Player player) {
        ClickData cd = clicks.computeIfAbsent(player.getUniqueId(), k -> new ClickData());
        long now = System.currentTimeMillis();
        cd.lastAdded = now;
        cd.clickTimes.addLast(now);
        cleanupClickTimes(cd, now);
        int cps = cd.clickTimes.size();
        if (cps > 15) {
            if (now - cd.lastWarn > 1500) {
                player.sendMessage("§c[AntiCheat] Autoclicker suspect - cliques limités.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                cd.lastWarn = now;
                cd.warnings++;
                String msg = "§c[AntiCheat] §e" + player.getName() + " §7autoclicker suspect (" + cps + " cps). ";
                for (Player op : Bukkit.getOnlinePlayers()) {
                    if (op.isOp() || op.hasPermission("xii.admin")) {
                        net.kyori.adventure.text.Component comp = net.kyori.adventure.text.Component.text(msg)
                                .append(net.kyori.adventure.text.Component.text("§b[TP]")
                                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tp " + op.getName() + " " + player.getName()))
                                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Se téléporter sur " + player.getName()))));
                        op.sendMessage(comp);
                    }
                }
            }
        } else if (cps > 12) {
            if (now - cd.lastWarn > 2500) {
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
        cleanupClickTimes(cd, now);
        if (cd.clickTimes.size() > 25) {
            return true;
        }
        return cd.clickTimes.size() > 17;
    }

    private void cleanupClickTimes(ClickData cd, long now) {
        while (!cd.clickTimes.isEmpty() && now - cd.clickTimes.peekFirst() > 1000) {
            cd.clickTimes.pollFirst();
        }
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
                    String msg = "§c[AntiCheat] §e" + player.getName() + " §7suspect (mining ratio " + String.format("%.2f", ratio) + "). ";
                    for (Player op : Bukkit.getOnlinePlayers()) {
                        if (op.isOp() || op.hasPermission("xii.admin")) {
                            net.kyori.adventure.text.Component comp = net.kyori.adventure.text.Component.text(msg)
                                    .append(net.kyori.adventure.text.Component.text("§b[TP]")
                                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tp " + op.getName() + " " + player.getName()))
                                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Se téléporter sur " + player.getName()))));
                            op.sendMessage(comp);
                        }
                    }
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
