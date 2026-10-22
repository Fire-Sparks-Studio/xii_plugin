package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.BaseMarkers;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.structure.StructureRotation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Traitement des REPÈRES (blocs de laine) posés manuellement par le
 * développeur dans l'empreinte d'une base, pour paramétrer le placement.
 *
 * PROTOCOLE REPÈRES (RÈGLE UTILISATEUR) :
 * - Laine ROUGE      : SURFACE de la base (sol) → après la pose de la
 *                      structure, ce bloc est remplacé par un grass_block
 *                      et son Y donne le NIVEAU DU SOL de la base.
 * - Laine BLEU CIEL  : marque un PORTILLON spruce à ouvrir à l'approche
 *                      d'un joueur de l'équipe (fermé pour les adversaires)
 *                      → le bloc est transformé en spruce_fence_gate
 *                      (orienté selon la rotation de la base) et
 *                      enregistré dans le GateManager.
 * - Laine VERTE (ou VERT CLAIR/laine lime) : emplacement du PNJ
 *                      "Upgrades d'équipe" (stoké par NpcManager).
 * - Laine JAUNE      : emplacement du PNJ "Objets/items" (idem).
 * - Laine VIOLETTE   : POINT DE SPAWN INTÉRIEUR de la base : le bloc est
 *                      remplacé par de l'AIR et la position devient le
 *                      spawn des joueurs de l'équipe (à l'intérieur de la
 *                      base, pas devant la façade).
 * - Bannière BLANCHE : à recolorer en couleur d'équipe (une fois les
 *                      bases attribuées).
 * - structure_void   : converti en AIR (la structure void est DANS la
 *                      base) et l'emplacement est mémorisé : il doit
 *                      rester vide de BLOCS NATURELS (ni terrain, ni
 *                      arbre, ni butte). L'équipe qui occupe la base
 *                      peut y poser ses propres blocs ; seuls les
 *                      adversaires y sont interdits.
 *                      Un BLOC ALTERNATIF peut être utilisé
 *                      si l'outil de sauvegarde du .nbt ne conserve pas
 *                      le structure_void (voir la configuration
 *                      {@code bases.void-marker-blocks} : BARRIER,
 *                      LIGHT...).
 *
 * NB : les repères sont appliqués APRÈS l'écriture de la structure,
 * sinon la pose des blocs du .nbt écraserait ces transformations.
 */
public class MarkerManager {

    /** Amplitude verticale scrutée AU-DESSUS de l'ancre (repères au sol /
     *  portillons) ; l'ancre étant 9 blocs sous le sol, +25 couvre le
     *  haut de la structure et au-delà. */
    private static final int SCAN_ABOVE_ANCHOR = 25;

    /** Direction du portillon dans le gabarit (le gabarit est dessiné
     *  face au sud : local +Z = "south"). */
    private static final String GATE_DIRECTION = "south";

    private final XiiPlugin plugin;

