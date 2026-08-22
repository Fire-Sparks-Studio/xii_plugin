package com.mceteams.xii.service;

import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerScore;
import com.mceteams.xii.model.PointEvent;
import com.mceteams.xii.model.TeamScore;

import java.util.*;

public class PointService {
    private Map<UUID, PlayerScore> playerScores = new HashMap<UUID, PlayerScore>();
    private Map<GameTeam, TeamScore> teamScores = new HashMap<>();
    private List<PointEvent> history = new ArrayList<>();

    public PlayerScore getPlayerScore(UUID uuid) {
        return playerScores.computeIfAbsent(uuid, PlayerScore::new);
    }

    public TeamScore getTeamScore(GameTeam team) {
        return teamScores.computeIfAbsent(team, TeamScore::new);
    }

    public void addPoints(PointEvent event) {
        PlayerScore playerScore = getPlayerScore(event.getPlayer());
        playerScore.addPoints(event.getCategory(), event.getAmount());

        TeamScore teamScore = getTeamScore(event.getTeam());
        teamScore.addPoints(event.getCategory(), event.getAmount());

        history.add(event);
    }

    public void reset() {
        playerScores.clear();
        teamScores.clear();
        history.clear();
    }

    public Collection<PlayerScore> getAllPlayerScores() {
        return playerScores.values();
    }
}
