package com.mceteams.xii.scoreboard;

import com.mceteams.xii.XiiPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Affichage TAB (liste des joueurs) : couleur selon l'équipe, gris pour
 * les spectateurs, header/footer informatifs.
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
     */
    public void update(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());

        String listName;
        if (data.isSpectator()) {
            listName = "§5[Spectateur] §7" + player.getName();
        } else if (team != null) {
            // Préfixe coloré de l'équipe + pseudo.
            listName = team.getColor().getColorCode() + "["
                    + team.getColor().getDisplayName() + "] §f" + player.getName();
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

    /** Footer : phase courante courte. */
    private Component footerFor(Player player) {
        var state = plugin.getGameManager().getState();
        String text = switch (state) {
            case PREPARATION -> "§7Préparation - Jour §b"
                    + plugin.getPhaseManager().currentDay() + "§8/12";
            case COMBAT -> "§7Combat - Jour §b"
                    + plugin.getPhaseManager().currentDay() + "§8/12";
            case ENDING -> "§6Fin de partie";
            default -> "§7En attente";
        };
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    /** Oublie les états appliqués (retour lobby). */
    public void resetAll() {
        lastApplied.clear();
    }
}
