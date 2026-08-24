package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.gui.AdminGUI;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Commande /admin : ouvre directement le menu d'administration.
 *
 * Équivalent au clic sur l'item TRIPWIRE du lobby, mais accessible
 * par commande à tout moment (les boutons du GUI s'adaptent eux-mêmes
 * à l'état courant du jeu : lancer, annuler, arrêter, gérer équipes).
 *
 * Permission requise : xii.admin.
 */
public class AdminCommand implements TabExecutor {

    private final XiiPlugin plugin;

    public AdminCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        // Réservé aux joueurs (une GUI s'ouvre à l'écran).
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "§cCommande réservée aux joueurs.");
            return true;
        }
        if (!player.hasPermission("xii.admin")) {
            MessageUtil.send(player, "§cPermission insuffisante (xii.admin).");
            return true;
        }

        new AdminGUI(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return List.of(); // aucune argument attendu
    }
}
