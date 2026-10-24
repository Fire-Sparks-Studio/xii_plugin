package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameZone;
import org.bukkit.World;

import java.lang.reflect.Method;

public class WorldEditRegenUtil {
    public static boolean regenerateZone(XiiPlugin plugin, GameZone zone) {
        if (zone == null || zone.getWorld() == null) {
            return false;
        }
        World bWorld = zone.getWorld();
        try {
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptWorld = bukkitAdapter.getMethod("adapt", World.class);
            Object weWorld = adaptWorld.invoke(null, bWorld);
            if (weWorld == null) {
                return false;
            }
            int cx = zone.getCenterLocation() != null ? zone.getCenterLocation().getBlockX() : (int) Math.round(zone.getCenterX());
            int cz = zone.getCenterLocation() != null ? zone.getCenterLocation().getBlockZ() : (int) Math.round(zone.getCenterZ());
            int baseRadius = 100;
            int minX = cx - baseRadius - 90;
            int maxX = cx + baseRadius + 90;
            int minZ = cz - baseRadius - 90;
            int maxZ = cz + baseRadius + 90;
            // include lobby area
            minX = Math.min(minX, cx - 100);
            maxX = Math.max(maxX, cx + 100);
            minZ = Math.min(minZ, cz - 100);
            maxZ = Math.max(maxZ, cz + 100);
            int minY = bWorld.getMinHeight();
            int maxY = bWorld.getMaxHeight() - 1;
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method bv3at = bv3.getMethod("at", int.class, int.class, int.class);
            Object pos1 = bv3at.invoke(null, minX, minY, minZ);
            Object pos2 = bv3at.invoke(null, maxX, maxY, maxZ);
            Class<?> cuboid = Class.forName("com.sk89q.worldedit.regions.CuboidRegion");
            Object region = cuboid.getConstructor(weWorld.getClass(), bv3, bv3).newInstance(weWorld, pos1, pos2);
            Class<?> worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit");
            Method getInstance = worldEdit.getMethod("getInstance");
            Object we = getInstance.invoke(null);
            Method newEditSession = worldEdit.getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"));
            Object editSession = newEditSession.invoke(we, weWorld);
            try {
                Class<?> regenOptions = Class.forName("com.sk89q.worldedit.world.RegenOptions");
                Method builder = regenOptions.getMethod("builder");
                Object bld = builder.invoke(null);
                Method build = bld.getClass().getMethod("build");
                Object options = build.invoke(bld);
                Method regen = weWorld.getClass().getMethod("regenerate", Class.forName("com.sk89q.worldedit.regions.Region"), Class.forName("com.sk89q.worldedit.extent.Extent"), regenOptions);
                Object res = regen.invoke(weWorld, region, editSession, options);
                if (res instanceof Boolean bres) {
                    return bres;
                }
                return true;
            } finally {
                if (editSession != null) {
                    try {
                        Method close = editSession.getClass().getMethod("close");
                        close.invoke(editSession);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[ZoneDelete] WorldEdit regen failed: " + t);
            return false;
        }
    }
}
