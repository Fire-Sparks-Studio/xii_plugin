package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public class BanMessageListener implements Listener {
    private final XiiPlugin plugin;

    public BanMessageListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            org.bukkit.BanList banList = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
            org.bukkit.BanEntry entry = banList.getBanEntry(event.getName());
            if (entry != null) {
                String reason = entry.getReason() != null ? entry.getReason() : "Aucune raison";
                String source = entry.getSource() != null ? entry.getSource() : "Console";
                String message = "§c§l----[VOUS AVEZ ÉTÉ BANNI]----\n\n"
                        + "§7Raison: §f" + reason + "\n"
                        + "§7Par: §f" + source + "\n\n"
                        + "§cSi vous pensez qu'il s'agit d'une erreur, contactez un administrateur.";
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
                return;
            }
        } catch (Exception ignored) {}

        try {
            org.bukkit.BanList ipBanList = Bukkit.getBanList(org.bukkit.BanList.Type.IP);
            if (event.getAddress() != null) {
                org.bukkit.BanEntry entry = ipBanList.getBanEntry(event.getAddress().getHostAddress());
                if (entry != null) {
                    String reason = entry.getReason() != null ? entry.getReason() : "Aucune raison";
                    String source = entry.getSource() != null ? entry.getSource() : "Console";
                    String message = "§c§l----[VOUS AVEZ ÉTÉ BANNI]----\n\n"
                            + "§7Raison: §f" + reason + "\n"
                            + "§7Par: §f" + source + "\n\n"
                            + "§cSi vous pensez qu'il s'agit d'une erreur, contactez un administrateur.";
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_BANNED) {
            String reason = event.getKickMessage();
            if (reason == null || reason.isEmpty()) {
                reason = "Aucune raison";
            }
            String message = "§c§l----[VOUS AVEZ ÉTÉ BANNI]----\n\n"
                    + "§7Raison: §f" + reason + "\n\n"
                    + "§cSi vous pensez qu'il s'agit d'une erreur, contactez un administrateur.";
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, message);
        }
    }
}
