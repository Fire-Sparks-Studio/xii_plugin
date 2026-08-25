package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Surveillance du COMBAT (spec §18).
 *
 * Le listener détecte les coups joueur->joueur, délègue le calcul à
 * CombatService (classes + MORE_DAMAGE) puis applique les dégâts finaux.
 * Aucun calcul ici : extraction + délégation (spec §2).
 */
public class CombatListener implements Listener {

    private final XiiPlugin plugin;

    public CombatListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système combat actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isCombatListenerEnabled();
    }

    /**
     * Coup joueur -> joueur : calcul des dégâts via CombatService.
     * Priorité HIGH : après ProtectionListener/TeamListener qui ont déjà
     * annulé les coups illégaux.
     *
     * FIX : l'attaquant est résolu AUSSI via projectile (flèche, trident...)
     * sinon tout le pipeline était contourné à l'arc (dégâts dans les
     * bases en préparation notamment).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!systemEnabled()) {
            return;
        }
        Player attacker = com.mceteams.xii.util.DamageUtil
                .resolveAttacker(event.getDamager());
        if (!(event.getEntity() instanceof Player victim) || attacker == null) {
            return;
        }
        // Un spectateur ne peut PAS frapper (invisible mais parfois
        // en survie : on bloque à la source).
        if (plugin.getProtectionService().isSpectator(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
            return;
        }

        // Délégation complète du calcul au service.
        boolean cancel =
                plugin.getCombatService().handleDamage(attacker, victim, event.getDamage());
        if (cancel) {
            event.setCancelled(true);
            return;
        }

        // Application des dégâts recalculés (classe + sous-phase).
        double computed = plugin.getCombatService().consumePendingDamage(victim);
        if (computed > 0) {
            event.setDamage(computed);
        }

        // UPGRADE Garde : résistance temporaire après avoir encaissé.
        tryApplyGarde(victim);
    }

    /**
     * Fall damage : annulé pour la classe AGILE (spec §31).
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!systemEnabled()) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getClassService().shouldCancelFallDamage(player)) {
            event.setCancelled(true); // Agile : aucun fall damage
        }
    }

    // -----------------------------------------------------------------
    // UPGRADES défensives : Résistance / Pas léger / Garde
    // -----------------------------------------------------------------

    /** Dernière proc Garde par joueur (anti-up-time permanent). */
    private final java.util.Map<java.util.UUID, Long> gardeLast =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Réductions de dégâts par upgrade, sur TOUTES les sources :
     * - RÉSISTANCE : -5% par niveau ;
     * - PAS LÉGER  : -25% par niveau sur le fall damage (III = immunisé).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageUpgrades(EntityDamageEvent event) {
        if (!systemEnabled() || event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        var data = plugin.getPlayerManager().getData(victim);

        // Résistance : réduction globale.
        int resistance = data.getUpgradeLevel(
                com.mceteams.xii.enums.PlayerUpgrade.RESISTANCE);
        if (resistance > 0) {
            event.setDamage(event.getDamage() * (1.0 - 0.05 * resistance));
        }

        // Pas léger : réduction du fall uniquement.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            int pasLeger = data.getUpgradeLevel(
                    com.mceteams.xii.enums.PlayerUpgrade.PAS_LEGER);
            if (pasLeger >= 3) {
                event.setCancelled(true);   // III : immunisé totalement
            } else if (pasLeger > 0) {
                event.setDamage(event.getDamage() * (1.0 - 0.25 * pasLeger));
            }
        }
    }

    /**
     * GARDE : après avoir REÇU un coup joueur->joueur, résistance
     * temporaire dont la durée dépend du niveau. Cooldown configuré.
     */
    private void tryApplyGarde(Player victim) {
        var data = plugin.getPlayerManager().getData(victim);
        int garde = data.getUpgradeLevel(
                com.mceteams.xii.enums.PlayerUpgrade.GARDE);
        if (garde < 1) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfigManager()
                .getGardeCooldownSeconds() * 1000L;
        Long last = gardeLast.get(victim.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        gardeLast.put(victim.getUniqueId(), now);

        // Niveau I : Resist I 3s · II : Resist I 5s · III : Resist II 5s.
        int durationTicks = garde >= 2 ? 100 : 60;
        int amplifier = garde >= 3 ? 1 : 0;
        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.RESISTANCE,
                durationTicks, amplifier, true, false));
        com.mceteams.xii.util.MessageUtil.sendActionBar(victim,
                "§b🛡 Garde activée !");
    }
}
