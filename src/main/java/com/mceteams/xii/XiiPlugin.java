package com.mceteams.xii;

import com.mceteams.xii.commands.XCommands;
import com.mceteams.xii.listener.HotbarListener;
import com.mceteams.xii.listener.MiningListener;
import com.mceteams.xii.listener.PlaceListener;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.HotbarManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.service.PointService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class XiiPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getLogger().info("===[XiiPlugin ENABLING]===");

        // Registering Managers and Services
        getLogger().info("[XiiPlugin]: Registering up Managers & Services");
        TeamManager teamManager = new TeamManager();
        PointService pointService = new PointService();
        GameManager gameManager = new GameManager();
        HotbarManager hotbarManager = new HotbarManager(teamManager);
        getLogger().info("[XiiPlugin]: Managers & Services Registered!");

        // Registering Listeners
        getLogger().info("[XiiPlugin]: Registering Listeners");
        HotbarListener hotbarListener = new HotbarListener(teamManager, gameManager, hotbarManager);
        MiningListener miningListener = new MiningListener(pointService, teamManager);
        PlaceListener placeListener = new PlaceListener(miningListener);

        getServer().getPluginManager().registerEvents(hotbarListener, this);
        getServer().getPluginManager().registerEvents(miningListener, this);
        getServer().getPluginManager().registerEvents(placeListener, this);
        getLogger().info("[XiiPlugin]: Listeners Registered!");

        getLogger().info("[XiiPlugin]: Registering Commands");
        XCommands commands = new XCommands(teamManager, gameManager);

        Objects.requireNonNull(getCommand("xii")).setExecutor(commands);
        getLogger().info("[XiiPlugin]: Commands Registered!");

        getLogger().info("===[XiiPlugin READY]===");
    }

    @Override
    public void onDisable() {
        getLogger().info("XiiPlugin disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Welcome, " + event.getPlayer().getName() + "!"));
    }
}
