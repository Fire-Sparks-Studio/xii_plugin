package com.mceteams.xii.service;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PlayerDataManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TeamAdminService {
    private final TeamManager teamManager;
    private final GameManager gameManager;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;

    public TeamAdminService(TeamManager teamManager, GameManager gameManager, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.gameManager = gameManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public enum Result { SUCCESS, ALREADY_EXISTS, NOT_FOUND, FULL, INVALID, PERMISSION }

    // ========== Team CRUD ==========

    public Result createTeam(Player actor, TeamColor color) {
        if (teamManager.getTeam(color) != null) return Result.ALREADY_EXISTS;
        teamManager.createTeam(color);
        soundService.play(actor, GameSound.TEAM_CREATED);
        actor.sendMessage(Messages.TEAM_CREATED.get(getLang(actor), color.getName(getLang(actor))));
        return Result.SUCCESS;
    }

    public Result deleteTeam(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        String teamColored = team.getColor().getColorCode() + team.getColor().getName(lang);
        teamManager.deleteTeam(team);
        soundService.play(actor, GameSound.TEAM_DELETED);
        actor.sendMessage(Messages.TEAM_DELETED.get(lang, teamColored));
        return Result.SUCCESS;
    }

    // ========== Heart ==========

    public Result destroyHeart(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        String teamColored = team.getColor().getColorCode() + team.getColor().getName(lang);
        teamManager.destroyHeart(team);
        soundService.play(actor, GameSound.HEART_DESTROYED);
        Bukkit.broadcast(Component.text(Messages.HEART_DESTROYED.get(lang, teamColored)));
        return Result.SUCCESS;
    }

    public Result restoreHeart(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        String teamColored = team.getColor().getColorCode() + team.getColor().getName(lang);
        team.setHeartAlive(true);
        soundService.play(actor, GameSound.HEART_RESTORED);
        Bukkit.broadcast(Component.text(Messages.HEART_RESTORED.get(lang, teamColored)));
        return Result.SUCCESS;
    }

    // ========== Eliminate / Revive ==========

    public Result eliminateTeam(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        team.destroyHeart();
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(Messages.ELIMINATED_VICTIM.get(playerDataManager.getLang(p)));
            }
        }
        soundService.play(actor, GameSound.SUCCESS);
        Bukkit.broadcast(Component.text(Messages.ELIMINATED_TEAM.get(lang, team.getColor().getColorCode() + team.getColor().getName(lang))));
        return Result.SUCCESS;
    }

    public Result reviveTeam(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        String teamColored = team.getColor().getColorCode() + team.getColor().getName(lang);
        team.setHeartAlive(true);
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SURVIVAL);
                soundService.play(p, GameSound.PLAYER_REVIVED);
            }
        }
        soundService.play(actor, GameSound.TEAM_REVIVED);
        Bukkit.broadcast(Component.text(Messages.TEAM_REVIVED.get(lang, teamColored)));
        return Result.SUCCESS;
    }

    // ========== Player Management ==========

    public Result addPlayer(Player actor, Player target, GameTeam team) {
        Lang lang = getLang(actor);
        if (teamManager.addPlayer(target.getUniqueId(), team)) {
            soundService.play(actor, GameSound.PLAYER_ADDED);
            actor.sendMessage(Messages.TEAM_PLAYER_ADDED.get(lang, target.getName(), team.getColor().getName(lang)));
            soundService.play(target, GameSound.TEAM_JOIN);
            target.sendMessage(Messages.JOIN_SUCCESS.get(lang, team.getColor().getName(lang)));
            return Result.SUCCESS;
        } else {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.JOIN_TEAM_FULL.get(lang));
            return Result.FULL;
        }
    }

    public Result removePlayer(Player actor, Player target) {
        Lang lang = getLang(actor);
        teamManager.removePlayer(target.getUniqueId());
        soundService.play(actor, GameSound.PLAYER_REMOVED);
        actor.sendMessage(Messages.TEAM_PLAYER_REMOVED.get(lang, target.getName()));
        soundService.play(target, GameSound.TEAM_LEAVE);
        target.sendMessage(Messages.LEAVE_SUCCESS.get(lang));
        return Result.SUCCESS;
    }

    // ========== Max Members ==========

    public Result setMaxMembers(Player actor, GameTeam team, int max) {
        Lang lang = getLang(actor);
        team.setMaxPlayers(max);
        soundService.play(actor, GameSound.SUCCESS);
        actor.sendMessage(Messages.OPTIONS_LIMIT_SET.get(lang, team.getColor().getName(lang), max));
        return Result.SUCCESS;
    }

    // ========== TP Base ==========

    public Result tpBaseTeam(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        if (team.getSpawn() == null) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.TPBASE_SPAWN_NOT_SET.get(lang));
            return Result.NOT_FOUND;
        }
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                soundService.play(p, GameSound.TELEPORT);
                p.teleport(team.getSpawn());
            }
        }
        soundService.play(actor, GameSound.SUCCESS);
        actor.sendMessage(Messages.TPBASE_TEAM_DONE.get(lang, team.getColor().getName(lang)));
        return Result.SUCCESS;
    }

    public Result tpBasePlayer(Player actor, Player target) {
        Lang lang = getLang(actor);
        GameTeam team = teamManager.getTeam(target.getUniqueId());
        if (team == null) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.TPBASE_PLAYER_NO_TEAM.get(lang, target.getName()));
            return Result.NOT_FOUND;
        }
        if (team.getSpawn() == null) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.TPBASE_SPAWN_NOT_SET.get(lang));
            return Result.NOT_FOUND;
        }
        soundService.play(target, GameSound.TELEPORT);
        target.teleport(team.getSpawn());
        soundService.play(actor, GameSound.SUCCESS);
        actor.sendMessage(Messages.TPBASE_PLAYER_DONE.get(lang, target.getName()));
        return Result.SUCCESS;
    }

    public Result tpBaseAll(Player actor) {
        Lang lang = getLang(actor);
        for (Player online : Bukkit.getOnlinePlayers()) {
            GameTeam team = teamManager.getTeam(online.getUniqueId());
            if (team != null && team.getSpawn() != null) {
                soundService.play(online, GameSound.TELEPORT);
                online.teleport(team.getSpawn());
            }
        }
        soundService.play(actor, GameSound.SUCCESS);
        actor.sendMessage(Messages.TPBASE_ALL_DONE.get(lang));
        return Result.SUCCESS;
    }

    // ========== Join / Leave ==========

    public Result joinTeam(Player actor, GameTeam team) {
        Lang lang = getLang(actor);
        if (teamManager.addPlayer(actor.getUniqueId(), team)) {
            soundService.play(actor, GameSound.TEAM_JOIN);
            actor.sendMessage(Messages.JOIN_SUCCESS.get(lang, team.getColor().getName(lang)));
            return Result.SUCCESS;
        } else {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.JOIN_TEAM_FULL.get(lang));
            return Result.FULL;
        }
    }

    public Result leaveTeam(Player actor) {
        Lang lang = getLang(actor);
        teamManager.removePlayer(actor.getUniqueId());
        soundService.play(actor, GameSound.TEAM_LEAVE);
        actor.sendMessage(Messages.LEAVE_SUCCESS.get(lang));
        return Result.SUCCESS;
    }

    // ========== Game Control ==========

    public Result startGame(Player actor) {
        Lang lang = getLang(actor);
        if (gameManager.getState() != GameState.WAITING) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.DAY_GAME_STARTED.get(lang));
            return Result.INVALID;
        }
        if (teamManager.getTeamCount() < 2) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.DAY_MIN_TEAMS.get(lang));
            return Result.INVALID;
        }
        gameManager.startGame();
        soundService.playToAll(GameSound.GAME_START);
        Bukkit.broadcast(Component.text(Messages.GAME_STARTED.get(lang)));
        return Result.SUCCESS;
    }

    public Result stopGame(Player actor) {
        Lang lang = getLang(actor);
        if (gameManager.getState() == GameState.WAITING) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.DAY_GAME_STOPPED.get(lang));
            return Result.INVALID;
        }
        gameManager.endGame();
        soundService.playToAll(GameSound.GAME_STOP);
        Bukkit.broadcast(Component.text(Messages.GAME_STOPPED.get(lang)));
        return Result.SUCCESS;
    }

    public Result setDay(Player actor, int day) {
        Lang lang = getLang(actor);
        if (day < 1 || day > 12) {
            soundService.play(actor, GameSound.ERROR);
            actor.sendMessage(Messages.DAY_INVALID.get(lang));
            return Result.INVALID;
        }
        gameManager.getDayManager().setDay(day);
        soundService.playToAll(GameSound.DAY_CHANGE);
        Bukkit.broadcast(Component.text(Messages.DAY_ANNOUNCE.get(lang, day)));
        return Result.SUCCESS;
    }

    // ========== Allow Toggle ==========

    public Result toggleJoin(Player actor, boolean value) {
        gameManager.setJoinEnabled(value);
        soundService.play(actor, GameSound.CHANGE);
        actor.sendMessage(Messages.OPTIONS_ALLOW_TOGGLED.get(getLang(actor)));
        return Result.SUCCESS;
    }

    public Result toggleLeave(Player actor, boolean value) {
        gameManager.setLeaveEnabled(value);
        soundService.play(actor, GameSound.CHANGE);
        actor.sendMessage(Messages.OPTIONS_LEAVE_TOGGLED.get(getLang(actor)));
        return Result.SUCCESS;
    }

    // ========== Helpers ==========

    private Lang getLang(Player player) {
        return playerDataManager.getLang(player);
    }
}
