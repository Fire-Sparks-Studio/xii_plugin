package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Restrictions "lobby" et règles de protection (spec §12/§18).
 *
 * Ce listener est actif quand gameSystems.isProtectionListenerEnabled()
 * vaut true (états WAITING / COUNTDOWN / CLASS_SELECTION / ENDING) :
 * il bloque tout ce qui doit rester impossible pendant l'attente.
 *
 * Priorité LOW : s'exécute avant les listeners de gameplay
 * (MiningListener/BlockPlaceListener) pour court-circuiter proprement.
 */
public class ProtectionListener implements Listener {

    private final XiiPlugin plugin;

    public ProtectionListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système actif ? (pattern officiel, spec §33 : jamais de test de phase ici) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isProtectionListenerEnabled();
    }

    // -----------------------------------------------------------------
    // Casser / poser
    // -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Protection des colis : on ne peut JAMAIS casser un coffre de colis.
        if (plugin.getPackageManager().at(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                    "§c✘ Ce colis est protégé.");
            return;
        }

        if (!systemEnabled()) {
            return;
        }
        if (plugin.getProtectionService().shouldBlockWorldInteraction(player)) {
            event.setCancelled(true);
            return;
        }
        // Un spectateur ne casse JAMAIS de bloc, même en pleine partie.
        if (plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        // Règles de bases pendant toute la phase de jeu : structure
        // incassable, champs protégés, blocs posés par l'équipe =
        // cassables par l'équipe seule (pose/casse "propres dès le début").
        var state = plugin.getGameManager().getState();
        if (state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT) {
            var block = event.getBlock();
            if (!plugin.getProtectionService().canModifyBlock(player, block)) {
                event.setCancelled(true);
                com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                        reasonMessage(block));
                return;
            }
            // Casse autorisée : on oublie le bloc s'il avait été posé
            // par l'équipe (il disparaît du monde).
            var base = plugin.getBaseManager()
                    .baseContainingBlock(block.getLocation());
            if (base != null) {
                base.removeOwnedBlock(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.getProtectionService().shouldBlockWorldInteraction(player)) {
            event.setCancelled(true);
            return;
        }
        // Un spectateur ne pose JAMAIS de bloc.
        if (plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
            return;
        }
        var state = plugin.getGameManager().getState();
        if ((state == com.mceteams.xii.enums.GameState.PREPARATION
                || state == com.mceteams.xii.enums.GameState.COMBAT)
                && !plugin.getProtectionService()
                        .canModifyBlock(player, event.getBlockPlaced())) {
            event.setCancelled(true);
            com.mceteams.xii.util.MessageUtil.sendActionBar(player,
                    "§c✘ Vous ne pouvez rien poser dans cette base.");
        }
    }

    /** Message d'explication selon la raison du refus de casse. */
    private String reasonMessage(org.bukkit.block.Block block) {
        if (plugin.getStructureManager().isStructureBlock(block.getLocation())) {
            return "§c✘ Bloc de structure intouchable.";
        }
        if (plugin.getProtectionService().isProtectedTerrain(block.getType())) {
            return "§c✘ Ce champ est protégé.";
        }
        return "§c✘ Vous ne pouvez rien casser dans cette base.";
    }

    // -----------------------------------------------------------------
    // Base adverse en PRÉPARATION : aucune interaction possible
    // -----------------------------------------------------------------

    /**
     * RÈGLE UTILISATEUR : pendant la PRÉPARATION (jours 1-6), les joueurs
     * ADVERSES ne peuvent interagir avec RIEN dans une base adverse
     * (ouvrir un coffre, actionner une porte/porte-à-sas/levier/bouton...).
     * Seuls les PROPRIÉTAIRES (et l'admin) peuvent utiliser les blocs
     * interactifs de LEUR base. Le PvP de casse/pose est déjà géré par
     * canModifyBlock ; ici on verrouille les INTERACTIONS (clic droit).
     */
    @EventHandler
    public void onEnemyBaseInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        var state = plugin.getGameManager().getState();
        if (state != com.mceteams.xii.enums.GameState.PREPARATION) {
            return; // en préparation uniquement
        }
        var clicked = event.getClickedBlock();
        if (clicked == null || !isInteractive(clicked.getType())) {
            return;
        }
        var base = plugin.getBaseManager().baseContainingBlock(
                clicked.getLocation());
        if (base == null) {
            return; // hors base : libre
        }
        // Portillons : gérés par onGateInteract (réservés aux proprios).
        if (plugin.getGateManager().isGate(clicked.getType())) {
            return;
        }
        var owner = plugin.getTeamManager()
                .getTeamOf(event.getPlayer().getUniqueId());
        if (owner == null || owner.getColor() != base.getColor()) {
            if (!event.getPlayer().hasPermission("xii.admin")) {
                event.setCancelled(true);
                com.mceteams.xii.util.MessageUtil.sendActionBar(
                        event.getPlayer(),
                        "§c✘ Vous ne pouvez pas interagir avec cette base en préparation.");
            }
        }
    }

    /** Le bloc déclenche une interface / action (conteneur, porte...). */
    private boolean isInteractive(org.bukkit.Material type) {
        return type == org.bukkit.Material.CHEST
                || type == org.bukkit.Material.TRAPPED_CHEST
                || type == org.bukkit.Material.FURNACE
                || type == org.bukkit.Material.SMOKER
                || type == org.bukkit.Material.BLAST_FURNACE
                || type == org.bukkit.Material.BARREL
                || type == org.bukkit.Material.CRAFTING_TABLE
                || type == org.bukkit.Material.ANVIL
                || type == org.bukkit.Material.ENCHANTING_TABLE
                || type == org.bukkit.Material.GRINDSTONE
                || type == org.bukkit.Material.BREWING_STAND
                || type == org.bukkit.Material.STONECUTTER
                || type == org.bukkit.Material.CARTOGRAPHY_TABLE
                || type == org.bukkit.Material.SMITHING_TABLE
                || type == org.bukkit.Material.LOOM
                || type == org.bukkit.Material.LEVER
                || type.name().contains("BUTTON")
                || type.name().contains("DOOR")
                || type.name().contains("TRAPDOOR")
                || type == org.bukkit.Material.REPEATER
                || type == org.bukkit.Material.COMPARATOR;
    }

    // -----------------------------------------------------------------
    // Table d'enchantement des bases : DÉCORATIF, non utilisable
    // -----------------------------------------------------------------

    /**
     * RÈGLE UTILISATEUR : les tables d'enchantement présentes dans les
     * bases sont purement décoratives. Aucun joueur (allié ou adversaire)
     * ne peut ouvrir l'interface d'enchantement d'une table située dans
     * une base.
     */
    @EventHandler
    public void onEnchantTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        var clicked = event.getClickedBlock();
        if (clicked == null
                || clicked.getType() != org.bukkit.Material.ENCHANTING_TABLE) {
            return;
        }
        // Restriction uniquement dans l'empreinte d'une base (en pleine
        // partie). Hors base la table reste libre.
        var base = plugin.getBaseManager().baseContainingBlock(
                clicked.getLocation());
        if (base == null) {
            return;
        }
        var state = plugin.getGameManager().getState();
        if (state != com.mceteams.xii.enums.GameState.PREPARATION
                && state != com.mceteams.xii.enums.GameState.COMBAT) {
            return;
        }
        event.setCancelled(true);
        com.mceteams.xii.util.MessageUtil.sendActionBar(event.getPlayer(),
                "§7Cette table d'enchantement est décorative.");
    }

    // -----------------------------------------------------------------
    // Portillons des bases : interaction réservée aux propriétaires
    // -----------------------------------------------------------------

    /**
     * RÈGLE UTILISATEUR : pendant la PRÉPARATION, un adversaire ne peut
     * PAS utiliser les portillons d'une base (s'ouvrant seuls pour les
     * propriétaires). On bloque donc le clic droit sur un portillon dont
     * le joueur n'est pas propriétaire (l'ouverture automatique se charge
     * du reste).
     */
    @EventHandler
    public void onGateInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        var clicked = event.getClickedBlock();
        if (clicked == null
                || !plugin.getGateManager().isGate(clicked.getType())) {
            return;
        }
        if (!plugin.getGateManager().canUseGate(
                event.getPlayer(), clicked.getLocation())) {
            event.setCancelled(true);
            com.mceteams.xii.util.MessageUtil.sendActionBar(event.getPlayer(),
                    "§c✘ Ce portillon est réservé à l'équipe propriétaire.");
        }
    }

    // -----------------------------------------------------------------
    // Champs : piétinement interdit + fondation conservée
    // -----------------------------------------------------------------

    /**
     * Sauter/piétiner un champ (farmland) dans une base est interdit :
     * le sol ne doit jamais revenir en terre. Si le sol a quand même
     * cédé (physics), on le restaure au tick suivant.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) {
            return;
        }
        var block = event.getClickedBlock();
        if (block == null || block.getType() != org.bukkit.Material.FARMLAND) {
            return;
        }
        var base = plugin.getBaseManager().baseContainingBlock(block.getLocation());
        if (base == null) {
            return;
        }
        if (!plugin.getProtectionService().shouldBlockWorldInteraction(
                event.getPlayer())) {
            event.setCancelled(true);
        }
        // Sécurité : restaure un éventuel sol déjà retrampé.
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!block.getType().isAir()
                    && block.getType() != org.bukkit.Material.FARMLAND) {
                block.setBlockData(
                        org.bukkit.Bukkit.createBlockData("minecraft:farmland"),
                        false);
            }
        });
    }

    // -----------------------------------------------------------------
    // Eau de base : non collectable (seaux)
    // -----------------------------------------------------------------

    /**
     * L'eau/lave d'une base est décorative : on ne peut pas la collecter
     * avec un seau (spec §18). Les champs "eau" des bases restent en
     * place pendant toute la partie.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!systemEnabled() || event.isCancelled()) {
            return;
        }
        var clicked = event.getBlockClicked();
        if (clicked == null) {
            return;
        }
        var base = plugin.getBaseManager().baseContainingBlock(clicked.getLocation());
        if (base == null) {
            return;
        }
        event.setCancelled(true);
        com.mceteams.xii.util.MessageUtil.sendActionBar(event.getPlayer(),
                "§c✘ Vous ne pouvez pas collecter l'eau de cette base.");
    }

    // -----------------------------------------------------------------
    // PvP
    // -----------------------------------------------------------------

    /**
     * Blocage du PvP dans les états protégés. Pendant PREPARATION/COMBAT,
     * la décision fine appartient à CombatService via CombatListener.
     * Résolution AUSSI via projectile (flèches...) cf. DamageUtil.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player attacker = com.mceteams.xii.util.DamageUtil
                .resolveAttacker(event.getDamager());
        if (!(event.getEntity() instanceof Player victim) || attacker == null) {
            return; // seulement joueur -> joueur ici
        }
        if (plugin.getGameManager().getState() == com.mceteams.xii.enums.GameState.PREPARATION
                || plugin.getGameManager().getState() == com.mceteams.xii.enums.GameState.COMBAT) {
            return; // gameplay : géré par CombatListener
        }
        // Lobby : PvP interdit.
        if (!plugin.getProtectionService().isPvpAllowed(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Ramasser / jeter
    // -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Un spectateur ne ramasse rien, même en pleine partie.
        if (plugin.getProtectionService().shouldBlockPickup(player)
                || plugin.getProtectionService().isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // Un spectateur ne jette rien.
        if (plugin.getProtectionService().shouldBlockDrop(event.getPlayer())
                || plugin.getProtectionService().isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------
    // Faim gelée + inventaires interdits aux spectateurs
    // -----------------------------------------------------------------

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);       // barre de faim verrouillée
            player.setSaturation(0f);       // mais SANS saturation
        }
    }

    /**
     * Un spectateur ne peut PAS ouvrir de conteneur (coffres, fours...).
     * Nos propres GUIs passent par openInventory et déclenchent aussi
     * cet événement : on laisse passer celles du plugin.
     */
    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.getProtectionService().isSpectator(player)) {
            return;
        }
        // Nos GUIs ont un holder plugin => autorisées.
        if (event.getInventory().getHolder() instanceof org.bukkit.inventory.InventoryHolder holder
                && holder.getClass().getName().startsWith("com.mceteams.xii")) {
            return;
        }
        event.setCancelled(true);
    }
}
