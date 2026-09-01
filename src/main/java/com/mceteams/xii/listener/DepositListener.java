package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * COFFRE DE DÉPÔT des minerais / redstone (spec mécanique nouvelle).
 *
 * Chaque base possède un coffre proche du centre ; les joueurs y
 * déposent leurs ressources et ferment le GUI : la conversion s'opère
 * à la FERMETURE (InventoryCloseEvent).
 *
 * - MINERAIS : points de minage (config) x le facteur de dépôt x la
 *   quantité, attribués au déposant ET à son équipe (PointService).
 *   Les items sont supprimés du coffre.
 * - REDSTONE : pénalité retirée à CHAQUE AUTRE équipe (par poudre),
 *   avec message broadcast et son. La redstone n'est PAS comptée comme
 *   minerai positif.
 *
 * Restrictions :
 * - en PRÉPARATION le coffre est réservé à l'équipe propriétaire ;
 * - en COMBAT tout le monde peut y accéder (sabotage à la redstone) ;
 * - le coffre ne peut JAMAIS être cassé pendant une partie.
 */
public class DepositListener implements Listener {

    private final XiiPlugin plugin;

    public DepositListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Objet de dépôt -> minerai considéré pour la valeur (ConfigManager
     * donne les points PAR MINERAI, pas par item). La redstone est traitée
     * séparément (pénalité) et n'apparaît donc pas ici.
     */
    private static final Map<Material, Material> DEPOSIT_ORE = Map.ofEntries(
            Map.entry(Material.COAL, Material.COAL_ORE),
            Map.entry(Material.RAW_IRON, Material.IRON_ORE),
            Map.entry(Material.IRON_INGOT, Material.IRON_ORE),
            Map.entry(Material.RAW_GOLD, Material.GOLD_ORE),
            Map.entry(Material.GOLD_INGOT, Material.GOLD_ORE),
            Map.entry(Material.RAW_COPPER, Material.COPPER_ORE),
            Map.entry(Material.COPPER_INGOT, Material.COPPER_ORE),
            Map.entry(Material.LAPIS_LAZULI, Material.LAPIS_ORE),
            Map.entry(Material.DIAMOND, Material.DIAMOND_ORE),
            Map.entry(Material.EMERALD, Material.EMERALD_ORE),
            Map.entry(Material.AMETHYST_SHARD, Material.AMETHYST_CLUSTER),
            Map.entry(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS),
            Map.entry(Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS)
    );

    /** Une partie (préparation ou combat) est-elle en cours ? */
    private boolean matchRunning() {
        var state = plugin.getGameManager().getState();
        return state == GameState.PREPARATION || state == GameState.COMBAT;
    }

