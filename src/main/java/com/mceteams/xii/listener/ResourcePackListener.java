package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Resource pack serveur OBLIGATOIRE (style Hypixel) :
 *
 * - à la CONNEXION : envoi du pack (URL + SHA-1 depuis la config) avec
 *   un message d'acceptation personnalisé ;
 * - au STATUT : si le joueur REFUSE ou que le téléchargement échoue
 *   alors que le pack est obligatoire => DÉCONNEXION automatique.
 *
 * Le pack lui-même (zip de textures/modèles custom) est hébergé par
 * l'administrateur ; la config resource-pack.* pilote tout.
 */
public class ResourcePackListener implements Listener {

    private final XiiPlugin plugin;

    public ResourcePackListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Le système resource-pack est-il actif (activé + URL fournie) ? */
    private boolean enabled() {
        return plugin.getConfigManager().isResourcePackEnabled()
                && !plugin.getConfigManager().getResourcePackUrl().isBlank();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) {
            return;
        }
        var cfg = plugin.getConfigManager();

        String sha1 = cfg.getResourcePackSha1();
        String promptText = cfg.getResourcePackPrompt();
        var prompt = promptText == null || promptText.isBlank() ? null :
                LegacyComponentSerializer.legacySection().deserialize(promptText);

        event.getPlayer().setResourcePack(
                cfg.getResourcePackUrl(),
                sha1 == null || sha1.isBlank() ? null : sha1,
                cfg.isResourcePackMandatory(),
                prompt);
    }

    /**
     * Refus / échec de téléchargement : déconnexion différée de 2 s
     * (laisse le client afficher l'état, puis kick propre).
     */
    @EventHandler
    public void onStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent event) {
        if (!enabled() || !plugin.getConfigManager().isResourcePackMandatory()) {
            return;
        }
        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                Player player = event.getPlayer();
                String message =
                        plugin.getConfigManager().getResourcePackKickMessage();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(LegacyComponentSerializer
                                .legacySection().deserialize(message));
                    }
                }, 40L);
            }
            default -> { /* ACCEPTED / SUCCESSFULLY_LOADED / DISCARDED */ }
        }
    }
}
