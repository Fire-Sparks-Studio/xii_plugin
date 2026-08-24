package com.mceteams.xii.command;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.TeamUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /teams (spec §34) : gestion centralisée des équipes.
 *
 * Sous-commandes officielles :
 *   /teams                          => liste des équipes
 *   /teams create <couleur> [taille] => crée l'équipe (+ équipe Bukkit)
 *   /teams remove <couleur|joueur>   => supprime une équipe OU retire un membre
 *   /teams add <joueur> <couleur>    => ajoute un joueur à une équipe
 *   /teams set <couleur> size <n>    => ajuste la taille maximale
 *
 * Les mutations nécessitent la permission xii.admin.
 */
public class TeamCommand implements TabExecutor {

    private final XiiPlugin plugin;

    public TeamCommand(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        // --- Aucun argument : liste des équipes -----------------------
        if (args.length == 0) {
            listTeams(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "add" -> handleAdd(sender, args);
            case "set" -> handleSet(sender, args);
            case "eliminate" -> handleEliminate(sender, args);
            case "revive" -> handleRevive(sender, args);
            case "heart" -> handleHeart(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    // -----------------------------------------------------------------
    // Sous-commandes
    // -----------------------------------------------------------------

    /** Liste toutes les équipes existantes. */
    private void listTeams(CommandSender sender) {
        if (plugin.getTeamManager().isEmpty()) {
            MessageUtil.send(sender, "§7Aucune équipe. "
                    + "§8(/teams create <couleur>)");
            return;
        }
        MessageUtil.send(sender, "§bÉquipes :");
        for (var team : plugin.getTeamManager().all()) {
            MessageUtil.send(sender, " " + team.getColor().getColoredName()
                    + " §7- §f" + team.getPlayerCount() + "/" + team.getMaxPlayers()
                    + (team.isHeartAlive() ? "" : " §c(coeur détruit)")
                    + (team.isEliminated() ? " §4éliminée" : ""));
        }
    }

    /** /teams create <couleur> [taille] */
    private void handleCreate(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, "§cUsage : /teams create <couleur> [taille]");
            return;
        }
        TeamColor color = TeamUtil.parse(args[1]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue. "
                    + "Disponibles : " + TeamUtil.displayNames());
            return;
        }
        int size = args.length >= 3 ? parseIntSafe(args[2], -1) : -1;
        boolean created = plugin.getTeamManager().createTeam(color);
        if (!created) {
            MessageUtil.send(sender, "§cCette équipe existe déjà.");
            return;
        }
        if (size > 0 && !plugin.getTeamManager().setMaxPlayers(color, size)) {
            MessageUtil.send(sender, "§7Taille invalide, défaut appliqué.");
        }
        MessageUtil.send(sender, "§aÉquipe créée : " + color.getColoredName());
    }

    /**
     * /teams remove <couleur|joueur>
     * Désambiguïsation : si l'argument est une couleur => suppression
     * d'équipe ; sinon => retrait du joueur de son équipe.
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender,
                    "§cUsage : /teams remove <couleur|joueur>");
            return;
        }

        TeamColor color = TeamUtil.parse(args[1]);
        if (color != null) {
            boolean removed = plugin.getTeamManager().removeTeam(color);
            MessageUtil.send(sender, removed
                    ? "§aÉquipe supprimée : " + color.getColoredName()
                    : "§cÉquipe introuvable.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.send(sender, "§cNi couleur ni joueur valide.");
            return;
        }
        boolean removed = plugin.getTeamManager().removePlayer(target.getUniqueId());
        MessageUtil.send(sender, removed
                ? "§a" + target.getName() + " §7retiré de son équipe."
                : "§cCe joueur n'a pas d'équipe.");
        if (removed) {
            MessageUtil.send(target, "§7Vous avez été retiré de votre équipe.");
            plugin.getLobbyItemManager().giveLobbyItems(target);
        }
    }

    /** /teams add <joueur> <couleur> */
    private void handleAdd(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            MessageUtil.send(sender, "§cUsage : /teams add <joueur> <couleur>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.send(sender, "§cJoueur introuvable ou hors ligne.");
            return;
        }
        TeamColor color = TeamUtil.parse(args[2]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue. "
                    + "Disponibles : " + TeamUtil.displayNames());
            return;
        }
        var result = plugin.getTeamManager().addPlayer(target.getUniqueId(), color);
        switch (result) {
            case OK -> {
                MessageUtil.send(sender, "§a" + target.getName()
                        + " §7rejoint l'équipe " + color.getColoredName());
                MessageUtil.send(target, "§7Vous rejoignez l'équipe "
                        + color.getColoredName());
                plugin.getLobbyItemManager().giveLobbyItems(target);
            }
            case TEAM_NOT_FOUND -> MessageUtil.send(sender,
                    "§cÉquipe inexistante. Créez-la d'abord (/teams create).");
            case ALREADY_IN_TEAM -> MessageUtil.send(sender,
                    "§cLe joueur est déjà dans cette équipe.");
            case FULL -> MessageUtil.send(sender, "§cÉquipe pleine !");
        }
    }

