package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.model.PlayerData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Application des règles des CLASSES (spec §31).
 *
 * Les passifs sont appliqués :
 * - au moment du choix (sélection) ;
 * - à l'entrée en préparation ;
 * - après chaque respawn.
 *
 * Détail par classe :
 * - MINEUR    : fonte auto (MiningService) + RANGÉE DU HAUT verrouillée
 *               par des BARRIères non déplaçables (slots 9..17), qui se
 *               remettent même après un /clear (sweep chaque seconde).
 * - TRAVAILLEUR : +25% points équipe (PointService) + 10 PV
 * - ROBUSTE   : 15 PV, dégâts x0.85 et vitesse x0.85
 * - AGILE     : vitesse x1.2, aucun fall damage
 * - GUERRIER  : dégâts x1.25 + 14 PV
 */
public class ClassService {

    /**
     * MALUS MINEUR : la rangée du HAUT de l'inventaire (première ligne
     * visible du panneau, slots 9 à 17) est occupée par des items
     * BARRIERE insortables (tag PDC => protégés par InventoryListener)
     * et restaurés après /clear grâce au sweep + réapplications.
     */
    public static final int MINER_LOCKED_ROW_START = 9;
    public static final int MINER_LOCKED_ROW_END = 17;

    /** Type interne PDC des barrières du Mineur. */
    public static final String MINER_BARRIER_TYPE = "miner_barrier";

    /**
     * Constantes vanilla EXACTES (au lieu de getDefaultValue() dont le
     * comportement s'est avéré peu fiable sur certains builds récents :
     * c'était la cause du "speed anormal" du Guerrier).
     */
    private static final double VANILLA_MAX_HEALTH = 20.0;
    private static final double VANILLA_MOVEMENT_SPEED = 0.10000000149011612D;
    private static final double VANILLA_ATTACK_SPEED = 4.0;

    private final XiiPlugin plugin;

    public ClassService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applique les passifs d'ATTRIBUTS + effets d'INVENTAIRE de la
     * classe courante. Idempotent : repart toujours des constantes
     * vanilla et pose/retire les barrières du Mineur.
     *
     * NB : fonctionne même SANS classe choisie (remet tout à zéro),
     * ce qui permet le nettoyage lors des resets.
     */
    public void applyPassives(Player player, PlayerData data) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerClass playerClass = data.getPlayerClass(); // peut être null

