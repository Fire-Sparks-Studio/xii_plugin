package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerData;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.entity.Player;

/**
 * Logique métier du COMBAT (spec §18/§23/§30).
 *
 * Le CombatListener détecte les coups et délègue ICI :
 * - vérification friendly fire (jamais entre coéquipiers) ;
 * - application des multiplicateurs de dégâts des classes
 *   (Tank -15%, Guerrier +25%) et de la sous-phase MORE_DAMAGE (x2) ;
 * - tenue de la "fenêtre de combat" de 15 s pour qualifier les
 *   déconnexions (spec §30) ;
 * - enregistrement des kills / premier kill / kill streak d'équipe.
 */
public class CombatService {

    private final XiiPlugin plugin;
    /** Le premier kill de la partie a-t-il déjà été attribué ? */
    private boolean firstKillAwarded = false;

    public CombatService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Dégâts
    // -----------------------------------------------------------------

    /**
     * Traite un coup joueur -> joueur.
     *
     * @return true si le coup doit être annulé (friendly fire).
     */
    public boolean handleDamage(Player attacker, Player victim, double baseDamage) {
        PlayerData attackerData = plugin.getPlayerManager().getData(attacker);
        PlayerData victimData = plugin.getPlayerManager().getData(victim);

        // Friendly fire : jamais entre coéquipiers (double sécurité avec
        // l'équipe Bukkit, qui est la source primaire).
        GameTeam attackerTeam = plugin.getTeamManager().getTeamOf(attacker.getUniqueId());
        if (attackerTeam != null && attackerTeam.hasPlayer(victim.getUniqueId())) {
            return true;
        }

        double damage = computeDamage(attackerData, baseDamage);

        // Fenêtre de combat : la victime est "en combat" pendant 15 s.
        victimData.setLastDamager(attacker.getUniqueId());
        victimData.setLastDamageTime(System.currentTimeMillis());

        // On renvoie les dégâts calculés via le return pour que le
        // listener applique event.setDamage(damage).
        setPendingDamage(victim, damage);
        return false;
    }

    /**
     * Calcul PUR des dégâts finaux : classe de l'attaquant + MORE_DAMAGE.
     */
    public double computeDamage(PlayerData attackerData, double baseDamage) {
        double multiplier = 1.0;

        if (attackerData.getPlayerClass() == PlayerClass.TANK) {
            multiplier *= 0.85;   // Robuste : -15% dégâts infligés (§31)
        } else if (attackerData.getPlayerClass() == PlayerClass.WARRIOR) {
            multiplier *= 1.25;   // Guerrier : +25% dégâts infligés (§31)
        }
        if (plugin.getPhaseManager().getCombatSubPhase()
                == CombatSubPhase.MORE_DAMAGE) {
            multiplier *= 2.0;    // MORE_DAMAGE : x2 (§23)
        }
        return baseDamage * multiplier;
    }

    /** Champ transitoire : dernier dégât calculé par joueur. */
    private final java.util.Map<java.util.UUID, Double> pendingDamage =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void setPendingDamage(Player victim, double damage) {
        pendingDamage.put(victim.getUniqueId(), damage);
    }

    /**
     * @return les dégâts calculés par handleDamage (0 si aucun).
     */
    public double consumePendingDamage(Player victim) {
        Double damage = pendingDamage.remove(victim.getUniqueId());
        return damage == null ? 0 : damage;
    }

    /**
     * Ce joueur est-il dans la fenêtre de combat ? (spec §30)
     * Utilisé par ConnectionListener pour décider si une déconnexion
     * compte comme une mort.
     */
    public boolean isInCombatWindow(PlayerData data) {
        long windowMillis = plugin.getConfigManager().getCombatWindowSeconds() * 1000L;
        return data.wasRecentlyDamagedByPlayer(System.currentTimeMillis(), windowMillis);
    }

    // -----------------------------------------------------------------
    // Kills
    // -----------------------------------------------------------------

    /**
     * Enregistre un kill attribué (appelé par DeathService).
     *
     * @param killer tueur (peut être null : mort environnementale).
     */
    public void registerKill(Player killer, Player victim) {
        // Kill streak de l'équipe de la victime : réinitialisé (spec §19).
        var victimTeam = plugin.getTeamManager().getTeamOf(victim.getUniqueId());
        if (victimTeam != null) {
            victimTeam.resetKillStreak();
        }

        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return; // pas d'attributaire => pas de points de kill
        }

        var killerTeam = plugin.getTeamManager().getTeamOf(killer.getUniqueId());

        // Premier kill de la partie (une seule fois).
        if (!firstKillAwarded) {
            firstKillAwarded = true;
            plugin.getPointService().award(killer,
                    PointCategory.FIRST_KILL,
                    plugin.getConfigManager().getFirstKillPoints(),
                    "premier kill");
            MessageUtil.broadcast("§6" + killer.getName()
                    + " §7signe le §epremier kill§7 !");
        } else {
            plugin.getPointService().award(killer,
                    PointCategory.KILL,
                    plugin.getConfigManager().getKillPoints(),
                    "kill");
        }

        // Kill streak d'équipe (suivi + annonce tous les 3).
        if (killerTeam != null) {
            killerTeam.setKillStreak(killerTeam.getKillStreak() + 1);
            if (killerTeam.getKillStreak() % 3 == 0) {
                MessageUtil.broadcast("L'équipe "
                        + killerTeam.getColor().getColoredName()
                        + " §7enchaîne §e" + killerTeam.getKillStreak() + " kills§7 !");
            }
        }
    }

    /** Nouvelle partie : le premier kill redevient attribuable. */
    public void resetMatchState() {
        firstKillAwarded = false;
        pendingDamage.clear();
    }
}
