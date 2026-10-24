package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.regex.Pattern;

public class AntiCheatListener implements Listener {
    private final XiiPlugin plugin;
    private static final Pattern EZ_PATTERN = Pattern.compile("(?i)(^|[^a-z])e+z+([^a-z]|$)|(^|[^a-z])gg([^a-z]|$)");

    public AntiCheatListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAnimation(PlayerAnimationEvent event) {
        Player p = event.getPlayer();
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().onLeftClick(p);
            if (plugin.getAntiCheatManager().shouldCancelClick(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player p = event.getPlayer();
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().onLeftClick(p);
            if (plugin.getAntiCheatManager().shouldCancelClick(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        if (plugin.getAntiCheatManager() != null) {
            plugin.getAntiCheatManager().onBlockBreak(p, event.getBlock().getType());
        }
    }

    @EventHandler
    public void onInvOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (p.hasPermission("xii.admin")) return;
        if (plugin.getAntiCheatManager() != null && plugin.getAntiCheatManager().isVanished(p)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (p.hasPermission("xii.admin")) return;
        if (plugin.getAntiCheatManager() != null && plugin.getAntiCheatManager().isVanished(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String msg = event.getMessage();
        if (msg == null) return;
        // detect EZ variations but be careful not to match words containing ez inside like attendez/allez? check boundaries
        String lower = msg.toLowerCase();
        // more strict: look for token ez/gg
        boolean bad = false;
        // split by non-letters
        String[] tokens = lower.split("[^a-z]+");
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            // Seul "ez" (et ses variantes) est bloqué ; "gg"/"ggwp" sont autorisés.
            if (t.equals("ez") || t.equals("ezz") || t.equals("ezzz") || t.equals("ezzzz")) {
                bad = true;
                break;
            }
            // avoid words that contain ez as substring inside longer word? tokens are words separated by non-letters
        }
        if (!bad) {
            // also check if it's exactly ez surrounded by punctuation
            if (lower.matches(".*(^|\\W)(e+z+)(\\W|$).*")) {
                bad = true;
            }
        }
        if (bad) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage("§8§oCe n'est pas très gentil");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
            });
        }
    }
}
