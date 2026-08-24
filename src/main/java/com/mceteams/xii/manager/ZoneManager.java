package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.config.DataManager;
import com.mceteams.xii.model.GameZone;
import org.bukkit.Location;

/**
 * Gère LA zone de jeu : existence, données, persistance (spec §3).
 *
 * IMPORTANT : le plugin ne crée PAS de monde. La zone est définie dans
 * le monde où l'opérateur exécute /zone set. Tant qu'aucune zone
 * n'existe, le serveur reste un serveur Minecraft normal.
 *
 * Ce manager ne fait QUE de la donnée/persistance : l'orchestration
 * (génération des structures, téléportations, passage en WAITING) est
 * faite par GameManager.
 */
public class ZoneManager {

    private final XiiPlugin plugin;
    private final DataManager dataManager;

    /** Zone courante, ou null si aucune zone n'est définie. */
    private GameZone zone;

    public ZoneManager(XiiPlugin plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;

        // Chargement initial depuis data.yml (fait aussi au démarrage,
        // mais on garde le manager cohérent dès sa création).
        this.zone = dataManager.loadZone();
    }

    /** Une zone est-elle définie ? */
    public boolean hasZone() {
        return zone != null;
    }

    /** @return la zone courante (peut être null). */
    public GameZone getZone() {
        return zone;
    }

    /**
     * Définit la zone à partir de la position de l'opérateur (spec §10).
     * Le centre = position exacte (X, Y, Z) de l'opérateur.
     */
    public GameZone defineZone(Location operatorLocation) {
        this.zone = new GameZone(
                operatorLocation.getWorld().getName(),
                operatorLocation.getX(),
                operatorLocation.getY(),
                operatorLocation.getZ(),
                plugin.getConfigManager().getZoneSize()
        );
        dataManager.saveZone(this.zone);
        plugin.getLogger().info("[Zone] Définie dans '" + zone.getWorldName()
                + "' au centre " + zone.getCenterX() + "/" + zone.getCenterY()
                + "/" + zone.getCenterZ());
        return this.zone;
    }

    /**
     * Remplace la zone chargée sans re-sauvegarder (utilisé au démarrage
     * après validation du monde).
     */
    public void setLoadedZone(GameZone loaded) {
        this.zone = loaded;
    }

    /**
     * Supprime la zone configurée (spec §11).
     * On ne supprime JAMAIS le monde Minecraft : on efface seulement
     * les données du plugin.
     */
    public void deleteZone() {
        this.zone = null;
        dataManager.clearZone();
    }

    /**
     * Le monde associé à la zone existe-t-il encore ? (spec §9)
     * Si non, la zone doit être invalidée au démarrage.
     */
    public boolean isZoneWorldValid() {
        return zone == null || zone.getWorld() != null;
    }
}
