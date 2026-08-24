package com.mceteams.xii.model;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

/**
 * Une base effectivement placée dans le monde (spec §7).
 * Le BaseManager calcule position + rotation, puis crée ce model.
 */
public class GameBase {

    /** Couleur de l'équipe propriétaire de la base. */
    private final TeamColor color;
    /** Point d'ancrage utilisé pour placer la structure .nbt. */
    private final Location anchor;
    /** Centre géométrique de la base (zone de protection PvP). */
    private final Location center;
    /** Rayon (blocs) de la zone de protection autour du centre. */
    private final int radius;
    /** Spawn des joueurs de l'équipe. */
    private final Location spawn;
    /** Position exacte du bloc coeur (géré par CoreService). */
    private final Location coreLocation;

    public GameBase(TeamColor color,
                    Location anchor,
                    Location center,
                    int radius,
                    Location spawn,
                    Location coreLocation) {
        this.color = color;
        this.anchor = anchor.clone();
        this.center = center.clone();
        this.radius = radius;
        this.spawn = spawn.clone();
        this.coreLocation = coreLocation.clone();
    }

    public TeamColor getColor() {
        return color;
    }

    public Location getAnchor() {
        return anchor.clone();
    }

    public Location getCenter() {
        return center.clone();
    }

    public int getRadius() {
        return radius;
    }

    public Location getSpawn() {
        return spawn.clone();
    }

    public Location getCoreLocation() {
        return coreLocation.clone();
    }

    /**
     * La location est-elle à l'intérieur de la zone protégée de cette
     * base ? Utilisé par ProtectionService (PvP interdit/autorisé).
     */
    public boolean contains(Location location) {
        if (location == null) {
            return false;
        }
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= radius;
    }
}
