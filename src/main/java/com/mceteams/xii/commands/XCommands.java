package com.mceteams.xii.commands;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.SetupManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerScore;
import com.mceteams.xii.model.TeamScore;
import com.mceteams.xii.service.PointService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Stream;

public class XCommands implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final SetupManager setupManager;
    private final PointService pointService;

    public XCommands(TeamManager teamManager, GameManager gameManager, PointService pointService, SetupManager setupManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.pointService = pointService;
        this.setupManager = setupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("join")) {
            handleJoin(player, args);
            return true;
        }

        if (cmd.equals("leave")) {
            handleLeave(player);
            return true;
        }

        if (!cmd.equals("xii")) return true;

        if (args.length == 0) {
            player.sendMessage("§cUsage: /xii <commande>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "teams" -> handleTeams(player, args);
            case "day" -> handleDay(player, args);
            case "allow" -> handleAllow(player, args);
            case "points" -> handlePoints(player, args);
            case "admin" -> handleAdmin(player, args);
            default -> player.sendMessage("§cCommande inconnue: " + args[0]);
        }
        return true;
    }

    // ========== /join /leave ==========

    private void handleJoin(Player player, String[] args) {
        if (!player.hasPermission("xii.play")) {
            player.sendMessage("§cTu n'as pas la permission !");
            return;
        }
        if (!gameManager.isJoinEnabled()) {
            player.sendMessage("§cLes teams sont fermées !");
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            player.sendMessage("§cLa partie a déjà commencé !");
            return;
        }
        if (teamManager.getTeam(player.getUniqueId()) != null) {
            player.sendMessage("§cTu es déjà dans une équipe !");
            return;
        }
        if (args.length < 1) {
            player.sendMessage("§cUsage: /join <couleur>");
            return;
        }
        TeamColor color;
        try {
            color = TeamColor.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + args[0]);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        if (teamManager.addPlayer(player.getUniqueId(), team)) {
            player.sendMessage("§aTu as rejoint l'équipe " + color.getName(Lang.FR) + " !");
        } else {
            player.sendMessage("§cL'équipe est complète !");
        }
    }

    private void handleLeave(Player player) {
        if (!player.hasPermission("xii.play")) {
            player.sendMessage("§cTu n'as pas la permission !");
            return;
        }
        if (!gameManager.isLeaveEnabled()) {
            player.sendMessage("§cLe leave est désactivé !");
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            player.sendMessage("§cLa partie a déjà commencé !");
            return;
        }
        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§cTu n'es dans aucune équipe !");
            return;
        }
        teamManager.removePlayer(player.getUniqueId());
        player.sendMessage("§aTu as quitté l'équipe.");
    }

    // ========== /xii teams ==========

    private void handleTeams(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii teams <create|delete|add|remove|heart|eliminate|revive|tpbase|options>");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "create" -> handleTeamsCreate(player, args);
            case "delete" -> handleTeamsDelete(player, args);
            case "add" -> handleTeamsAdd(player, args);
            case "remove" -> handleTeamsRemove(player, args);
            case "heart" -> handleTeamsHeart(player, args);
            case "eliminate" -> handleTeamsEliminate(player, args);
            case "revive" -> handleTeamsRevive(player, args);
            case "tpbase" -> handleTeamsTpBase(player, args);
            case "options" -> handleTeamsOptions(player, args);
            default -> player.sendMessage("§cSous-commande inconnue: " + sub);
        }
    }

    private void handleTeamsCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams create <couleur>");
            return;
        }
        TeamColor color;
        try {
            color = TeamColor.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + args[2]);
            return;
        }
        if (teamManager.getTeam(color) != null) {
            player.sendMessage("§cCette équipe existe déjà !");
            return;
        }
        teamManager.createTeam(color);
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " créée !");
    }

    private void handleTeamsDelete(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams delete <couleur>");
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2]);
        if (color == null) return;
        teamManager.deleteTeam(teamManager.getTeam(color));
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " supprimée !");
    }

    private void handleTeamsAdd(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUsage: /xii teams add <joueur> <couleur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[2]);
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[3]);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);
        if (teamManager.addPlayer(target.getUniqueId(), team)) {
            player.sendMessage("§a" + target.getName() + " ajouté à l'équipe " + color.getName(Lang.FR) + " !");
        } else {
            player.sendMessage("§cL'équipe est complète !");
        }
    }

    private void handleTeamsRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams remove <joueur>");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[2]);
            return;
        }
        teamManager.removePlayer(target.getUniqueId());
        player.sendMessage("§a" + target.getName() + " retiré de son équipe !");
    }

    private void handleTeamsHeart(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUsage: /xii teams heart <couleur> <destroy|restore>");
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2]);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);

        String action = args[3].toLowerCase();
        switch (action) {
            case "destroy" -> {
                teamManager.destroyHeart(team);
                player.sendMessage("§aCœur de l'équipe " + color.getName(Lang.FR) + " détruit !");
            }
            case "restore" -> {
                team.setHeartAlive(true);
                player.sendMessage("§aCœur de l'équipe " + color.getName(Lang.FR) + " restauré !");
            }
            default -> player.sendMessage("§cUsage: /xii teams heart <couleur> <destroy|restore>");
        }
    }

    private void handleTeamsEliminate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams eliminate <couleur>");
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2]);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);
        team.destroyHeart();
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage("§cVous avez été éliminé !");
            }
        }
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " éliminée !");
    }

    private void handleTeamsRevive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams revive <couleur>");
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2]);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);
        team.setHeartAlive(true);
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " réanimée !");
    }

    private void handleTeamsTpBase(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams tpbase <couleur|@a|joueur>");
            return;
        }

        String target = args[2];

        if (target.equals("@a")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                GameTeam team = teamManager.getTeam(online.getUniqueId());
                if (team != null && team.getSpawn() != null) {
                    online.teleport(team.getSpawn());
                }
            }
            player.sendMessage("§aTous les joueurs ont été téléportés à leur base !");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            GameTeam team = teamManager.getTeam(targetPlayer.getUniqueId());
            if (team == null) {
                player.sendMessage("§c" + target + " n'est dans aucune équipe !");
                return;
            }
            if (team.getSpawn() == null) {
                player.sendMessage("§cLe spawn de cette équipe n'est pas défini !");
                return;
            }
            targetPlayer.teleport(team.getSpawn());
            player.sendMessage("§a" + target + " téléporté à sa base !");
            return;
        }

        TeamColor color = getExistingTeamColor(player, target);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);
        if (team.getSpawn() == null) {
            player.sendMessage("§cLe spawn de cette équipe n'est pas défini !");
            return;
        }
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.teleport(team.getSpawn());
            }
        }
        player.sendMessage("§aJoueurs de l'équipe " + color.getName(Lang.FR) + " téléportés !");
    }

    private void handleTeamsOptions(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii teams options <allow|<couleur>>");
            return;
        }

        // /xii teams options allow join/leave true/false
        if (args[2].equalsIgnoreCase("allow")) {
            if (args.length < 5) {
                player.sendMessage("§cUsage: /xii teams options allow <join|leave> <true|false>");
                return;
            }
            String what = args[3].toLowerCase();
            boolean value = Boolean.parseBoolean(args[4]);
            switch (what) {
                case "join" -> {
                    gameManager.setJoinEnabled(value);
                    player.sendMessage("§aJoin " + (value ? "activé" : "désactivé") + " !");
                }
                case "leave" -> {
                    gameManager.setLeaveEnabled(value);
                    player.sendMessage("§aLeave " + (value ? "activé" : "désactivé") + " !");
                }
                default -> player.sendMessage("§cUsage: /xii teams options allow <join|leave> <true|false>");
            }
            return;
        }

        // /xii teams options <couleur> <setspawn|setheart|maxmembers>
        TeamColor color = getExistingTeamColor(player, args[2]);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);

        if (args.length < 4) {
            player.sendMessage("§cUsage: /xii teams options " + color.getFormattedName() + " <setspawn|setheart|maxmembers>");
            return;
        }

        String option = args[3].toLowerCase();
        switch (option) {
            case "setspawn" -> {
                team.setSpawn(player.getLocation());
                player.sendMessage("§aSpawn de l'équipe " + color.getName(Lang.FR) + " défini !");
            }
            case "setheart" -> {
                team.setHeartLocation(player.getLocation());
                player.sendMessage("§aCœur de l'équipe " + color.getName(Lang.FR) + " défini !");
            }
            case "maxmembers" -> {
                if (args.length < 5) {
                    player.sendMessage("§cUsage: /xii teams options " + color.getFormattedName() + " maxmembers <nombre>");
                    return;
                }
                int max;
                try {
                    max = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cNombre invalide !");
                    return;
                }
                team.setMaxPlayers(max);
                player.sendMessage("§aLimite de l'équipe " + color.getName(Lang.FR) + " mise à " + max + " !");
            }
            default -> player.sendMessage("§cOption inconnue: " + option);
        }
    }

    // ========== Helpers ==========

    private TeamColor getExistingTeamColor(Player player, String input) {
        TeamColor color;
        try {
            color = TeamColor.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + input);
            return null;
        }
        if (teamManager.getTeam(color) == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return null;
        }
        return color;
    }

    // ========== /xii day ==========

    private void handleDay(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii day <start|stop|set>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (gameManager.getState() != GameState.WAITING) {
                    player.sendMessage("§cLa partie a déjà commencé !");
                    return;
                }
                if (teamManager.getTeamCount() < 2) {
                    player.sendMessage("§cIl faut au moins 2 équipes !");
                    return;
                }
                gameManager.startGame();
                Bukkit.broadcast(Component.text("\n§6§lXII DAYS §7a commencé !\n"));
            }
            case "stop" -> {
                if (gameManager.getState() == GameState.WAITING) {
                    player.sendMessage("§cLa partie n'a pas commencé !");
                    return;
                }
                gameManager.endGame();
                Bukkit.broadcast(Component.text("\n§c§lXII DAYS §7a été arrêté !\n"));
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /xii day set <1-12>");
                    return;
                }
                int day;
                try {
                    day = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cNombre invalide !");
                    return;
                }
                if (day < 1 || day > 12) {
                    player.sendMessage("§cLe jour doit être entre 1 et 12 !");
                    return;
                }
                gameManager.getDayManager().setDay(day);
                Bukkit.broadcast(Component.text("\n§6§lJour §c§l" + day + " §6§l!\n"));
            }
            default -> player.sendMessage("§cSous-commande inconnue: " + args[1]);
        }
    }

    // ========== /xii allow ==========

    private void handleAllow(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii allow <item|block> <true|false>");
            return;
        }
        String what = args[1].toLowerCase();
        boolean allow = Boolean.parseBoolean(args[2]);
        switch (what) {
            case "item" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne tiens aucun item !");
                    return;
                }
                if (allow) {
                    gameManager.getBlacklistedItems().remove(hand.getType());
                    player.sendMessage("§a" + hand.getType().name() + " retiré de la blacklist !");
                } else {
                    gameManager.getBlacklistedItems().add(hand.getType());
                    player.sendMessage("§c" + hand.getType().name() + " ajouté à la blacklist !");
                }
            }
            case "block" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage("§cTu ne vises aucun bloc !");
                    return;
                }
                if (allow) {
                    gameManager.getBlacklistedItems().remove(target.getType());
                    player.sendMessage("§a" + target.getType().name() + " retiré de la blacklist !");
                } else {
                    gameManager.getBlacklistedItems().add(target.getType());
                    player.sendMessage("§c" + target.getType().name() + " ajouté à la blacklist !");
                }
            }
            default -> player.sendMessage("§cUsage: /xii allow <item|block> <true|false>");
        }
    }

    // ========== /xii points ==========

    private void handlePoints(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii points <set|reset> <joueur|couleur> [valeur]");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "set" -> handlePointsSet(player, args);
            case "reset" -> handlePointsReset(player, args);
            default -> player.sendMessage("§cSous-commande inconnue: " + args[1]);
        }
    }

    private void handlePointsSet(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUsage: /xii points set <joueur|couleur> <points>");
            return;
        }
        int points;
        try {
            points = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cNombre invalide !");
            return;
        }
        String target = args[2];
        TeamColor color;
        try {
            color = TeamColor.valueOf(target.toUpperCase());
            GameTeam team = teamManager.getTeam(color);
            if (team == null) {
                player.sendMessage("§cCette équipe n'existe pas !");
                return;
            }
            TeamScore score = pointService.getTeamScore(team);
            score.setTotal(points);
            player.sendMessage("§aPoints de l'équipe " + color.getName(Lang.FR) + " mis à " + points + " !");
        } catch (IllegalArgumentException e) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                player.sendMessage("§cJoueur introuvable: " + target);
                return;
            }
            PlayerScore score = pointService.getPlayerScore(targetPlayer.getUniqueId());
            score.setTotal(points);
            player.sendMessage("§aPoints de " + targetPlayer.getName() + " mis à " + points + " !");
        }
    }

    private void handlePointsReset(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii points reset <joueur|couleur>");
            return;
        }
        String target = args[2];
        TeamColor color;
        try {
            color = TeamColor.valueOf(target.toUpperCase());
            GameTeam team = teamManager.getTeam(color);
            if (team == null) {
                player.sendMessage("§cCette équipe n'existe pas !");
                return;
            }
            pointService.getTeamScore(team).reset();
            player.sendMessage("§aPoints de l'équipe " + color.getName(Lang.FR) + " réinitialisés !");
        } catch (IllegalArgumentException e) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                player.sendMessage("§cJoueur introuvable: " + target);
                return;
            }
            pointService.getPlayerScore(targetPlayer.getUniqueId()).reset();
            player.sendMessage("§aPoints de " + targetPlayer.getName() + " réinitialisés !");
        }
    }

    // ========== /xii admin ==========

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii admin <setup|quit>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "setup" -> setupManager.setup();
            case "quit" -> setupManager.quit();
            default -> player.sendMessage("§cSous-commande inconnue: " + args[1]);
        }
    }

    // ========== Tab Complete ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("join")) {
            if (args.length == 1) {
                return getExistingTeams().stream()
                        .map(c -> c.getFormattedName())
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
            return Collections.emptyList();
        }

        if (cmd.equals("leave")) return Collections.emptyList();

        if (!cmd.equals("xii")) return Collections.emptyList();

        if (args.length == 1) {
            return Stream.of("teams", "day", "allow", "points", "admin")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "teams" -> {
                    return Stream.of("create", "delete", "add", "remove", "heart", "eliminate", "revive", "tpbase", "options")
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "day" -> {
                    return Stream.of("start", "stop", "set")
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "allow" -> {
                    return Stream.of("item", "block")
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "points" -> {
                    return Stream.of("set", "reset")
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "admin" -> {
                    return Stream.of("setup", "quit")
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("teams")) {
                switch (args[1].toLowerCase()) {
                    case "create" -> {
                        return getNonExistingTeams().stream()
                                .map(TeamColor::getFormattedName)
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .toList();
                    }
                    case "delete", "heart", "eliminate", "revive", "tpbase" -> {
                        return getExistingTeams().stream()
                                .map(TeamColor::getFormattedName)
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .toList();
                    }
                    case "add" -> {
                        return null; // online players
                    }
                    case "remove" -> {
                        return null; // online players
                    }
                    case "options" -> {
                        List<String> list = new ArrayList<>(getExistingTeams().stream()
                                .map(TeamColor::getFormattedName)
                                .toList());
                        list.add("allow");
                        return list.stream()
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .toList();
                    }
                }
            }
            if (args[0].equalsIgnoreCase("day") && args[1].equalsIgnoreCase("set")) {
                return Stream.of("1","2","3","4","5","6","7","8","9","10","11","12")
                        .filter(s -> s.startsWith(args[2]))
                        .toList();
            }
            if (args[0].equalsIgnoreCase("points")) {
                return null; // online players + team colors
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("teams")) {
                switch (args[1].toLowerCase()) {
                    case "heart" -> {
                        return Stream.of("destroy", "restore")
                                .filter(s -> s.startsWith(args[3].toLowerCase()))
                                .toList();
                    }
                    case "add" -> {
                        return getExistingTeams().stream()
                                .map(TeamColor::getFormattedName)
                                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                .toList();
                    }
                    case "options" -> {
                        if (args[2].equalsIgnoreCase("allow")) {
                            return Stream.of("join", "leave")
                                    .filter(s -> s.startsWith(args[3].toLowerCase()))
                                    .toList();
                        }
                        // It's a team name → show options
                        try {
                            TeamColor.valueOf(args[2].toUpperCase());
                            return Stream.of("setspawn", "setheart", "maxmembers")
                                    .filter(s -> s.startsWith(args[3].toLowerCase()))
                                    .toList();
                        } catch (IllegalArgumentException ignored) {}
                    }
                    case "tpbase" -> {
                        List<String> list = new ArrayList<>(getExistingTeams().stream()
                                .map(TeamColor::getFormattedName)
                                .toList());
                        list.add("@a");
                        return list.stream()
                                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                                .toList();
                    }
                }
            }
            if (args[0].equalsIgnoreCase("points") && args[1].equalsIgnoreCase("set")) {
                return null; // number input
            }
        }

        if (args.length == 5) {
            if (args[0].equalsIgnoreCase("teams")) {
                if (args[1].equalsIgnoreCase("options")) {
                    if (args[2].equalsIgnoreCase("allow")) {
                        return Stream.of("true", "false")
                                .filter(s -> s.startsWith(args[4].toLowerCase()))
                                .toList();
                    }
                    try {
                        TeamColor.valueOf(args[2].toUpperCase());
                        if (args[3].equalsIgnoreCase("maxmembers")) {
                            return null; // number input
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        return Collections.emptyList();
    }

    // ========== Helpers for Tab Complete ==========

    private List<TeamColor> getExistingTeams() {
        List<TeamColor> list = new ArrayList<>();
        for (TeamColor c : TeamColor.values()) {
            if (teamManager.getTeam(c) != null) {
                list.add(c);
            }
        }
        return list;
    }

    private List<TeamColor> getNonExistingTeams() {
        List<TeamColor> list = new ArrayList<>();
        for (TeamColor c : TeamColor.values()) {
            if (teamManager.getTeam(c) == null) {
                list.add(c);
            }
        }
        return list;
    }
}
