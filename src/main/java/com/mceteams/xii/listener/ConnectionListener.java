package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Événements de connexion/déconnexion (spec §9/§12/§30).
 *
 * - JOIN : selon l'état du jeu => zone d'attente ou réintégration.
 * - QUIT : préparation => traité comme une mort ; combat => mort
 *   UNIQUEMENT si le joueur était dans la fenêtre de combat de 15 s.
 *
 * La logique est déléguée à ConnectionListener -> managers/services.
 */
public class ConnectionListener implements Listener {

    private final XiiPlugin plugin;

    public ConnectionListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Connexion
    // -----------------------------------------------------------------

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameState state = plugin.getGameManager().getState();

        // Les spectateurs (morts/éliminés) doivent être invisibles pour
        // le nouveau arrivant aussi.
        plugin.getSpectatorService().hideAllSpectatorsFrom(player);

        // Serveur normal : aucune interférence (spec §3/§9).
        if (state == GameState.NONE) {
            return;
        }

        var data = plugin.getPlayerManager().getData(player);
        boolean wasDisconnected = data.isDisconnected();
        data.setDisconnected(false);

        switch (state) {
            // Attente : tout nouveau joueur rejoint la zone d'attente (§12).
            case WAITING -> plugin.getGameManager().sendToLobby(
                    player, plugin.getGameManager().getLobbySpawn());

            // Partie en cours : réintégration selon son statut.
            case COUNTDOWN, CLASS_SELECTION ->
                    plugin.getGameManager().sendToLobby(
                            player, plugin.getGameManager().getLobbySpawn());

            case PREPARATION, COMBAT -> handleMidGameJoin(player, wasDisconnected);

            default -> { /* ENDING : on laisse le joueur où il est. */ }
        }
    }

    /**
     * Réintégration en pleine partie :
     * - avec équipe et non éliminé => retour à sa base ;
     *   s'il attendait un respawn => spectateur temporaire jusqu'au tick ;
     * - sans équipe / éliminé => spectateur permanent.
     */
    private void handleMidGameJoin(Player player, boolean wasDisconnected) {
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        var data = plugin.getPlayerManager().getData(player);

        if (team != null && !data.isEliminated()) {
            // Message d'équipe "il est revenu" (spec §30).
            if (wasDisconnected) {
                plugin.getTeamManager().notifyMembers(team,
                        "§a" + player.getName() + " §7est revenu.",
                        player.getUniqueId());
            }
            if (data.isAlive()) {
                // Vivant : retour direct à la base.
                if (team.getSpawn() != null) {
                    player.teleport(team.getSpawn());
                }
                plugin.getClassService().applyPassives(player, data);
            } else {
                // Mort en attente de respawn : spectateur temporaire,
                // la RespawnTask fera revenir le joueur à échéance.
                plugin.getSpectatorService().enter(player);
                MessageUtil.send(player, "§7Réapparition en cours...");
            }
        } else {
            // Sans équipe ou éliminé : spectateur permanent.
            plugin.getSpectatorService().enterPermanent(player);
        }
    }

    // -----------------------------------------------------------------
    // Déconnexion
    // -----------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameState state = plugin.getGameManager().getState();

        if (state == GameState.NONE || state == GameState.WAITING
                || state == GameState.COUNTDOWN || state == GameState.ENDING) {
            return; // hors gameplay : rien de spécial
        }

        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());

        // Joueur vivant uniquement : les morts ont déjà leur traitement.
        if (!data.isAlive()) {
            return;
        }

        // Message d'équipe "il a quitté" (spec §30).
        if (team != null) {
            plugin.getTeamManager().notifyMembers(team,
                    "§c" + player.getName() + " §7a quitté la partie.",
                    player.getUniqueId());
        }
        data.setDisconnected(true);

        // Qualification : préparation => toujours une mort ; combat =>
        // mort seulement dans la fenêtre de combat (logique centralisée
        // dans DeathService/CombatService, spec §30).
        com.mceteams.xii.enums.DeathCause cause =
                plugin.getDeathService().qualifyDisconnect(data);
        if (cause != null) {
            plugin.getDeathService().handleOfflineDeath(player.getUniqueId(), cause);
        }
    }
}
