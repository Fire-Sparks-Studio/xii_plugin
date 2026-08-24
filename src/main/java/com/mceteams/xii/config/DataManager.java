package com.mceteams.xii.config;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameZone;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Données persistantes du jeu (data.yml) - spec §38.
 *
 * On ne persiste QUE la zone. RÈGLE UTILISATEUR : après un
 * redémarrage, la zone persistée est IGNORÉE et purgée - le serveur
 * repart en mode normal, /zone set est requis pour relancer le jeu.
 */
public class DataManager {

    private final XiiPlugin plugin;
    private final FileManager fileManager;

    public DataManager(XiiPlugin plugin, FileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }

    /**
     * Charge la zone persistée.
     *
     * @return la zone, ou null si aucune zone n'est définie ou si les
     * données sont invalides.
     */
    public GameZone loadZone() {
        FileConfiguration data = fileManager.loadData();
        if (!data.getBoolean("zone.defined", false)) {
            return null;
        }
        String world = data.getString("zone.world", "");
        if (world.isBlank()) {
            return null;
        }
        return new GameZone(
                world,
                data.getDouble("zone.x"),
                data.getDouble("zone.y"),
                data.getDouble("zone.z"),
                data.getInt("zone.size", 2000)
        );
    }

    /** Sauvegarde la zone (appelé après /zone set). */
    public void saveZone(GameZone zone) {
        FileConfiguration data = fileManager.loadData();
        data.set("zone.defined", true);
        data.set("zone.world", zone.getWorldName());
        data.set("zone.x", zone.getCenterX());
        data.set("zone.y", zone.getCenterY());
        data.set("zone.z", zone.getCenterZ());
        data.set("zone.size", zone.getSize());
        fileManager.saveData(data);
    }

    /**
     * Efface les données de zone (spec §11 : /zone delete, ou spec §9 :
     * monde disparu => zone considérée comme invalide).
     */
    public void clearZone() {
        FileConfiguration data = fileManager.loadData();
        data.set("zone.defined", false);
        data.set("zone.world", "");
        fileManager.saveData(data);
        plugin.getLogger().info("Données de zone supprimées.");
    }
}
