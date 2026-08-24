package com.mceteams.xii.model;

import org.bukkit.Location;

/**
 * Une météorite tombée (ou en cours de chute) sur la map (spec §22).
 *
 * Implémentation : une boule de feu spawnée en altitude avec une
 * vitesse dirigée vers le sol. À l'impact :
 * - détruit une zone de blocs (explosion vanilla) ;
 * - inflige 35% à 50% de la vie max aux joueurs proches
 *   (application manuelle par MeteoriteService).
 */
public class Meteorite {

    /** Point visé au sol. */
    private final Location target;
    /** Puissance de l'explosion (rayon de destruction). */
    private final double power;
    /** Rayon d'application des dégâts joueurs. */
    private final int damageRadius;
    /** Dégâts réels appliqués (fraction entre 0.35 et 0.50). */
    private final double damagePercent;
    /** Horodatage du lancement. */
    private final long launchedAt;

    public Meteorite(Location target,
                     double power,
                     int damageRadius,
                     double damagePercent) {
        this.target = target.clone();
        this.power = power;
        this.damageRadius = damageRadius;
        this.damagePercent = damagePercent;
        this.launchedAt = System.currentTimeMillis();
    }

    public Location getTarget() {
        return target.clone();
    }

    public double getPower() {
        return power;
    }

    public int getDamageRadius() {
        return damageRadius;
    }

    public double getDamagePercent() {
        return damagePercent;
    }

    public long getLaunchedAt() {
        return launchedAt;
    }
}
