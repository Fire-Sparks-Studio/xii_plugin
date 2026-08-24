package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.config.ConfigManager;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.manager.PackageManager;
import com.mceteams.xii.model.Package;
import com.mceteams.xii.util.LocationUtil;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Logique métier des COLIS / packages (spec §17).
 *
 * Un colis = un coffre posé à un point ALÉATOIRE de la zone, rempli
 * avec la table de loot. Le premier joueur qui l'ouvre gagne les
 * points PACKAGE (et parfois RARE_ITEM selon la probabilité configurée).
 *
 * L'apparition est déclenchée par PackageTask (intervalles aléatoires),
 * plus fréquente pendant PACKAGE_UPGRADE (facteur lu dans la config).
 */
public class PackageService {

    private final XiiPlugin plugin;
    private final Random random = new Random();

    public PackageService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Apparition
    // -----------------------------------------------------------------

    /**
     * Fait apparaître un colis à un point aléatoire de la zone.
     *
     * ASYNCHRONE : le chunk cible est chargé via getChunkAtAsync avant
     * toute lecture de terrain (sinon gel du serveur, cf. LocationUtil).
     *
     * @return toujours null immédiatement ; le colis est créé plus tard
     *         par callback (l'ancienne valeur de retour n'est plus utilisée).
     */
    public Package spawnRandomPackage() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return null;
        }
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return null; // mécanique limitée à la préparation
        }

        // Position résolue une fois le chunk chargé (thread principal).
        LocationUtil.randomSurfaceInAsync(zone, this::placePackageAt);
        return null;
    }

    /** Place réellement le coffre du colis à la position donnée. */
    private void placePackageAt(Location spawnLocation) {
        if (spawnLocation == null) {
            return;
        }
        // Re-vérification d'état : le tick asynchrone peut arriver après
        // un changement d'état (fin de préparation, arrêt de partie...).
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return;
        }

        // Pose du coffre.
        Block block = spawnLocation.getBlock();
        block.setType(Material.CHEST);

        // Remplissage avec la table de loot.
        boolean containsRareItem = fillChest(block);

        // Enregistrement du model.
        Package pack = new Package(UUID.randomUUID(), block.getLocation(),
                containsRareItem);
        plugin.getPackageManager().register(pack);

        MessageUtil.broadcast("§eUn colis est apparu§7 ! §8(x" + block.getX()
                + " y" + block.getY() + " z" + block.getZ() + ")");
    }

    /** Remplit le coffre du colis ; @return true si objet rare dedans. */
    private boolean fillChest(Block chestBlock) {
        BlockState state = chestBlock.getState();
        if (!(state instanceof Chest chest)) {
            return false;
        }
        ConfigManager config = plugin.getConfigManager();
        boolean rare = random.nextDouble() < config.getRareItemChance();

        for (ConfigManager.LootEntry entry : config.getLootTable()) {
            int amount = entry.randomAmount(random);
            if (amount > 0) {
                chest.getInventory().setItem(
                        random.nextInt(chest.getInventory().getSize()),
                        new ItemStack(entry.material(), amount));
            }
        }

        // Objet rare : bonus symbolique posé par-dessus le loot standard.
        if (rare) {
            chest.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE, 1));
        }
        chest.update();
        return rare;
    }

    // -----------------------------------------------------------------
    // Ouverture
    // -----------------------------------------------------------------

    /**
     * Traite l'ouverture d'un colis par un joueur.
     *
     * @param opener joueur qui ouvre
     * @param pack   colis concerné.
     */
    public void handleOpen(Player opener, Package pack) {
        if (pack.isOpened()) {
            return; // déjà revendiqué
        }
        pack.setOpened(true);
        plugin.getPackageManager().unregister(pack.getId());

        // Points PACKAGE pour l'ouvreur (multiplicateurs via PointService).
        plugin.getPointService().award(opener, PointCategory.PACKAGE,
                plugin.getConfigManager().getPackagePoints(), "colis ouvert");

        // Bonus éventuel : objet rare.
        if (pack.containsRareItem()) {
            plugin.getPointService().award(opener, PointCategory.RARE_ITEM,
                    plugin.getConfigManager().getRareItemPoints(), "objet rare");
            MessageUtil.broadcast("§6" + opener.getName()
                    + " §7a trouvé un §eobjet rare§7 !");
        } else {
            MessageUtil.sendActionBar(opener, "§aColis récupéré !");
        }
    }

    /**
     * Intervalle aléatoire avant le prochain colis, en tenant compte du
     * facteur d'upgrade si PACKAGE_UPGRADE est active (spec §17).
     */
    public int nextSpawnDelaySeconds() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int min = plugin.getConfigManager().getPackageMinIntervalSeconds();
        int max = plugin.getConfigManager().getPackageMaxIntervalSeconds();

        var phaseManager = plugin.getPhaseManager();
        boolean upgraded = phaseManager.getPhase() == com.mceteams.xii.enums.GamePhase.PREPARATION
                && phaseManager.getPreparationSubPhase()
                == com.mceteams.xii.enums.PreparationSubPhase.PACKAGE_UPGRADE;
        if (upgraded) {
            double factor = plugin.getConfigManager().getPackageUpgradeFactor();
            min = (int) Math.max(1, min / factor);
            max = (int) Math.max(min + 1, max / factor);
        }
        return min + rng.nextInt(Math.max(1, max - min));
    }
}
