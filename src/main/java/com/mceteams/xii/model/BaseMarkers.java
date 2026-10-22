package com.mceteams.xii.model;

import org.bukkit.Location;

import java.util.List;

/**
 * Résultat du traitement des REPÈRES d'une base (protocole repères).
 *
 * Les repères (laines) sont lus dans l'empreinte de la structure APRÈS
 * sa pose. Ils décrivent la base (niveau du sol, spawn, portillons, PNJ,
 * emplacements à laisser vides, bannières) mais ne sont PAS encore
 * rattachés à une équipe : l'affectation des bases aux équipes étant
 * TIRÉE AU SORT au lancement de la partie, le rattachement se fait
 * plus tard (cf. BaseManager#assignBasesToTeams).
 */
public record BaseMarkers(
        /** Point de spawn INTÉRIEUR de la base (laine VIOLETTE), ou null. */
        Location spawn,
        /** Portillons (laines BLEU CIEL) à transformer en spruce. */
        List<Location> gates,
        /** Emplacement du PNJ "Upgrades d'équipe" (laine LIME), ou null. */
        Location upgradeNpc,
        /** Emplacement du PNJ "Objets/items" (laine JAUNE), ou null. */
        Location itemNpc,
        /** Emplacements {@code structure_void} (à laisser vides). */
        List<Location> voidSlots,
        /** Bannières blanches à recolorer en couleur d'équipe. */
        List<Location> banners,
        /** Niveau du sol de la base (laine ROUGE), ou null si absent. */
        Integer floorY) {

    public BaseMarkers {
        gates = gates == null ? List.of() : List.copyOf(gates);
        voidSlots = voidSlots == null ? List.of() : List.copyOf(voidSlots);
        banners = banners == null ? List.of() : List.copyOf(banners);
    }

    /** @return true si au moins un repère exploitable a été trouvé. */
    public boolean hasContent() {
        return spawn != null || floorY != null || upgradeNpc != null
                || itemNpc != null || !gates.isEmpty() || !voidSlots.isEmpty()
                || !banners.isEmpty();
    }
}
