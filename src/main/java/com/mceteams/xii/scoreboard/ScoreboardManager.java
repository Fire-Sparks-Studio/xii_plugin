package com.mceteams.xii.scoreboard;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PreparationSubPhase;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Affichage SIDEBAR (spec §38 : "Affichage").
 *
 * Contenu selon l'état : état du jeu, jour courant, sous-phase,
 * temps restant, équipe du joueur, classement des équipes par points.
 *
 * Implémentation simple : reconstruction des lignes à chaque mise à
 * jour (1x/seconde via PhaseTask). Une version "diff" éviterait le
 * léger scintillement ; documenté comme amélioration possible.
 */
public class ScoreboardManager {

    private final XiiPlugin plugin;
    /** Scoreboard dédié par joueur. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

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
        Objective objective = ensureObjective(board, player);

        // Construction des lignes.
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§7État : §f" + displayName(state));
        if (state == GameState.PREPARATION || state == GameState.COMBAT) {
            int day = plugin.getPhaseManager().currentDay();
            lines.add("§7Jour : §b" + day + "§8/12");
            lines.add("§7Phase : §f" + subPhaseName());
            lines.add(timeLine());
            GameTeam team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
            if (team != null) {
                lines.add("");
                lines.add("§7Vivants : §a" + plugin.getTeamManager().aliveCount(team));
                lines.add("§7Coeur : " + (team.isHeartAlive() ? "§a❤" : "§c✘"));
                lines.add(team.getColor().getColoredName() + " §e"
                        + team.getScore().getTotal() + " pts");
            } else {
                lines.add("");
                lines.add("§7Statut : §5Spectateur");
            }
            lines.add("");
            appendRanking(lines);
        }

        // Application (scores décroissants = ordre visuel haut -> bas).
        clearEntries(board);
        int scoreValue = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(scoreValue--);
        }
    }

    /** Ligne temps restant de la sous-phase (mm:ss). */
    private String timeLine() {
        int remaining = plugin.getPhaseManager().getRemainingSeconds(
                plugin.getConfigManager().getSubPhaseDurationSeconds());
        return String.format("§7Temps : §f%d:%02d", remaining / 60, remaining % 60);
    }

    /** Top équipes par points totaux (max 4 lignes). */
    private void appendRanking(java.util.List<String> lines) {
        var ranking = plugin.getTeamManager().all().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getScore().getTotal(), a.getScore().getTotal()))
                .limit(4)
                .toList();
        boolean first = true;
        for (GameTeam team : ranking) {
            lines.add((first ? "§6Classement :" : "") + team.getColor().getColoredName()
                    + " §e" + team.getScore().getTotal());
            first = false;
        }
    }

    /** Nom FR de l'état courant. */
    private String displayName(GameState state) {
        return switch (state) {
            case NONE -> "Normal";
            case WAITING -> "Attente";
            case COUNTDOWN -> "Lancement";
            case CLASS_SELECTION -> "Classes";
            case PREPARATION -> "Préparation";
            case COMBAT -> "Combat";
            case ENDING -> "Fin";
        };
    }

    /** Nom FR de la sous-phase courante. */
    private String subPhaseName() {
        var phaseManager = plugin.getPhaseManager();
        if (phaseManager.getPhase() == GamePhase.PREPARATION
                && phaseManager.getPreparationSubPhase() != null) {
            PreparationSubPhase sub = phaseManager.getPreparationSubPhase();
            return switch (sub) {
                case START -> "Début";
                case PACKAGES -> "Colis";
                case DUNGEONS -> "Donjons";
                case POINT_UPGRADES -> "Points x2";
                case PACKAGE_UPGRADE -> "Colis ++";
                case DUNGEON_RESTOCK -> "Restock";
            };
        }
        if (phaseManager.getPhase() == GamePhase.COMBAT
                && phaseManager.getCombatSubPhase() != null) {
            CombatSubPhase sub = phaseManager.getCombatSubPhase();
            return switch (sub) {
                case START -> "Ouverture";
                case METEORITES -> "Météorites";
                case MORE_DAMAGE -> "Dégâts x2";
                case ALL_CORE_DESTRUCTION -> "Cœurs détruits";
                case MORE_METEORITES -> "Météorites ++";
                case SUDDEN_DEATH -> "Mort subite";
            };
        }
        return "-";
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
                    net.kyori.adventure.text.Component.text(
                            MessageUtil.PREFIX + "§bXII DAYS"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        player.setScoreboard(board);
        return objective;
    }

    /** Vide les entrées actuelles de la sidebar (avant réécriture). */
    private void clearEntries(Scoreboard board) {
        Objective objective = board.getObjective("xii_sidebar");
        if (objective == null) {
            return;
        }
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
    }

    /** Retire complètement la sidebar d'un joueur. */
    public void clear(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        clearEntries(board);
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
}
