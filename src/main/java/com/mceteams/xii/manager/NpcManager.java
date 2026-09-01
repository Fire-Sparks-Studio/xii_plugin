package com.mceteams.xii.manager;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;

import java.util.EnumMap;
import java.util.Map;

/**
 * PNJ d'équipe (emplacements repérés par des laines dans l'empreinte
 * d'une base).
 *
 * RÈGLE UTILISATEUR (protocole repères) :
 * - Laine VERTE  → emplacement du PNJ "Upgrades d'équipe" ;
 * - Laine JAUNE  → emplacement du PNJ "Objets/items".
 *
 * Pour l'instant on se contente de STOCKER la position : la
 * configuration des PNJ (apparence, inventaires, menu) se fera plus tard.
 * Les positions sont stockées en coordonnées de bloc (sans yaw/pitch).
 */
public class NpcManager {

    /** Emplacement du PNJ "Upgrades d'équipe" par équipe (ou null). */
    private final Map<TeamColor, Location> upgradeNpcByTeam =
            new EnumMap<>(TeamColor.class);
    /** Emplacement du PNJ "Objets/items" par équipe (ou null). */
    private final Map<TeamColor, Location> itemNpcByTeam =
            new EnumMap<>(TeamColor.class);

    /** Enregistre l'emplacement du PNJ d'upgrade d'une équipe. */
    public void setUpgradeNpc(TeamColor color, Location location) {
        if (color != null && location != null && location.getWorld() != null) {
            upgradeNpcByTeam.put(color, blockLocation(location));
        }
    }

    /** Enregistre l'emplacement du PNJ d'objets d'une équipe. */
    public void setItemNpc(TeamColor color, Location location) {
        if (color != null && location != null && location.getWorld() != null) {
            itemNpcByTeam.put(color, blockLocation(location));
        }
    }

    /** Emplacement du PNJ d'upgrade d'une équipe (clone), ou null. */
    public Location getUpgradeNpc(TeamColor color) {
        Location loc = upgradeNpcByTeam.get(color);
        return loc == null ? null : loc.clone();
    }

    /** Emplacement du PNJ d'objets d'une équipe (clone), ou null. */
    public Location getItemNpc(TeamColor color) {
        Location loc = itemNpcByTeam.get(color);
        return loc == null ? null : loc.clone();
    }

    /** Vide les emplacements (début d'une nouvelle partie). */
    public void resetAll() {
        upgradeNpcByTeam.clear();
        itemNpcByTeam.clear();
    }

    /** Normalise une position en coordonnées de bloc (sans yaw/pitch). */
    private Location blockLocation(Location location) {
        return new Location(location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}