package com.mceteams.xii;

import org.bukkit.plugin.java.JavaPlugin;

public class XiiPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("===[XII Days]===");
        getLogger().info("===[READY]===");
    }

    @Override
    public void onDisable() {
        getLogger().info("===[DISABLED]===");
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }
}
