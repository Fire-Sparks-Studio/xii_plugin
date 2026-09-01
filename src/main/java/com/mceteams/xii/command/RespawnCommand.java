package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /respawn : forcer le retour à la vie d'un joueur mort.
 *
 * Usage :
 *   /respawn               => vous repassez vivant dans votre base.
 *   /respawn <joueur>      => force le respawn d'un joueur (admin).
 *
 * Utile quand un coeur d'équipe est détruit : le respawn normal est
 * bloqué, seul ceci (ou un totem de revive) peut ramener un membre.
 * Nécessite xii.admin pour cibler un autre joueur.
 */
public class RespawnCommand implements TabExecutor {

    private final XiiPlugin plugin;

    public RespawnCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        // Cible : le joueur visé, sinon le sender s'il est un joueur.
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("xii.admin")) {
                MessageUtil.send(sender,
                        "§cPermission insuffisante (xii.admin).");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                MessageUtil.send(sender,
                        "§cJoueur introuvable ou hors ligne : §7" + args[0]);
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender,
                        "§cUsage : /respawn <joueur>");
                return true;
            }
            target = player;
        }

        boolean done = plugin.getRespawnManager()
                .forceRespawn(target.getUniqueId());
        if (done) {
            MessageUtil.send(target, "§a✦ §fVous êtes réapparu !");
            if (target != sender) {
                MessageUtil.send(sender,
                        "§a✔ " + target.getName() + " a été ramené dans la partie.");
            }
        } else {
            MessageUtil.send(sender,
                    "§cImpossible : " + target.getName() + " est déjà vivant.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        String typed = args[0].toLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase().startsWith(typed)) {
                suggestions.add(online.getName());
            }
        }
        return suggestions;
    }
}