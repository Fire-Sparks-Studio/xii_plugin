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
            // handleRespawn traite le respawn MÊME si le joueur est hors
            // ligne (la décoche = une vraie mort, timers réels) : il
            // reviendra vivant à sa reconnexion, après l'échéance.
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
            // Même HORS LIGNE le respawn est consommé (ramené vivant à sa
            // reconnexion) : la mort d'une déconnexion est une vraie mort.
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
            // Titre différencié si la cause est une DÉCONNEXION : pas
            // "TU ES MORT" mais "TU AS ÉTÉ DÉCONNECTÉ" (même punition).
            boolean wasDisconnect =
                    plugin.getPlayerManager().getData(entry.getKey()).getDeathCause()
                            == com.mceteams.xii.enums.DeathCause.DISCONNECT;
            com.mceteams.xii.util.MessageUtil.sendTitle(player,
                    wasDisconnect ? "§cTU AS ÉTÉ DÉCONNECTÉ" : "§cTU ES MORT",
                    "§7Réapparition dans §e" + seconds + " s",
                    0, 25, 0);
        }
    }

    /**
     * Exécute le respawn d'un joueur :
     * - COMBAT + coeur détruit => élimination DÉFINITIVE ;
     * - sinon => retour dans sa base (spec §19 dernier point).
     *
     * Traite le respawn MÊME SI LE JOUEUR EST HORS LIGNE : la mort est
     * une vraie mort (même punition/timing que les morts en ligne), seuls
     * les effets physiques (sortie spectateur, téléportation, son) sont
     * différés jusqu'à sa reconnexion (ConnectionListener.handleMidGameJoin
     * retrouvera un joueur VIVANT et le ramènera à sa base).
     *
     * @return toujours true (l'entrée est consommée : plus de report).
     */
    private boolean handleRespawn(UUID uuid) {
        var data = plugin.getPlayerManager().getData(uuid);
        Player player = Bukkit.getPlayer(uuid);
        boolean online = player != null && player.isOnline();

        var team = plugin.getTeamManager().getTeamOf(uuid);
        // Équipe sans coeur en COMBAT : PAS de respawn automatique.
        // Le joueur rejoint le pool "totem de revive" : seul un totem
        // utilisé par son équipe pourra le ramener.
        boolean combatActive =
                plugin.getGameManager().getState()
                        == com.mceteams.xii.enums.GameState.COMBAT;
        if (combatActive && team != null && !team.isHeartAlive()) {
            data.setAlive(false);
            revivePending.add(uuid);
            if (online) {
                plugin.getSpectatorService().enterPermanent(player);
                MessageUtil.send(player,
                        "§5☾ §7Ton coeur d'équipe est détruit : tu reviendras "
                                + "uniquement via un §dtotem de revive§5.");
            }
            // L'ÉQUIPE peut être marquée éliminée (dernier debout) même si
            // un totem pourra plus tard ramener ses membres.
            plugin.getTeamManager().updateElimination(team);
            plugin.getGameManager().checkVictoryConditions();
            return true;
        }

        // Respawn normal : retour dans sa base.
        data.setAlive(true);
        data.setDeathCause(null);
        data.clearLastDamage();
        if (online) {
            plugin.getSpectatorService().exit(player);
            PlayerUtil.heal(player);

            Location spawnPoint = team != null && team.getSpawn() != null
                    ? team.getSpawn()
                    : plugin.getZoneManager().getZone().getCenterLocation();
            if (spawnPoint != null) {
                player.teleport(spawnPoint);
            }
            // Les passifs (vie/speed des classes) sont réappliqués car le
            // reset a remis les attributs par défaut ; vie remplie.
            plugin.getClassService().applyPassives(player, data, true);

            MessageUtil.send(player, "§aRéapparition !");
            // Title bien visible (le joueur sortait du mode spectateur),
            // SANS sous-titre : le chat a déjà annoncé "Réapparition !".
            MessageUtil.sendTitle(player, "§aRÉAPPARITION", "", 10, 50, 10);
        } else {
            // Traitement hors ligne : exit() ne s'applique qu'aux joueurs
            // EN LIGNE, on force donc le drapeau ici (les restes physiques
            // seront nettoyés à la reconnexion par ensureNormalState).
            data.setSpectator(false);
        }
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
     * TOTEM DE REVIVE : ramène UN membre MORT (pas besoin d'être dans un
     * pool particulier). Réinitialise son état complet : vivant, non
     * éliminé, sortie du spectateur, retour à la base, passifs réappliqués
     * (vie remplie). Purge aussi les éventuels respawns programmés pour
     * éviter le double retour.
     *
     * NB : si l'ÉQUIPE avait été marquée éliminée pendant l'absence du
     * joueur, il revient quand même (règle "un mort = ressuscitable").
     *
     * @return false si le joueur est inconnu ou déjà VIVANT.
     */
    public boolean reviveByTotem(UUID playerUuid) {
        var data = plugin.getPlayerManager().getData(playerUuid);
        if (data == null || data.isAlive()) {
            return false;
        }
        // Sort de toutes les files : le retour sera géré par ce totem.
        revivePending.remove(playerUuid);
        readyAt.remove(playerUuid);
        combatPending.remove(playerUuid);

        data.setAlive(true);
        data.setEliminated(false);
        data.setDeathCause(null);
        data.clearLastDamage();

        Player player = Bukkit.getPlayer(playerUuid);
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
            plugin.getClassService().applyPassives(player, data, true);
            com.mceteams.xii.util.MessageUtil.send(player,
                    "§d✦ §fUn §dtotem de revive§f t'a ramené dans la partie !");
        } else {
            // Ramené hors ligne : mêmes réserves que pour un respawn
            // classique (état nettoyé à la reconnexion).
            data.setSpectator(false);
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
