package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.Package;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des colis (packages) actuellement posés dans le monde
 * (spec §17). La logique de spawn est dans PackageService ; ce manager
 * ne fait que détenir les models et fournir les recherches.
 */
public class PackageManager {

    private final XiiPlugin plugin;
    /** Colis actifs par identifiant. */
    private final Map<UUID, Package> activePackages = new ConcurrentHashMap<>();

    public PackageManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Enregistre un colis nouvellement spawné. */
    public void register(Package pack) {
        activePackages.put(pack.getId(), pack);
    }

    /**
     * @return le colis posé à cette location (coffre), ou null.
     */
    public Package at(Location chestLocation) {
        for (Package pack : activePackages.values()) {
            Location loc = pack.getLocation();
            if (loc.getWorld().equals(chestLocation.getWorld())
                    && loc.getBlockX() == chestLocation.getBlockX()
                    && loc.getBlockY() == chestLocation.getBlockY()
                    && loc.getBlockZ() == chestLocation.getBlockZ()) {
                return pack;
            }
        }
        return null;
    }

    /**
     * @return le colis d'identifiant donné, ou null.
     */
    public Package byId(UUID packageId) {
        return activePackages.get(packageId);
    }

    /** Retire un colis du registre (après ouverture ou nettoyage). */
    public void unregister(UUID packageId) {
        activePackages.remove(packageId);
    }

    /** @return copie de la liste des colis actifs. */
    public List<Package> all() {
        return new ArrayList<>(activePackages.values());
    }

    /**
     * Supprime physiquement tous les colis restants (blocs CHEST -> AIR)
     * et vide le registre. Utilisé par /party stop (spec §35).
     */
    public void removeAllBlocks() {
        Iterator<Package> iterator = activePackages.values().iterator();
        while (iterator.hasNext()) {
            Package pack = iterator.next();
            Location loc = pack.getLocation();
            // Colonnes de fumee + feu de camp du colis : on coupe tout.
            if (plugin.getPackageService() != null) {
                plugin.getPackageService().stopSmokeBeacon(pack.getId());
            }
            if (loc.getWorld() != null) {
                Block chest = loc.getBlock();
                Block above = chest.getRelative(0, 1, 0);
                if (above.getType() == org.bukkit.Material.CAMPFIRE) {
                    above.setType(org.bukkit.Material.AIR, false);
                }
                if (chest.getType() == org.bukkit.Material.CHEST) {
                    chest.setType(org.bukkit.Material.AIR);
                }
            }
            iterator.remove();
        }
    }

    /** Vide uniquement le registre (les blocs restent, ex : zone delete). */
    public void clearRegistry() {
        activePackages.clear();
    }
}
