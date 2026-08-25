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
import java.util.Collections;
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
 * - largage        : chat avec les coordonnées cibles ;
 * - récupération   : chat SANS nom ("Un colis a été récupéré !") ;
 * - objet rare     : chat AVEC le nom du joueur (idem légendaire plus tard).
 *
 * VOLONTAIREMENT SILENCIEUX à l'atterrissage : le colis tombe sous les
 * yeux des joueurs et les coordonnées sont données au largage ; aucun
 * message/titre/son ne confirme la pose du coffre.
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

        // Surface SÈCHE garantie (jamais sur/dans l'eau), chargée async.
        com.mceteams.xii.util.LocationUtil.randomDrySurfaceInAsync(zone,
                this::launchParachuteDrop);
    }

    // -----------------------------------------------------------------
    // DROP EN PARACHUTE
    // -----------------------------------------------------------------

    /**
     * DEBUG : lance un colis en parachute au-dessus d'une position DONNÉE
     * (généralement celle d'un administrateur) sans aucune recherche de
     * surface sèche. Permet de tester visuellement la descente immédiatement.
     */
    public void spawnDebugAbove(Location groundSpot) {
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return;
        }
        launchParachuteDrop(groundSpot);
    }

    /** Tag du bloc coffre en chute. */
    public static final String FALLING_TAG = "xii_package_falling";
    /** Tag du stand-parachute au-dessus du coffre. */
    public static final String PARACHUTE_TAG = "xii_package_parachute";

    /** Tâches de chute lente par entité FallingBlock. */
    private final java.util.Map<UUID, org.bukkit.scheduler.BukkitRunnable> slowFallTasks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Lance un coffre en chute avec PARACHUTE depuis le ciel vers la
     * surface sèche résolue :
     * - FallingBlock CHEST taggué, descente freinée (~0.18/tick) ;
     * - ArmorStand INVISIBLE coiffé d'une BANNIÈRE blanche = parachute
     *   (un stand invisible rend toujours son équipement) ;
     * - à l'atterrissage (WorldListener -> handlePackageLanded) :
     *   remplissage du loot + enregistrement, sans annonce.
     *
     * Le chunk cible est chargé ASYNC avant le spawn : sans ça, un colis
     * lâché loin des joueurs tombait dans un chunk non chargé (chute
     * invisible, coffre disparu).
     */
    private void launchParachuteDrop(Location ground) {
        if (ground == null) {
            return;
        }
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return;
        }
        var world = ground.getWorld();

        world.getChunkAtAsync(
                ground.getBlockX() >> 4, ground.getBlockZ() >> 4)
                .thenAccept(chunk -> {
                    // Spawn d'entités : TOUJOURS sur le thread principal.
                    if (org.bukkit.Bukkit.isPrimaryThread()) {
                        dropWithParachute(ground);
                    } else {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> dropWithParachute(ground));
                    }
                });
    }

    /** Spawn effectif (thread principal, chunk cible chargé). */
    private void dropWithParachute(Location ground) {
        if (ground == null
                || plugin.getGameManager().getState() != GameState.PREPARATION) {
            return;
        }
        var world = ground.getWorld();

        Location start = ground.clone().add(0, 30, 0);

        org.bukkit.entity.FallingBlock falling = world.spawn(start,
                org.bukkit.entity.FallingBlock.class, fb -> {
                    fb.setBlockData(Material.CHEST.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.addScoreboardTag(FALLING_TAG);
                });

        // Parachute : stand INVISIBLE coiffé d'une bannière blanche.
        // Un armor stand invisible continue de rendre son équipement :
        // seule la bannière flotte au-dessus du coffre.
        org.bukkit.entity.ArmorStand parachute = world.spawn(
                start.clone().add(0, 1.4, 0),
                org.bukkit.entity.ArmorStand.class, as -> {
                    as.setVisible(false);
                    as.setSmall(true);
                    as.setGravity(false);
                    as.setBasePlate(false);
                    as.setArms(false);
                    as.setInvulnerable(true);
                    as.setMarker(true);
                    as.getEquipment().setHelmet(
                            new ItemStack(Material.WHITE_BANNER));
                    as.addScoreboardTag(PARACHUTE_TAG);
                });

        // Descente freinée : on maintient une vitesse de chute douce
        // et le parachute reste soudé au coffre pendant toute la descente.
        UUID fallingId = falling.getUniqueId();
        org.bukkit.scheduler.BukkitRunnable slow = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!falling.isValid()) {
                    cancel();
                    slowFallTasks.remove(fallingId);
                    return;
                }
                var v = falling.getVelocity();
                if (v.getY() < -0.18) {
                    v.setY(-0.18);
                    falling.setVelocity(v);
                }
                parachute.teleport(falling.getLocation().add(0, 1.3, 0));
            }
        };
        slow.runTaskTimer(plugin, 1L, 1L);
        slowFallTasks.put(fallingId, slow);

        // Annonce de LARGAGE : chat + TITRE pour tous (avec coordonnées).
        String coords = "§b(" + start.getBlockX() + " " + ground.getBlockY()
                + " " + start.getBlockZ() + ")";
        MessageUtil.broadcast("§e✦ §fUn colis descend du ciel §7! " + coords);
        for (Player online : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendTitle(online,
                    "§e§lCOLIS",
                    "§7Un colis descend du ciel " + coords,
                    10, 50, 10);
            com.mceteams.xii.util.SoundUtil.play(online,
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.9f);
        }
    }

    /**
     * ATTERRISSAGE (appelé par WorldListener sur EntityChangeBlockEvent) :
     * arrêt de la descente freinée, retrait du parachute, puis
     * finalisation SILENCIEUSE du colis (loot + enregistrement).
     */
    public void handlePackageLanded(org.bukkit.entity.FallingBlock falling,
                                    org.bukkit.event.entity.EntityChangeBlockEvent event) {
        org.bukkit.scheduler.BukkitRunnable slow =
                slowFallTasks.remove(falling.getUniqueId());
        if (slow != null) {
            try { slow.cancel(); } catch (IllegalStateException ignored) {}
        }

        Block landedBlock = event.getBlock();

        // Retire le parachute juste après l'impact : passager/stand
        // suivi par la task + balayage de proximité autour du coffre.
        java.util.List<org.bukkit.entity.Entity> riders =
                new java.util.ArrayList<>(falling.getPassengers());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            riders.forEach(org.bukkit.entity.Entity::remove);
            for (var entity : landedBlock.getWorld().getNearbyEntities(
                    landedBlock.getLocation().add(0.5, 1, 0.5), 2, 2, 2)) {
                if (entity.getScoreboardTags().contains(PARACHUTE_TAG)) {
                    entity.remove();
                }
            }
        }, 2L);

        // Hors préparation : retire simplement le coffre posé.
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> landedBlock.setType(Material.AIR));
            return;
        }

        // Finalisation au tick suivant (bloc réellement posé).
        Bukkit.getScheduler().runTask(plugin, () -> finalizePackageAt(landedBlock));
    }

    /**
     * Finalise un coffre de colis DÉJÀ POSÉ dans le monde :
     * remplissage loot + enregistrement. AUCUNE annonce : l'atterrissage
     * est volontairement silencieux (le largage a déjà été annoncé).
     */
    private void finalizePackageAt(Block block) {
        boolean containsRareItem = fillChest(block);

        Package pack = new Package(UUID.randomUUID(), block.getLocation(),
                containsRareItem);
        plugin.getPackageManager().register(pack);
    }

    /**
     * Remplit le coffre du colis via le SYSTÈME DE LOOT
     * (table sélectionnée par LootManager selon la sous-phase,
     * génération pondérée par LootService).
     *
     * @return true si le contenu contient un objet RARE/LÉGENDAIRE
     *         (upgrade Rare+ ou Totem) - utilisé pour les annonces.
     */
    private boolean fillChest(Block chestBlock) {
        BlockState state = chestBlock.getState();
        if (!(state instanceof Chest chest)) {
            return false;
        }

        List<ItemStack> loot = generateLootForPackages();

        // Placement aléatoire dans des slots distincts.
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.getInventory().getSize(); i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots, random);

        boolean containsRareOrLegendary = false;
        int slotIndex = 0;
        for (ItemStack stack : loot) {
            if (slotIndex >= slots.size()) {
                break; // table trop généreuse pour le coffre
            }
            chest.getInventory().setItem(slots.get(slotIndex++), stack);
            if (com.mceteams.xii.util.ItemUtil.isRareOrLegendary(stack)) {
                containsRareOrLegendary = true;
            }
        }
        chest.update();
        return containsRareOrLegendary;
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
                // Le joueur a fermé l'inventaire / s'est déconnecté /
                // s'est fait taper : chargement INTERROMPU => son dédié,
                // le colis reste intact (à refaire).
                if (!player.isOnline()
                        || !(player.getOpenInventory().getTopInventory()
                        .getHolder() instanceof OpeningHolder holder)
                        || !holder.packageId().equals(pack.getId())) {
                    if (player.isOnline()) {
                        playInterruption(player);
                    }
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
     * Génère le contenu d'un colis : table choisie par LootManager
     * (progression) + tirages pondérés par LootService.
     * TOUT est protégé : en cas d'échec, contenu de secours - un coffre
     * vide ne doit JAMAIS arriver. Chaque génération est loggée.
     */
    private List<ItemStack> generateLootForPackages() {
        var table = plugin.getLootManager().getPackageTable(
                plugin.getPhaseManager().getPreparationSubPhase());
        try {
            List<ItemStack> loot = plugin.getLootService().generate(table);
            plugin.getLogger().info("[Loot] " + table + " -> "
                    + loot.size() + " stack(s)");
            if (!loot.isEmpty()) {
                return loot;
            }
            plugin.getLogger().warning("[Loot] Table " + table
                    + " vide => contenu de secours.");
        } catch (Throwable throwable) {
            plugin.getLogger().severe("[Loot] Génération échouée ("
                    + table + ") : " + throwable);
            throwable.printStackTrace();
        }
        // Contenu de secours garanti non vide.
        List<ItemStack> fallback = new ArrayList<>();
        fallback.add(new ItemStack(Material.IRON_INGOT, 6));
        fallback.add(new ItemStack(Material.COAL, 10));
        fallback.add(new ItemStack(Material.BREAD, 3));
        return fallback;
    }

    /**
     * GARDE-FOU À L'OUVERTURE : si le coffre est vide au moment où le
     * joueur termine l'animation (quelle que soit la cause racine),
     * on RÉGÉNÈRE son contenu immédiatement.
     */
    private void ensureChestLoot(Inventory chestInventory) {
        if (chestInventory == null || !chestInventory.isEmpty()) {
            return;
        }
        plugin.getLogger().warning("[Loot] Coffre de colis VIDE à "
                + "l'ouverture => régénération.");
        List<ItemStack> loot = generateLootForPackages();
        int slot = 0;
        for (ItemStack stack : loot) {
            if (slot >= chestInventory.getSize()) {
                break;
            }
            chestInventory.setItem(slot++, stack);
        }
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

        // GARDE-FOU : coffre vide à l'ouverture => régénération immédiate.
        ensureChestLoot(chest.getInventory());

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

                // Premier item disponible : parcours GAUCHE -> DROITE,
                // LIGNE PAR LIGNE de HAUT EN BAS (slots 0..26 dans l'ordre,
                // PAS en spirale contrairement à l'animation d'ouverture).
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

                // Transfert d'UN stack + son "click" de DROPPER.
                session.chestInventory.setItem(sourceSlot, null);
                var leftovers = player.getInventory().addItem(moving);
                leftovers.values().forEach(rest ->
                        player.getWorld().dropItemNaturally(player.getLocation(), rest));
                com.mceteams.xii.util.SoundUtil.play(player,
                        Sound.BLOCK_DISPENSER_DISPENSE, 0.7f, 1.2f);
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
     * Son d'INTERRUPTION imprévue : DEUX plings joués EN MÊME TEMPS,
     * volontairement sur des gammes différentes (demi-ton de décalage)
     * => dissonance courte qui signe l'interruption.
     */
    private void playInterruption(Player player) {
        com.mceteams.xii.util.SoundUtil.play(player,
                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.944f);
        com.mceteams.xii.util.SoundUtil.play(player,
                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
    }

    /**
     * Fermeture PRÉMATURÉE par le joueur : les items RESTANTS sont
     * DROPpés AU SOL au coffre, puis le coffre disparaît.
     * Appelé par InventoryListener (fermeture) et PackageListener (coup).
     */
    public void handleTransferClose(Player player, Inventory closedInventory) {
        TransferSession session = transfers.get(player.getUniqueId());
        if (session == null || !session.chestInventory.equals(closedInventory)) {
            return; // pas notre transfert
        }
        cancelSession(session);
        transfers.remove(player.getUniqueId());

        // Son d'interruption + DROP AU SOL des items restants.
        playInterruption(player);
        for (int slot = 0; slot < session.chestInventory.getSize(); slot++) {
            ItemStack item = session.chestInventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                session.chestInventory.setItem(slot, null);
                session.chestLocation.getWorld()
                        .dropItemNaturally(session.chestLocation, item);
            }
        }
        removeChestBlock(session);
        MessageUtil.sendActionBar(player, "§cTransfert interrompu - colis au sol.");
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
