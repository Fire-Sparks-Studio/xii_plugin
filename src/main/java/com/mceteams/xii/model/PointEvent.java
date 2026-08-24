package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.UUID;

/**
 * Trace d'un événement de points (spec §32).
 * Créé par PointService à chaque attribution/retrait : utile pour le
 * debug, les logs et de futurs systèmes d'historique.
 */
public class PointEvent {

    /** Joueur concerné (peut être null pour un événement d'équipe). */
    private final UUID playerId;
    /** Équipe concernée (peut être null). */
    private final UUID teamId;
    /** Catégorie de points. */
    private final PointCategory category;
    /** Montant final appliqué (après multiplicateurs), négatif = retrait. */
    private final int points;
    /** Motif libre en français (ex : "minerai diamant"). */
    private final String reason;
    /** Horodatage de l'événement. */
    private final long timestamp;

    public PointEvent(UUID playerId,
                      UUID teamId,
                      PointCategory category,
                      int points,
                      String reason) {
        this.playerId = playerId;
        this.teamId = teamId;
        this.category = category;
        this.points = points;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public PointCategory getCategory() {
        return category;
    }

    public int getPoints() {
        return points;
    }

    public String getReason() {
        return reason;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
