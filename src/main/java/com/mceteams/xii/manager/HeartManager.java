package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameSound;
import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.Messages;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.service.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

public class HeartManager {
    private final TeamManager teamManager;
    private final PlayerDataManager playerDataManager;
    private final SoundService soundService;

    public HeartManager(TeamManager teamManager, PlayerDataManager playerDataManager, SoundService soundService) {
        this.teamManager = teamManager;
        this.playerDataManager = playerDataManager;
        this.soundService = soundService;
    }

    public void destroyHeart(Player destroyer, GameTeam team) {
        team.destroyHeart();

        Lang destroyerLang = playerDataManager.getLang(destroyer);
        String teamColored = team.getColor().getColorCode() + team.getColor().getName(destroyerLang);
        String destroyerColored = destroyer.getName();
        GameTeam destroyerTeam = teamManager.getTeam(destroyer.getUniqueId());
        if (destroyerTeam != null) {
            destroyerColored = destroyerTeam.getColor().getColorCode() + destroyer.getName();
        }
        Bukkit.broadcast(Component.text(Messages.HEART_BROADCAST.get(destroyerLang, teamColored, destroyerColored)));

        for (UUID uuid : team.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Lang memberLang = playerDataManager.getLang(player);
                soundService.play(player, GameSound.HEART_DESTROYED);
                player.showTitle(Title.title(
                        Component.text(Messages.HEART_TITLE.get(memberLang)),
                        Component.text(Messages.HEART_SUBTITLE.get(memberLang)),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
                ));
            }
        }
    }

    public boolean isHeartAlive(GameTeam team) {
        return team.isHeartAlive();
    }
}
