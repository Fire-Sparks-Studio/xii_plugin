package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gère le calendrier des respawn (spec §19/§29).
 *
 * Règle du délai :
 *   1ère mort = 5 s, chaque mort ajoute +5 s, maximum 30 s.
 *   (valeurs configurables : respawn.step-seconds / max-seconds)
 *
 * Le détail exact du calendrier est centralisé ICI (spec §29) :
 * la RespawnTask appelle processDue() chaque seconde et ce manager
 * décide qui revient, où, ou qui est éliminé définitivement.
 */
public class RespawnManager {

    private final XiiPlugin plugin;

    /** Nombre de morts par joueur (alimente le délai croissant). */
    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    /** Timestamp (ms) auquel le respawn sera effectif (PRÉPARATION). */
    private final Map<UUID, Long> readyAt = new HashMap<>();
    /**
     * Joueurs morts en attente de respawn en COMBAT : pas de timer,
     * ils reviennent tous AU DÉBUT DE LA PROCHAINE SOUS-PHASE.
     */
    private final Map<UUID, Boolean> combatPending = new HashMap<>();

    public RespawnManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calcul PUR du délai de respawn en secondes (testable sans serveur,
     * spec §37 : RespawnManagerTest).
     *
     * @param deathCount numéro de la mort (1 = première mort)
     */
    public static int computeDelaySeconds(int deathCount, int stepSeconds, int maxSeconds) {
        if (deathCount <= 0) {
            return stepSeconds;
        }
        return Math.min(deathCount * stepSeconds, maxSeconds);
    }

    /**
     * Enregistre une mort et programme son retour.
     *
     * - PRÉPARATION : délai croissant 5s..30s (timer classique).
     * - COMBAT (jour 7+) : PAS de timer ; le joueur attendra le début
     *   de la prochaine sous-phase ({@link #processSubPhaseStart()}).
     *
     * @return le délai appliqué en secondes, ou -1 si reporté à la
     *         prochaine sous-phase (pour les messages).
     */
    public int schedule(UUID playerUuid) {
        boolean combatActive =
                plugin.getGameManager().getState()
                        == com.mceteams.xii.enums.GameState.COMBAT;
        if (combatActive) {
            combatPending.put(playerUuid, true);
            return -1;
        }

        int deaths = deathCounts.merge(playerUuid, 1, Integer::sum);
        int delay = computeDelaySeconds(
                deaths,
                plugin.getConfigManager().getRespawnStepSeconds(),
                plugin.getConfigManager().getRespawnMaxSeconds());
        readyAt.put(playerUuid, System.currentTimeMillis() + delay * 1000L);
        return delay;
    }

    /**
     * Traite les respawn minutés arrivés à échéance (PRÉPARATION).
     * Appelé par RespawnTask chaque seconde.
     */
    public void processDue() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = readyAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue; // pas encore à échéance
            }
            iterator.remove();
            handleRespawn(entry.getKey());
        }
    }

    /**
     * COMBAT : appelé À CHAQUE DÉBUT DE SOUS-PHASE. Tous les joueurs
     * morts en attente reviennent dans leur base - SAUF si leur équipe
     * n'a plus de coeur (élimination définitive).
     */
    public void processSubPhaseStart() {
        if (combatPending.isEmpty()) {
            return;
        }
        for (UUID uuid : List.copyOf(combatPending.keySet())) {
            combatPending.remove(uuid);
            handleRespawn(uuid);
        }
    }

    /**
     * Exécute le respawn d'un joueur :
     * - COMBAT + coeur détruit => élimination DÉFINITIVE ;
     * - sinon => retour dans sa base (spec §19 dernier point).
     */
    private void handleRespawn(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        var data = plugin.getPlayerManager().getData(uuid);

        // Joueur toujours hors ligne : il reprendra via ConnectionListener.
        data.setDisconnected(false);
        if (player == null || !player.isOnline()) {
            return;
        }

        var team = plugin.getTeamManager().getTeamOf(uuid);

        // Équipe sans coeur en COMBAT : plus aucun respawn possible (§29).
        boolean combatActive =
                plugin.getGameManager().getState() == com.mceteams.xii.enums.GameState.COMBAT;
        if (combatActive && team != null && !team.isHeartAlive()) {
            data.setEliminated(true);
            data.setAlive(false);
            plugin.getSpectatorService().enterPermanent(player);
            MessageUtil.broadcast("§c✘ §e" + player.getName()
                    + " §7est définitivement §céliminé§7 !");
            plugin.getGameManager().checkVictoryConditions();
            return;
        }

        // Respawn normal : retour dans sa base.
        data.setAlive(true);
        data.setDeathCause(null);
        plugin.getSpectatorService().exit(player);
        PlayerUtil.heal(player);

        Location spawnPoint = team != null && team.getSpawn() != null
                ? team.getSpawn()
                : plugin.getZoneManager().getZone().getCenterLocation();
        if (spawnPoint != null) {
            player.teleport(spawnPoint);
        }
        // Les passifs (vie/speed des classes) sont réappliqués car le
        // reset a remis les attributs par défaut.
        plugin.getClassService().applyPassives(player, data);

        MessageUtil.send(player, "§aRéapparition ! Bon retour.");
    }

    /** Un respawn est-il programmé pour ce joueur ? */
    public boolean isPending(UUID uuid) {
        return readyAt.containsKey(uuid) || combatPending.containsKey(uuid);
    }

    /** Remise à zéro complète (nouvelle partie / arrêt). */
    public void clearAll() {
        deathCounts.clear();
        readyAt.clear();
        combatPending.clear();
    }
}
