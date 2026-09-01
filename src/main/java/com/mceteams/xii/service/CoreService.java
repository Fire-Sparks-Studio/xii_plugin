package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logique métier des COEURS d'équipe (spec §28).
 *
 * Chaque base possède CINQ cristaux (sea_lantern dans le .nbt) :
 * - 4 cristaux répartis dans les 4 tours : ce sont les BOUCLIERS,
 *   ils se détruisent individuellement (petits points + annonce) ;
 * - 1 cristal central au coeur de la base : c'est LE COEUR de l'équipe
 *   (60 points de destruction auto + élimination), il est INATTACCABLE
 *   tant qu'au moins une tour est encore debout.
 *
 * Un coeur peut être :
 * - vivant ;
 * - protégé (tours debout) ;
 * - détruit par un joueur adverse (points CORE attribués) ;
 * - détruit AUTOMATIQUEMENT (sous-phase ALL_CORE_DESTRUCTION, §24) :
 *   ce n'est PAS une mort de joueur, aucune DeathCause n'est créée ;
 * - associé à une équipe éliminée.
 *
 * La destruction enregistre : l'équipe concernée, le joueur responsable
 * (peut être null), les points associés, l'état d'élimination.
 */
public class CoreService {

    /** Bloc cristal (sea crystal) attendu dans les structures de base. */
    public static final Material CRYSTAL_MATERIAL = Material.SEA_LANTERN;

    private final XiiPlugin plugin;

    /**
     * Tableau des cristaux + coffre de dépôt par couleur (remplis par
     * BaseManager après placement de la structure).
     */
    private final Map<TeamColor, BaseCrystals> bases = new EnumMap<>(TeamColor.class);

    /** Localisation des cristaux d'une base. */
    public static final class BaseCrystals {
        /** Cristal central = LE COEUR (source de vérité des points/élimination). */
        public Location center;
        /** Cristaux des 4 tours (boucliers du coeur). */
        public final List<Location> towers = new ArrayList<>();
        /** Tours encore debout (boucliers actifs). */
        public final Set<Location> aliveTowers = new HashSet<>();
        /** Coffre de dépôt des minerais (proche du centre). */
        public Location chest;
    }

    public CoreService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Enregistrement (appelé par BaseManager après placement)
    // -----------------------------------------------------------------

    /**
     * Déclare les cristaux et le coffre de dépôt d'une base.
     *
     * @param center position du cristal central (le coeur)
     * @param towers positions des 4 cristaux de tours
     * @param chest  position du coffre de dépôt (ou null sans coffre)
     */
    public void registerBaseCrystals(TeamColor color, Location center,
                                     List<Location> towers, Location chest) {
        BaseCrystals data = new BaseCrystals();
        data.center = center.clone();
        for (Location tower : towers) {
            data.towers.add(tower.clone());
            data.aliveTowers.add(tower.clone());
        }
        data.chest = chest != null ? chest.clone() : null;
        bases.put(color, data);
    }

    /** Oublie les cristaux/coffre d'une base (équipe retirée/éliminée). */
    public void unregisterBase(TeamColor color) {
        bases.remove(color);
    }

    /** Cette position est-elle un cristal d'équipe (tour ou centre) ? */
    public boolean isCrystalBlock(Block block) {
        return getTeamByCrystalBlock(block) != null;
    }

    /** @return l'équipe propriétaire du cristal à cette position, ou null. */
    public GameTeam getTeamByCrystalBlock(Block block) {
        TeamColor color = null;
        for (Map.Entry<TeamColor, BaseCrystals> entry : bases.entrySet()) {
            BaseCrystals data = entry.getValue();
            if (sameBlock(data.center, block)) {
                color = entry.getKey();
                break;
            }
            for (Location tower : data.towers) {
                if (sameBlock(tower, block)) {
                    color = entry.getKey();
                    break;
                }
            }
            if (color != null) {
                break;
            }
        }
        return color == null ? null : plugin.getTeamManager().getTeam(color);
    }

