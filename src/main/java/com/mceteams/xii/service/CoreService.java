package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
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
     * @param breaker   joueur responsable (null si automatique/explosion)
     * @param automatic true si destruction automatique (ALL_CORE_DESTRUCTION)
     */
    public void breakCore(GameTeam team, Player breaker, boolean automatic) {
        if (team == null || !team.isHeartAlive()) {
            return; // déjà détruit
        }

        // 1. État du coeur + suppression physique du bloc.
        team.setHeartAlive(false);
        removeCoreBlock(team.getColor());

        // 2. Points au responsable (sauf destruction automatique).
        if (!automatic && breaker != null) {
            plugin.getPointService().award(breaker,
                    PointCategory.CORE,
                    plugin.getConfigManager().getCorePoints(),
                    "coeur détruit");
        }

        // 3. Annonce.
        String by = breaker != null ? " §7par §c" + breaker.getName() : "";
        MessageUtil.broadcast("§4Le cœur de l'équipe "
                + team.getColor().getColoredName() + " §4a été détruit"
                + by + "§4 !");
        SoundUtil.broadcast(org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);

        // 4. Mise à jour de l'élimination + victoire éventuelle.
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
                breakCore(team, null, true);
                anyDestroyed = true;
            }
        }
        if (anyDestroyed) {
            MessageUtil.broadcast("§4Tous les cœurs ont été détruits !");
        }
    }

    /** Supprime physiquement le bloc coeur (set AIR). */
    private void removeCoreBlock(TeamColor color) {
        Location core = coreLocations.get(color);
        if (core != null && core.getWorld() != null) {
            core.getBlock().setType(org.bukkit.Material.AIR);
        }
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
