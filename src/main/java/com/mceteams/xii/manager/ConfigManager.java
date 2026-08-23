package com.mceteams.xii.manager;

import com.mceteams.xii.enums.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ConfigManager {
    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void saveSetupState(boolean isSetup) {
        plugin.getConfig().set("setup", isSetup);
        plugin.saveConfig();
    }

    public boolean loadSetupState() {
        return plugin.getConfig().getBoolean("setup", false);
    }

    public void saveWorldName(String worldName) {
        plugin.getConfig().set("world", worldName);
        plugin.saveConfig();
    }

    public String loadWorldName() {
        return plugin.getConfig().getString("world", null);
    }

    public void saveLobbyLocation(Location loc) {
        if (loc == null) return;
        String path = "lobby";
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.saveConfig();
    }

    public Location loadLobbyLocation() {
        String path = "lobby";
        if (!plugin.getConfig().contains(path + ".world")) return null;
        String worldName = plugin.getConfig().getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        return new Location(world, x, y, z);
    }

    public void saveDungeonLocations(Location[] locations) {
        for (int i = 0; i < locations.length; i++) {
            Location loc = locations[i];
            if (loc == null) continue;
            String path = "dungeons." + i;
            plugin.getConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getConfig().set(path + ".x", loc.getX());
            plugin.getConfig().set(path + ".y", loc.getY());
            plugin.getConfig().set(path + ".z", loc.getZ());
        }
        plugin.saveConfig();
    }

    public Location[] loadDungeonLocations() {
        Location[] locations = new Location[4];
        for (int i = 0; i < 4; i++) {
            String path = "dungeons." + i;
            if (!plugin.getConfig().contains(path + ".world")) continue;
            String worldName = plugin.getConfig().getString(path + ".world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = plugin.getConfig().getDouble(path + ".x");
            double y = plugin.getConfig().getDouble(path + ".y");
            double z = plugin.getConfig().getDouble(path + ".z");
            locations[i] = new Location(world, x, y, z);
        }
        return locations;
    }

    public void saveBaseLocations(java.util.List<Location> locations) {
        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            if (loc == null) continue;
            String path = "bases." + i;
            plugin.getConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getConfig().set(path + ".x", loc.getX());
            plugin.getConfig().set(path + ".y", loc.getY());
            plugin.getConfig().set(path + ".z", loc.getZ());
        }
        plugin.saveConfig();
    }

    public java.util.List<Location> loadBaseLocations() {
        java.util.List<Location> locations = new java.util.ArrayList<>();
        if (!plugin.getConfig().contains("bases")) return locations;
        var section = plugin.getConfig().getConfigurationSection("bases");
        if (section == null) return locations;
        for (String key : section.getKeys(false)) {
            String path = "bases." + key;
            String worldName = plugin.getConfig().getString(path + ".world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            double x = plugin.getConfig().getDouble(path + ".x");
            double y = plugin.getConfig().getDouble(path + ".y");
            double z = plugin.getConfig().getDouble(path + ".z");
            locations.add(new Location(world, x, y, z));
        }
        return locations;
    }

    public void clearGameConfig() {
        plugin.getConfig().set("setup", false);
        plugin.getConfig().set("world", null);
        plugin.getConfig().set("lobby", null);
        plugin.getConfig().set("dungeons", null);
        plugin.getConfig().set("bases", null);
        plugin.saveConfig();
    }

    public void forceState(GameState state) {
        plugin.getConfig().set("state", state.name());
        plugin.saveConfig();
    }

    public GameState loadState() {
        String stateName = plugin.getConfig().getString("state", null);
        if (stateName == null) return GameState.NON_SETUP;
        try {
            return GameState.valueOf(stateName);
        } catch (IllegalArgumentException e) {
            return GameState.NON_SETUP;
        }
    }
}