    // -----------------------------------------------------------------
    // Conversion des dépôts (à la fermeture du GUI)
    // -----------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!matchRunning()) {
            return;
        }
        Player player = (Player) event.getPlayer();

        Location location = locationOf(event.getInventory());
        if (location == null) {
            return;
        }
        GameTeam owner = plugin.getCoreService()
                .getTeamByDepositChest(location.getBlock());
        if (owner == null) {
            return; // pas le coffre de dépôt enregistré d'une base
        }

        // Inventaire → cumuls.
        int redstone = 0;
        int totalRawPoints = 0;
        int depositedItems = 0;
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getAmount() <= 0) {
                continue;
            }
            if (item.getType() == Material.REDSTONE) {
                redstone += item.getAmount();
                depositedItems += item.getAmount();
                continue;
            }
            Material ore = DEPOSIT_ORE.get(item.getType());
            if (ore != null) {
                int unit = plugin.getConfigManager().getMiningPoints(ore);
                totalRawPoints += unit * item.getAmount();
                depositedItems += item.getAmount();
            }
        }

        if (depositedItems == 0) {
            return;
        }

        // Les items sont CONSOMMÉS : le coffre est vidé.
        event.getInventory().clear();

        // --- Redstone : pénalité à chaque AUTRE équipe ----------------
        if (redstone > 0) {
            int perDust = plugin.getConfigManager().getRedstonePenalty();
            int totalPenalty = perDust * redstone;
            for (GameTeam team : plugin.getTeamManager().all()) {
                if (team == owner) {
                    continue; // l'équipe du déposant n'est pas pénalisée
                }
                int penalty = perDust * redstone;
                team.getScore().addPenalty(penalty);
                // RÈGLE UTILISATEUR (messages CIBLÉS) : chaque équipe
                // touchée voit la perte, l'équipe offensive voit "fait
                // perdre" (jamais les autres équipes).
                com.mceteams.xii.util.MessageUtil.broadcastToTeam(team,
                        " §cVotre équipe a perdu §l" + penalty
                                + " §r§cpoints à cause de "
                                + owner.getColor().getColorCode()
                                + owner.getColor().getDisplayName() + ".");
            }
            // À l'équipe OFFENSIVE (celle qui dépose la redstone).
            com.mceteams.xii.util.MessageUtil.broadcastToTeam(owner,
                    " §3Vous avez fait perdre §l" + totalPenalty
                            + " §r§3points à vos adversaires.");
            SoundUtil.broadcast(Sound.ENTITY_WITHER_HURT, 1.0f, 0.7f);
        }

        // --- Minerais : points x facteur, au déposant + son équipe ----
        if (totalRawPoints > 0) {
            double multiplier = plugin.getConfigManager().getDepositMultiplier();
            int points = (int) Math.round(totalRawPoints * multiplier);
            plugin.getPointService().award(player, PointCategory.MINING,
                    points, "minerais déposés");
            // RÈGLE UTILISATEUR : l'apport de points pour les MINERAIS
            // déposés est signalé UNIQUEMENT à l'équipe du déposant
            // ("[joueur] a apporté X points à l'équipe"), jamais aux
            // autres équipes.
            var depositorTeam = plugin.getTeamManager()
                    .getTeamOf(player.getUniqueId());
            if (depositorTeam != null) {
                com.mceteams.xii.util.MessageUtil.broadcastToTeam(depositorTeam,
                        " §a¤ §f" + depositorTeam.getColor().getColorCode()
                                + player.getName() + " §7a apporté §a" + points
                                + " §7points à l'équipe.");
            }
            SoundUtil.broadcast(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        }
    }

    // -----------------------------------------------------------------
    // Ouverture réservée (PRÉPARATION) et protection du bloc
    // -----------------------------------------------------------------

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!matchRunning()) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            Location location = locationOf(event.getInventory());
            if (location == null) {
                return;
            }
            GameTeam owner = plugin.getCoreService()
                    .getTeamByDepositChest(location.getBlock());
            if (owner == null) {
                return;
            }
            // En PRÉPARATION : équipe propriétaire (ou admin) uniquement.
            if (plugin.getGameManager().getState() == GameState.PREPARATION) {
                var openerTeam = plugin.getTeamManager()
                        .getTeamOf(player.getUniqueId());
                if (openerTeam != owner
                        && !player.hasPermission("xii.admin")) {
                    event.setCancelled(true);
                    MessageUtil.sendActionBar(player,
                            "§c✘ Coffre réservé : vous n'appartenez pas à "
                                    + owner.getColor().getColorCode()
                                    + "l'équipe " + owner.getColor().getDisplayName()
                                    + "§c.");
                }
            }
            // En COMBAT : tout le monde y accède.
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!matchRunning()) {
            return;
        }
        if (plugin.getCoreService().isDepositChest(event.getBlock())) {
            event.setCancelled(true);
            MessageUtil.send(event.getPlayer(),
                    "§c✘ Le coffre de dépôt est indestructible pendant la partie.");
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Position du coffre (simple ou double) associé à un inventaire. */
    private Location locationOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof Chest chest) {
            return chest.getBlock().getLocation();
        }
        if (inventory.getHolder() instanceof DoubleChest doubleChest) {
            return new Location(doubleChest.getWorld(),
                    doubleChest.getX(), doubleChest.getY(), doubleChest.getZ());
        }
        return null;
    }

    /** Nom du joueur coloré avec SA couleur d'équipe (blanc si sans équipe). */
    private String coloredPlayerName(Player player) {
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        String colorCode = team != null
                ? team.getColor().getColorCode()
                : "§f";
        return colorCode + player.getName();
    }
}