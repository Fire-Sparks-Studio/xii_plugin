package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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

        // Envoi du resource pack si configuré.
        String packUrl = plugin.getConfigManager().getResourcePackUrl();
        if (packUrl != null && !packUrl.isEmpty()) {
            String sha1 = plugin.getConfigManager().getResourcePackSha1();
            String prompt = plugin.getConfigManager().getResourcePackPrompt();
            try {
                byte[] sha1Bytes = sha1 != null && sha1.length() == 40
                        ? hexToBytes(sha1) : null;
                java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
                        packUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                // Force = true pour re-demander le pack à chaque fois
                player.setResourcePack(uuid, packUrl, sha1Bytes,
                        prompt != null ? prompt : "", true);
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible d'envoyer le resource pack: " + e.getMessage());
            }
        }

        // Les spectateurs (morts/éliminés) doivent être invisibles pour
        // le nouveau arrivant aussi.
        plugin.getSpectatorService().hideAllSpectatorsFrom(player);

        // TAB : header/footer + fausses entrées d'infos pour le nouveau
        // joueur (no-op si NONE : le plugin n'interfère pas).
        plugin.getTabManager().onJoin(player);

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
                // Vivant : retour direct à la base. Nettoyage préventif des
                // restes spectateur (invisibilité persistée par la vanilla)
                // si la vie a été restaurée pendant la déconnexion.
                plugin.getSpectatorService().ensureNormalState(player);
                if (team.getSpawn() != null) {
                    player.teleport(team.getSpawn());
                }
                plugin.getClassService().applyPassives(player, data, true);
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
    // Respawn (lobby / spectators)
    // -----------------------------------------------------------------

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameState state = plugin.getGameManager().getState();

        // Lobby : forcer le spawn au centre du lobby.
        if (state == GameState.WAITING || state == GameState.COUNTDOWN
                || state == GameState.CLASS_SELECTION) {
            Location lobbySpawn = plugin.getGameManager().getLobbySpawn();
            if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }
            return;
        }

        // Spectateur : forcer le respawn au lobby pour éviter
        // la boucle de morts dans le vide.
        var data = plugin.getPlayerManager().getData(player);
        if (data.isSpectator()) {
            Location lobbySpawn = plugin.getGameManager().getLobbySpawn();
            if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }
        }
    }

    // -----------------------------------------------------------------
    // Déconnexion
    // -----------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameState state = plugin.getGameManager().getState();

        // TAB : oublie l'état poussé à ce joueur qui se déconnecte.
        plugin.getTabManager().onQuit(player.getUniqueId());

        // Barre d'action des points : purge le flux cumulé du joueur.
        plugin.getPointFeedService().onQuit(player.getUniqueId());

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

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
