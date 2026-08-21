package com.mceteams.xii.manager;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.model.GameTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

public class HeartManager {
    private TeamManager teamManager;

    public HeartManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void destroyHeart(Player destroyer, GameTeam team) {
        team.destroyHeart();

        Bukkit.broadcast(Component.text("\n§4§lCOEUR DÉTRUIT > §c§l" + team.getColor().getName(Lang.FR) + " §7Cœur détruit par §7" + destroyer.getName() + "§r!\n"));
        for (UUID uuid : team.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(Title.title(
                        Component.text("§c§lCOEUR DÉTRUIT !"),
                        Component.text("§eVous ne respawnerez plus !"),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
                ));
            }
        }
    }

    public boolean isHeartAlive(GameTeam team) {
        return team.isHeartAlive();
    }
}
