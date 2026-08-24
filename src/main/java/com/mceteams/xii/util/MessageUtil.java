package com.mceteams.xii.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utilitaire d'affichage des messages (100% français, spec §1 : plus
 * aucun système de traduction).
 *
 * STYLE : pas de préfixe [XII] - les annonces sont directement
 * "Hypixel-like" : symboles (☠ ✦ ☄ ⚔ ✔ ✘), couleurs vives,
 * séparateurs barrés pour les grandes phases.
 */
public final class MessageUtil {

    /**
     * Séparateur de phase (style serveur compétitif) : ligne pleine
     * en strikethrough sombre.
     */
    public static final String SEPARATOR =
            "§8§m                                                    ";

    /** Préfixe désactivé (conservé pour compatibilité d'API). */
    @SuppressWarnings("unused")
    public static final String PREFIX = "";

    private MessageUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /** Envoie un message brut à un joueur/commande (pas de préfixe). */
    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(message);
        }
    }

    /** Diffuse un message brut à tout le serveur (pas de préfixe). */
    public static void broadcast(String message) {
        if (message != null) {
            Bukkit.broadcastMessage(message);
        }
    }

    /**
     * Affiche un titre + sous-titre à un joueur (utilisé notamment
     * pour "TU ES MORT / Réapparition dans X secondes", spec §29).
     *
     * @param fadeIn  ticks de fondu entrant
     * @param stay    ticks d'affichage
     * @param fadeOut ticks de fondu sortant
     */
    public static void sendTitle(Player player,
                                 String title,
                                 String subtitle,
                                 int fadeIn,
                                 int stay,
                                 int fadeOut) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendTitle(
                title == null ? "" : title,
                subtitle == null ? "" : subtitle,
                fadeIn, stay, fadeOut
        );
    }

    /** Titre avec durées par défaut (10/50/10 ticks). */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 50, 10);
    }

    /**
     * Barre d'action (action bar) via la surcharge sendActionBar de Paper.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendActionBar(message == null ? "" : message);
    }
}
