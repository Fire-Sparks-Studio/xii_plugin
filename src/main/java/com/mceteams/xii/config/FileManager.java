package com.mceteams.xii.config;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Gestion des fichiers physiques du plugin (spec §38).
 *
 * Responsabilités :
 * - créer le dossier du plugin ;
 * - extraire les fichiers par défaut (config.yml, data.yml) ;
 * - fournir des FileConfiguration chargées/sauvegardées.
 */
public class FileManager {

    private final XiiPlugin plugin;
    private final File configFile;
    private final File dataFile;

    public FileManager(XiiPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    /**
     * Prépare les fichiers au démarrage du plugin.
     * saveDefaultConfig() extrait config.yml du jar si absent.
     */
    public void setup() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier du plugin.");
        }
        plugin.saveDefaultConfig();
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
    }

    /** Recharge config.yml depuis le disque. */
    public FileConfiguration loadConfig() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    /** Charge data.yml (données persistantes : la zone). */
    public FileConfiguration loadData() {
        return YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Sauvegarde une configuration dans data.yml. */
    public void saveData(FileConfiguration data) {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Sauvegarde de data.yml impossible : "
                    + exception.getMessage());
        }
    }
}
