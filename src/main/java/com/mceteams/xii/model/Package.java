package com.mceteams.xii.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Un colis (package) spawné dans la zone de jeu (spec §17).
 *
 * Un colis est un coffre posé à un point aléatoire de la zone. Le
 * premier joueur qui l'ouvre remporte les points PACKAGE (et parfois
 * RARE_ITEM) : le model passe alors en "opened".
 */
public class Package {

    /** Identifiant unique du colis. */
    private final UUID id;
    /** Position exacte du coffre. */
    private final Location location;
    /** Horodatage du spawn (ms). */
    private final long spawnedAt;
    /** Le colis a-t-il déjà été ouvert/revendiqué ? */
    private boolean opened;
    /** Le colis contenait-il un objet rare ? (déterminé au spawn) */
    private final boolean containsRareItem;

    public Package(UUID id, Location location, boolean containsRareItem) {
        this.id = id;
        this.location = location.clone();
        this.spawnedAt = System.currentTimeMillis();
        this.opened = false;
        this.containsRareItem = containsRareItem;
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public long getSpawnedAt() {
        return spawnedAt;
    }

    public boolean isOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
    }

    public boolean containsRareItem() {
        return containsRareItem;
    }
}
