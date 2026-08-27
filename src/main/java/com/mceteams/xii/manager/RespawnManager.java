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
            // handleRespawn retourne FALSE si le joueur est encore HORS
            // LIGNE : le respawn reste DANS LA MAP et sera retenté à la
            // seconde suivante (sinon il était PERDU définitivement et le
            // joueur retombait en spectateur permanent à son retour).
            if (handleRespawn(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    /**
     * Joueurs morts dont le coeur a été détruit APRÈS leur mort : ils ne
     * peuvent PAS réapparaître normalement - uniquement via un futur
     * TOTEM DE REVIVE utilisé par leur équipe. En attente ici.
     */
    private final java.util.Set<UUID> revivePending =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * COMBAT : appelé À CHAQUE DÉBUT DE SOUS-PHASE. Tous les joueurs
     * morts en attente reviennent dans leur base - SAUF si leur équipe
     * n'a plus de coeur (=> pool "totem de revive").
     */
    public void processSubPhaseStart() {
        // Les joueurs du pool totem n'y touchent pas (revive manuel seul).
        for (UUID uuid : List.copyOf(combatPending.keySet())) {
            if (revivePending.contains(uuid)) {
                combatPending.remove(uuid); // déjà géré, pas de timer
                continue;
            }
            // Hors ligne => le joueur reste en attente (retenté à la
            // prochaine sous-phase) au lieu de perdre son respawn.
            if (handleRespawn(uuid)) {
                combatPending.remove(uuid);
            }
        }
    }

    /**
     * Affiche le COMPTE À REBOURS DANS LE TITRE chaque seconde pour les
     * joueurs morts en attente (préparation). Appelé par RespawnTask.
     */
    public void updateWaitingTitles() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : readyAt.entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining <= 0) {
                continue; // traité par processDue
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            int seconds = (int) Math.ceil(remaining / 1000.0);
            com.mceteams.xii.util.MessageUtil.sendTitle(player,
                    "§cTU ES MORT",
                    "§7Réapparition dans §e" + seconds + " s",
                    0, 25, 0);
        }
    }

    /**
     * Exécute le respawn d'un joueur :
     * - COMBAT + coeur détruit => élimination DÉFINITIVE ;
     * - sinon => retour dans sa base (spec §19 dernier point).
     *
     * @return true si le joueur a été traité (respawn effectué ou mise
     *         au pool totem), false s'il est encore HORS LIGNE (respawn
     *         reporté, à retenter).
     */
    private boolean handleRespawn(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        var data = plugin.getPlayerManager().getData(uuid);

        // Joueur toujours hors ligne : le respawn reste PROGRAMMÉ et
        // sera retenté à la seconde suivante. Ne pas marquer
        // disconnected=false ici : l'annonce "est revenu" sera émise par
        // sa reconnexion.
        if (player == null || !player.isOnline()) {
            return false;
        }

        var team = plugin.getTeamManager().getTeamOf(uuid);
        // Équipe sans coeur en COMBAT : PAS de respawn automatique.
        // Le joueur rejoint le pool "totem de revive" (mécanique à venir) :
        // seul un totem utilisé par son équipe pourra le ramener.
        boolean combatActive =
                plugin.getGameManager().getState()
                        == com.mceteams.xii.enums.GameState.COMBAT;
        if (combatActive && team != null && !team.isHeartAlive()) {
            data.setAlive(false);
            revivePending.add(uuid);
            plugin.getSpectatorService().enterPermanent(player);
            MessageUtil.send(player,
                    "§5☾ §7Ton coeur d'équipe est détruit : tu reviendras "
                            + "uniquement via un §dtotem de revive§5.");
            // L'ÉQUIPE peut être marquée éliminée (dernier debout) même si
            // un totem pourra plus tard ramener ses membres.
            plugin.getTeamManager().updateElimination(team);
            plugin.getGameManager().checkVictoryConditions();
            return true;
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
        // Title bien visible (le joueur sortait du mode spectateur).
        MessageUtil.sendTitle(player, "§aRÉAPPARITION",
                "§7Bon retour dans la partie !", 10, 50, 10);
        return true;
    }

    /** Un respawn est-il programmé pour ce joueur ? */
    public boolean isPending(UUID uuid) {
        return readyAt.containsKey(uuid) || combatPending.containsKey(uuid);
    }

    /**
     * Ce joueur est-il dans le pool "totem de revive" ? (mort avec coeur
     * d'équipe détruit après coup - retour possible uniquement via totem)
     */
    public boolean isAwaitingRevive(UUID uuid) {
        return revivePending.contains(uuid);
    }

    /**
     * TOTEM DE REVIVE (mécanique à venir) : ramène un joueur du pool.
     * Réinitialise son état complet : vivant, non éliminé, sortie du
     * spectateur, retour à la base, passifs réappliqués.
     *
     * NB : si l'ÉQUIPE avait été marquée éliminée pendant son absence,
     * c'est à l'appelant (futur item) de décider de la réhabiliter aussi
     * selon les règles choisies pour le totem.
     *
     * @return false si le joueur n'était pas dans le pool.
     */
    public boolean reviveByTotem(UUID playerUuid) {
        if (!revivePending.remove(playerUuid)) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        var data = plugin.getPlayerManager().getData(playerUuid);
        data.setAlive(true);
        data.setEliminated(false);
        data.setDeathCause(null);

        if (player != null && player.isOnline()) {
            plugin.getSpectatorService().exit(player);
            com.mceteams.xii.util.PlayerUtil.heal(player);
            var team = plugin.getTeamManager().getTeamOf(playerUuid);
            Location spawnPoint = team != null && team.getSpawn() != null
                    ? team.getSpawn()
                    : plugin.getZoneManager().getZone().getCenterLocation();
            if (spawnPoint != null) {
                player.teleport(spawnPoint);
            }
            plugin.getClassService().applyPassives(player, data);
            com.mceteams.xii.util.MessageUtil.send(player,
                    "§d✦ §fUn §dtotem de revive§f t'a ramené dans la partie !");
        }
        return true;
    }

    /** Remise à zéro complète (nouvelle partie / arrêt). */
    public void clearAll() {
        deathCounts.clear();
        readyAt.clear();
        combatPending.clear();
        revivePending.clear();
    }

    /**
     * Purge uniquement les FILES D'ATTENTE (timers + combat + totem), en
     * conservant les compteurs de morts. Utilisé par /party set.
     */
    public void clearPending() {
        readyAt.clear();
        combatPending.clear();
        revivePending.clear();
    }
}
