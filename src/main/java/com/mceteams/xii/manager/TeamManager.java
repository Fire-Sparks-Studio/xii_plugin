package com.mceteams.xii.manager;

import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.enums.TeamColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TeamManager {
    private final List<GameTeam> teams = new ArrayList<>();
    private final Map<UUID, GameTeam> teamMap = new HashMap<>();

    private Scoreboard getScoreboard() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    private NamedTextColor toNamedTextColor(ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    private Team getOrCreateVanillaTeam(TeamColor color) {
        Scoreboard scoreboard = getScoreboard();
        String teamName = "xii_" + color.name();
        Team vanillaTeam = scoreboard.getTeam(teamName);
        if (vanillaTeam == null) {
            vanillaTeam = scoreboard.registerNewTeam(teamName);
            vanillaTeam.setAllowFriendlyFire(false);
            vanillaTeam.setCanSeeFriendlyInvisibles(true);
            vanillaTeam.color(toNamedTextColor(color.getChatColor()));
        }
        return vanillaTeam;
    }

    public GameTeam createTeam(TeamColor teamColor) {
        GameTeam team = new GameTeam(teams.size(), teamColor);
        teams.add(team);
        getOrCreateVanillaTeam(teamColor);
        return team;
    }

    public boolean addPlayer(UUID uuid, GameTeam team) {
        removePlayer(uuid);
        if (team.getPlayers().size() >= team.getMaxPlayers()) return false;
        team.addPlayer(uuid);
        teamMap.put(uuid, team);

        // Vanilla team
        Team vanillaTeam = getOrCreateVanillaTeam(team.getColor());
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            vanillaTeam.addPlayer(player);
        } else {
            vanillaTeam.addEntry(uuid.toString());
        }
        return true;
    }

    public void removePlayer(UUID player) {
        GameTeam team = teamMap.get(player);
        if (team != null) {
            team.removePlayer(player);
            teamMap.remove(player);

            // Vanilla team
            Team vanillaTeam = getScoreboard().getTeam("xii_" + team.getColor().name());
            if (vanillaTeam != null) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(player);
                if (p != null) {
                    vanillaTeam.removePlayer(p);
                } else {
                    vanillaTeam.removeEntry(player.toString());
                }
            }
        }
    }

    public List<GameTeam> getAliveTeams() {
        List<GameTeam> alive = new ArrayList<>();
        for (GameTeam team : teams) {
            if (team.isAlive()) {
                alive.add(team);
            }
        }
        return alive;
    }

    public GameTeam getTeam(UUID uuid) {
        return teamMap.get(uuid);
    }

    public GameTeam getTeam(TeamColor color) {
        for (GameTeam team : teams) {
            if (team.getColor() == color) return team;
        }
        return null;
    }

    public List<GameTeam> getTeams() {
        return teams;
    }

    public void destroyHeart(GameTeam team) {
        team.destroyHeart();
    }

    public int getTeamCount() {
        return teams.size();
    }

    public void addPlayerToMap(UUID uuid, GameTeam team) {
        teamMap.put(uuid, team);

        // Vanilla team
        Team vanillaTeam = getOrCreateVanillaTeam(team.getColor());
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            vanillaTeam.addPlayer(player);
        } else {
            vanillaTeam.addEntry(uuid.toString());
        }
    }

    public void deleteTeam(GameTeam team) {
        for (UUID uuid : team.getPlayers()) {
            teamMap.remove(uuid);
        }
        teams.remove(team);

        // Vanilla team
        Team vanillaTeam = getScoreboard().getTeam("xii_" + team.getColor().name());
        if (vanillaTeam != null) {
            vanillaTeam.unregister();
        }
    }

    public void reset() {
        for (GameTeam team : teams) {
            for (UUID uuid : team.getPlayers()) {
                teamMap.remove(uuid);
            }
        }
        teams.clear();

        // Clear all vanilla teams
        Scoreboard scoreboard = getScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("xii_")) {
                team.unregister();
            }
        }
    }

}
