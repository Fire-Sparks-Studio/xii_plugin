package com.mceteams.xii.model;

import com.mceteams.xii.enums.PointCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Score personnel d'un joueur : points par catégorie.
 *
 * Les points de pénalité (morts, spec §19) sont stockés séparément et
 * déduits du total (ils ne polluent pas les catégories "positives").
 */
public class PlayerScore {

    /** Points par catégorie. */
    private final Map<PointCategory, Integer> points;
    /** Cumul des pénalités (retraits). */
    private int penalties;

    public PlayerScore() {
        this.points = new EnumMap<>(PointCategory.class);

        for (PointCategory category : PointCategory.values()) {
            points.put(category, 0);
        }
        this.penalties = 0;
    }

    public int get(PointCategory category) {
        return points.getOrDefault(category, 0);
    }

    public void set(PointCategory category, int value) {
        points.put(category, Math.max(0, value));
    }

    /** Ajoute des points positifs dans une catégorie. */
    public void add(PointCategory category, int value) {
        points.put(category, Math.max(0, get(category) + value));
    }

    /**
     * Enregistre une pénalité (points retirés, valeur positive).
     */
    public void addPenalty(int amount) {
        penalties += Math.max(0, amount);
    }

    /**
     * Total = somme des catégories positives - pénalités.
     */
    public int getTotal() {
        int total = 0;
        for (int value : points.values()) {
            total += value;
        }
        return total - penalties;
    }

    public int getPenalties() {
        return penalties;
    }

    public void reset() {
        for (PointCategory category : PointCategory.values()) {
            points.put(category, 0);
        }
        penalties = 0;
    }

    public Map<PointCategory, Integer> getPoints() {
        return Map.copyOf(points);
    }
}