    public MarkerManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Analyse l'empreinte d'une base et applique les repères trouvés.
     * Doit être appelé UNE FOIS la structure écrite (thread principal).
     *
     * @param anchor   coin d'origine (bas) de l'empreinte 35x35 de la base
     * @param rotation rotation appliquée à la structure (orientation des
     *                 portillons)
     * @return les repères trouvés (spawn, portillons, PNJ, bannières, ...)
     */
    public BaseMarkers processBase(Location anchor, StructureRotation rotation) {
        World world = anchor.getWorld();
        if (world == null) {
            return new BaseMarkers(null, List.of(), null, null, List.of(),
                    List.of(), null);
        }
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        int baseY = anchor.getBlockY();

        Location spawn = null;
        Integer floorY = null;
        Location upgradeNpc = null;
        Location itemNpc = null;
        List<Location> gates = new ArrayList<>();
        List<Location> banners = new ArrayList<>();
        List<Location> voidSlots = new ArrayList<>();
        int grass = 0;

        // Boîte identique à l'empreinte contenue par GameBase (+1 de marge).
        for (int x = ax - 1; x <= ax + GameBase.SIZE; x++) {
            for (int z = az - 1; z <= az + GameBase.SIZE; z++) {
                for (int y = baseY; y <= baseY + SCAN_ABOVE_ANCHOR; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    // RÈGLE UTILISATEUR : le repère "ESPACE VIDE"
                    // (structure_void, ou le bloc alternatif défini en
                    // config) doit finir en AIR et la position doit
                    // rester vide (pose interdite).
                    if (isVoidMarker(block.getType())) {
                        block.setType(Material.AIR, false);
                        voidSlots.add(block.getLocation());
                        continue;
                    }
                    switch (block.getType()) {
                        case RED_WOOL -> {
                            block.setType(Material.GRASS_BLOCK, false);
                            floorY = y;
                            grass++;
                        }
                        case LIGHT_BLUE_WOOL -> {
                            block.setType(Material.SPRUCE_FENCE_GATE, false);
                            applyGateFacing(block, rotation);
                            gates.add(block.getLocation());
                        }
                        // Le gabarit actuel repère le PNJ upgrade avec une
                        // laine VERT CLAIR (lime) ; on accepte aussi la
                        // laine verte historique.
                        case GREEN_WOOL, LIME_WOOL ->
                                upgradeNpc = block.getLocation();
                        case YELLOW_WOOL ->
                                itemNpc = block.getLocation();
                        // RÈGLE UTILISATEUR : la laine VIOLETTE marque le
                        // SPAWN INTÉRIEUR de la base. On la remplace par de
                        // l'AIR : le joueur apparaît là, à l'intérieur.
                        case PURPLE_WOOL -> {
                            block.setType(Material.AIR, false);
                            spawn = block.getLocation();
                        }
                        // Bannières blanches : recolorées plus tard, une
                        // fois les bases attribuées aux équipes.
                        case WHITE_BANNER, WHITE_WALL_BANNER ->
                                banners.add(block.getLocation());
                        default -> {
                            // aucun repère
                        }
                    }
                }
            }
        }

        if (grass + gates.size() + banners.size() + voidSlots.size() > 0
                || spawn != null || upgradeNpc != null || itemNpc != null) {
            plugin.getLogger().info("[Repères] base : " + grass
                    + " sol(s) (→herbe), " + gates.size()
                    + " portillon(s) (→spruce), " + voidSlots.size()
                    + " espace(s) vide(s) nettoyé(s), " + banners.size()
                    + " bannière(s) blanche(s), spawn="
                    + (spawn == null ? "aucun" : spawn.getBlockX() + ","
                    + spawn.getBlockY() + "," + spawn.getBlockZ()));
        }

        return new BaseMarkers(spawn, gates, upgradeNpc, itemNpc, voidSlots,
                banners, floorY);
    }