    /** /teams set <couleur> size <n> */
    private void handleSet(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 4 || !args[2].equalsIgnoreCase("size")) {
            MessageUtil.send(sender,
                    "§cUsage : /teams set <couleur> size <nombre>");
            return;
        }
        TeamColor color = TeamUtil.parse(args[1]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue.");
            return;
        }
        int size = parseIntSafe(args[3], -1);
        boolean ok = plugin.getTeamManager().setMaxPlayers(color, size);
        MessageUtil.send(sender, ok
                ? "§aTaille max de " + color.getColoredName()
                + " §adéfinie sur §f" + size
                : "§cTaille invalide (inférieure à l'effectif actuel ?).");
    }

    // -----------------------------------------------------------------
    // Administration gameplay : éliminer / réhabiliter / coeur
    // -----------------------------------------------------------------

    /** /teams eliminate <couleur> : élimination forcée. */
    private void handleEliminate(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, "§cUsage : /teams eliminate <couleur>");
            return;
        }
        TeamColor color = TeamUtil.parse(args[1]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue.");
            return;
        }
        boolean ok = plugin.getTeamManager().forceEliminate(color);
        MessageUtil.send(sender, ok
                ? "§c✘ Équipe " + color.getColoredName() + " §céliminée."
                : "§cImpossible (inexistante ou déjà éliminée).");
    }

    /** /teams revive <couleur> : réhabilitation d'une équipe éliminée. */
    private void handleRevive(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            MessageUtil.send(sender, "§cUsage : /teams revive <couleur>");
            return;
        }
        TeamColor color = TeamUtil.parse(args[1]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue.");
            return;
        }
        boolean ok = plugin.getTeamManager().reviveTeam(color);
        MessageUtil.send(sender, ok
                ? "§a✔ Équipe " + color.getColoredName() + " §aréhabilitée."
                : "§cImpossible (inexistante ou non éliminée).");
    }

    /**
     * /teams heart destroy <couleur>  => détruit le coeur (sans points).
     * /teams heart restore <couleur>  => restaure le coeur.
     */
    private void handleHeart(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            MessageUtil.send(sender,
                    "§cUsage : /teams heart <destroy|restore> <couleur>");
            return;
        }
        TeamColor color = TeamUtil.parse(args[2]);
        if (color == null) {
            MessageUtil.send(sender, "§cCouleur inconnue.");
            return;
        }
        String action = args[1].toLowerCase();

        switch (action) {
            case "destroy" -> {
                var team = plugin.getTeamManager().getTeam(color);
                if (team == null) {
                    MessageUtil.send(sender, "§cÉquipe inexistante.");
                    return;
                }
                // automatic=true : pas de points attribués pour un acte admin.
                plugin.getCoreService().breakCore(team, null, true, true);
                MessageUtil.send(sender,
                        "§4✖ Coeur de " + color.getColoredName() + " §4détruit.");
            }
            case "restore" -> {
                boolean ok = plugin.getCoreService().restoreCore(color);
                MessageUtil.send(sender, ok
                        ? "§a✔ Coeur de " + color.getColoredName() + " §arestauré."
                        : "§cImpossible (inexistante ou coeur déjà vivant).");
            }
            default -> MessageUtil.send(sender,
                    "§cAction inconnue : destroy ou restore.");
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("xii.admin")) {
            MessageUtil.send(sender, "§cPermission insuffisante (xii.admin).");
            return false;
        }
        return true;
    }

    private int parseIntSafe(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void sendUsage(CommandSender sender) {
        MessageUtil.send(sender, "§cSous-commandes : create, remove, add, set, "
                + "eliminate, revive, heart");
    }

    // -----------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("create", "remove", "add", "set",
                    "eliminate", "revive", "heart"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "create", "set", "remove", "eliminate", "revive" ->
                        addColors(suggestions);
                case "heart" -> suggestions.addAll(List.of("destroy", "restore"));
                case "add" -> Bukkit.getOnlinePlayers()
                        .forEach(p -> suggestions.add(p.getName()));
                default -> { }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "set" -> suggestions.add("size");
                case "add" -> addColors(suggestions);
                case "heart" -> addColors(suggestions);
                default -> { }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            suggestions.addAll(List.of("4", "6", "8", "12"));
        }

        // Filtre selon ce que le sender tape déjà.
        String typed = args[args.length - 1].toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(typed))
                .toList();
    }

    /** Ajoute les 4 couleurs (minuscules) aux suggestions. */
    private void addColors(List<String> suggestions) {
        for (TeamColor color : TeamColor.values()) {
            suggestions.add(color.name().toLowerCase());
        }
    }
}
