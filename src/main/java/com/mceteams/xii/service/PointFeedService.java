package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Barre d'action des POINTS CUMULÉS.
 *
 * Problème corrigé : chaque attribution de points écrasait la barre
 * d'action du joueur (message unique "+1 pts (chunk découvert)").
 * Ici, les points reçus pendant une fenêtre courte sont MUTUALISÉS :
 * - même style  => les montants se SOMMENT ("+3 pts (chunk découvert)") ;
 * - styles différents => ils s'AFFICHENT À CÔTÉ
 *   ("+2 pts (kill) §8| §a+3 pts (chunk découvert)").
 *
 * La barre est rafraîchie périodiquement par une task lancée dans
 * XiiPlugin (tick toutes les 200 ms) et nettoyée dès que la fenêtre
 * d'affichage expire.
 *
 * NB : point d'entrée unique = PointService.award (la pénalité de mort
 * ne passe pas ici : on n'accumule pas une perte de points).
 */
public class PointFeedService {

    /** Fenêtre d'affichage cumulée (ms). */
    private static final long WINDOW_MS = 3000L;
    /** Séparateur entre les styles affichés côte à côte. */
    private static final String SEPARATOR = " §8| ";

    private final XiiPlugin plugin;
    /** Flux en cours par joueur. */
    private final Map<UUID, Feed> feeds = new HashMap<>();

    public PointFeedService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ajoute des points au flux du joueur (le même style cumule son
     * montant, la fenêtre d'affichage est rallongée).
     *
     * @param player joueur concerné
     * @param amount montant final (après multiplicateurs)
     * @param reason libellé FR du motif, ex "chunk découvert"/"kill"
     */
    public void push(Player player, int amount, String reason) {
        if (player == null || !player.isOnline() || amount <= 0) {
            return;
        }
        Feed feed = feeds.computeIfAbsent(
                player.getUniqueId(), k -> new Feed());
        feed.amountByReason.merge(reason, amount, Integer::sum);
        feed.deadline = System.currentTimeMillis() + WINDOW_MS;
    }

    /**
     * Task périodique : affiche les flux actifs et purge ceux dont la
     * fenêtre est expirée (la barre est alors effacée).
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Feed feed = feeds.get(player.getUniqueId());
            if (feed == null) {
                continue;
            }
            if (now >= feed.deadline) {
                feeds.remove(player.getUniqueId());
                player.sendActionBar(" ");
                continue;
            }
            player.sendActionBar(buildMessage(feed));
        }
    }

    /** Oublie le flux d'un joueur qui se déconnecte. */
    public void onQuit(UUID uuid) {
        feeds.remove(uuid);
    }

    /** Message "§a+X pts §7(motif)" par style, reliés par un séparateur. */
    private static String buildMessage(Feed feed) {
        StringBuilder builder = new StringBuilder();
        feed.amountByReason.forEach((reason, amount) -> {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append("§a+").append(amount)
                    .append(" pts §7(").append(reason).append(")");
        });
        return builder.toString();
    }

    /** Flux d'un joueur (LinkedHashMap : ordre d'apparition stable). */
    private static final class Feed {
        final Map<String, Integer> amountByReason = new LinkedHashMap<>();
        long deadline;
    }
}