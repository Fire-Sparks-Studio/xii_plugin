package com.mceteams.xii.scoreboard;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PreparationSubPhase;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Affichage SIDEBAR - format demandé :
 *
 *   §6§lXII DAYS                      (titre)
 *   Jour 4/12
 *   Prochaine : Colis dans 7:32
 *   (vide)
 *   §9Bleu ♥ 5                        <- coeur vivant + joueurs vivants
 *   §eJaune ♡ 2                       <- coeur détruit + joueurs vivants
 *   §cRouge ✘                         <- éliminée
 *   §aVert ♥ 8 §8Vous                 <- votre équipe : marqueur gris
 *   (vide)
 *   §7Votre classe : §aMineur
 *   §7Points équipe : §e240           <- total de VOTRE équipe uniquement
 *
 * NB : si une équipe n'existe pas, elle est affichée comme déjà
 * éliminée (✘). Les entrées identiques sont rendues uniques par des
 * codes couleur invisibles (contrainte du scoreboard vanilla).
 */
public class ScoreboardManager {

    private final XiiPlugin plugin;
    /** Scoreboard dédié par joueur. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** Entrées sidebar par joueur (pour clear sélectif). */
    private final Map<UUID, Set<String>> sidebarEntries = new HashMap<>();

    public ScoreboardManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Met à jour la sidebar de tous les joueurs en ligne. */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /**
     * Met à jour la sidebar d'un joueur.
     */
    public void update(Player player) {
        GameState state = plugin.getGameManager().getState();

        // Hors partie : sidebar masquée.
        if (state == GameState.NONE || state == GameState.WAITING) {
            clear(player);
            return;
        }

        Scoreboard board = boardOf(player);
        Objective sidebarObjective = ensureObjective(board, player);

        List<String> lines = buildLines(player, state);
        uniquify(lines);

        // Application (scores décroissants = ordre visuel haut -> bas).
        clearEntries(board, player.getUniqueId());
        Set<String> tracked = sidebarEntries.computeIfAbsent(
                player.getUniqueId(), k -> new HashSet<>());
        tracked.clear();
        int scoreValue = lines.size();
        for (String line : lines) {
            sidebarObjective.getScore(line).setScore(scoreValue--);
            tracked.add(line);
        }

        // Mise à jour de l'affichage de la santé (en dessous du nom).
        // NB : l'objective utilise Criteria.HEALTH, dont les scores sont
        // AUTO-GÉRÉS par le serveur (lecture seule : setScore = exception).
        // On s'assure simplement que l'objective BELOW_NAME est bien posée
        // sur le scoreboard du joueur (fait dans ensureObjective).
    }

    // -----------------------------------------------------------------
    // Construction des lignes
    // -----------------------------------------------------------------

    private List<String> buildLines(Player player, GameState state) {
        List<String> lines = new ArrayList<>();

        if (state == GameState.ENDING) {
            lines.add("§7Partie terminée");
            lines.add("");
            appendRanking(lines);
            return lines;
        }

        var phaseManager = plugin.getPhaseManager();

        // --- Espace sous le titre --------------------------------------
        lines.add("");

        // --- Ligne jour courant ---------------------------------------
        int day = phaseManager.currentDay();
        lines.add("§7Jour §b§l" + day + "§8/12");

        // --- Prochaine sous-phase + timer ------------------------------
        lines.add(nextSubPhaseLine());

        lines.add("");

        // --- Lignes des QUATRE équipes ---------------------------------
        appendTeamLines(lines, player);

        lines.add("");

        // --- Rappel de VOTRE CLASSE (et non plus l'équipe) --------------
        var data = plugin.getPlayerManager().getData(player);
        if (data.getPlayerClass() != null) {
            lines.add("§7Votre classe : " + data.getPlayerClass().getColoredName());
        } else {
            lines.add("§7Aucune classe");
        }

        // --- Points de VOTRE équipe (jamais ceux des adversaires) -------
        var viewerTeam =
                plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (viewerTeam != null) {
            lines.add("§7Points équipe : §e"
                    + viewerTeam.getScore().getTotal());
        }
        return lines;
    }

    /**
     * Ligne "<événement> dans m:ss" (timer de la sous-phase à venir).
     */
    private String nextSubPhaseLine() {
        var phaseManager = plugin.getPhaseManager();
        String nextName;
        GamePhase phase = phaseManager.getPhase();

        if (phase == GamePhase.PREPARATION) {
            PreparationSubPhase current = phaseManager.getPreparationSubPhase();
            PreparationSubPhase[] values = PreparationSubPhase.values();
            nextName = current.ordinal() + 1 < values.length
                    ? subPhaseFr(values[current.ordinal() + 1])
                    : "Combat";
        } else if (phase == GamePhase.COMBAT) {
            CombatSubPhase current = phaseManager.getCombatSubPhase();
            CombatSubPhase[] values = CombatSubPhase.values();
            nextName = current.ordinal() + 1 < values.length
                    ? combatSubFr(values[current.ordinal() + 1])
                    : "Fin de partie";
        } else {
            nextName = "-";
        }

        int remaining = phaseManager.getRemainingSeconds(
                plugin.getConfigManager().getSubPhaseDurationSeconds());
        String time = String.format("%d:%02d", remaining / 60, remaining % 60);

        return "§f" + nextName + " §7dans §e" + time;
    }

