package com.mceteams.xii.listener;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Détection de l'OUVERTURE des colis (spec §17).
 *
 * Le clic droit sur un coffre enregistré comme colis délègue à
 * PackageService (points PACKAGE, objet rare éventuel).
 */
public class PackageListener implements Listener {

    private final XiiPlugin plugin;

    public PackageListener(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Système colis actif ? (spec §33) */
    private boolean systemEnabled() {
        return plugin.getGameSystems().isPackageListenerEnabled();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!systemEnabled()) {
            return;
        }
        // Clic droit sur un bloc uniquement.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();

        // Ce bloc est-il un colis actif ?
        var pack = plugin.getPackageManager().at(event.getClickedBlock().getLocation());
        if (pack == null || pack.isOpened()) {
            return;
        }

        // CRUCIAL : annule l'interaction SINON vanilla ouvre le vrai coffre
        // par-dessus notre GUI d'animation (aucune attente visible).
        event.setCancelled(true);

        // LANCE L'ANIMATION d'ouverture (spirale ~5 s) : les points et
        // l'accès au vrai coffre n'arrivent qu'À LA FIN de l'animation.
        // Si le joueur ferme avant, il devra recommencer.
        plugin.getPackageService().startOpeningAnimation(player, pack);
    }

    /**
     * Se faire TAPER ferme automatiquement les GUI de colis (animation
     * ou transfert) - mécanique anti-ABUS : on ne loot pas impunément
     * sous les coups ennemis. Le service sait reconnaître ses GUIs.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        plugin.getPackageService().handleHitDuringGui(player);
    }
}
