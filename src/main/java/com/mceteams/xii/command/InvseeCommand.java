package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.gui.InvseeGUI;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public class InvseeCommand implements TabExecutor {
    private final XiiPlugin plugin;

    public InvseeCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            MessageUtil.send(sender, "§cCommande réservée aux joueurs.");
            return true;
        }
        if (!admin.hasPermission("xii.admin")) {
            MessageUtil.send(admin, "§cPermission insuffisante (xii.admin).");
            return true;
        }
        if (args.length < 1) {
            MessageUtil.send(admin, "§cUsage : /invsee <joueur>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            MessageUtil.send(admin, "§cJoueur introuvable ou hors ligne.");
            return true;
        }
        new InvseeGUI(plugin, target).open(admin);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
