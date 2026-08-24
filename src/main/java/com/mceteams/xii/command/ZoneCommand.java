package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Commande /zone (spec §3/§10/§11) : configuration de la zone de jeu.
 *
 *   /zone set    => définit le centre à la position de l'opérateur,
 *                   génère la zone d'attente + bases + donjons, passe
 *                   en WAITING et téléporte tout le monde.
 *   /zone delete => retire la zone : serveur Minecraft normal.
 *
 * La zone EST le déclencheur de configuration (plus aucun /setup).
 */
public class ZoneCommand implements TabExecutor {

    private final XiiPlugin plugin;

    public ZoneCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("xii.admin")) {
            MessageUtil.send(sender, "§cPermission insuffisante (xii.admin).");
            return true;
        }
        // /zone set doit être exécuté par un joueur (position requise).
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "§cCommande réservée aux joueurs.");
            return true;
        }
        if (args.length != 1) {
            MessageUtil.send(sender, "§cUsage : /zone <set|delete>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player);
            case "delete" -> handleDelete();
            default -> MessageUtil.send(sender, "§cUsage : /zone <set|delete>");
        }
        return true;
    }

    /**
     * /zone set : séquence officielle (spec §10).
     * Si une partie est en cours, on l'arrête proprement avant.
     */
    private void handleSet(Player operator) {
        var state = plugin.getGameManager().getState();

        // Sécurité : une partie en cours est arrêtée d'abord.
        if (state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT
                || state == com.mceteams.xii.enums.GameState.COUNTDOWN
                || state == com.mceteams.xii.enums.GameState.CLASS_SELECTION) {
            MessageUtil.broadcast("§cLa zone est reconfigurée : arrêt de la partie.");
            plugin.getGameManager().stopParty();
        }

        // 1/2/3/4 : position opérateur -> GameZone -> sauvegarde.
        plugin.getZoneManager().defineZone(operator.getLocation());

        // 5..11 : génération, WAITING, restrictions, téléportations.
        plugin.getGameManager().setupZone();

        MessageUtil.send(operator, "§aZone définie à votre position. "
                + "Taille : " + plugin.getConfigManager().getZoneSize() + " blocs.");
    }

    /** /zone delete : retour au serveur normal (spec §11). */
    private void handleDelete() {
        if (!plugin.getZoneManager().hasZone()) {
            MessageUtil.broadcast("§7Aucune zone définie.");
            return;
        }
        plugin.getGameManager().deleteZone();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "delete").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
