package com.mceteams.xii.manager;

import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.PointValue;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PointEvent;
import com.mceteams.xii.service.PointService;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CombatManager {
    private TeamManager teamManager;
    private PointService pointService;
    private boolean firstBloodTaken;
    private Map<GameTeam, Integer> killStreak;

    public CombatManager(TeamManager teamManager, PointService pointService) {
        this.teamManager = teamManager;
        this.pointService = pointService;
        this.firstBloodTaken = false;
        this.killStreak = new HashMap<>();
    }

    public void onPlayerKill(Player killer, Player victim) {
        GameTeam killerTeam = teamManager.getTeam(killer.getUniqueId());
        GameTeam victimTeam = teamManager.getTeam(victim.getUniqueId());

        if (!this.firstBloodTaken) {
            this.firstBloodTaken = true;
            pointService.addPoints(new PointEvent(killer.getUniqueId(), killerTeam, PointCategory.KILL, PointValue.FIRST_BLOOD.getValue(), PointValue.FIRST_BLOOD.getSource(), null));
        }

        this.killStreak.merge(killerTeam, 1, Integer::sum);
        pointService.addPoints(new PointEvent(killer.getUniqueId(), killerTeam, PointCategory.KILL, PointValue.KILL_PLAYER.getValue(), PointValue.KILL_PLAYER.getSource(), null));
        pointService.addPoints(new PointEvent(killer.getUniqueId(), killerTeam, PointCategory.KILL, (killStreak.get(killerTeam)-1) * PointValue.KILL_STREAK.getValue(), PointValue.KILL_STREAK.getSource(), null));
        this.killStreak.put(victimTeam, 0);
    }
}
