package com.mceteams.xii.structure;

/**
 * Rotation d'une structure lors de son placement.
 *
 * Convention (documentée pour le développeur des .nbt) :
 * TOUTES les structures sont dessinées en regardant vers le SUD (+Z).
 * Cette classe calcule la rotation à appliquer pour que la structure
 * regarde dans la direction voulue.
 *
 * Sens de rotation vu de dessus : nord = -Z, est = +X, sud = +Z, ouest = -X.
 */
public enum StructureRotation {

    /** Aucune rotation : face au sud. */
    NONE,
    /** +90° horaire : sud -> ouest. */
    CLOCKWISE_90,
    /** 180° : sud -> nord. */
    CLOCKWISE_180,
    /** -90° (horaire x3) : sud -> est. */
    COUNTERCLOCKWISE_90;

    /**
     * Rotation nécessaire pour que la structure placée à {@code anchor}
     * regarde vers le point {@code target} (ex : les bases regardent
     * vers le centre de la zone, spec §7).
     *
     * @param anchor position de la structure
     * @param target point à viser (centre de la map)
     * @return la rotation adaptée
     */
    public static StructureRotation facingToward(org.bukkit.Location anchor,
                                                 org.bukkit.Location target) {
        double dx = target.getX() - anchor.getX();
        double dz = target.getZ() - anchor.getZ();

        // On choisit l'axe dominant pour obtenir une rotation cardinaire.
        if (Math.abs(dz) >= Math.abs(dx)) {
            // La cible est plutôt au nord (-Z) ou au sud (+Z).
            return dz > 0 ? NONE          // cible au sud => regarder sud
                    : CLOCKWISE_180;      // cible au nord => regarder nord
        }
        // La cible est plutôt à l'est (+X) ou à l'ouest (-X).
        return dx > 0 ? COUNTERCLOCKWISE_90   // cible à l'est => regarder est
                : CLOCKWISE_90;               // cible à l'ouest => regarder ouest
    }
}
