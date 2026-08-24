package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.DeathCause;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerData;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Logique métier des MORTS (spec §19/§29/§30).
 *
 * Séquence officielle d'une mort (spec §19) :
 * 1. la mort est enregistrée (alive=false, DeathCause) ;
 * 2. des points peuvent être retirés (pénalité) ;
 * 3. le kill streak de l'équipe de la victime est réinitialisé ;
 *    (fait par CombatService.registerKill)
 * 4. le joueur part en spectateur temporaire ;
 * 5. il reçoit son titre de mort ;
 * 6. son délai de respawn est programmé (RespawnManager).
 */
public class DeathService {

    private final XiiPlugin plugin;

    public DeathService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Traite la mort d'un joueur EN LIGNE.
     *
     * @param victim joueur mort
     * @param cause  cause qualifiée (PLAYER/DISCONNECT/OTHER)
     * @param killer tueur éventuel (null si environnemental).
     */
    public void handleDeath(Player victim, DeathCause cause, Player killer) {
        PlayerData data = plugin.getPlayerManager().getData(victim);
        if (!data.isAlive()) {
            return; // déjà mort (double événement) => ignorer
        }

        // 1. Enregistrement.
        data.setAlive(false);
        data.setDeathCause(cause);

        // Kills / streaks / points du tueur (spec §18 : surveillance combat).
        plugin.getCombatService().registerKill(killer, victim);

        // 2. Pénalité de points.
        int penalty = plugin.getConfigManager().getDeathPenalty();
        plugin.getPointService().remove(victim, penalty, "mort");

        // 4. Spectateur temporaire.
        plugin.getSpectatorService().enter(victim);

        // 5. Titre de mort + délai annoncé (texte FRANÇAIS, spec §29).
        int delay = plugin.getRespawnManager().schedule(victim.getUniqueId());
        MessageUtil.sendTitle(victim,
                "§cTU ES MORT",
                "§7Réapparition dans §e" + delay + " seconde(s)",
                10, 60, 10);
        SoundUtil.playDeath(victim);

        // Annonce à tous + vérification victoire anticipée (style Hypixel).
        String killerInfo = killer != null
                ? " §7par §c" + killer.getName()
                : "";
        MessageUtil.broadcast("§c☠ §f" + victim.getName()
                + " §7est mort" + killerInfo + ".");
        plugin.getGameManager().checkVictoryConditions();
    }

    /**
     * Traite une mort SANS joueur en ligne :
     * - déconnexion pendant la préparation (traitée comme une mort, §30) ;
     * - déconnexion en fenêtre de combat (DeathCause.DISCONNECT, §30).
     *
     * Pas de titre ni de spectateur : le joueur est hors ligne. Il sera
     * géré à sa reconnexion par ConnectionListener.
     */
    public void handleOfflineDeath(UUID victimUuid, DeathCause cause) {
        PlayerData data = plugin.getPlayerManager().getData(victimUuid);
        if (!data.isAlive()) {
            return;
        }
        data.setAlive(false);
        data.setDeathCause(cause);

        // Pénalité + respawn programmé même hors ligne.
        int penalty = plugin.getConfigManager().getDeathPenalty();
        data.getScore().addPenalty(penalty);

        var team = plugin.getTeamManager().getTeamOf(victimUuid);
        if (team != null) {
            team.resetKillStreak();          // streak réinitialisé
            team.getScore().addPenalty(penalty);
        }

        plugin.getRespawnManager().schedule(victimUuid);
        plugin.getGameManager().checkVictoryConditions();
    }

    /**
     * La cause doit-elle être qualifiée DISCONNECT ? Centralise la règle
     * spec §30 : uniquement si le joueur était dans la fenêtre de combat.
     */
    public DeathCause qualifyDisconnect(PlayerData data) {
        boolean preparationActive =
                plugin.getGameManager().getState() == GameState.PREPARATION;
        if (preparationActive) {
            return DeathCause.OTHER;      // préparation : toute déconnexion = mort
        }
        if (plugin.getCombatService().isInCombatWindow(data)) {
            return DeathCause.DISCONNECT; // fenêtre de combat 15 s
        }
        return null;                      // pas une mort : simple absence
    }
}
