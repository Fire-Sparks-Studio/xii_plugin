package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /party (spec §34) : gestion du lancement de la partie.
 *
 * Sous-commandes officielles :
 *   /party start      => lance le compte à rebours (spec §13)
 *   /party stop       => annule le countdown ou arrête la partie (§35)
 *   /party set <jour> => saut direct à un jour 1..12 (12 sous-phases)
 */
public class PartyCommand implements TabExecutor {

    private final XiiPlugin plugin;

    public PartyCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("xii.admin")) {
            MessageUtil.send(sender, "§cPermission insuffisante (xii.admin).");
            return true;
        }
        if (args.length == 0) {
            MessageUtil.send(sender, "§cUsage : /party <start|stop|set <jour>>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "set" -> handleSetDay(sender, args);
            case "package" -> handlePackage(sender);
            default -> MessageUtil.send(sender,
                    "§cSous-commandes : start, stop, set, package");
        }
        return true;
    }

    /** /party start : uniquement depuis WAITING, zone + équipe requises.
     * La validation est centralisée dans GameManager.startParty(). */
    private void handleStart(CommandSender sender) {
        String error = plugin.getGameManager().startParty();
        if (error != null) {
            MessageUtil.send(sender, "§c" + error);
        } else {
            MessageUtil.send(sender, "§aLancement en cours...");
        }
    }

    /**
     * /party stop :
     * - pendant le COUNTDOWN => annule le compte à rebours ;
     * - pendant une partie => arrêt complet et retour WAITING (spec §35).
     */
    private void handleStop(CommandSender sender) {
        var state = plugin.getGameManager().getState();
        if (state == com.mceteams.xii.enums.GameState.COUNTDOWN) {
            plugin.getGameManager().cancelCountdown();
        } else if (state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT) {
            plugin.getGameManager().stopParty();
        } else if (state == com.mceteams.xii.enums.GameState.CLASS_SELECTION) {
            // Sélection interrompue : retour attente complet.
            plugin.getGameManager().stopParty();
        } else {
            MessageUtil.send(sender, "§cAucune partie en cours.");
        }
    }

    /** /party set <jour> : saut direct à la sous-phase N (1..12). */
    private void handleSetDay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender,
                    "§cUsage : /party set <jour> (1 = début préparation, "
                            + "7 = début combat)");
            return;
        }
        int day;
        try {
            day = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            MessageUtil.send(sender, "§cJour invalide.");
            return;
        }
        String error = plugin.getGameManager().skipToDay(day);
        if (error != null) {
            MessageUtil.send(sender, "§c" + error);
        } else {
            MessageUtil.send(sender, "§aSaut vers le jour §f" + day
                    + " §aeffectué.");
        }
    }

    /**
     * /party package : force l'apparition IMMÉDIATE d'un colis
     * (utile pour tester sans attendre l'intervalle aléatoire).
     * Fonctionne uniquement pendant la PRÉPARATION.
     */
    private void handlePackage(CommandSender sender) {
        if (plugin.getGameManager().getState()
                != com.mceteams.xii.enums.GameState.PREPARATION) {
            MessageUtil.send(sender,
                    "§cLes colis n'apparaissent que pendant la préparation.");
            return;
        }
        plugin.getPackageService().spawnRandomPackage();
        MessageUtil.send(sender, "§e✦ Colis en cours d'apparition...");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("start", "stop", "set", "package"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (int day = 1; day <= 12; day++) {
                suggestions.add(String.valueOf(day));
            }
        }
        String typed = args[args.length - 1].toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(typed))
                .toList();
    }
}
