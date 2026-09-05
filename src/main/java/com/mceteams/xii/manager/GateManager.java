package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * PORTILLONS d'accès des bases (protocole repères).
 *
 * RÈGLE UTILISATEUR :
 * - Les portillons sont DÉSIGNÉS par une laine BLEU CIEL placée dans
 *   l'empreinte de la base (cf. MarkerManager) : seuls ces portillons
 *   sont gérés et s'ouvrent automatiquement. Les autres fence_gate
 *   (ex. portillons de champ) restent fermés et non gérés.
 * - Pendant la PRÉPARATION, un portillon s'OUVRE seulement quand un
 *   JOUEUR PROPRIÉTAIRE de la base est à ≤ {@link #OPEN_RANGE} blocs DE CE
 *   portillon. Chaque portillon est évalué individuellement (un villager
 *   devant un portillon n'ouvre pas les autres).
 * - Les adversaires ne peuvent pas l'utiliser (interaction drole bloquée)
 *   et, seuls, ils le voient fermé.
 * - "Local" : Minecraft n'a qu'un état de bloc serveur-global, on se
 *   rapproche donc du comportement local en n'ouvrant qu'à l'approche
 *   d'un propriétaire.
 * - DÈS LE COMBAT (jour 7+) : les portillons sont DÉTRUITS (blocs
 *   remplacés par de l'air), l'accès est libre partout.
 */
public class GateManager {

    /** Distance HORIZONTALE (blocs XZ) d'un propriétaire déclenchant
     *  l'ouverture. En 2D pour que le portillon s'ouvre dès qu'on
     *  l'approche (peu importe la différence de hauteur, ex. sur un
     *  escalier) et se ferme dès qu'on s'éloigne. */
    private static final double OPEN_RANGE_XZ = 7.0;
    /** Tolérance verticale (blocs) entre le joueur et le portillon. */
    private static final double OPEN_RANGE_Y = 6.0;

    private final XiiPlugin plugin;

    /** Portillons par équipe (ajoutés par MarkerManager). */
    private final Map<TeamColor, List<Location>> gatesByTeam =
            new EnumMap<>(TeamColor.class);

    private org.bukkit.scheduler.BukkitTask task;

    public GateManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre un portillon à ouvrir pour une équipe (appelé par
     * MarkerManager à la pose d'une laine bleu ciel).
     */
    public void registerGate(TeamColor color, Location gate) {
        if (color == null || gate == null || gate.getWorld() == null) {
            return;
        }
        gatesByTeam.computeIfAbsent(color, k -> new ArrayList<>())
                .add(gate.clone());
    }

    // -----------------------------------------------------------------
    // Démarrage / arrêt
    // -----------------------------------------------------------------

    /**
     * Démarre la surveillance des portillons (appelé au passage en
     * PRÉPARATION). Passe la tâche périodique de mise à jour des états.
     */
    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, () -> updateGates(), 10L, 10L);
    }

    /** Arrête la surveillance (fin de préparation / partie / arrêt). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        gatesByTeam.clear();
    }

    /**
     * Détruit DÉFINITIVEMENT tous les portillons (jour 7, entrée en
     * COMBAT) : blocs remplacés par de l'air, accès libre.
     */
    public void removeAllGates() {
        for (List<Location> gates : gatesByTeam.values()) {
            for (Location gate : gates) {
                var block = gate.getBlock();
                if (!block.getType().isAir()) {
                    block.setType(Material.AIR, false);
                }
            }
        }
        gatesByTeam.clear();
        stop();
    }

    // -----------------------------------------------------------------
    // Mise à jour de l'état (par portillon, indépendamment)
    // -----------------------------------------------------------------

    /**
     * Ouvre/ferme chaque portillon selon la proximité d'un PROPRIÉTAIRE.
     * En COMBAT les portillons sont détruits : on ne fait rien.
     */
    private void updateGates() {
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return;
        }
        for (Map.Entry<TeamColor, List<Location>> entry
                : gatesByTeam.entrySet()) {
            for (Location gate : entry.getValue()) {
                boolean open = ownerNear(gate, entry.getKey());
                setOpen(gate, open);
            }
        }
    }

    /** Ferme/ouvre un unique portillon. */
    private void setOpen(Location gate, boolean open) {
        var blockData = gate.getBlock().getBlockData();
        if (blockData instanceof Openable openable
                && openable.isOpen() != open) {
            openable.setOpen(open);
            gate.getBlock().setBlockData(blockData, false);
        }
    }

    /** Un bloc est-il un portillon spruce/oak ? (repère interactions). */
    public boolean isGate(Material type) {
        return type == Material.SPRUCE_FENCE_GATE
                || type == Material.OAK_FENCE_GATE;
    }

    /** Un JOUEUR PROPRIÉTAIRE de la base est-il à portée de CE portillon ? */
    private boolean ownerNear(Location gate, TeamColor ownerColor) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            var team = plugin.getTeamManager().getTeamOf(online.getUniqueId());
            if (team == null || team.getColor() != ownerColor) {
                continue; // pas propriétaire
            }
            Location p = online.getLocation();
            if (!p.getWorld().equals(gate.getWorld())) {
                continue;
            }
            // Distance HORIZONTALE (XZ) : c'est l'essentiel pour ouvrir
            // une porte devant laquelle on avance. La hauteur n'est
            // vérifiée qu'en tolérance pour ignorer les étages.
            double dx = p.getX() - gate.getX();
            double dz = p.getZ() - gate.getZ();
            double dy = Math.abs(p.getY() - gate.getY());
            if (dx * dx + dz * dz <= OPEN_RANGE_XZ * OPEN_RANGE_XZ
                    && dy <= OPEN_RANGE_Y) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Vérification d'interaction (appelée par ProtectionListener)
    // -----------------------------------------------------------------

    /**
     * En PRÉPARATION, seuls les PROPRIÉTAIRES peuvent interagir avec un
     * portillon (l'ouverture est de toute façon automatique pour eux).
     *
     * @return true si {@code player} peut utiliser ce portillon.
     */
    public boolean canUseGate(Player player, Location gate) {
        if (plugin.getGameManager().getState() != GameState.PREPARATION) {
            return true; // en combat il n'y a plus de portillons
        }
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        for (Map.Entry<TeamColor, List<Location>> entry
                : gatesByTeam.entrySet()) {
            if (entry.getValue().contains(gate)) {
                return team != null && team.getColor() == entry.getKey();
            }
        }
        return true;
    }

    /** Nombre total de portillons gérés (diagnostic). */
    public int count() {
        int total = 0;
        for (List<Location> gates : gatesByTeam.values()) {
            total += gates.size();
        }
        return total;
    }
}