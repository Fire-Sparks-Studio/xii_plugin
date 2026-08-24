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

    private PlayerUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Remet le joueur dans un état Minecraft "neuf" :
     * vie/nourriture/xp pleins, effets et feu retirés, mode survie,
     * invulnérabilité désactivée, chute annulée, inventaire vidé.
     */
    public static void reset(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Vie : on repasse par l'attribut pour couvrir les classes
        // qui modifient la vie max (Tank 15 PV, Guerrier 14 PV...).
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double base = maxHealthAttr.getDefaultValue();
            maxHealthAttr.setBaseValue(base);
            player.setHealth(base);
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
