package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.TeamUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les équipes du jeu (spec §6).
 *
 * Deux niveaux :
 * 1. GameTeam (model) = SOURCE DE VÉRITÉ gameplay
 *    (points, coeur, élimination, spawn, kill streak...).
 * 2. Team Bukkit = confort vanilla uniquement :
 *    friendly fire OFF, collisions OFF entre membres, préfixe chat.
 *
 * La logique est volontairement centralisée ICI.
 */
public class TeamManager {

    /**
     * Plugin hôte. Peut être NULL dans les tests unitaires : toute
     * utilisation est gardée par {@link #hasPlugin()} pour que la logique
     * métier pure reste testable sans serveur (spec §37).
     */
    private final XiiPlugin plugin;
    /** Taille par défaut d'une équipe créée (config ou valeur de test). */
    private final int defaultMaxPlayers;
    /** Équipes par couleur. */
    private final Map<TeamColor, GameTeam> teams = new ConcurrentHashMap<>();

    public TeamManager(XiiPlugin plugin, int defaultMaxPlayers) {
        this.plugin = plugin;
        this.defaultMaxPlayers = defaultMaxPlayers;
    }

    private boolean hasPlugin() {
        return plugin != null;
    }

    /** Raccourci interne vers le PlayerManager (null-safe). */
    private com.mceteams.xii.manager.PlayerManager playersOrNull() {
        return hasPlugin() ? plugin.getPlayerManager() : null;
    }

    // -----------------------------------------------------------------
    // CRUD équipes
    // -----------------------------------------------------------------

    /**
     * Crée une équipe d'une couleur donnée + son équipe Bukkit.
     *
     * @return false si la couleur est déjà utilisée.
     */
    public boolean createTeam(TeamColor color) {
        if (teams.containsKey(color)) {
            return false;
        }
        GameTeam team = new GameTeam(color, defaultMaxPlayers);
        teams.put(color, team);
        syncBukkitTeam(team);

        // Si la base existe déjà (zone déjà générée), câble directement
        // le spawn de l'équipe (sinon il ne serait posé qu'au prochain
        // buildBases).
        if (hasPlugin()) {
            var base = plugin.getBaseManager().getBase(color);
            if (base != null) {
                team.setSpawn(base.getSpawn());
            }
            plugin.getLogger().info("[Teams] Équipe créée : " + color.getColoredName());
        }
        return true;
    }

