package com.mceteams.xii.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Résolution de l'ATTAQUANT RÉEL d'un coup (spec §18).
 *
 * Pour les PROJECTILES (flèche, trident, boule de neige...),
 * EntityDamageByEntityEvent#getDamager() renvoie le PROJECTILE et non
 * le joueur : sans résolution, tout le pipeline PvP (protection des
 * bases en préparation, friendly fire...) était contourné à l'arc.
 */
public final class DamageUtil {

    private DamageUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * @return le joueur à l'origine du coup : direct, ou tireur du
     * projectile ; null si aucun joueur n'est responsable (mob, environnement).
     */
    public static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