    /**
     * Oriente le portillon selon la rotation de la base.
     *
     * ATTENTION : la rotation est appliquée ici (et non par le poseur de
     * template) car le portillon est CRÉÉ après la pose de la structure
     * (remplacement de la laine bleu ciel) : il faut donc l'orienter à la
     * main dans le repère du MONDE.
     */
    private void applyGateFacing(Block block, StructureRotation rotation) {
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            String facing = rotation.rotateDirection(GATE_DIRECTION);
            directional.setFacing(BlockFace.valueOf(
                    facing.toUpperCase(Locale.ROOT)));
        }
        if (data instanceof Openable openable) {
            openable.setOpen(false);
        }
        block.setBlockData(data, false);
    }

    /**
     * Ce bloc est-il un repère "ESPACE VIDE" du gabarit ?
     *
     * RÈGLE UTILISATEUR : le repère canonique est le
     * {@code structure_void}. Si l'outil de sauvegarde du .nbt ne le
     * conserve pas, le développeur peut le remplacer par un autre bloc
     * (BARRIER, LIGHT... : invisibles), listé en configuration
     * ({@code bases.void-marker-blocks}).
     *
     * Dans tous les cas : le bloc finit en AIR et l'emplacement est
     * mémorisé comme "à laisser vide" (pose interdite).
     */
    private boolean isVoidMarker(Material type) {
        if (type == null) {
            return false;
        }
        if (type == Material.STRUCTURE_VOID) {
            return true;
        }
        return plugin.getConfigManager().getVoidMarkerMaterials().contains(type);
    }

    /**
     * Recolore les bannières BLANCHES d'une base en couleur d'équipe
     * (la rotation / l'orientation de la bannière et ses motifs sont
     * conservés).
     *
     * Deux méthodes sont utilisées (robustesse) :
     * - l'API "état de bloc" (Banner#setBaseColor) : conserve l'orientation
     *   et les motifs de la bannière ;
     * - à défaut, la reconstruction du BlockData (matériau + propriétés).
     *
     * @param banners positions des bannières blanches
     * @param color   couleur de l'équipe propriétaire de la base
     * @return nombre de bannières recolorées
     */
    public int recolorBanners(List<Location> banners, TeamColor color) {
        if (banners == null || banners.isEmpty() || color == null) {
            return 0;
        }
        int recolored = 0;
        for (Location location : banners) {
            Block block = location.getBlock();
            if (!isBanner(block.getType())) {
                continue;
            }
            if (recolorBanner(block, color)) {
                recolored++;
            }
        }
        return recolored;
    }

    /**
     * Recolore UNE bannière (la rotation, l'orientation et les motifs sont
     * conservés).
     *
     * MC 1.21.5+ : la couleur de base d'une bannière EST le TYPE DU BLOC
     * (BLUE_BANNER, RED_WALL_BANNER, ...) : l'API "état de bloc"
     * ({@code Banner#setBaseColor}) n'agit plus que sur les bannières
     * "fantômes" de bouclier (cf. javadoc Bukkit : "Only valid for shield
     * pseudo banners, otherwise base depends on block type"). C'est donc
     * le MATÉRIAU qu'il faut changer pour que la bannière change
     * vraiment de couleur (les motifs, eux, sont conservés par le
     * serveur).
     *
     * @return true si la bannière a bien été recolorée.
     */
    private boolean recolorBanner(Block block, TeamColor color) {
        Material target = bannerMaterial(block.getType(), color);
        if (target == null) {
            return false;
        }
        // 1. Changement de MATÉRIAU (seule méthode qui change la couleur
        //    affichée depuis la 1.21.5), en conservant les propriétés de
        //    la bannière (rotation / orientation).
        try {
            BlockData data = block.getBlockData();
            String asString = data.getAsString();
            int bracket = asString.indexOf('[');
            String properties = bracket >= 0 ? asString.substring(bracket) : "";
            String recoloredData = "minecraft:" + target.getKey().getKey()
                    + properties;
            // applyPhysics = false : la bannière garde sa place exacte
            // (une bannière sans support tombe si la physique est
            // appliquée).
            block.setBlockData(Bukkit.createBlockData(recoloredData), false);
            return true;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            plugin.getLogger().warning("[Repères] Bannière non "
                    + "recolorable en " + color + " : " + ex.getMessage());
        }
        // 2. Repli : API "état de bloc" (versions antérieures à la 1.21.5).
        try {
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Banner banner) {
                banner.setBaseColor(dyeColor(color));
                state.update(true, false);
                return true;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Repères] Bannière non recolorable "
                    + "via l'état de bloc : " + throwable.getMessage());
        }
        return false;
    }

    /** Ce matériau est-il une bannière (debout ou mur) ? */
    private static boolean isBanner(Material type) {
        String name = type.name();
        return name.endsWith("_BANNER");
    }

    /** Couleur de teinture (= couleur d'équipe) d'une bannière. */
    private static org.bukkit.DyeColor dyeColor(TeamColor color) {
        return switch (color) {
            case BLUE -> org.bukkit.DyeColor.BLUE;
            case YELLOW -> org.bukkit.DyeColor.YELLOW;
            case RED -> org.bukkit.DyeColor.RED;
            case GREEN -> org.bukkit.DyeColor.GREEN;
        };
    }

    /**
     * Matériau de la bannière recolorée (ou null si le bloc n'est pas une
     * bannière). Les bannières de mur ({@code *_WALL_BANNER}) gardent leur
     * type (posées sur un mur).
     */
    private Material bannerMaterial(Material current, TeamColor color) {
        String name = current.name();
        if (name.endsWith("_WALL_BANNER")) {
            return switch (color) {
                case BLUE -> Material.BLUE_WALL_BANNER;
                case YELLOW -> Material.YELLOW_WALL_BANNER;
                case RED -> Material.RED_WALL_BANNER;
                case GREEN -> Material.GREEN_WALL_BANNER;
            };
        }
        if (name.endsWith("_BANNER")) {
            return switch (color) {
                case BLUE -> Material.BLUE_BANNER;
                case YELLOW -> Material.YELLOW_BANNER;
                case RED -> Material.RED_BANNER;
                case GREEN -> Material.GREEN_BANNER;
            };
        }
        return null;
    }
}