        // --- Vie maximale ------------------------------------------
        // + UPGRADE Vitalité : +2 PV par niveau.
        int vitality = data.getUpgradeLevel(com.mceteams.xii.enums.PlayerUpgrade.VITALITE);
        double maxHealth = playerClass == null ? VANILLA_MAX_HEALTH
                : switch (playerClass) {
                    case WORKER -> 10.0;   // Travailleur : 5 coeurs / 10 PV
                    case TANK -> 30.0;     // Robuste : 15 COEURS / 30 PV
                    case WARRIOR -> 14.0;  // Guerrier : 7 coeurs / 14 PV
                    default -> VANILLA_MAX_HEALTH;
                };
        maxHealth += 2.0 * vitality;
        AttributeInstance maxHealthAttribute =
                player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }

        // --- Vitesse -------------------------------------------------
        // + UPGRADES Agilité (+5%/niv) et Endurance (+3%/niv, adaptation :
        // la faim étant gelée en compétition, son effet vanilla est nul).
        double speedMultiplier = playerClass == null ? 1.0
                : switch (playerClass) {
                    case AGILE -> 1.20;    // Agile : +20%
                    case TANK -> 0.85;     // Robuste : -15%
                    default -> 1.0;
                };
        speedMultiplier *= 1.0 + 0.05 * data.getUpgradeLevel(
                com.mceteams.xii.enums.PlayerUpgrade.AGILITE);
        speedMultiplier *= 1.0 + 0.03 * data.getUpgradeLevel(
                com.mceteams.xii.enums.PlayerUpgrade.ENDURANCE);
        AttributeInstance speedAttribute =
                player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(VANILLA_MOVEMENT_SPEED * speedMultiplier);
        }

        // --- Vitesse d'attaque ------------------------------------------
        // UPGRADE Frappe rapide : +10% par niveau.
        int attackSpeedLevel = data.getUpgradeLevel(
                com.mceteams.xii.enums.PlayerUpgrade.FRAPPE_RAPIDE);
        AttributeInstance attackSpeedAttribute =
                player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttribute != null) {
            attackSpeedAttribute.setBaseValue(
                    VANILLA_ATTACK_SPEED * (1.0 + 0.10 * attackSpeedLevel));
        }

        // --- Barrières Mineur (pose si mineur / retire sinon) ----------
        refreshMinerBarriers(player, playerClass == PlayerClass.MINER);
    }

    /**
     * Pose les barrières si Mineur, les RETIRE sinon (un changement de
     * classe ne doit pas laisser de barrières fantômes).
     */
    public void refreshMinerBarriers(Player player, boolean isMiner) {
        var inventory = player.getInventory();
        boolean changed = false;

        for (int slot = MINER_LOCKED_ROW_START; slot <= MINER_LOCKED_ROW_END; slot++) {
            ItemStack current = inventory.getItem(slot);
            boolean currentIsBarrier = com.mceteams.xii.util.ItemUtil
                    .isType(current, MINER_BARRIER_TYPE);

            if (isMiner && !currentIsBarrier) {
                inventory.setItem(slot, buildBarrier());
                changed = true;
            } else if (!isMiner && currentIsBarrier) {
                inventory.setItem(slot, null);
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /** L'item barrière standard du Mineur (tag PDC => insortable). */
    private ItemStack buildBarrier() {
        return com.mceteams.xii.util.ItemUtil.tag(
                com.mceteams.xii.util.ItemUtil.buildNamedItem(
                        org.bukkit.Material.BARRIER,
                        "§c§lEmplacement verrouillé",
                        java.util.List.of("§7Malus de la classe §6Mineur")),
                MINER_BARRIER_TYPE);
    }

    /**
     * Le fall damage doit-il être annulé pour ce joueur ? (Agile)
     */
    public boolean shouldCancelFallDamage(Player player) {
        return plugin.getPlayerManager().getData(player)
                .getPlayerClass() == PlayerClass.AGILE;
    }

    /**
     * Ce slot fait-il partie de la rangée verrouillée du Mineur ?
     * (slots 9 à 17 = rangée du HAUT de l'inventaire principal).
     */
    public boolean isMinerLockedSlot(int slot) {
        return slot >= MINER_LOCKED_ROW_START && slot <= MINER_LOCKED_ROW_END;
    }

/**
     * Passe de nettoyage chaque seconde (PhaseTask) :
     * - Mineur     : barrières présentes, AUCUN objet étranger dans la
     *                ligne => les objets étrangers sont DROP au sol au lieu
     *                d'être supprimés (couvre le cas du /clear).
     * - non-Mineur : barrières retirées.
     */
    public void sweepMinerLockedRow() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerManager().getData(player);
            boolean isMiner = data.getPlayerClass() == PlayerClass.MINER;
            var inventory = player.getInventory();
            boolean changed = false;

            for (int slot = MINER_LOCKED_ROW_START; slot <= MINER_LOCKED_ROW_END; slot++) {
                ItemStack item = inventory.getItem(slot);
                boolean isBarrier = com.mceteams.xii.util.ItemUtil
                        .isType(item, MINER_BARRIER_TYPE);

                if (isMiner) {
                    if (!isBarrier) {
                        // Objet étranger OU barrière manquante (/clear) :
                        // on drop l'éventuel objet au sol et on restaure la barrière.
                        if (item != null && !item.getType().isAir()) {
                            inventory.setItem(slot, null);
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                        }
                        inventory.setItem(slot, buildBarrier());
                        changed = true;
                    }
                } else if (isBarrier) {
                    inventory.setItem(slot, null);
                    changed = true;
                }
            }
            if (changed) {
                player.updateInventory();
            }
        }
    }
}
