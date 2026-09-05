package com.mceteams.xii.model;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Une base effectivement placée dans le monde (spec §7).
 * Le BaseManager calcule position + rotation, puis crée ce model.
 */
public class GameBase {

    /**
     * Empreinte carrée de la structure de base (35x35, cf. bases.nbt).
     * La structure est centrée autour du centre de zone : après rotation
     * la boîte occupe toujours [anchor .. anchor+34] sur X ET Z.
     */
    public static final int SIZE = 35;
    /** Demi-largeur de la boîte (le centre = un bloc entier). */
    public static final int HALF = (SIZE - 1) / 2;

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

    /**
     * Positions (coordonnées de bloc) posées PAR L'ÉQUIPE pendant la
     * partie : seuls ces blocs sont cassables par l'équipe propriétaire
     * (pose/casse "propres dès le début"). Les blocs de la structure
     * restent, eux, inviolables.
     */
    private final Set<Location> ownedBlocks = new LinkedHashSet<>();

    /**
     * Emplacements de {@code structure_void} du gabarit : les SEULS blocs
     * où l'équipe propriétaire est autorisée à POSER quelque chose dans
     * sa base (RÈGLE UTILISATEUR). Enregistrés pendant le processing des
     * repères (MarkerManager), avant leur conversion en air.
     */
    private final Set<Location> voidSlots = new LinkedHashSet<>();

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

    // -----------------------------------------------------------------
    // Zones (PvP + empreinte des blocs)
    // -----------------------------------------------------------------

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

    /**
     * La location est-elle dans l'EMPREINTE (rectangle) de la structure,
     * marge +1 ? C'est cette zone qui subit les règles de protection des
     * blocs (structure incassable, champs protégés, blocs de l'équipe).
     */
    public boolean containsBlock(Location location) {
        if (location == null) {
            return false;
        }
        if (!location.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        return x >= ax - 1 && x <= ax + SIZE
                && z >= az - 1 && z <= az + SIZE;
    }

    // -----------------------------------------------------------------
    // Blocs posés par l'équipe (cassables par les propriétaires)
    // -----------------------------------------------------------------

    /** Enregistre un bloc posé par l'équipe pendant la partie. */
    public void addOwnedBlock(Location location) {
        if (location != null && location.getWorld() != null) {
            ownedBlocks.add(blockLocation(location));
        }
    }

    /** Oublie un bloc posé (il a été cassé). */
    public void removeOwnedBlock(Location location) {
        if (location != null) {
            ownedBlocks.remove(blockLocation(location));
        }
    }

    /** Ce bloc a-t-il été posé par l'équipe propriétaire ? */
    public boolean isOwnedBlock(Location location) {
        return location != null && ownedBlocks.contains(blockLocation(location));
    }

    /** Vide la liste des blocs posés (début d'une nouvelle partie). */
    public void clearOwnedBlocks() {
        ownedBlocks.clear();
    }

    // -----------------------------------------------------------------
    // Emplacements de pose autorisés (structure_void du gabarit)
    // -----------------------------------------------------------------

    /** Enregistre un emplacement void (structure_void) posable par l'équipe. */
    public void addVoidSlot(Location location) {
        if (location != null && location.getWorld() != null) {
            voidSlots.add(blockLocation(location));
        }
    }

    /** Le joueur peut-il poser à cet emplacement (void du gabarit) ? */
    public boolean isVoidSlot(Location location) {
        return location != null && voidSlots.contains(blockLocation(location));
    }

    /** Vide les emplacements void (début d'une nouvelle partie). */
    public void clearVoidSlots() {
        voidSlots.clear();
    }

    /** Normalise une position en coordonnées de bloc (sans yaw/pitch). */
    private Location blockLocation(Location location) {
        return new Location(location.getWorld(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}