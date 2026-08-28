package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.PlayerClass;
import com.mceteams.xii.gui.ClassSelectionGUI;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gère le processus de sélection de classe (spec §14/§31).
 *
 * Responsabilités :
 * - ouvrir la GUI de sélection pour tous les joueurs concernés ;
 * - enregistrer les choix ;
 * - à la fin des 30 secondes : attribuer une classe ALÉATOIRE aux
 *   joueurs qui n'ont rien choisi (il n'existe PAS de classe par
 *   défaut, spec §14) ;
 * - demander au ClassService d'appliquer les passifs.
 */
public class ClassManager {

    private final XiiPlugin plugin;

    /** Joueurs dont la GUI de sélection a déjà été ouverte. */
    private final Set<UUID> selectionOpened = new HashSet<>();

    public ClassManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre la sélection de classe pour tous les joueurs ÉQUIPÉS.
     * Les joueurs sans équipe deviendront spectateurs : inutile de
     * leur faire choisir une classe.
     */
    public void openSelectionForAll() {
        selectionOpened.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getData(player);
            if (!data.hasTeam()) {
                continue;
            }
            openSelection(player);
        }
        MessageUtil.broadcast("§eChoisissez votre classe §7(§f30 secondes§7) !");
    }

    /** Ouvre la GUI de sélection pour un joueur précis. */
    public void openSelection(Player player) {
        new ClassSelectionGUI(plugin, player).open();
        selectionOpened.add(player.getUniqueId());
    }

    /**
     * Enregistre le choix d'un joueur et applique immédiatement les
     * passifs (visibles dès la sélection).
     */
    public void select(Player player, PlayerClass playerClass) {
        var data = plugin.getPlayerManager().getData(player);
        data.setPlayerClass(playerClass);
        // fillHealth=true : au choix de la classe on récupère le NOUVEAU
        // max (ex : Robuste passe à 15 coeurs, barre remplie).
        plugin.getClassService().applyPassives(player, data, true);
        MessageUtil.send(player,
                "§7Classe choisie : " + playerClass.getColoredName());
    }

    /**
     * Fin de la sélection : chaque joueur équipé SANS classe reçoit une
     * classe aléatoire (spec §14 : pas de classe par défaut).
     */
    public void assignRandomMissing() {
        PlayerClass[] classes = PlayerClass.values();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getData(player);
            if (!data.hasTeam() || data.hasClass()) {
                continue;
            }
            PlayerClass randomClass = classes[ThreadLocalRandom.current()
                    .nextInt(classes.length)];
            data.setPlayerClass(randomClass);
            MessageUtil.send(player,
                    "§7Aucune classe choisie => §eclasse aléatoire§7 : "
                            + randomClass.getColoredName());
        }
        selectionOpened.clear();
    }

    /**
     * Fin des 30 secondes de sélection (appelé par le timer du GameManager) :
     * 1. classe ALÉATOIRE pour chaque joueur équipé qui n'a pas choisi ;
     * 2. fermeture forcée des GUI de sélection encore ouvertes.
     */
    public void finalizeSelection() {
        assignRandomMissing();

        // Ferme les ClassSelectionGUI encore ouvertes (joueurs n'ayant
        // pas choisi : leur classe vient d'être attribuée aléatoirement).
        for (Player player : Bukkit.getOnlinePlayers()) {
            var top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof ClassSelectionGUI) {
                player.closeInventory();
            }
        }
    }

    /** Remise à zéro (retour WAITING). */
    public void resetAll() {
        selectionOpened.clear();
        for (var data : plugin.getPlayerManager().all()) {
            data.setPlayerClass(null);
        }
    }
}
