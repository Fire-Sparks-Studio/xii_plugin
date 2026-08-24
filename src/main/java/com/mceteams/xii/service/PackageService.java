package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.config.ConfigManager;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.manager.PackageManager;
import com.mceteams.xii.model.Package;
import com.mceteams.xii.util.LocationUtil;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Logique métier des COLIS / packages (spec §17).
 *
 * Un colis = un coffre posé à un point ALÉATOIRE de la zone, rempli
 * avec la table de loot.
 *
 * OUVERTURE ANIMÉE : le clic sur un colis ouvre une GUI de 27 vitres
 * grises qui passent en vertes UNE PAR UNE en SPIRALE HORAIRE depuis
 * le coin haut-gauche jusqu'au centre (~5 secondes). Chaque vitre
 * joue un "pling" dont le pitch monte de +0.15 toutes les 2 vitres.
 * À la fin : points PACKAGE (+ RARE_ITEM éventuel) et accès au coffre.
 *
 * Annonces :
 * - spawn          : chat + titre avec coordonnées ;
 * - récupération   : chat SANS nom ("Un colis a été récupéré !") ;
 * - objet rare     : chat AVEC le nom du joueur (idem légendaire plus tard).
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
     */
    public void spawnRandomPackage() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return; // mécanique limitée à la préparation
        }

        // Position résolue une fois le chunk chargé (thread principal).
        LocationUtil.randomSurfaceInAsync(zone, this::placePackageAt);
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

        // Annonce CHAT + TITRE pour tous les joueurs (coordonnées partout,
        // en BLEU CIEL).
        String coords = "§b(" + block.getX() + " " + block.getY()
                + " " + block.getZ() + ")";
        MessageUtil.broadcast("§e✦ §fUn colis est apparu §7! " + coords);
        for (Player online : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendTitle(online,
                    "§e§lCOLIS",
                    "§7Un colis est apparu " + coords,
                    10, 50, 10);
            com.mceteams.xii.util.SoundUtil.play(online,
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        }
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

        // Objet rare : une UPGRADE (ou le TOTEM) tirée selon la rareté :
        // Commun 60% · Rare 30% · Épique 9% · Légendaire 1%.
        // TAGGÉ PDC => droppé à la mort du porteur.
        if (rare) {
            ItemStack rareItem = rollRareUpgradeItem();
            chest.getInventory().setItem(0, rareItem);
        }
        chest.update();
        return rare;
    }

    /**
     * Tire un objet upgrade pondéré par rareté et construit son item.
     */
    private ItemStack rollRareUpgradeItem() {
        double roll = random.nextDouble();
        com.mceteams.xii.enums.ItemRarity rarity =
                roll < 0.60 ? com.mceteams.xii.enums.ItemRarity.COMMON
                : roll < 0.90 ? com.mceteams.xii.enums.ItemRarity.RARE
                : roll < 0.99 ? com.mceteams.xii.enums.ItemRarity.EPIC
                : com.mceteams.xii.enums.ItemRarity.LEGENDARY;

        // Candidats de cette rareté (le légendaire = uniquement le Totem).
        List<com.mceteams.xii.enums.PlayerUpgrade> candidates = new ArrayList<>();
        for (com.mceteams.xii.enums.PlayerUpgrade upgrade :
                com.mceteams.xii.enums.PlayerUpgrade.values()) {
            if (upgrade.getRarity() == rarity) {
                candidates.add(upgrade);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(com.mceteams.xii.enums.PlayerUpgrade.VITALITE);
        }

        com.mceteams.xii.enums.PlayerUpgrade chosen =
                candidates.get(random.nextInt(candidates.size()));
        return plugin.getUpgradeService().createItem(chosen);
    }

    // -----------------------------------------------------------------
    // Ouverture animée (spirale horaire, ~5 secondes)
    // -----------------------------------------------------------------

    /**
     * Démarre l'animation d'ouverture pour un joueur : GUI 3x9 remplie
     * de vitres grises qui verdissent en spirale horaire depuis le coin
     * haut-gauche jusqu'au centre. À la fin, les points sont attribués
     * et le VRAI coffre s'ouvre.
     */
    public void startOpeningAnimation(Player player, Package pack) {
        Inventory gui = Bukkit.createInventory(
                new OpeningHolder(pack.getId()), 27, "§eOuverture du colis...");

        // Remplissage initial : vitres gris foncé.
        ItemStack gray = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            gui.setItem(slot, gray);
        }
        player.openInventory(gui);

        // Ordre spirale horaire (haut-gauche -> centre).
        List<Integer> spiralOrder = spiralOrder27();
        ItemStack lime = new ItemStack(Material.LIME_STAINED_GLASS_PANE);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                // Le joueur a fermé l'inventaire / s'est déconnecté :
                // animation annulée, le colis reste intact.
                if (!player.isOnline()
                        || !(player.getOpenInventory().getTopInventory()
                        .getHolder() instanceof OpeningHolder holder)
                        || !holder.packageId().equals(pack.getId())) {
                    cancel();
                    return;
                }

                if (step >= spiralOrder.size()) {
                    cancel();
                    finishOpening(player, pack, gui);
                    return;
                }

                // Une vitre passe au vert + pling montant.
                gui.setItem(spiralOrder.get(step), lime);
                float pitch = 0.5f + (step / 2) * 0.15f; // +0.15 toutes les 2 vitres
                com.mceteams.xii.util.SoundUtil.play(player,
                        Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L); // 27 x 2 ticks = ~5.4 secondes
    }

    /**
     * Fin d'animation : attribution des points puis TRANSFERT PROGRESSIF
     * du contenu du coffre vers l'inventaire du joueur.
     */
    private void finishOpening(Player player, Package pack, Inventory gui) {
        handleOpen(player, pack);

        // Petite pose finale puis bascule vers le transfert item par item.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Block chestBlock = pack.getLocation().getBlock();
            if (chestBlock.getState() instanceof Chest) {
                startTransfer(player, pack);
            } else {
                player.closeInventory(); // coffre détruit entre-temps
            }
        }, 10L);
    }

    // -----------------------------------------------------------------
    // Transfert progressif du contenu (un item à la fois)
    // -----------------------------------------------------------------

    /**
     * Session de transfert d'un colis vers un joueur :
     * - les CLICS sont interdits (rien n'est prenable à la main) ;
     * - les items passent AUTOMATIQUEMENT un par un dans l'inventaire ;
     * - si le joueur FERME l'inventaire => tout le reste est donné
     *   directement et le COFFRE EST SUPPRIMÉ.
     */
    private static final class TransferSession {
        final Inventory chestInventory;
        final Location chestLocation;
        BukkitRunnable task;

        TransferSession(Inventory chestInventory, Location chestLocation) {
            this.chestInventory = chestInventory;
            this.chestLocation = chestLocation;
        }
    }

    /** Sessions actives : joueur -> transfert en cours. */
    private final java.util.Map<UUID, TransferSession> transfers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Ce joueur a-t-il un transfert de colis en cours ? */
    public boolean isTransferring(UUID playerUuid) {
        return transfers.containsKey(playerUuid);
    }

    /**
     * Le joueur s'est fait TAPER pendant qu'il avait une GUI de colis
     * ouverte (animation ou transfert) => tout se ferme :
     * - animation : annulée, le colis reste intact (à refaire) ;
     * - transfert  : le reste est donné directement + coffre supprimé.
     * Appelé par PackageListener sur EntityDamageEvent.
     */
    public void handleHitDuringGui(Player player) {
        // Transfert en cours -> clôture comme une fermeture prématurée.
        TransferSession session = transfers.get(player.getUniqueId());
        if (session != null) {
            handleTransferClose(player, session.chestInventory);
            Bukkit.getScheduler().runTask(plugin,
                    (Runnable) player::closeInventory);
            return;
        }

        // Animation en cours -> fermeture simple ; la task d'animation
        // se rend compte seule que l'inventaire n'est plus le sien et
        // s'annule (le colis reste non revendiqué).
        var top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof OpeningHolder) {
            player.closeInventory();
        }
    }

    /** Cet inventaire est-il celui d'un transfert actif ? */
    public boolean isTransferInventory(Inventory inventory) {
        return transfers.values().stream()
                .anyMatch(s -> s.chestInventory.equals(inventory));
    }

    /**
     * Démarre le transfert automatique du contenu du coffre vers le
     * joueur (un item toutes les 2 ticks, avec son de ramassage).
     */
    private void startTransfer(Player player, Package pack) {
        // Sécurité : un autre transfert sur CE coffre ? Ouverture normale.
        boolean chestAlreadyClaimed = transfers.values().stream()
                .anyMatch(s -> s.chestLocation.equals(pack.getLocation()));
        Block chestBlock = pack.getLocation().getBlock();
        if (!(chestBlock.getState() instanceof Chest chest)) {
            return;
        }
        if (chestAlreadyClaimed) {
            player.openInventory(chest.getInventory());
            return;
        }

        TransferSession session = new TransferSession(
                chest.getInventory(), pack.getLocation());
        transfers.put(player.getUniqueId(), session);
        player.openInventory(session.chestInventory);

        session.task = new BukkitRunnable() {
            @Override
            public void run() {
                // Joueur parti (déco) : items jetés au sol au coffre,
                // coffre supprimé, session nettoyée.
                if (!player.isOnline()) {
                    dumpAndRemoveChest(session);
                    return;
                }

                // Premier item disponible dans le coffre ?
                int sourceSlot = -1;
                ItemStack moving = null;
                for (int slot = 0; slot < session.chestInventory.getSize(); slot++) {
                    ItemStack candidate = session.chestInventory.getItem(slot);
                    if (candidate != null && !candidate.getType().isAir()) {
                        sourceSlot = slot;
                        moving = candidate;
                        break;
                    }
                }

                if (moving == null) {
                    // Coffre vide : fin propre.
                    completeTransfer(player, session);
                    return;
                }

                // Transfert d'UN stack + son de pickup.
                session.chestInventory.setItem(sourceSlot, null);
                var leftovers = player.getInventory().addItem(moving);
                leftovers.values().forEach(rest ->
                        player.getWorld().dropItemNaturally(player.getLocation(), rest));
                com.mceteams.xii.util.SoundUtil.play(player,
                        Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.0f);
            }
        };
        session.task.runTaskTimer(plugin, 2L, 2L);
    }

    /** Fin réussie : fermeture + suppression physique du coffre. */
    private void completeTransfer(Player player, TransferSession session) {
        cancelSession(session);
        transfers.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
        removeChestBlock(session);
        MessageUtil.sendActionBar(player, "§aColis entièrement récupéré !");
    }

    /**
     * Fermeture PRÉMATURÉE par le joueur : tout le reste est donné
     * directement puis le coffre disparaît. Appelé par InventoryListener.
     */
    public void handleTransferClose(Player player, Inventory closedInventory) {
        TransferSession session = transfers.get(player.getUniqueId());
        if (session == null || !session.chestInventory.equals(closedInventory)) {
            return; // pas notre transfert
        }
        cancelSession(session);
        transfers.remove(player.getUniqueId());

        for (int slot = 0; slot < session.chestInventory.getSize(); slot++) {
            ItemStack item = session.chestInventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                session.chestInventory.setItem(slot, null);
                var leftovers = player.getInventory().addItem(item);
                leftovers.values().forEach(rest ->
                        player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
        removeChestBlock(session);
        MessageUtil.sendActionBar(player, "§aColis récupéré !");
    }

    /** Joueur déconnecté pendant le transfert : items jetés au sol. */
    private void dumpAndRemoveChest(TransferSession session) {
        cancelSession(session);
        for (int slot = 0; slot < session.chestInventory.getSize(); slot++) {
            ItemStack item = session.chestInventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                session.chestInventory.setItem(slot, null);
                session.chestLocation.getWorld()
                        .dropItemNaturally(session.chestLocation, item);
            }
        }
        removeChestBlock(session);
    }

    /** Arrête la task d'une session (best-effort). */
    private void cancelSession(TransferSession session) {
        if (session.task != null) {
            try {
                session.task.cancel();
            } catch (IllegalStateException ignored) {
                // pas schedulée / déjà annulée
            }
        }
    }

    /** Supprime physiquement le bloc coffre du colis. */
    private void removeChestBlock(TransferSession session) {
        if (session.chestLocation.getBlock().getType() == Material.CHEST) {
            session.chestLocation.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Traite l'ouverture (points + annonces). Idempotent : un colis ne
     * peut être revendiqué qu'une fois.
     */
    public void handleOpen(Player opener, Package pack) {
        if (pack.isOpened()) {
            return; // déjà revendiqué par quelqu'un d'autre
        }
        pack.setOpened(true);
        plugin.getPackageManager().unregister(pack.getId());

        // Points PACKAGE pour l'ouvreur (multiplicateurs via PointService).
        plugin.getPointService().award(opener, PointCategory.PACKAGE,
                plugin.getConfigManager().getPackagePoints(), "colis ouvert");

        // Annonce SANS nom : juste l'information que le colis est parti.
        MessageUtil.broadcast("§7✦ Un colis a été §frécupéré§7.");

        // Bonus éventuel : objet rare (AVEC le nom du chanceux).
        if (pack.containsRareItem()) {
            plugin.getPointService().award(opener, PointCategory.RARE_ITEM,
                    plugin.getConfigManager().getRareItemPoints(), "objet rare");
            MessageUtil.broadcast("§6★ " + opener.getName()
                    + " §7a trouvé un objet §e§lRARE§7 !");
            // TODO : objet LÉGENDAIRE (même pattern, annonce dédiée plus tard).
        } else {
            MessageUtil.sendActionBar(opener, "§aColis récupéré !");
        }
    }

    /**
     * Le spawn AUTOMATIQUE est-il autorisé maintenant ?
     * Uniquement pendant les sous-phases dédiées (jour 2,4,5,6) - PAS au
     * jour 1 (START) ni au jour 3 (DUNGEONS).
     *
     * NB : l'OUVERTURE des colis déjà posés reste possible pendant TOUTE
     * la préparation (flag packageEnabled global) ; ceci ne concerne que
     * l'apparition par le PackageTask.
     */
    public boolean canSpawnNow() {
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return false;
        }
        var sub = plugin.getPhaseManager().getPreparationSubPhase();
        return sub == com.mceteams.xii.enums.PreparationSubPhase.PACKAGES
                || sub == com.mceteams.xii.enums.PreparationSubPhase.POINT_UPGRADES
                || sub == com.mceteams.xii.enums.PreparationSubPhase.PACKAGE_UPGRADE
                || sub == com.mceteams.xii.enums.PreparationSubPhase.DUNGEON_RESTOCK;
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
        boolean upgraded =
                phaseManager.getPhase() == com.mceteams.xii.enums.GamePhase.PREPARATION
                && phaseManager.getPreparationSubPhase()
                        == com.mceteams.xii.enums.PreparationSubPhase.PACKAGE_UPGRADE;
        if (upgraded) {
            double factor = plugin.getConfigManager().getPackageUpgradeFactor();
            min = (int) Math.max(1, min / factor);
            max = (int) Math.max(min + 1, max / factor);
        }
        return min + rng.nextInt(Math.max(1, max - min));
    }

    /**
     * Ordre de traversal EN SPIRALE HORAIRE d'une grille 3x9 :
     * rangée haut gauche->droite, bord droit haut->bas, rangée bas
     * droite->gauche, bord gauche bas->haut, puis spirale interne...
     * jusqu'au centre. Correspond exactement à la demande visuelle.
     */
    static List<Integer> spiralOrder27() {
        List<Integer> order = new ArrayList<>(27);
        final int rows = 3;
        final int cols = 9;
        int rStart = 0, rEnd = rows - 1, cStart = 0, cEnd = cols - 1;

        while (rStart <= rEnd && cStart <= cEnd) {
            for (int c = cStart; c <= cEnd; c++) {           // haut : -> 
                order.add(rStart * cols + c);
            }
            rStart++;
            for (int r = rStart; r <= rEnd; r++) {           // droite : v
                order.add(r * cols + cEnd);
            }
            cEnd--;
            if (rStart <= rEnd) {
                for (int c = cEnd; c >= cStart; c--) {       // bas : <-
                    order.add(rEnd * cols + c);
                }
                rEnd--;
            }
            if (cStart <= cEnd) {
                for (int r = rEnd; r >= rStart; r--) {       // gauche : ^
                    order.add(r * cols + cStart);
                }
                cStart++;
            }
        }
        return order;
    }

    /**
     * Holder identifiable de la GUI d'ouverture (pattern InventoryHolder) :
     * permet à InventoryListener d'annuler tous les clics dedans et de
     * détecter une fermeture prématurée. Conserve l'id du colis visé.
     */
    public record OpeningHolder(UUID packageId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // jamais utilisé : le holder sert de marqueur
        }
    }
}
