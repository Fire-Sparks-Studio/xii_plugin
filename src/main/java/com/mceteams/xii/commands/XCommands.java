package com.mceteams.xii.commands;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.SetupManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerScore;
import com.mceteams.xii.model.TeamScore;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.SoundService;
import com.mceteams.xii.service.TeamAdminService;
import com.mceteams.xii.ui.AdminGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Stream;

public class XCommands implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final SetupManager setupManager;
    private final PointService pointService;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;
    private final TeamAdminService teamAdminService;

    public XCommands(TeamManager teamManager, GameManager gameManager, PointService pointService, SetupManager setupManager, PlayerDataManager playerDataManager, SoundService soundService, TeamAdminService teamAdminService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.pointService = pointService;
        this.setupManager = setupManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
        this.teamAdminService = teamAdminService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.NOT_PLAYER.get(Lang.FR));
            return true;
        }
        Lang lang = playerDataManager.getLang(player);

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("join")) {
            handleJoin(player, args, lang);
            return true;
        }

        if (cmd.equals("leave")) {
            handleLeave(player, lang);
            return true;
        }

        if (cmd.equals("admin")) {
            if (!player.hasPermission("xii.admin")) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.NO_PERMISSION.get(lang));
                return true;
            }
            if (!setupManager.isSetup()) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.SETUP_REQUIRED.get(lang));
                return true;
            }
            soundService.play(player, GameSound.CLICK);
            AdminGUI adminGUI = new AdminGUI(teamManager, gameManager, playerDataManager);
            player.openInventory(adminGUI.create(player));
            return true;
        }

        if (!cmd.equals("xii")) return true;

        if (args.length == 0) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii <commande>"));
            return true;
        }

        boolean isSetupCommand = args[0].equalsIgnoreCase("admin") && args.length >= 2 && args[1].equalsIgnoreCase("setup");

        if (gameManager.getState() == GameState.NON_SETUP && !isSetupCommand) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.SETUP_REQUIRED.get(lang));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "teams" -> handleTeams(player, args, lang);
            case "day" -> handleDay(player, args, lang);
            case "allow" -> handleAllow(player, args, lang);
            case "points" -> handlePoints(player, args, lang);
            case "admin" -> handleAdmin(player, args, lang);
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.UNKNOWN_COMMAND.get(lang, args[0]));
            }
        }
        return true;
    }

    // ========== /join /leave ==========

    private void handleJoin(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.play")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }
        if (!setupManager.isSetup()) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.SETUP_REQUIRED.get(lang));
            return;
        }
        if (!gameManager.isJoinEnabled()) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_TEAM_CLOSED.get(lang));
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_GAME_STARTED.get(lang));
            return;
        }
        if (teamManager.getTeam(player.getUniqueId()) != null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_ALREADY_IN_TEAM.get(lang));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(Messages.USAGE.get(lang, "/join <couleur>"));
            return;
        }
        TeamColor color;
        try {
            color = TeamColor.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_UNKNOWN_COLOR.get(lang, args[0]));
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_TEAM_NOT_EXIST.get(lang));
            return;
        }
        teamAdminService.joinTeam(player, team);
    }

    private void handleLeave(Player player, Lang lang) {
        if (!player.hasPermission("xii.play")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }
        if (!setupManager.isSetup()) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.SETUP_REQUIRED.get(lang));
            return;
        }
        if (!gameManager.isLeaveEnabled()) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.LEAVE_DISABLED.get(lang));
            return;
        }
        if (gameManager.getState() != GameState.WAITING) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.LEAVE_GAME_STARTED.get(lang));
            return;
        }
        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.LEAVE_NOT_IN_TEAM.get(lang));
            return;
        }
        teamAdminService.leaveTeam(player);
    }

    // ========== /xii teams ==========

    private void handleTeams(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.admin")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams <create|delete|add|remove|heart|eliminate|revive|tpbase|options>"));
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "create" -> handleTeamsCreate(player, args, lang);
            case "delete" -> handleTeamsDelete(player, args, lang);
            case "add" -> handleTeamsAdd(player, args, lang);
            case "remove" -> handleTeamsRemove(player, args, lang);
            case "heart" -> handleTeamsHeart(player, args, lang);
            case "eliminate" -> handleTeamsEliminate(player, args, lang);
            case "revive" -> handleTeamsRevive(player, args, lang);
            case "tpbase" -> handleTeamsTpBase(player, args, lang);
            case "options" -> handleTeamsOptions(player, args, lang);
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.TEAMS_UNKNOWN_SUB.get(lang, sub));
            }
        }
    }

    private void handleTeamsCreate(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams create <couleur>"));
            return;
        }
        TeamColor color;
        try {
            color = TeamColor.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_UNKNOWN_COLOR.get(lang, args[2]));
            return;
        }
        teamAdminService.createTeam(player, color);
    }

    private void handleTeamsDelete(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams delete <couleur>"));
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2], lang);
        if (color == null) return;
        teamAdminService.deleteTeam(player, teamManager.getTeam(color));
    }

    private void handleTeamsAdd(Player player, String[] args, Lang lang) {
        if (args.length < 4) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams add <joueur> <couleur>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.PLAYER_NOT_FOUND.get(lang, args[2]));
            return;
        }

        TeamColor color = getExistingTeamColor(player, args[3], lang);
        if (color == null) return;

        teamAdminService.addPlayer(player, target, teamManager.getTeam(color));
    }

    private void handleTeamsRemove(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams remove <joueur>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.PLAYER_NOT_FOUND.get(lang, args[2]));
            return;
        }

        teamAdminService.removePlayer(player, target);
    }

    private void handleTeamsHeart(Player player, String[] args, Lang lang) {
        if (args.length < 4) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams heart <couleur> <destroy|restore>"));
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2], lang);
        if (color == null) return;
        GameTeam team = teamManager.getTeam(color);

        String action = args[3].toLowerCase();
        switch (action) {
            case "destroy" -> teamAdminService.destroyHeart(player, team);
            case "restore" -> teamAdminService.restoreHeart(player, team);
            default -> player.sendMessage(Messages.USAGE.get(lang, "/xii teams heart <couleur> <destroy|restore>"));
        }
    }

    private void handleTeamsEliminate(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams eliminate <couleur>"));
            return;
        }

        TeamColor color = getExistingTeamColor(player, args[2], lang);
        if (color == null) return;

        teamAdminService.eliminateTeam(player, teamManager.getTeam(color));
    }

    private void handleTeamsRevive(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams revive <couleur>"));
            return;
        }
        TeamColor color = getExistingTeamColor(player, args[2], lang);
        if (color == null) return;

        teamAdminService.reviveTeam(player, teamManager.getTeam(color));
    }

    private void handleTeamsTpBase(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams tpbase <couleur|@a|joueur>"));
            return;
        }

        String target = args[2];

        if (target.equals("@a")) {
            teamAdminService.tpBaseAll(player);
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            teamAdminService.tpBasePlayer(player, targetPlayer);
            return;
        }

        TeamColor color = getExistingTeamColor(player, target, lang);
        if (color == null) return;

        teamAdminService.tpBaseTeam(player, teamManager.getTeam(color));
    }

    private void handleTeamsOptions(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams options <allow|<couleur>>"));
            return;
        }

        if (args[2].equalsIgnoreCase("allow")) {
            if (args.length < 5) {
                player.sendMessage(Messages.OPTIONS_ALLOW_USAGE.get(lang));
                return;
            }
            String what = args[3].toLowerCase();
            boolean value = Boolean.parseBoolean(args[4]);
            switch (what) {
                case "join" -> teamAdminService.toggleJoin(player, value);
                case "leave" -> teamAdminService.toggleLeave(player, value);
                default -> player.sendMessage(Messages.OPTIONS_ALLOW_USAGE.get(lang));
            }
            return;
        }

        TeamColor color = getExistingTeamColor(player, args[2], lang);
        if (color == null) return;

        if (args.length < 4) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii teams options " + color.getFormattedName() + " maxmembers"));
            return;
        }

        String option = args[3].toLowerCase();
        switch (option) {
            case "maxmembers" -> {
                if (args.length < 5) {
                    player.sendMessage(Messages.OPTIONS_MAX_MEMBERS_USAGE.get(lang, color.getFormattedName()));
                    return;
                }
                int max;
                try {
                    max = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    soundService.play(player, GameSound.ERROR);
                    player.sendMessage(Messages.INVALID_NUMBER.get(lang));
                    return;
                }
                teamAdminService.setMaxMembers(player, teamManager.getTeam(color), max);
            }
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.TEAM_OPTIONS_UNKNOWN.get(lang, option));
            }
        }
    }

    // ========== /xii day ==========

    private void handleDay(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.admin")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii day <start|stop|set>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> teamAdminService.startGame(player);
            case "stop" -> teamAdminService.stopGame(player);
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(Messages.USAGE.get(lang, "/xii day set <1-12>"));
                    return;
                }
                int day;
                try {
                    day = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    soundService.play(player, GameSound.ERROR);
                    player.sendMessage(Messages.INVALID_NUMBER.get(lang));
                    return;
                }
                teamAdminService.setDay(player, day);
            }
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.TEAMS_UNKNOWN_SUB.get(lang, args[1]));
            }
        }
    }

    // ========== /xii allow ==========

    private void handleAllow(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.admin")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii allow <item|block> <true|false>"));
            return;
        }
        String what = args[1].toLowerCase();
        boolean allow = Boolean.parseBoolean(args[2]);
        switch (what) {
            case "item" -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    soundService.play(player, GameSound.ERROR);
                    player.sendMessage(Messages.ALLOW_NO_ITEM.get(lang));
                    return;
                }
                if (allow) {
                    gameManager.getRestrictionManager().whitelistItem(hand.getType());
                    soundService.play(player, GameSound.SUCCESS);
                    player.sendMessage(Messages.ALLOW_ITEM_REMOVED.get(lang, hand.getType().name()));
                } else {
                    gameManager.getRestrictionManager().blacklistItem(hand.getType());
                    soundService.play(player, GameSound.SUCCESS_HIGH);
                    player.sendMessage(Messages.ALLOW_ITEM_ADDED.get(lang, hand.getType().name()));
                }
            }
            case "block" -> {
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                if (target == null || target.getType() == Material.AIR) {
                    soundService.play(player, GameSound.ERROR);
                    player.sendMessage(Messages.ALLOW_NO_BLOCK.get(lang));
                    return;
                }
                if (allow) {
                    gameManager.getRestrictionManager().whitelistItem(target.getType());
                    soundService.play(player, GameSound.SUCCESS);
                    player.sendMessage(Messages.ALLOW_BLOCK_REMOVED.get(lang, target.getType().name()));
                } else {
                    gameManager.getRestrictionManager().blacklistItem(target.getType());
                    soundService.play(player, GameSound.SUCCESS_HIGH);
                    player.sendMessage(Messages.ALLOW_BLOCK_ADDED.get(lang, target.getType().name()));
                }
            }
            default -> player.sendMessage(Messages.USAGE.get(lang, "/xii allow <item|block> <true|false>"));
        }
    }

    // ========== /xii points ==========

    private void handlePoints(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.admin")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Messages.POINTS_USAGE.get(lang));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "set" -> handlePointsSet(player, args, lang);
            case "reset" -> handlePointsReset(player, args, lang);
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.POINTS_UNKNOWN_SUB.get(lang, args[1]));
            }
        }
    }

    private void handlePointsSet(Player player, String[] args, Lang lang) {
        if (args.length < 4) {
            player.sendMessage(Messages.POINTS_SET_USAGE.get(lang));
            return;
        }
        int points;
        try {
            points = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.INVALID_NUMBER.get(lang));
            return;
        }
        String target = args[2];
        TeamColor color;
        try {
            color = TeamColor.valueOf(target.toUpperCase());
            GameTeam team = teamManager.getTeam(color);
            if (team == null) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.JOIN_TEAM_NOT_EXIST.get(lang));
                return;
            }
            TeamScore score = pointService.getTeamScore(team);
            score.setTotal(points);
            soundService.play(player, GameSound.SUCCESS);
            player.sendMessage(Messages.POINTS_TEAM_SET.get(lang, color.getName(lang), points));
        } catch (IllegalArgumentException e) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.PLAYER_NOT_FOUND.get(lang, target));
                return;
            }
            PlayerScore score = pointService.getPlayerScore(targetPlayer.getUniqueId());
            score.setTotal(points);
            soundService.play(player, GameSound.SUCCESS);
            player.sendMessage(Messages.POINTS_PLAYER_SET.get(lang, targetPlayer.getName(), points));
        }
    }

    private void handlePointsReset(Player player, String[] args, Lang lang) {
        if (args.length < 3) {
            player.sendMessage(Messages.POINTS_RESET_USAGE.get(lang));
            return;
        }
        String target = args[2];
        TeamColor color;
        try {
            color = TeamColor.valueOf(target.toUpperCase());
            GameTeam team = teamManager.getTeam(color);

            if (team == null) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.JOIN_TEAM_NOT_EXIST.get(lang));
                return;
            }

            pointService.getTeamScore(team).reset();
            soundService.play(player, GameSound.SUCCESS_HIGH);
            player.sendMessage(Messages.POINTS_TEAM_RESET.get(lang, color.getName(lang)));
        } catch (IllegalArgumentException e) {
            Player targetPlayer = Bukkit.getPlayer(target);

            if (targetPlayer == null) {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.PLAYER_NOT_FOUND.get(lang, target));
                return;
            }

            pointService.getPlayerScore(targetPlayer.getUniqueId()).reset();
            soundService.play(player, GameSound.SUCCESS_HIGH);
            player.sendMessage(Messages.POINTS_PLAYER_RESET.get(lang, targetPlayer.getName()));
        }
    }

    // ========== /xii admin ==========

    private void handleAdmin(Player player, String[] args, Lang lang) {
        if (!player.hasPermission("xii.admin")) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.NO_PERMISSION.get(lang));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.USAGE.get(lang, "/xii admin <setup|stop>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "setup" -> {
                JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("XII-Days");
                setupManager.setup(plugin, player);
            }
            case "stop" -> {
                if (!setupManager.isSetup()) {
                    soundService.play(player, GameSound.ERROR);
                    player.sendMessage(Messages.ADMIN_STOP_NO_SETUP.get(lang));
                    return;
                }
                JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("XII-Days");
                setupManager.quit(plugin);
                soundService.playToAll(GameSound.SETUP_STOP);
                Bukkit.broadcast(Component.text(Messages.ADMIN_STOP.get(lang)));
            }
            default -> {
                soundService.play(player, GameSound.ERROR);
                player.sendMessage(Messages.TEAMS_UNKNOWN_SUB.get(lang, args[1]));
            }
        }
    }

    // ========== Helpers ==========

    private TeamColor getExistingTeamColor(Player player, String input, Lang lang) {
        TeamColor color;
        try {
            color = TeamColor.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_UNKNOWN_COLOR.get(lang, input));
            return null;
        }
        if (teamManager.getTeam(color) == null) {
            soundService.play(player, GameSound.ERROR);
            player.sendMessage(Messages.JOIN_TEAM_NOT_EXIST.get(lang));
            return null;
        }
        return color;
    }

    // ========== Tab Complete ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("join")) {
            if (args.length == 1) {
                return getExistingTeams().stream()
                        .map(TeamColor::getFormattedName)
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
                    return Stream.of("setup", "stop")
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
                        return null;
                    }
                    case "remove" -> {
                        return null;
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
                return null;
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
                        try {
                            TeamColor.valueOf(args[2].toUpperCase());
                            return Stream.of("maxmembers")
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
                return null;
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
                            return null;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        return Collections.emptyList();
    }

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
