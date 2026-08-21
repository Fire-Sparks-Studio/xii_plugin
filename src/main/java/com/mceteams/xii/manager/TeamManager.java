package com.mceteams.xii.manager;

import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.enums.TeamColor;

import java.util.*;

public class TeamManager {
    private final List<GameTeam> teams = new ArrayList<>();
    private final Map<UUID, GameTeam> teamMap = new HashMap<>();

    public GameTeam createTeam(TeamColor teamColor) {
        GameTeam team = new GameTeam(teams.size(), teamColor);
        teams.add(team);
        return team;
    }

    public boolean addPlayer(UUID uuid, GameTeam team) {
        removePlayer(uuid);
        if (team.getPlayers().size() >= 10) return false;
        team.addPlayer(uuid);
        teamMap.put(uuid, team);
        return true;
    }

    public void removePlayer(UUID player) {
        GameTeam team = teamMap.get(player);
        if (team != null) {
            team.removePlayer(player);
            teamMap.remove(player);
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

    public void deleteTeam(GameTeam team) {
        for (UUID uuid : team.getPlayers()) {
            teamMap.remove(uuid);
        }
        teams.remove(team);
    }


}
