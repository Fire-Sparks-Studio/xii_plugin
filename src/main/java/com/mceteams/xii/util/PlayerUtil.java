package com.mceteams.xii.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Utilitaires "joueur" : remise à zéro complète d'un état Bukkit.
 * Utilisé avant d'entrer en partie, au respawn, au retour au lobby...
 */
public final class PlayerUtil {

    /** Constantes vanilla (cf. ClassService) pour des resets exacts. */
    private static final double VANILLA_MAX_HEALTH = 20.0;
    private static final double VANILLA_MOVEMENT_SPEED = 0.10000000149011612D;

    private PlayerUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Remet le joueur dans un état Minecraft "neuf" :
     * vie/vitesse/saturations aux valeurs vanilla, effets et feu
     * retirés, mode survie, invulnérabilité désactivée, chute annulée,
     * inventaire vidé.
     */
    public static void reset(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Vie : constante vanilla (les classes réappliqueront leurs valeurs).
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(VANILLA_MAX_HEALTH);
            player.setHealth(VANILLA_MAX_HEALTH);
        }

        // Vitesse : remise au niveau vanilla également (sinon une vitesse
        // de classe précédente "collerait" au joueur après un reset).
        var speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(VANILLA_MOVEMENT_SPEED);
        }

        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setLevel(0);
        player.setExp(0f);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.updateInventory();
    }

    /**
     * Soigne le joueur sans toucher à son inventaire (respawn).
     */
    public static void heal(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    /**
     * Retire uniquement l'effet d'invisibilité (sortie de spectateur).
     */
    public static void removeInvisibility(Player player) {
        if (player != null) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }
}
