package com.mceteams.xii.manager;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class BasePlacementManager {
    private static final int CENTER_X = 0;
    private static final int CENTER_Z = 0;
    private static final int DUNGEON_DISTANCE = 500;

    private static final double MIN_DISTANCE_FROM_CENTER = 80.0;
    private static final double MIN_DISTANCE_BETWEEN_BASES = 100.0;
    private static final double MIN_DISTANCE_FROM_DUNGEONS = 150.0;
    private static final double BASE_SIZE = 32.0;

    private final List<Location> baseLocations = new ArrayList<>();

    public List<Location> calculateBasePositions(World world, int teamCount) {
        baseLocations.clear();

        if (teamCount <= 0) return baseLocations;

        double radius = MIN_DISTANCE_FROM_CENTER;
        int maxAttempts = 100;

        while (radius < DUNGEON_DISTANCE - 50) {
            List<Location> candidates = generateCirclePositions(world, teamCount, radius);

            if (isValidConfiguration(candidates)) {
                baseLocations.addAll(candidates);
                return baseLocations;
            }

            radius += 20;
        }

        baseLocations.addAll(generateCirclePositions(world, teamCount, MIN_DISTANCE_FROM_CENTER));
        return baseLocations;
    }

    private List<Location> generateCirclePositions(World world, int count, double radius) {
        List<Location> positions = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double x = CENTER_X + radius * Math.cos(angle);
            double z = CENTER_Z + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            positions.add(new Location(world, x, y, z));
        }

        return positions;
    }

    private boolean isValidConfiguration(List<Location> positions) {
        for (int i = 0; i < positions.size(); i++) {
            Location a = positions.get(i);

            if (a.distanceSquared(new Location(a.getWorld(), CENTER_X, a.getY(), CENTER_Z)) < MIN_DISTANCE_FROM_CENTER * MIN_DISTANCE_FROM_CENTER) {
                return false;
            }

            if (isTooCloseToAnyDungeon(a)) {
                return false;
            }

            for (int j = i + 1; j < positions.size(); j++) {
                Location b = positions.get(j);
                if (a.distanceSquared(b) < MIN_DISTANCE_BETWEEN_BASES * MIN_DISTANCE_BETWEEN_BASES) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isTooCloseToAnyDungeon(Location loc) {
        double minDistSq = MIN_DISTANCE_FROM_DUNGEONS * MIN_DISTANCE_FROM_DUNGEONS;

        Location d1 = new Location(loc.getWorld(), DUNGEON_DISTANCE, loc.getY(), DUNGEON_DISTANCE);
        Location d2 = new Location(loc.getWorld(), DUNGEON_DISTANCE, loc.getY(), -DUNGEON_DISTANCE);
        Location d3 = new Location(loc.getWorld(), -DUNGEON_DISTANCE, loc.getY(), DUNGEON_DISTANCE);
        Location d4 = new Location(loc.getWorld(), -DUNGEON_DISTANCE, loc.getY(), -DUNGEON_DISTANCE);

        return loc.distanceSquared(d1) < minDistSq
                || loc.distanceSquared(d2) < minDistSq
                || loc.distanceSquared(d3) < minDistSq
                || loc.distanceSquared(d4) < minDistSq;
    }

    public List<Location> getBaseLocations() {
        return baseLocations;
    }

    public void clear() {
        baseLocations.clear();
    }
}
