package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
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
 * - après chaque respawn (les attributs ont été réinitialisés).
 *
 * Détail par classe :
 * - MINEUR    : fonte auto (gérée par MiningService) + ligne verrouillée
 * - TRAVAILLEUR : +25% points équipe (géré par PointService) + 10 PV
 * - ROBUSTE   : 15 PV, dégâts x0.85 et vitesse x0.85
 *               (dégâts gérés par CombatService)
 * - AGILE     : vitesse x1.2, aucun fall damage (annulé dans les listeners)
 * - GUERRIER  : dégâts x1.25 (CombatService) + 14 PV
 */
public class ClassService {

    /** Slot bas de la rangée verrouillée du Mineur : slots 27..35. */
    public static final int MINER_LOCKED_ROW_START = 27;
    public static final int MINER_LOCKED_ROW_END = 35;

    private final XiiPlugin plugin;

    public ClassService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applique les passifs d'ATTRIBUTS de la classe du joueur.
     * Appelable plusieurs fois : on repart toujours des valeurs par défaut.
     */
    public void applyPassives(Player player, PlayerData data) {
        if (player == null || !player.isOnline() || !data.hasClass()) {
            return;
        }
        PlayerClass playerClass = data.getPlayerClass();

        // --- Vie maximale ------------------------------------------
        double maxHealth = switch (playerClass) {
            case WORKER -> 10.0;   // Travailleur : 5 coeurs / 10 PV
            case TANK -> 15.0;     // Robuste : 15 PV
            case WARRIOR -> 14.0;  // Guerrier : 7 coeurs / 14 PV
            default -> 20.0;       // valeur vanilla pour les autres
        };
        AttributeInstance maxHealthAttribute =
                player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }

        // --- Vitesse -------------------------------------------------
        double speedMultiplier = switch (playerClass) {
            case AGILE -> 1.20;    // Agile : +20% vitesse
            case TANK -> 0.85;     // Robuste : -15% vitesse
            default -> 1.0;
        };
        AttributeInstance speedAttribute =
                player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            double base = speedAttribute.getDefaultValue();
            speedAttribute.setBaseValue(base * speedMultiplier);
        }
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
     * (inventaire principal : slots 27 à 35 = dernière ligne).
     */
    public boolean isMinerLockedSlot(int slot) {
        return slot >= MINER_LOCKED_ROW_START && slot <= MINER_LOCKED_ROW_END;
    }

    /**
     * Passe de nettoyage : retire tout objet qui traînerait dans la
     * rangée verrouillée d'un Mineur (appelé chaque seconde par PhaseTask).
     */
    public void sweepMinerLockedRow() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerManager().getData(player);
            if (data.getPlayerClass() != PlayerClass.MINER) {
                continue;
            }
            var inventory = player.getInventory();
            boolean changed = false;
            for (int slot = MINER_LOCKED_ROW_START; slot <= MINER_LOCKED_ROW_END; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    inventory.setItem(slot, null);
                    changed = true;
                }
            }
            if (changed) {
                player.updateInventory();
            }
        }
    }

    /**
     * La sélection de classe est-elle actuellement ouverte ? (état du jeu)
     */
    public boolean isSelectionActive() {
        return plugin.getGameManager().getState() == GameState.CLASS_SELECTION;
    }
}
