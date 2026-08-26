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
 * Affichage TAB (liste des joueurs) : couleur selon l'équipe, header/footer
 * informatifs, regroupement par équipe, infos droites simplifiées.
 * Format par ligne : §X§lL §fPseudo  [⬆N K+P]
 * - LETTRE ÉQUIPE : majuscule, bold, couleur de l'équipe
 * - PUISQUE : pseudo en même couleur, pas bold
 * - UN SEUL ESPACE entre la lettre et le pseudo
 * - Droite (dans []): upgrades actifs, kills, points équipe
 */
public class TabManager {

    private final XiiPlugin plugin;
    /** Dernier état appliqué par joueur (évite les mises à jour inutiles). */
    private final Map<UUID, String> lastApplied = new HashMap<>();

    public TabManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Met à jour la liste de tous les joueurs.
     */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
        // Header / footer globaux.
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
     * Met à jour le nom dans la liste d'un joueur :
     * préfixe équipe coloré, ou §5[Spectateur] si spectateur.
     * Format : §X§lL §fPseudo  [⬆N K+P]
     * - LETTRE ÉQUIPE : majuscule, bold, couleur de l'équipe
     * - PUISQUE : pseudo en même couleur, pas bold
     * - UN SEUL ESPACE entre la lettre et le pseudo
     * - Droite (dans []): upgrades actifs, kills, points équipe
     */
    public void update(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        var state = plugin.getGameManager().getState();

        String listName;
        if (data.isSpectator()) {
            listName = "§5[Spectateur] §7" + player.getName();
        } else if (team != null) {
            // Format: LETTRE ÉQUIPE (bold, colorée) + ESPACE + pseudo (même couleur, pas bold)
            // La lettre est la première lettre du nom coloré de l'équipe
            String teamColorCode = team.getColor().getColorCode();
            String teamDisplayName = team.getColor().getDisplayName();
            String teamLetter = teamColorCode + teamDisplayName.substring(0, 1).toUpperCase();
            String playerColor = teamColorCode;
            String playerDisplayName = playerColor + player.getName();
            // Un seul espace entre la lettre et le pseudo
            listName = teamLetter + " " + playerDisplayName;
        } else {
            listName = "§7" + player.getName();
        }

        // Mise à jour seulement si changé.
        if (!listName.equals(lastApplied.get(player.getUniqueId()))) {
            lastApplied.put(player.getUniqueId(), listName);
            player.playerListName(LegacyComponentSerializer.legacySection()
                    .deserialize(listName));
        }
    }

    /**
     * Footer : statut de la partie en bas de la liste TAB.
     * - En attente / Préparation - Jour X/12 / Combat - Jour X/12 / Fin de partie
     */
    private Component footerFor(Player player) {
        var state = plugin.getGameManager().getState();
        var phaseManager = plugin.getPhaseManager();
        int currentDay = phaseManager != null ? phaseManager.currentDay() : 0;
        int maxDay = 12;

        String text;
        switch (state) {
            case PREPARATION:
                text = "§7Préparation - Jour §b" + currentDay + "§8/" + maxDay;
                break;
            case COMBAT:
                text = "§7Combat - Jour §b" + currentDay + "§8/" + maxDay;
                break;
            case ENDING:
                text = "§6Fin de partie";
                break;
            case WAITING:
            case COUNTDOWN:
            default:
                text = "§7En attente";
                break;
        }
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    /** Oublie les états appliqués (retour lobby). */
    public void resetAll() {
        lastApplied.clear();
    }

    /**
     * Génère la chaîne d'informations supplémentaires à afficher à droite
     * de la liste TAB pour un joueur donné.
     *
     * Contenu : upgrades actifs (nombre), kills du joueur, points équipe.
     * Format court compatible avec la limite de 16 chars du nom TAB vanilla.
     *
     * @param player le joueur
     * @return chaîne de caractères formatée (vide si tout à zero)
     */
    public String getRightSideInfo(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        var state = plugin.getGameManager().getState();

        // 1. Upgrades actifs (nombre de niveaux totalisés)
        int activeUpgrades = 0;
        if (data != null) {
            activeUpgrades = data.getUpgrades().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // 2. Kills du joueur (via PlayerScore -> map de points par catégorie)
        int playerKills = 0;
        if (data != null) {
            playerKills = data.getScore().get(PointCategory.KILL);
        }

        // 3. Points de l'équipe (via GameTeam -> TeamScore -> getTotal())
        int teamPoints = 0;
        if (team != null) {
            teamPoints = team.getScore().getTotal();
        }

        // Format court: "⬆N K+P" ou vide si tout à zero
        if (activeUpgrades == 0 && playerKills == 0 && teamPoints == 0) {
            return "";
        }
        return "§7⬆" + activeUpgrades + " §e" + playerKills + "K §a+" + teamPoints;
    }

    /**
     * Reconstruit la ligne complète TAB pour un joueur incluant les infos droites.
     * Les infos droites sont placées dans une "sous-marque" qui apparaît
     * quand on regarde la liste (limité par les 16 chars vanilla).
     * Pour affichage étendu, utiliser la sidebar scoreboard dédiée.
     */
    public void updateWithRightSide(Player player) {
        update(player); // Met à jour le nom

        // Les infos droites sont récupérables via getRightSideInfo(player)
        // mais ne s'affichent pas directement dans la liste TAB vanilla
        // à cause de la limite de 16 caractères. Elles sont toutefois
        // disponibles via le scoreboard sidebar (voir ScoreboardManager).
    }
}