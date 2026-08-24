package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logique métier du MINAGE (spec §18).
 *
 * Le MiningListener détecte la casse d'un bloc et délègue ICI :
 * - points par minerai (config) via PointService ;
 * - bonus Mineur : minerais AUTOMATIQUEMENT FONDUS (drop lingot) ;
 * - anti-duplication : un minerai déjà miné (ou POSÉ par un joueur,
 *   ex : silk touch) ne rapporte plus rien.
 */
public class MiningService {

    private final XiiPlugin plugin;

    /**
     * Positions de minerais DÉJÀ minées pendant la partie : une seconde
     * casse au même endroit ne rapporte rien (anti-duplication).
     */
    private final Set<Long> minedPositions = ConcurrentHashMap.newKeySet();

    /**
     * Positions de minerais PLACÉS par des joueurs (silk touch) :
     * alimenté par BlockPlaceListener, consommé ici.
     */
    private final Set<Long> placedPositions = ConcurrentHashMap.newKeySet();

    public MiningService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Fondre automatiquement (bonus Mineur)
    // -----------------------------------------------------------------

    /** Correspondance minerai -> version cuite. */
    private static final Map<Material, Material> SMELT_MAP = Map.ofEntries(
            Map.entry(Material.IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP)
    );

    /**
     * Noms FRANÇAIS des minerais (affichés dans la barre d'action au
     * lieu du nom technique Minecraft type "IRON_ORE").
     */
    private static final Map<Material, String> ORE_NAMES_FR = Map.ofEntries(
            Map.entry(Material.COAL_ORE, "Charbon"),
            Map.entry(Material.DEEPSLATE_COAL_ORE, "Charbon"),
            Map.entry(Material.COPPER_ORE, "Cuivre"),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, "Cuivre"),
            Map.entry(Material.IRON_ORE, "Fer"),
            Map.entry(Material.DEEPSLATE_IRON_ORE, "Fer"),
            Map.entry(Material.GOLD_ORE, "Or"),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, "Or"),
            Map.entry(Material.NETHER_GOLD_ORE, "Or du Nether"),
            Map.entry(Material.REDSTONE_ORE, "Redstone"),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "Redstone"),
            Map.entry(Material.LAPIS_ORE, "Lapis-lazuli"),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, "Lapis-lazuli"),
            Map.entry(Material.DIAMOND_ORE, "Diamant"),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "Diamant"),
            Map.entry(Material.EMERALD_ORE, "Émeraude"),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, "Émeraude"),
            Map.entry(Material.ANCIENT_DEBRIS, "Débris ancien"),
            Map.entry(Material.AMETHYST_CLUSTER, "Améthyste")
    );

    // -----------------------------------------------------------------
    // Traitement d'une casse
    // -----------------------------------------------------------------

    /**
     * Traite la casse d'un bloc par un joueur.
     *
     * @param block le bloc cassé
     * @param event l'événement original (pour annuler les drops si
     *              fonte automatique du Mineur)
     */
    public void handleBlockBreak(Player player, Block block,
                                 org.bukkit.event.block.BlockBreakEvent event) {
        Material type = block.getType();
        int basePoints = plugin.getConfigManager().getMiningPoints(type);
        if (basePoints <= 0) {
            return; // pas un minerai suivi => rien à faire
        }

        long positionKey = keyOf(block.getLocation());

        // Anti-duplication 1 : minerai posé par un joueur => aucun point.
        if (placedPositions.remove(positionKey)) {
            MessageUtil.sendActionBar(player,
                    "§7Minerai posé par un joueur : aucun point.");
            return;
        }
        // Anti-duplication 2 : position déjà minée => aucun point.
        if (!minedPositions.add(positionKey)) {
            return;
        }

        // Attribution des points via PointService (multiplicateurs gérés là-bas).
        // Le nom affiché est le nom FRANÇAIS du minerai.
        String oreName = ORE_NAMES_FR.getOrDefault(type, type.name().toLowerCase());
        plugin.getPointService().award(player, PointCategory.MINING,
                basePoints, "minerai de " + oreName);

        // Bonus Mineur : fonte automatique du drop (§31).
        PlayerDataHolder holder = classOf(player);
        if (holder.clazz() == PlayerClass.MINER) {
            Material smelted = SMELT_MAP.get(type);
            if (smelted != null) {
                // On annule les drops vanilla et on drop le résultat cuit
                // (1 bloc de minerai = 1 lingot/scrap fondu).
                event.setDropItems(false);
                Location location = block.getLocation().add(0.5, 0.5, 0.5);
                block.getWorld().dropItemNaturally(location,
                        new ItemStack(smelted, 1));
            }
        }
    }

    /** Enregistre qu'un joueur a POSÉ ce minerai (anti-duplication). */
    public void trackPlacedOre(Block block) {
        placedPositions.add(keyOf(block.getLocation()));
    }

    private long keyOf(Location location) {
        long x = location.getBlockX();
        long y = location.getBlockY();
        long z = location.getBlockZ();
        return (x & 0x3FFFFFF) << 38 | (z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    /** Petit record interne pour lire la classe sans double lookup. */
    private record PlayerDataHolder(PlayerClass clazz) {
    }

    private PlayerDataHolder classOf(Player player) {
        return new PlayerDataHolder(
                plugin.getPlayerManager().getData(player).getPlayerClass());
    }

    /** Nouvelle partie : on oublie les positions. */
    public void resetMatchState() {
        minedPositions.clear();
        placedPositions.clear();
    }
}