    /** Ce bloc est-il un cristal de TOUR (bouclier) d'une équipe ? */
    public boolean isTowerCrystal(Block block) {
        for (BaseCrystals data : bases.values()) {
            for (Location tower : data.towers) {
                if (sameBlock(tower, block)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ce bloc est-il le cristal CENTRAL (coeur) d'une équipe ? */
    public boolean isCenterCrystal(Block block) {
        for (BaseCrystals data : bases.values()) {
            if (sameBlock(data.center, block)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Coffre de dépôt (minerais / redstone)
    // -----------------------------------------------------------------

    /** @return l'équipe propriétaire du coffre de dépôt à cette position. */
    public GameTeam getTeamByDepositChest(Block block) {
        for (Map.Entry<TeamColor, BaseCrystals> entry : bases.entrySet()) {
            if (sameBlock(entry.getValue().chest, block)) {
                return plugin.getTeamManager().getTeam(entry.getKey());
            }
        }
        return null;
    }

    /** Ce bloc est-il un coffre de dépôt enregistré ? */
    public boolean isDepositChest(Block block) {
        return getTeamByDepositChest(block) != null;
    }

    // -----------------------------------------------------------------
    // Boucliers (tours)
    // -----------------------------------------------------------------

    /** Nombre de tours encore debout pour une équipe. */
    public int towersAlive(TeamColor color) {
        BaseCrystals data = bases.get(color);
        return data == null ? 0 : data.aliveTowers.size();
    }

    /** Le coeur est-il protégé par au moins une tour encore debout ? */
    public boolean isHeartShielded(TeamColor color) {
        return towersAlive(color) > 0;
    }

    /**
     * Détruit UN cristal de tour (bouclier).
     *
     * @param team    équipe dont la tour est détruite
     * @param block   bloc cristal cassé
     * @param breaker joueur responsable (null si explosion/auto)
     */
    public void breakTowerCrystal(GameTeam team, Block block, Player breaker) {
        if (team == null) {
            return;
        }
        BaseCrystals data = bases.get(team.getColor());
        if (data == null) {
            return;
        }

        // Identifie la position enregistrée correspondant à ce bloc.
        Location tower = null;
        for (Location candidate : data.towers) {
            if (sameBlock(candidate, block)) {
                tower = candidate;
                break;
            }
        }
        if (tower == null || !data.aliveTowers.remove(tower)) {
            return; // déjà détruite
        }

        // Suppression physique du cristal.
        if (tower.getWorld() != null) {
            tower.getBlock().setType(Material.AIR);
        }

        int remaining = data.aliveTowers.size();

        // Petits points au destructeur (config).
        if (breaker != null) {
            plugin.getPointService().award(breaker, PointCategory.CORE,
                    plugin.getConfigManager().getCoreTowerPoints(),
                    "cristal de tour détruit");
        }

        String destroyerPart = breaker != null
                ? " par " + coloredPlayerName(breaker) : "";
        MessageUtil.broadcast("\n§f§lCRISTAL DETRUIT > §rle cristal d'une tour de "
                + team.getColor().getColorCode() + "l'équipe "
                + team.getColor().getDisplayName() + "§r a été détruit"
                + destroyerPart + "§7 (" + remaining
                + " tour(s) restante(s)).\n");
        SoundUtil.broadcast(org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);

        // Dernière tour détruite : le coeur est enfin exposé.
        if (remaining == 0) {
            MessageUtil.broadcast("\n§f§lCOEUR EXPOSE > §rles tours de "
                    + team.getColor().getColorCode() + "l'équipe "
                    + team.getColor().getDisplayName()
                    + "§r sont détruites : §4le cœur est désormais exposé§r !\n");
            SoundUtil.broadcast(org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
        }
    }

    /** Fait tomber toutes les tours d'un coup (destruction auto). */
    private void clearAllTowers(BaseCrystals data) {
        for (Location tower : new ArrayList<>(data.aliveTowers)) {
            if (tower.getWorld() != null) {
                tower.getBlock().setType(Material.AIR);
            }
            data.aliveTowers.remove(tower);
        }
    }

    // -----------------------------------------------------------------
    // Destruction du coeur (cristal central)
    // -----------------------------------------------------------------

    /**
     * Détruit le coeur d'une équipe.
     *
     * NB : le coeur est INATTACCABLE tant que des tours sont debout ; la
     * vérification du bouclier appartient aux listeners (CoreListener,
     * WorldListener). Cette méthode exécute la destruction elle-même.
     *
     * @param team      équipe dont le coeur est détruit
     * @param breaker   joueur responsable (null si explosion/auto/admin)
     * @param silentPoints true => aucun point attribué (admin/auto)
     * @param byAdmin   true => l'annonce précise "par un administrateur"
     */
    public void breakCore(GameTeam team, Player breaker,
                          boolean silentPoints, boolean byAdmin) {
        if (team == null || !team.isHeartAlive()) {
            return; // déjà détruit
        }

        // 1. État du coeur + suppression physique du cristal central.
        team.setHeartAlive(false);
        removeCoreBlock(team.getColor());

        // 2. Points au responsable (sauf destruction auto/admin).
        if (!silentPoints && breaker != null) {
            plugin.getPointService().award(breaker,
                    PointCategory.CORE,
                    plugin.getConfigManager().getCorePoints(),
                    "coeur détruit");
        }

        // 3. Annonce CHAT personnalisée :
        //    - membre de l'équipe touchée : "votre coeur a été détruit..."
        //    - autres : "le coeur de l'équipe X a été détruit..."
        //    Attribution : "par <joueur coloré>" ou "par un administrateur".
        String destroyerPart;
        if (byAdmin) {
            destroyerPart = " §7par §fun administrateur";
        } else if (breaker != null) {
            destroyerPart = " par " + coloredPlayerName(breaker);
        } else {
            destroyerPart = "";
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            var playerTeam = plugin.getTeamManager().getTeamOf(online.getUniqueId());
            if (playerTeam == team) {
                MessageUtil.send(online,
                        "\n§f§lDESTRUCTION COEUR > §rvotre coeur a été détruit"
                                + destroyerPart + ".\n");
                // TITRE dédié aux membres de l'équipe touchée.
                MessageUtil.sendTitle(online,
                        "§c§lCOEUR DETRUIT !",
                        "§fVous ne pouvez plus réapparaitre.",
                        10, 80, 20);
            } else {
                MessageUtil.send(online,
                        "\n§f§lDESTRUCTION COEUR > §rle coeur de "
                                + team.getColor().getColorCode()
                                + "l'équipe " + team.getColor().getDisplayName()
                                + "§r a été détruit" + destroyerPart + ".\n");
            }
        }

        // 4. Son : GROWL DE DRAGON (pitch 1), comme le début de partie.
        SoundUtil.broadcast(org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);

        // 5. Mise à jour de l'élimination + victoire éventuelle.
        plugin.getTeamManager().updateElimination(team);
        plugin.getGameManager().checkVictoryConditions();
    }

    /**
     * Sous-phase ALL_CORE_DESTRUCTION (spec §24) : TOUS les cœurs encore
     * actifs sont détruits automatiquement. Ce n'est PAS une mort de
     * joueur : aucun DeathCause, aucune téléportation. Les tours tombent
     * aussi pour que l'événement soit visuellement complet.
     */
    public void destroyAllCores() {
        boolean anyDestroyed = false;
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.COMBAT) {
            return;
        }
        for (GameTeam team : plugin.getTeamManager().all()) {
            if (team.isHeartAlive()) {
                BaseCrystals data = bases.get(team.getColor());
                if (data != null) {
                    clearAllTowers(data);
                }
                breakCore(team, null, true, false);
                anyDestroyed = true;
            }
        }
        if (anyDestroyed) {
            MessageUtil.broadcast("\n§4✖ §fTous les cœurs §7ont été réduits à néant !\n");
        }
    }

    /**
     * RESTAURE le coeur d'une équipe (commande admin) :
     * - remet l'état "vivant" ;
     * - repose physiquement le cristal central ET les 4 cristaux de tours
     *   à leurs positions enregistrées ;
     * - annonce la restauration à tous.
     *
     * @return false si l'équipe est inconnue ou si le coeur vivait déjà.
     */
    public boolean restoreCore(TeamColor color) {
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team == null || team.isHeartAlive()) {
            return false;
        }
        team.setHeartAlive(true);

        BaseCrystals data = bases.get(color);
        if (data != null) {
            // Coeur central.
            if (data.center != null && data.center.getWorld() != null) {
                data.center.getBlock().setType(CRYSTAL_MATERIAL);
            }
            // Les 4 tours (cristaux = boucliers).
            data.aliveTowers.clear();
            for (Location tower : data.towers) {
                if (tower.getWorld() != null) {
                    tower.getBlock().setType(CRYSTAL_MATERIAL);
                }
                data.aliveTowers.add(tower);
            }
        }

        MessageUtil.broadcast(" ");
        MessageUtil.broadcast("§f§lRESTAURATION COEUR > §rle coeur et les cristaux de "
                + team.getColor().getColorCode()
                + "l'équipe " + team.getColor().getDisplayName()
                + "§r §font été restaurés§r.");
        MessageUtil.broadcast(" ");
        SoundUtil.broadcast(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return true;
    }

    /** Supprime physiquement le cristal central d'une équipe (set AIR). */
    private void removeCoreBlock(TeamColor color) {
        BaseCrystals data = bases.get(color);
        if (data != null && data.center != null && data.center.getWorld() != null) {
            data.center.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Nom du joueur coloré avec SA couleur d'équipe (blanc si sans équipe).
     */
    private String coloredPlayerName(Player player) {
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        String colorCode = team != null
                ? team.getColor().getColorCode()
                : "§f";
        return colorCode + player.getName();
    }

    /** Deux positions pointent-elles le même bloc ? */
    private boolean sameBlock(Location location, Block block) {
        if (location == null || block == null) {
            return false;
        }
        return location.getWorld() != null
                && location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    /**
     * Nouvelle partie : les positions restent, les états des tours sont
     * ré-armés (le coeur est remis via TeamManager.resetTransientState).
     */
    public void resetAll() {
        for (BaseCrystals data : bases.values()) {
            data.aliveTowers.clear();
            data.aliveTowers.addAll(data.towers);
        }
    }
}