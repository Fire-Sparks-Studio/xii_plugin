package com.mceteams.xii.commands;

import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerScore;
import com.mceteams.xii.model.TeamScore;
import com.mceteams.xii.service.PointService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class XCommands implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private PointService pointService;

    public XCommands(TeamManager teamManager, GameManager gameManager) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("xii.play")) {
            player.sendMessage("§cTu n'as pas la permission !");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /xii <commande>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            // Publique
            case "join" -> handleJoin(player, args);
            case "leave" -> handleLeave(player);

            // Admin
            case "allowjoin" -> handleAllowJoin(player, args);
            case "allowleave" -> handleAllowLeave(player, args);
            case "createteam" -> handleCreateTeam(player, args);
            case "deleteteam" -> handleDeleteTeam(player, args);
            case "addmember" -> handleAddMember(player, args);
            case "removemember" -> handleRemoveMember(player, args);
            case "setspawn" -> handleSetSpawn(player, args);
            case "setheart" -> handleSetHeart(player, args);
            case "start" -> handleStart(player);
            case "stop" -> handleStop(player);
            case "setday" -> handleSetDay(player, args);
            case "tpbase" -> handleTpBase(player, args);
            case "destroyheart" -> handleDestroyHeart(player, args);
            case "revive" -> handleRevive(player, args);
            case "restoreheart" -> handleRestoreHeart(player, args);
            case "eliminate" -> handleEliminate(player, args);
            case "blacklist" -> handleBlacklist(player, args);
            case "unblacklist" -> handleUnblacklist(player, args);
            case "give" -> handleGive(player, args);
            case "setpoints" -> handleSetPoints(player, args);
            case "resetpoints" -> handleResetPoints(player, args);
            case "maxmembers" -> handleMaxMembers(player, args);

            // Autre
            default -> player.sendMessage("§cCommande inconnue: " + args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xii.play")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("join", "leave"));
            if (sender.hasPermission("xii.admin")) {
                completions.addAll(Arrays.asList(
                        "allowjoin", "allowleave", "createteam", "deleteteam",
                        "addmember", "removemember", "setspawn", "setheart",
                        "start", "stop", "setday", "tpbase", "destroyheart",
                        "revive", "restoreheart", "eliminate", "blacklist",
                        "unblacklist", "give", "setpoints", "resetpoints", "maxmembers"
                ));
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "join", "createteam", "deleteteam", "setspawn", "setheart",
                     "tpbase", "destroyheart", "restoreheart", "eliminate" -> {
                    return Arrays.stream(TeamColor.values())
                            .map(c -> c.name())
                            .filter(s -> s.startsWith(args[1].toUpperCase()))
                            .toList();
                }
                case "addmember", "removemember", "revive", "give", "setpoints", "resetpoints" -> {
                    return null; // retourne les joueurs connectés automatiquement
                }
                case "allowjoin", "allowleave" -> {
                    return Arrays.asList("true", "false");
                }
                case "setday" -> {
                    return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
                }
                case "maxmembers" -> {
                    return Arrays.stream(TeamColor.values())
                            .map(c -> c.name())
                            .filter(s -> s.startsWith(args[1].toUpperCase()))
                            .toList();
                }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "addmember" -> {
                    return Arrays.stream(TeamColor.values())
                            .map(c -> c.name())
                            .filter(s -> s.startsWith(args[2].toUpperCase()))
                            .toList();
                }
                case "give" -> {
                    return Arrays.stream(Material.values())
                            .map(m -> m.name())
                            .filter(s -> s.startsWith(args[2].toUpperCase()))
                            .limit(20)
                            .toList();
                }
            }
        }

        return Collections.emptyList();
    }

    // Publique

    private void handleJoin(Player player, String[] args) {
        if (!gameManager.isJoinEnabled()) {
            player.sendMessage("§cLes teams sont fermées !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii join <couleur>");
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

        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }

        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            team = teamManager.createTeam(color);
        }

        if (teamManager.addPlayer(player.getUniqueId(), team)) {
            player.sendMessage("§aTu as rejoint l'équipe " + color.getName(Lang.FR) + " !");
        } else {
            player.sendMessage("§cL'équipe est complète !");
        }
    }

    private void handleLeave(Player player) {
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

    // Admin

    private void handleAllowJoin(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii allowjoin <true|false>");
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        gameManager.setJoinEnabled(enabled);
        player.sendMessage("§aJoin " + (enabled ? "activé" : "désactivé") + " !");
    }

    private void handleAllowLeave(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii allowleave <true|false>");
            return;
        }

        boolean enabled = Boolean.parseBoolean(args[1]);
        gameManager.setLeaveEnabled(enabled);
        player.sendMessage("§aLeave " + (enabled ? "activé" : "désactivé") + " !");
    }

    private void handleCreateTeam(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii createteam <COULEUR>");
            return;
        }

        String colorName = args[1].toUpperCase();
        TeamColor color;

        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }

        if (teamManager.getTeam(color) != null) {
            player.sendMessage("§cCette équipe existe déjà !");
            return;
        }

        teamManager.createTeam(color);
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " créée !");
    }

    private void handleDeleteTeam(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii deleteteam <COULEUR>");
            return;
        }

        String colorName = args[1].toUpperCase();
        TeamColor color;

        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }

        GameTeam team = teamManager.getTeam(color);

        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }

        teamManager.deleteTeam(team);
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " supprimée !");
    }

    private void handleAddMember(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii addmember <joueur> <COULEUR>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        String colorName = args[2].toUpperCase();
        TeamColor color;

        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }

        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            team = teamManager.createTeam(color);
        }

        if (teamManager.addPlayer(target.getUniqueId(), team)) {
            player.sendMessage("§a" + target.getName() + " ajouté à l'équipe " + color.getName(Lang.FR) + " !");
        } else {
            player.sendMessage("§cL'équipe est complète !");
        }
    }

    private void handleRemoveMember(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii removemember <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        teamManager.removePlayer(target.getUniqueId());
        player.sendMessage("§a" + target.getName() + " retiré de son équipe !");
    }

    private void handleSetSpawn(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }

        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§cTu n'es dans aucune équipe !");
            return;
        }

        team.setSpawn(player.getLocation());
        player.sendMessage("§aSpawn de l'équipe " + team.getColor().getName(Lang.FR) + " défini !");
    }

    private void handleSetHeart(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        GameTeam team = teamManager.getTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§cTu n'es dans aucune équipe !");
            return;
        }
        team.setHeartLocation(player.getLocation());
        player.sendMessage("§aCœur de l'équipe " + team.getColor().getName(Lang.FR) + " défini !");
    }

    private void handleStart(Player player) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
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

    private void handleStop(Player player) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (gameManager.getState() == GameState.WAITING) {
            player.sendMessage("§cLa partie n'a pas commencé !");
            return;
        }
        gameManager.endGame();
        Bukkit.broadcast(Component.text("\n§c§lXII DAYS §7a été arrêté !\n"));
    }

    private void handleSetDay(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii setday <jour>");
            return;
        }
        int day;
        try {
            day = Integer.parseInt(args[1]);
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

    private void handleTpBase(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii tpbase <COULEUR>");
            return;
        }
        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        if (team.getSpawn() == null) {
            player.sendMessage("§cLe spawn de cette équipe n'est pas défini !");
            return;
        }
        for (UUID uuid : team.getPlayers()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                target.teleport(team.getSpawn());
            }
        }
        player.sendMessage("§aJoueurs de l'équipe " + color.getName(Lang.FR) + " téléportés !");
    }

    private void handleDestroyHeart(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii destroyheart <COULEUR>");
            return;
        }
        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        teamManager.destroyHeart(team);
        player.sendMessage("§aCœur de l'équipe " + color.getName(Lang.FR) + " détruit !");
    }

    private void handleRevive(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii revive <joueur|COULEUR>");
            return;
        }
        String target = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(target);
            GameTeam team = teamManager.getTeam(color);
            if (team == null) {
                player.sendMessage("§cCette équipe n'existe pas !");
                return;
            }
            team.setHeartAlive(true);
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setGameMode(GameMode.SURVIVAL);
            }
            player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " réanimée !");
        } catch (IllegalArgumentException e) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                player.sendMessage("§cJoueur introuvable: " + target);
                return;
            }
            targetPlayer.setGameMode(GameMode.SURVIVAL);
            targetPlayer.setHealth(20);
            player.sendMessage("§a" + targetPlayer.getName() + " réanimé !");
        }
    }

    private void handleRestoreHeart(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii restoreheart <COULEUR>");
            return;
        }
        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        team.setHeartAlive(true);
        player.sendMessage("§aCœur de l'équipe " + color.getName(Lang.FR) + " restauré !");
    }

    private void handleEliminate(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii eliminate <COULEUR>");
            return;
        }
        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        team.destroyHeart();
        for (UUID uuid : team.getPlayers()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                target.setGameMode(GameMode.SPECTATOR);
                target.sendMessage("§cVous avez été éliminé !");
            }
        }
        player.sendMessage("§aÉquipe " + color.getName(Lang.FR) + " éliminée !");
    }

    private void handleBlacklist(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii blacklists <ITEM>");
            return;
        }
        Material material = Material.matchMaterial(args[1].toUpperCase());
        if (material == null) {
            player.sendMessage("§cItem inconnu: " + args[1]);
            return;
        }
        gameManager.getBlacklistedItems().add(material);
        player.sendMessage("§a" + material.name() + " blacklisté !");
    }

    private void handleUnblacklist(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii unblacklist <ITEM>");
            return;
        }
        Material material = Material.matchMaterial(args[1].toUpperCase());
        if (material == null) {
            player.sendMessage("§cItem inconnu: " + args[1]);
            return;
        }
        gameManager.getBlacklistedItems().remove(material);
        player.sendMessage("§a" + material.name() + " retiré de la blacklist !");
    }

    private void handleGive(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii give <joueur> <ITEM> [quantité]");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }
        Material material = Material.matchMaterial(args[2].toUpperCase());
        if (material == null) {
            player.sendMessage("§cItem inconnu: " + args[2]);
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cNombre invalide !");
                return;
            }
        }
        target.getInventory().addItem(new ItemStack(material, amount));
        player.sendMessage("§a" + amount + "x " + material.name() + " donné à " + target.getName() + " !");
    }

    private void handleSetPoints(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii setpoints <joueur|COULEUR> <points>");
            return;
        }
        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cNombre invalide !");
            return;
        }
        String target = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(target);
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

    private void handleResetPoints(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /xii resetpoints <joueur|COULEUR>");
            return;
        }
        String target = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(target);
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

    private void handleMaxMembers(Player player, String[] args) {
        if (!player.hasPermission("xii.admin")) {
            player.sendMessage("§cPermission refusée !");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /xii maxmembers <COULEUR> <nombre>");
            return;
        }
        String colorName = args[1].toUpperCase();
        TeamColor color;
        try {
            color = TeamColor.valueOf(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cCouleur inconnue: " + colorName);
            return;
        }
        GameTeam team = teamManager.getTeam(color);
        if (team == null) {
            player.sendMessage("§cCette équipe n'existe pas !");
            return;
        }
        int max;
        try {
            max = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cNombre invalide !");
            return;
        }
        team.setMaxPlayers(max);
        player.sendMessage("§aLimite de l'équipe " + color.getName(Lang.FR) + " mise à " + max + " !");
    }
}