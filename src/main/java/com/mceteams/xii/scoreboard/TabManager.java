package com.mceteams.xii.scoreboard;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Affichage TAB (liste des joueurs).
 *
 * FORMAT PAR LIGNE (en partie) :
 *   §X§lL Pseudo  §8│ §a⬆U §d⚡KS §e⚔K §6✦P
 * - LETTRE ÉQUIPE : majuscule, bold, couleur de l'équipe
 * - PSEUDO : même couleur, pas bold, UN SEUL espace après la lettre
 * - Côté droit : upgrades actifs (⬆), kill streak de l'ÉQUIPE (⚡),
 *   kills du JOUEUR (⚔), points apportés par le JOUEUR à son équipe (✦)
 *
 * Les joueurs sont GROUPÉS PAR ÉQUIPE via setListOrder (ordre de
 * l'enum TeamColor), les spectateurs en fin de liste.
 *
 * FOOTER (barre du bas) : statut de la partie
 * (En attente / Préparation - Jour X/12 / Combat - Jour X/12 / Fin).
 */
public class TabManager {

    private final XiiPlugin plugin;
    /** Dernier état appliqué par joueur (évite les mises à jour inutiles). */
    private final Map<UUID, String> lastApplied = new HashMap<>();

    /** Ordre de liste des spectateurs (tout en bas). */
    private static final int LIST_ORDER_SPECTATOR = 9900;
    /** Ordre de liste des joueurs sans équipe (lobby). */
    private static final int LIST_ORDER_NO_TEAM = 5000;

    public TabManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Met à jour la liste (noms, ordre, header/footer) de tous les joueurs. */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
        var serializer = LegacyComponentSerializer.legacySection();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(
                        serializer.deserialize("§b§lXII DAYS"),
                        footerFor(player));
            }
        });
    }

    /**
     * Met à jour le nom de liste d'un joueur (préfixe équipe + infos
     * droites), ainsi son ordre de tri (regroupement par équipe).
     */
    public void update(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        var state = plugin.getGameManager().getState();

        String listName;
        if (data.isSpectator()) {
            listName = "§5[Spectateur] §7" + player.getName();
        } else if (team != null) {
            String teamColorCode = team.getColor().getColorCode();
            // Lettre = 1re lettre du nom d'équipe, majuscule, bold, colorée.
            String letter = teamColorCode + "§l"
                    + team.getColor().getDisplayName().substring(0, 1).toUpperCase();
            // Pseudo : même couleur, PAS bold, un seul espace de séparation.
            String name = teamColorCode + player.getName();
            listName = letter + " " + name + rightSideInfo(player, data, team, state);
        } else {
            listName = "§7" + player.getName();
        }

        if (!listName.equals(lastApplied.get(player.getUniqueId()))) {
            lastApplied.put(player.getUniqueId(), listName);
            player.playerListName(LegacyComponentSerializer.legacySection()
                    .deserialize(listName));
        }

        player.setPlayerListOrder(listOrderOf(player, team, data));
    }

    /**
     * Ordre de tri TAB : les équipes se suivent dans l'ordre de TeamColor,
     * puis les joueurs sans équipe (lobby), puis les spectateurs.
     */
    private int listOrderOf(Player player, GameTeam team, PlayerData data) {
        if (data.isSpectator()) {
            return LIST_ORDER_SPECTATOR;
        }
        if (team != null) {
            return team.getColor().ordinal() * 100;
        }
        return LIST_ORDER_NO_TEAM;
    }

    /**
     * Informations affichées après le nom (côté droit de la ligne TAB) :
     * upgrades actifs, kill streak d'ÉQUIPE, kills du joueur,
     * points apportés à l'équipe par le joueur.
     * Vide hors partie (lobby) : rien à compter avant le lancement.
     */
    private String rightSideInfo(Player player, PlayerData data,
                                 GameTeam team, GameState state) {
        if (state == GameState.NONE || state == GameState.WAITING
                || state == GameState.COUNTDOWN) {
            return "";
        }

        int upgrades = data.getUpgrades().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int teamKillStreak = team.getKillStreak();
        int playerKills = data.getScore().get(PointCategory.KILL);
        int playerPoints = data.getScore().getTotal();

        return " §8│ §a⬆" + upgrades
                + " §d⚡" + teamKillStreak
                + " §e⚔" + playerKills
                + " §6✦" + playerPoints;
    }

    /**
     * Footer : statut de la partie en bas de la liste TAB.
     */
    private Component footerFor(Player player) {
        var state = plugin.getGameManager().getState();
        var phaseManager = plugin.getPhaseManager();
        int currentDay = phaseManager != null ? phaseManager.currentDay() : 0;
        int maxDay = 12;

        String text;
        switch (state) {
            case PREPARATION -> text = "§eStatut : §7Préparation - Jour §b"
                    + currentDay + "§8/" + maxDay;
            case COMBAT -> text = "§eStatut : §7Combat - Jour §b"
                    + currentDay + "§8/" + maxDay;
            case ENDING -> text = "§eStatut : §6Fin de partie";
            default -> text = "§eStatut : §7En attente";
        }
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    /** Oublie les états appliqués (retour lobby). */
    public void resetAll() {
        lastApplied.clear();
    }
}
