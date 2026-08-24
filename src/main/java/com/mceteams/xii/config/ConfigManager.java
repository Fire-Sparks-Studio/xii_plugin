package com.mceteams.xii.config;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès à la configuration statique (config.yml) - spec §38.
 *
 * Toutes les valeurs de gameplay réglables sont lues ICI et nulle part
 * ailleurs, pour que les services restent lisibles et testables.
 */
public class ConfigManager {

    /**
     * Version attendue du config.yml embarqué. Si le fichier installé
     * dans plugins/XII-Days/ est PLUS ANCIEN, il est remplacé par la
     * nouvelle ressource (l'ancien est sauvegardé en .bak) : sans ça,
     * les nouvelles valeurs de gameplay ne seraient jamais appliquées
     * (saveDefaultConfig n'écrase jamais un fichier existant).
     */
    private static final int CONFIG_VERSION = 3;

    private final XiiPlugin plugin;
    private FileConfiguration config;

    /** Cache des points par minerai (chargé une fois). */
    private final Map<Material, Integer> miningPoints = new EnumMap<>(Material.class);
    /** Cache du loot (matériau -> [min, max]). */
    private final List<LootEntry> lootTable = new ArrayList<>();

    public ConfigManager(XiiPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * (Re)charge config.yml et les caches dérivés.
     * Gère la migration automatique si la version du fichier diffère.
     */
    public void reload() {
        java.io.File configFile =
                new FileHolder(plugin).configFile();

        // Migration : fichier plus ancien que la version embarquée ?
        org.bukkit.configuration.file.YamlConfiguration onDisk =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        int installedVersion = onDisk.getInt("config-version", 0);
        if (installedVersion < CONFIG_VERSION) {
            try {
                if (configFile.exists()) {
                    java.io.File backup = new java.io.File(configFile.getParentFile(),
                            "config-backup-v" + installedVersion + ".yml");
                    java.nio.file.Files.copy(configFile.toPath(), backup.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                plugin.saveResource("config.yml", true); // écrase avec l'embarqué
                plugin.getLogger().info("[Config] Migrée v" + installedVersion
                        + " -> v" + CONFIG_VERSION
                        + " (ancienne copie : config-backup-v" + installedVersion + ".yml)");
            } catch (Exception exception) {
                plugin.getLogger().warning("[Config] Migration impossible : "
                        + exception.getMessage());
            }
        }

        config = plugin.getConfig();

        // --- Points de minage -------------------------------------
        miningPoints.clear();
        ConfigurationSection mining = config.getConfigurationSection("points.mining");
        if (mining != null) {
            putOre(mining, "amethyst", Material.AMETHYST_CLUSTER);
            putOre(mining, "coal", Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
            putOre(mining, "copper", Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
            putOre(mining, "iron", Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
            putOre(mining, "gold", Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                    Material.NETHER_GOLD_ORE);
            putOre(mining, "redstone", Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
            putOre(mining, "lapis", Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
            putOre(mining, "diamond", Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
            putOre(mining, "emerald", Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
            // Le debris ancien n'a pas de variante deepslate.
            int debris = mining.getInt("ancient-debris", 60);
            miningPoints.put(Material.ANCIENT_DEBRIS, debris);
        }

        // --- Table de loot ----------------------------------------
        lootTable.clear();
        for (String entry : config.getStringList("loot-table")) {
            // Format attendu : MATERIAU:min:max
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            try {
                Material material = Material.valueOf(parts[0].trim().toUpperCase());
                int min = Integer.parseInt(parts[1].trim());
                int max = Integer.parseInt(parts[2].trim());
                lootTable.add(new LootEntry(material, min, max));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Entrée de loot invalide : " + entry);
            }
        }
    }

    /** Petit holder pour accéder au fichier config de façon concise. */
    private record FileHolder(XiiPlugin plugin) {
        java.io.File configFile() {
            return new java.io.File(plugin.getDataFolder(), "config.yml");
        }
    }

    /** Enregistre un minerai avec toutes ses variantes (stone/deepslate). */
    private void putOre(ConfigurationSection section, String key,
                        Material... variants) {
        int points = section.getInt(key, 5);
        for (Material variant : variants) {
            miningPoints.put(variant, points);
        }
    }

    // -----------------------------------------------------------------
    // Zone (spec §3)
    // -----------------------------------------------------------------
    public int getZoneSize() {
        return config.getInt("zone.size", 2000);
    }

    public int getWaitingLobbyHeight() {
        return config.getInt("zone.waiting-lobby-height", 40);
    }

    // -----------------------------------------------------------------
    // Bases (spec §7)
    // -----------------------------------------------------------------
    public int getBaseRadius() {
        return config.getInt("bases.radius", 300);
    }

    public int getCoreOffsetY() {
        return config.getInt("bases.core-offset-y", 3);
    }

    public int getSpawnOffset() {
        return config.getInt("bases.spawn-offset", 4);
    }

    // -----------------------------------------------------------------
    // Durées (spec §13/§14/§17/§20)
    // -----------------------------------------------------------------
    public int getCountdownSeconds() {
        return config.getInt("durations.countdown", 5);
    }

    public int getClassSelectionSeconds() {
        return config.getInt("durations.class-selection", 30);
    }

    public int getSubPhaseDurationSeconds() {
        return config.getInt("durations.sub-phase", 600);
    }

    public int getEndingSeconds() {
        return config.getInt("durations.ending", 30);
    }

    // -----------------------------------------------------------------
    // Respawn (spec §19)
    // -----------------------------------------------------------------
    public int getRespawnStepSeconds() {
        return config.getInt("respawn.step-seconds", 5);
    }

    public int getRespawnMaxSeconds() {
        return config.getInt("respawn.max-seconds", 30);
    }

    // -----------------------------------------------------------------
    // Combat (spec §30)
    // -----------------------------------------------------------------
    public int getCombatWindowSeconds() {
        return config.getInt("combat.window-seconds", 15);
    }

    // -----------------------------------------------------------------
    // Points (spec §32)
    // -----------------------------------------------------------------
    public int getKillPoints() {
        return config.getInt("points.kill", 15);
    }

    public int getFirstKillPoints() {
        return config.getInt("points.first-kill", 30);
    }

    public int getExplorationPoints() {
        return config.getInt("points.exploration-per-chunk", 1);
    }

    public int getPackagePoints() {
        return config.getInt("points.package-open", 10);
    }

    public int getRareItemPoints() {
        return config.getInt("points.rare-item", 20);
    }

    public double getRareItemChance() {
        return config.getDouble("points.rare-item-chance", 0.15);
    }

    public int getCorePoints() {
        return config.getInt("points.core-destroyed", 50);
    }

    public int getDeathPenalty() {
        return config.getInt("points.death-penalty", 10);
    }

    /**
     * Points attribués pour un minerai donné (0 si ce n'est pas un
     * minerai suivi).
     */
    public int getMiningPoints(Material material) {
        return miningPoints.getOrDefault(material, 0);
    }

    /** Ce matériau est-il un minerai suivi par le système de minage ? */
    public boolean isTrackedOre(Material material) {
        return material != null && miningPoints.containsKey(material);
    }

    // -----------------------------------------------------------------
    // Packages (spec §17)
    // -----------------------------------------------------------------
    public int getPackageMinIntervalSeconds() {
        return config.getInt("packages.min-interval-seconds", 60);
    }

    public int getPackageMaxIntervalSeconds() {
        return config.getInt("packages.max-interval-seconds", 180);
    }

    public double getPackageUpgradeFactor() {
        return config.getDouble("packages.upgrade-factor", 2.0);
    }

    // -----------------------------------------------------------------
    // Météorites (spec §22/§25)
    // -----------------------------------------------------------------
    public int getMeteoriteMinIntervalSeconds() {
        return config.getInt("meteorites.min-interval-seconds", 45);
    }

    public int getMeteoriteMaxIntervalSeconds() {
        return config.getInt("meteorites.max-interval-seconds", 120);
    }

    public double getMeteoriteUpgradeFactor() {
        return config.getDouble("meteorites.upgrade-factor", 2.0);
    }

    public double getMeteoritePower() {
        return config.getDouble("meteorites.power", 6.0);
    }

    public int getMeteoriteRadius() {
        return config.getInt("meteorites.radius", 8);
    }

    public double getMeteoriteDamageMinPercent() {
        return config.getDouble("meteorites.damage-min-percent", 0.35);
    }

    public double getMeteoriteDamageMaxPercent() {
        return config.getDouble("meteorites.damage-max-percent", 0.50);
    }

    // -----------------------------------------------------------------
    // Mort subite (spec §26)
    // -----------------------------------------------------------------
    public int getSuddenDeathDragonIntervalSeconds() {
        return config.getInt("sudden-death.dragon-interval-seconds", 60);
    }

    // -----------------------------------------------------------------
    // Équipes (spec §6)
    // -----------------------------------------------------------------
    public int getDefaultMaxPlayersPerTeam() {
        return config.getInt("teams.max-players", 8);
    }

    /** @return la table de loot chargée (non modifiable). */
    public List<LootEntry> getLootTable() {
        return List.copyOf(lootTable);
    }

    /**
     * Petite structure interne : une entrée de la table de loot.
     */
    public record LootEntry(Material material, int min, int max) {

        /** Tire une quantité aléatoire entre min et max. */
        public int randomAmount(java.util.Random random) {
            if (max <= min) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }
    }
}