    /**
     * Supprime une équipe.
     *
     * En PLEINE PARTIE (préparation/combat) : les membres en ligne sont
     * envoyés en mode SPECTATEUR permanent et les conditions de victoire
     * sont revérifiées. En attente : ils perdent simplement leur équipe.
     *
     * @return false si l'équipe n'existait pas.
     */
    public boolean removeTeam(TeamColor color) {
        GameTeam team = teams.remove(color);
        if (team == null) {
            return false;
        }

        // Capture des membres AVANT purge.
        Set<UUID> formerMembers = team.getPlayers();

        // Détache tous les membres côté données plugin.
        var players = playersOrNull();
        if (players != null) {
            for (UUID memberId : formerMembers) {
                players.getData(memberId).setTeamId(null);
            }
        }
        team.clearPlayers();

        // Partie en cours => spectateurs permanents.
        boolean midGame = hasPlugin()
                && (plugin.getGameManager().getState()
                        == com.mceteams.xii.enums.GameState.PREPARATION
                || plugin.getGameManager().getState()
                        == com.mceteams.xii.enums.GameState.COMBAT);
        if (midGame) {
            for (UUID memberId : formerMembers) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null || !member.isOnline()) {
                    continue;
                }
                var data = plugin.getPlayerManager().getData(memberId);
                data.setAlive(false);
                plugin.getSpectatorService().enterPermanent(member);
                MessageUtil.send(member,
                        "§c✘ Votre équipe a été supprimée : vous passez §5SPECTATEUR§c.");
            }
        }

        // Supprime l'équipe Bukkit associée si présente.
        if (hasPlugin()) {
            var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team bukkitTeam = scoreboard.getTeam(TeamUtil.bukkitTeamName(color));
            if (bukkitTeam != null) {
                bukkitTeam.unregister();
            }
            // Revérifie la victoire : il reste peut-être une seule équipe !
            if (midGame) {
                plugin.getGameManager().checkVictoryConditions();
            }
        }
        return true;
    }

    /**
     * Ajoute un joueur à une équipe.
     *
     * @return résultat détaillé de l'opération.
     */
    public AddResult addPlayer(UUID playerUuid, TeamColor color) {
        GameTeam team = teams.get(color);
        if (team == null) {
            return AddResult.TEAM_NOT_FOUND;
        }
        // La source de vérité de l'appartenance est le Set de GameTeam :
        // on cherche l'équipe actuelle en scannant les équipes (fonctionne
        // aussi sans plugin, cf. tests unitaires).
        GameTeam currentTeam = findTeamContaining(playerUuid);
        if (currentTeam == team) {
            return AddResult.ALREADY_IN_TEAM;
        }
        if (currentTeam != null) {
            // Quitte proprement l'ancienne équipe avant de rejoindre.
            leaveTeam(playerUuid, currentTeam);
        }
        if (team.isFull()) {
            return AddResult.FULL;
        }
        team.addPlayer(playerUuid);
        mirrorTeamIdToPlayerData(playerUuid, team);
        addToBukkitTeam(playerUuid, team);
        return AddResult.OK;
    }

    /**
     * Retire un joueur de son équipe actuelle (quelle qu'elle soit).
     *
     * @return true si le joueur a bien été retiré.
     */
    public boolean removePlayer(UUID playerUuid) {
        GameTeam team = getTeamOf(playerUuid);
        if (team == null) {
            return false;
        }
        leaveTeam(playerUuid, team);
        removeFromBukkitTeam(playerUuid);
        return true;
    }

    /**
     * Modifie la taille maximale d'une équipe (/teams set <couleur> size <n>).
     */
    public boolean setMaxPlayers(TeamColor color, int maxPlayers) {
        GameTeam team = teams.get(color);
        if (team == null || maxPlayers < 1 || maxPlayers < team.getPlayerCount()) {
            return false;
        }
        team.setMaxPlayers(maxPlayers);
        return true;
    }

    // -----------------------------------------------------------------
    // Lectures
    // -----------------------------------------------------------------

    public GameTeam getTeam(TeamColor color) {
        return teams.get(color);
    }

    public Collection<GameTeam> all() {
        return teams.values();
    }

    public boolean isEmpty() {
        return teams.isEmpty();
    }

    /** @return l'équipe du joueur, ou null s'il n'en a pas. */
    public GameTeam getTeamOf(UUID playerUuid) {
        return findTeamContaining(playerUuid);
    }

    /**
     * Nombre de joueurs VIVANTS dans une équipe (pour la fin de partie,
     * spec §27). Sans plugin (tests) : compte simplement les membres.
     */
    public int aliveCount(GameTeam team) {
        int count = 0;
        var players = playersOrNull();
        for (UUID member : team.getPlayers()) {
            if (players == null) {
                count++; // contexte test : tout membre compte comme vivant
                continue;
            }
            var data = players.getData(member);
            if (data.isAlive() && !data.isEliminated()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Une équipe est-elle "sans vie" ? (coeur détruit ET plus aucun
     * membre vivant/ressuscitable => élimination, spec §28)
     */
    public boolean isDefinitivelyDead(GameTeam team) {
        if (team.isHeartAlive()) {
            return false;
        }
        return aliveCount(team) == 0;
    }

    /**
     * Met à jour le statut d'élimination d'une équipe et le diffuse.
     *
     * @return true si l'équipe vient d'être marquée éliminée.
     */
    public boolean updateElimination(GameTeam team) {
        if (!team.isEliminated() && isDefinitivelyDead(team)) {
            team.setEliminated(true);
            if (hasPlugin()) {
                // Format : EQUIPE ELIMINEE > L'équipe Jaune a été éliminée.
                // (label blanc gras, nom d'équipe dans sa couleur, reset)
                MessageUtil.broadcast("§f§lEQUIPE ELIMINEE > §r"
                        + team.getColor().getColorCode()
                        + "L'équipe " + team.getColor().getDisplayName()
                        + "§r a été éliminée.");
            }
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Notifications d'équipe (spec §30)
    // -----------------------------------------------------------------

    /**
     * Envoie un message à tous les membres EN LIGNE de l'équipe,
     * sauf l'auteur éventuel.
     */
    public void notifyMembers(GameTeam team, String message, UUID exceptUuid) {
        if (!hasPlugin()) {
            return; // pas de serveur (tests) : rien à notifier
        }
        for (UUID member : team.getPlayers()) {
            if (member.equals(exceptUuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                MessageUtil.send(player, message);
            }
        }
    }

    // -----------------------------------------------------------------
    // Synchronisation avec les équipes Bukkit/Paper (spec §6)
    // -----------------------------------------------------------------

    /**
     * Crée/configure l'équipe Bukkit correspondante : pas de friendly
     * fire, pas de collisions entre membres, préfixe coloré dans le chat.
     *
     * L'équipe Bukkit n'est PAS une source de vérité : si elle manque
     * (tests unitaires, serveur absent), on ignore silencieusement.
     */
    void syncBukkitTeam(GameTeam team) {
        try {
            var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team bukkitTeam = scoreboard.getTeam(TeamUtil.bukkitTeamName(team.getColor()));
            if (bukkitTeam == null) {
                bukkitTeam = scoreboard.registerNewTeam(TeamUtil.bukkitTeamName(team.getColor()));
            }
            bukkitTeam.setAllowFriendlyFire(false);           // pas de dégâts entre membres
            bukkitTeam.setOption(Team.Option.COLLISION_RULE,   // pas de push entre membres
                    Team.OptionStatus.NEVER);
            // Préfixe affiché devant le pseudo (chat + tab), ex "§9[Bleu] ".
            String prefix = team.getColor().getColorCode() + "[" + team.getColor().getDisplayName() + "] ";
            bukkitTeam.prefix(LegacyComponentSerializer.legacySection().deserialize(prefix));
            bukkitTeam.setColor(org.bukkit.ChatColor.getByChar(
                    team.getColor().getColorCode().replace("§", "")));
        } catch (Throwable ignored) {
            // Pas de serveur (tests unitaires) ou scoreboard indisponible :
            // le gameplay reste porté par GameTeam, donc rien à casser.
        }
    }

    /** Ajoute l'entrée joueur dans l'équipe Bukkit. */
    private void addToBukkitTeam(UUID uuid, GameTeam team) {
        try {
            var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team bukkitTeam = scoreboard.getTeam(TeamUtil.bukkitTeamName(team.getColor()));
            if (bukkitTeam != null) {
                bukkitTeam.addEntry(uuid.toString());
            }
        } catch (Throwable ignored) {
            // cf. syncBukkitTeam
        }
    }

    /** Retire l'entrée joueur de toute équipe Bukkit du plugin. */
    private void removeFromBukkitTeam(UUID uuid) {
        try {
            var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (GameTeam any : teams.values()) {
                Team bt = scoreboard.getTeam(TeamUtil.bukkitTeamName(any.getColor()));
                if (bt != null && bt.hasEntry(uuid.toString())) {
                    bt.removeEntry(uuid.toString());
                }
            }
        } catch (Throwable ignored) {
            // cf. syncBukkitTeam
        }
    }

    // -----------------------------------------------------------------
    // Reset (retour WAITING / fin de partie)
    // -----------------------------------------------------------------

    /**
     * Réinitialise les états TEMPORAIRES des équipes en conservant les
     * compositions (spec §35 point 9).
     */
    public void resetTransientState() {
        for (GameTeam team : teams.values()) {
            team.setHeartAlive(true);
            team.setEliminated(false);
            team.resetKillStreak();
            team.getScore().reset();
            syncBukkitTeam(team);
        }
    }

    /**
     * Supprime toutes les équipes (utilisée lors d'un /zone delete).
     */
    public void clearAll() {
        for (TeamColor color : teams.keySet().toArray(new TeamColor[0])) {
            removeTeam(color);
        }
    }

    // -----------------------------------------------------------------
    // Helpers internes
    // -----------------------------------------------------------------

    private UUID teamUniqueIdOf(GameTeam team) {
        // L'"id" logique d'une équipe = son nom Bukkit stable.
        return UUID.nameUUIDFromBytes(
                TeamUtil.bukkitTeamName(team.getColor()).getBytes());
    }

    /**
     * Recherche l'équipe contenant ce joueur en scannant les sets
     * membres (source de vérité, indépendante du plugin).
     */
    private GameTeam findTeamContaining(UUID playerUuid) {
        for (GameTeam team : teams.values()) {
            if (team.hasPlayer(playerUuid)) {
                return team;
            }
        }
        return null;
    }

    /** Sort le joueur de son équipe + miroir PlayerData. */
    private void leaveTeam(UUID playerUuid, GameTeam team) {
        team.removePlayer(playerUuid);
        var players = playersOrNull();
        if (players != null) {
            players.getData(playerUuid).setTeamId(null);
        }
    }

    /** Recopie l'appartenance dans PlayerData (confort des autres systèmes). */
    private void mirrorTeamIdToPlayerData(UUID playerUuid, GameTeam team) {
        var players = playersOrNull();
        if (players != null) {
            players.getData(playerUuid).setTeamId(
                    team == null ? null : teamUniqueIdOf(team));
        }
    }

    /** Résultats possibles de l'ajout d'un joueur à une équipe. */
    public enum AddResult {
        OK,
        TEAM_NOT_FOUND,
        ALREADY_IN_TEAM,
        FULL
    }
}
