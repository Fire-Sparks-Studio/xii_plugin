package com.mceteams.xii.listener;

import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.PointValue;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PointEvent;
import com.mceteams.xii.service.PointService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashSet;
import java.util.Set;

public class MiningListener implements Listener {
    private final Set<String> placedBlocks = new HashSet<>();
    private final PointService pointService;
    private final TeamManager teamManager;

    public MiningListener(PointService pointService, TeamManager teamManager) {

        this.pointService = pointService;
        this.teamManager = teamManager;
    }

    private PointValue getPointValue(Material material) {
        return switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> PointValue.MINING_DIAMOND;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> PointValue.MINING_EMERALD;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> PointValue.MINING_GOLD;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> PointValue.MINING_IRON;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> PointValue.MINING_LAPIS;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> PointValue.MINING_REDSTONE;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> PointValue.MINING_COAL;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> PointValue.MINING_COPPER;
            default -> null;
        };
    }

    @EventHandler
    public void BlockBreakEvent(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        GameTeam team = teamManager.getTeam(player.getUniqueId());

        if (team == null) return;

        if (placedBlocks.contains(locationToString(event.getBlock().getLocation()))) {
            placedBlocks.remove(locationToString(event.getBlock().getLocation()));
            return;
        }

        PointValue pointValue = getPointValue(material);
        if (pointValue != null) {
            pointService.addPoints(new PointEvent(player.getUniqueId(), team, PointCategory.MINING, pointValue.getValue(), pointValue.getSource(), null));
        }
    }

    public void addPlacedBlock(Location location) {
        placedBlocks.add(locationToString(location));
    }

    private String locationToString(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

}