    /**
     * Les 4 lignes d'équipes, format "<Couleur> <symbole><vivants>" :
     * ♥ = coeur vivant, ♡ = coeur détruit (joueurs encore en vie),
     * ✘ = éliminée / inexistante.
     * Votre équipe porte le marqueur gris "Vous".
     */
    private void appendTeamLines(List<String> lines, Player viewer) {
        GameTeam viewerTeam =
                plugin.getTeamManager().getTeamOf(viewer.getUniqueId());

        for (TeamColor color : TeamColor.values()) {
            GameTeam team = plugin.getTeamManager().getTeam(color);

            String line;
            if (team == null || team.isEliminated()) {
                // Équipe inexistante ou éliminée => croix rouge (pas de nombre).
                line = color.getColorCode() + color.getDisplayName() + " §c✘";
            } else if (team.isHeartAlive()) {
                // Coeur en vie => coeur plein SEUL (pas de nombre).
                line = color.getColorCode() + color.getDisplayName() + " §a♥";
            } else {
                // Coeur détruit mais joueurs encore debout =>
                // coeur vide + NOMBRE de joueurs vivants.
                line = color.getColorCode() + color.getDisplayName()
                        + " §c♡ §f" + plugin.getTeamManager().aliveCount(team);
            }

            // Marqueur "votre équipe" : VOUS en majuscules, gris clair, gras.
            if (viewerTeam != null && viewerTeam.getColor() == color) {
                line += " §7§lVOUS";
            }
            lines.add(line);
        }
    }

    /** Top équipes par points totaux (utilisé en ENDING). */
    private void appendRanking(List<String> lines) {
        var ranking = plugin.getTeamManager().all().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getScore().getTotal(), a.getScore().getTotal()))
                .limit(4)
                .toList();
        for (GameTeam team : ranking) {
            lines.add(team.getColor().getColoredName() + " §e"
                    + team.getScore().getTotal());
        }
    }

    /** Nom FR d'une sous-phase de préparation. */
    private String subPhaseFr(PreparationSubPhase sub) {
        return switch (sub) {
            case START -> "Début";
            case PACKAGES -> "Colis";
            case DUNGEONS -> "Donjons";
            case POINT_UPGRADES -> "Points x2";
            case PACKAGE_UPGRADE -> "Colis ++";
            case DUNGEON_RESTOCK -> "Restock";
        };
    }

    /** Nom FR d'une sous-phase de combat. */
    private String combatSubFr(CombatSubPhase sub) {
        return switch (sub) {
            case START -> "Ouverture";
            case METEORITES -> "Météorites";
            case MORE_DAMAGE -> "Dégâts x2";
            case ALL_CORE_DESTRUCTION -> "Cœurs détruits";
            case MORE_METEORITES -> "Météorites ++";
            case SUDDEN_DEATH -> "Mort subite";
        };
    }

    // -----------------------------------------------------------------
    // Unicité des entrées (le scoreboard refuse les doublons)
    // -----------------------------------------------------------------

    /**
     * Rend chaque ligne unique en ajoutant des codes couleur invisibles
     * (zéro largeur visuelle) aux doublons, notamment les lignes vides.
     */
    private void uniquify(List<String> lines) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String key = lines.get(i);
            if (seen.add(key)) {
                continue;
            }
            String unique = key;
            // On empile des codes couleur (invisibles) jusqu'à unicité.
            while (!seen.add(unique)) {
                unique = unique + "§c";
            }
            lines.set(i, unique);
        }
    }

    // -----------------------------------------------------------------
    // Gestion interne des scoreboards
    // -----------------------------------------------------------------

    /** Récupère (ou crée) le scoreboard personnel du joueur. */
    private Scoreboard boardOf(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), uuid ->
                Bukkit.getScoreboardManager().getNewScoreboard());
    }

    /** Crée/récupère l'objective de sidebar sur ce scoreboard. */
    private Objective ensureObjective(Scoreboard board, Player player) {
        Objective objective = board.getObjective("xii_sidebar");
        if (objective == null) {
            objective = board.registerNewObjective(
                    "xii_sidebar",
                    Criteria.DUMMY,
                    LegacyComponentSerializer.legacySection()
                            .deserialize("§6§lXII DAYS"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        // Objective santé sous le nametag (au-dessus de la tête) :
        // rendu "❤ 15" = titre (cœur rouge) + nombre de PV (INTEGER).
        // Les scores sont auto-gérés par le serveur via Criteria.HEALTH.
        Objective healthObjective = board.getObjective("xii_health");
        if (healthObjective == null) {
            healthObjective = board.registerNewObjective(
                    "xii_health",
                    Criteria.HEALTH,
                    LegacyComponentSerializer.legacySection()
                            .deserialize("§c❤"),
                    org.bukkit.scoreboard.RenderType.INTEGER);
            healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        player.setScoreboard(board);
        return objective;
    }

    /** Vide les entrées actuelles de la sidebar (avant réécriture). */
    private void clearEntries(Scoreboard board, UUID playerUuid) {
        Set<String> tracked = sidebarEntries.get(playerUuid);
        if (tracked == null) return;
        for (String entry : tracked) {
            board.resetScores(entry);
        }
    }

    /** Retire complètement la sidebar d'un joueur. */
    public void clear(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        clearEntries(board, player.getUniqueId());
        sidebarEntries.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        boards.remove(player.getUniqueId());
    }

    /** Oublie tous les scoreboards personnels (retour lobby). */
    public void resetAll() {
        for (UUID uuid : boards.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager()
                        .getMainScoreboard());
            }
        }
        boards.clear();
    }

    /** Composant titre exposé si besoin ailleurs. */
    public Component titleComponent() {
        return LegacyComponentSerializer.legacySection()
                .deserialize("§6§lXII DAYS");
    }
}