package com.mceteams.xii.manager;

import org.bukkit.Location;
import org.bukkit.World;

public class StructureManager {
    private Location lobbyLocation;
    private final Location[] dungeonLocations = new Location[4];

    public static final int DUNGEON_1_X = 500;
    public static final int DUNGEON_1_Z = 500;
    public static final int DUNGEON_2_X = 500;
    public static final int DUNGEON_2_Z = -500;
    public static final int DUNGEON_3_X = -500;
    public static final int DUNGEON_3_Z = 500;
    public static final int DUNGEON_4_X = -500;
    public static final int DUNGEON_4_Z = -500;

    public void initializeDungeonLocations(World world) {
        int baseY = world.getHighestBlockYAt(DUNGEON_1_X, DUNGEON_1_Z) + 1;
        dungeonLocations[0] = new Location(world, DUNGEON_1_X, baseY, DUNGEON_1_Z);
        dungeonLocations[1] = new Location(world, DUNGEON_2_X, world.getHighestBlockYAt(DUNGEON_2_X, DUNGEON_2_Z) + 1, DUNGEON_2_Z);
        dungeonLocations[2] = new Location(world, DUNGEON_3_X, world.getHighestBlockYAt(DUNGEON_3_X, DUNGEON_3_Z) + 1, DUNGEON_3_Z);
        dungeonLocations[3] = new Location(world, DUNGEON_4_X, world.getHighestBlockYAt(DUNGEON_4_X, DUNGEON_4_Z) + 1, DUNGEON_4_Z);
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(Location location) {
        this.lobbyLocation = location;
    }

    public Location[] getDungeonLocations() {
        return dungeonLocations;
    }

    public Location getDungeonLocation(int index) {
        if (index < 0 || index >= dungeonLocations.length) return null;
        return dungeonLocations[index];
    }
}
