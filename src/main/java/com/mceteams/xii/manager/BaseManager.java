package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.GameZone;
import org.bukkit.Location;

import java.util.EnumMap;
import java.util.Map;

/**
 * Gère les bases des équipes (spec §7).
 *
 * Disposition : les 4 bases sont posées sur les axes cardinaux à
 * distance fixe du centre :
 *   BLEU  au nord (-Z), ROUGE au sud (+Z),
 *   JAUNE à l'ouest (-X), VERT à l'est (+X).
 *
 * Ce choix garantit un espacement maximal entre bases ET une
 * compatibilité avec les donjons qui occupent les 4 diagonales
 * (+/-500 ; +/-500) — aucune superposition possible.
 *
 * Chaque base regarde vers le centre de la map (StructureRotation).
 */
public class BaseManager {

    private final XiiPlugin plugin;
    /** Bases placées, par couleur. */
    private final Map<TeamColor, GameBase> bases = new EnumMap<>(TeamColor.class);

    public BaseManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Construit et place les bases de toutes les équipes actives.
     * Appelé par GameManager après /zone set.
     */
    public void buildBases(GameZone zone) {
        bases.clear();

        int radius = plugin.getConfigManager().getBaseRadius();
        Location center = zone.getCenterLocation();
        if (center == null) {
            return;
        }

        // Définition des 4 positions cardinales.
        putBase(TeamColor.BLUE,
                center.clone().subtract(0, 0, radius),   // nord : -Z
                center);
        putBase(TeamColor.RED,
                center.clone().add(0, 0, radius),        // sud : +Z
                center);
        putBase(TeamColor.YELLOW,
                center.clone().subtract(radius, 0, 0),   // ouest : -X
                center);
        putBase(TeamColor.GREEN,
                center.clone().add(radius, 0, 0),        // est : +X
                center);

        plugin.getLogger().info("[Bases] " + bases.size() + " base(s) placée(s).");
    }

    /** Place une base et enregistre son GameBase. */
    private void putBase(TeamColor color, Location anchor, Location center) {
        String colorName = color.name().toLowerCase();
        boolean placed = plugin.getStructureManager()
                .placeBase(colorName, anchor, center);

        // Même si la structure manque (.nbt non fournie), on crée le model
        // pour que le reste du jeu fonctionne pendant le développement.
        if (!placed) {
            plugin.getLogger().warning("[Bases] Structure manquante pour "
                    + color.getColoredName() + " §7(base_" + colorName + ".nbt)");
        }

        int protectionRadius = 25; // rayon de la zone protégée autour du centre

        // Spawn : légèrement vers l'intérieur depuis l'ancrage.
        Location spawn = anchor.clone()
                .add(anchor.toVector().subtract(center.toVector())
                        .normalize().multiply(-plugin.getConfigManager().getSpawnOffset()));
        spawn.setY(worldGroundY(spawn));

        // Coeur : au-dessus de l'ancrage (offset configurable, à aligner
        // avec la structure fournie par le développeur).
        Location core = anchor.clone()
                .add(0, plugin.getConfigManager().getCoreOffsetY(), 0);

        bases.put(color, new GameBase(
                color, anchor, anchor.clone(), protectionRadius, spawn, core));

        // IMPORTANT : câble le spawn dans le GameTeam (source de vérité).
        // Sans ça, GameManager/RespawnManager ne trouvaient aucun spawn
        // et envoyaient les joueurs en spectateur par erreur !
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team != null) {
            team.setSpawn(spawn);
        }

        // Enregistrement du coeur auprès du CoreService.
        plugin.getCoreService().registerCore(color, core);
    }

    /** Trouve une hauteur "posable" au-dessus du sol pour un point. */
    private double worldGroundY(Location location) {
        var world = location.getWorld();
        if (world == null) {
            return location.getY();
        }
        return world.getHighestBlockYAt(
                location.getBlockX(), location.getBlockZ()) + 1.0;
    }

    /** @return la base d'une équipe, ou null si non construite. */
    public GameBase getBase(TeamColor color) {
        return bases.get(color);
    }

    /**
     * @return la base CONTENANT cette location (zone de protection),
     * ou null. Utilisé par ProtectionService pour les règles PvP.
     */
    public GameBase baseAt(org.bukkit.Location location) {
        for (GameBase base : bases.values()) {
            if (base.contains(location)) {
                return base;
            }
        }
        return null;
    }

    /** Vide toutes les bases (retour WAITING / zone supprimée). */
    public void clearAll() {
        bases.clear();
    }
}
