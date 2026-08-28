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
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Logique métier des COEURS d'équipe (spec §28).
 *
 * Un coeur peut être :
 * - vivant ;
 * - détruit par un joueur adverse (points CORE attribués) ;
 * - détruit AUTOMATIQUEMENT (sous-phase ALL_CORE_DESTRUCTION, §24) :
 *   ce n'est PAS une mort de joueur, aucune DeathCause n'est créée ;
 * - associé à une équipe éliminée.
 *
 * La destruction enregistre : l'équipe concernée, le joueur responsable
 * (peut être null), les points associés, l'état d'élimination.
 */
public class CoreService {

    private final XiiPlugin plugin;
    /** Positions des coeurs par couleur (remplies par BaseManager). */
    private final Map<TeamColor, Location> coreLocations = new EnumMap<>(TeamColor.class);

    public CoreService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Enregistrement (appelé par BaseManager après placement)
    // -----------------------------------------------------------------

    /** Déclare la position du bloc coeur d'une équipe. */
    public void registerCore(TeamColor color, Location location) {
        coreLocations.put(color, location);
    }

    /** Cette position est-elle un coeur d'équipe ? */
    public boolean isCoreBlock(org.bukkit.block.Block block) {
        for (Location core : coreLocations.values()) {
            if (core != null
                    && core.getWorld() != null
                    && core.getWorld().equals(block.getWorld())
                    && core.getBlockX() == block.getX()
                    && core.getBlockY() == block.getY()
                    && core.getBlockZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    /** @return l'équipe propriétaire du coeur à cette position, ou null. */
    public GameTeam getTeamByCoreBlock(org.bukkit.block.Block block) {
        TeamColor color = null;
        for (Map.Entry<TeamColor, Location> entry : coreLocations.entrySet()) {
            Location core = entry.getValue();
            if (core != null && core.getWorld() != null
                    && core.getWorld().equals(block.getWorld())
                    && core.getBlockX() == block.getX()
                    && core.getBlockY() == block.getY()
                    && core.getBlockZ() == block.getZ()) {
                color = entry.getKey();
                break;
            }
        }
        return color == null ? null : plugin.getTeamManager().getTeam(color);
    }

    // -----------------------------------------------------------------
    // Destruction
    // -----------------------------------------------------------------

    /**
     * Détruit le coeur d'une équipe.
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

        // 1. État du coeur + suppression physique du bloc.
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
     * joueur : aucun DeathCause, aucune téléportation.
     */
    public void destroyAllCores() {
        boolean anyDestroyed = false;
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.COMBAT) {
            return;
        }
        for (GameTeam team : plugin.getTeamManager().all()) {
            if (team.isHeartAlive()) {
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
     * - repose physiquement un bloc BEACON à la position enregistrée ;
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

        // Repose un bloc visible à l'emplacement du coeur (les structures
        // du développeur restent la référence visuelle ; le BEACON sert
        // de replacement standard après une destruction/administration).
        Location core = coreLocations.get(color);
        if (core != null && core.getWorld() != null) {
            core.getBlock().setType(org.bukkit.Material.BEACON);
        }

        MessageUtil.broadcast(" ");
        MessageUtil.broadcast("§f§lRESTAURATION COEUR > §rle coeur de "
                + team.getColor().getColorCode()
                + "l'équipe " + team.getColor().getDisplayName()
                + "§r §fa été restauré§r.");
        MessageUtil.broadcast(" ");
        SoundUtil.broadcast(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
        return true;
    }

    /** Supprime physiquement le bloc coeur (set AIR). */
    private void removeCoreBlock(TeamColor color) {
        Location core = coreLocations.get(color);
        if (core != null && core.getWorld() != null) {
            core.getBlock().setType(org.bukkit.Material.AIR);
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

    /** Nouvelle partie : les positions restent, les états sont reset via TeamManager. */
    public void resetAll() {
        // Les positions ne changent pas ; rien à purger ici.
    }

    /** Accès interne pour les logs/debug. */
    public Map<TeamColor, Location> getCoreLocations() {
        return Map.copyOf(coreLocations);
    }
}
