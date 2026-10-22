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
        if (args.length != 1) {
            MessageUtil.send(sender, "§cUsage : /zone <set|delete|info>");
            return true;
        }
        String action = args[0].toLowerCase();

        // /zone info : état de la zone (console ou joueur).
        if (action.equals("info")) {
            handleInfo(sender);
            return true;
        }

        // /zone set et /zone delete doivent être exécutés par un joueur.
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "§cCommande réservée aux joueurs.");
            return true;
        }

        switch (action) {
            case "set" -> handleSet(player);
            case "delete" -> handleDelete();
            default -> MessageUtil.send(sender, "§cUsage : /zone <set|delete|info>");
        }
        return true;
    }

    /**
     * /zone info : résumé de la zone de jeu et de la zone de combat.
     */
    private void handleInfo(CommandSender sender) {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null) {
            MessageUtil.send(sender, "§7Aucune zone définie.");
            return;
        }
        MessageUtil.send(sender, "§bZone de jeu §7: centre §f"
                + (int) zone.getCenterX() + "§7, §f" + (int) zone.getCenterZ()
                + " §7— taille §f" + zone.getSize() + " §7blocs.");
        MessageUtil.send(sender, "§7Bases à §f" + plugin.getConfigManager().getBaseRadius()
                + " §7blocs du centre.");
        var combatZone = plugin.getCombatZoneManager();
        if (combatZone.isActive()) {
            MessageUtil.send(sender, "§cZone de combat §7: rayon §b"
                    + (int) Math.ceil(combatZone.getCurrentRadius())
                    + " §7blocs — rayon final §b" + (int) combatZone.getEndRadius()
                    + " §7dans §f" + combatZone.getRemainingShrinkSeconds() + " s");
        } else {
            MessageUtil.send(sender, "§7Zone de combat : inactive (pendant le combat : "
                    + plugin.getConfigManager().getCombatZoneEndRadius()
                    + " blocs).");
        }
    }

    /**
     * /zone set : séquence officielle (spec §10).
     *
     * NOTE : le centre = la POSITION EXACTE de l'opérateur (X/Y/Z).
     * Le "rebase" des coordonnées d'un monde étant impossible dans
     * Minecraft, toute la génération est calculée RELATIVEMENT à ce
     * centre (bases +/-300, donjons +/-500...) : la map fonctionne
     * donc identiquement où qu'elle soit construite.
     *
     * Si une partie est en cours, on l'arrête proprement avant.
     */
    private void handleSet(Player operator) {
        var state = plugin.getGameManager().getState();

        // Sécurité : une partie en cours est arrêtée d'abord.
        if (state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT
                || state == com.mceteams.xii.enums.GameState.COUNTDOWN
                || state == com.mceteams.xii.enums.GameState.CLASS_SELECTION) {
            MessageUtil.broadcast("§c✘ La zone est reconfigurée : arrêt de la partie.");
            plugin.getGameManager().stopParty();
        }

        // 1/2/3/4 : position opérateur -> GameZone -> sauvegarde.
        plugin.getZoneManager().defineZone(operator.getLocation());

        // 5..11 : génération, WAITING, restrictions, téléportations.
        plugin.getGameManager().setupZone();

        var zone = plugin.getZoneManager().getZone();
        MessageUtil.send(operator, "§a✔ Zone définie au centre §f"
                + (int) zone.getCenterX() + "§7, §f" + (int) zone.getCenterZ()
                + " §7(dans §f" + zone.getWorldName() + "§7)"
                + "§a. Taille : " + plugin.getConfigManager().getZoneSize() + " blocs.");
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
            return List.of("set", "delete", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
