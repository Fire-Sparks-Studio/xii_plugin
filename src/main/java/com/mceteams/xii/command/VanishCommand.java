package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {
    private final XiiPlugin plugin;

    public VanishCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission insuffisante.");
            return true;
        }
        if (plugin.getAntiCheatManager() == null) {
            player.sendMessage("§cAntiCheat non initialisé.");
            return true;
        }
        plugin.getAntiCheatManager().toggleVanish(player);
        return true;
    }
}
