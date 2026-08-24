package com.mceteams.xii.task;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * MORT SUBITE (spec §26).
 *
 * Des dragons apparaissent régulièrement aux bords de la zone et
 * détruisent progressivement la map en volant vers le centre. Ils
 * peuvent aussi cibler occasionnellement les joueurs.
 *
 * La task s'arrête automatiquement à la fin de la sous-phase.
 */
public class SuddenDeathTask extends BukkitRunnable {

    private final XiiPlugin plugin;
    /** Secondes avant le prochain dragon. */
    private int secondsUntilDragon;
    /** Compteur de dragons spawnés (un sur trois cible un joueur). */
    private int dragonsSpawned = 0;

    public SuddenDeathTask(XiiPlugin plugin) {
        this.plugin = plugin;
        // Premier dragon rapide : 10 secondes après le début de la phase.
        this.secondsUntilDragon = 10;
    }

    @Override
    public void run() {
        // Auto-arrêt : uniquement pendant SUDDEN_DEATH.
        if (plugin.getPhaseManager().getCombatSubPhase()
                != com.mceteams.xii.enums.CombatSubPhase.SUDDEN_DEATH) {
            this.cancel();
            return;
        }

        if (--secondsUntilDragon > 0) {
            return;
        }
        spawnDragon();
        secondsUntilDragon =
                plugin.getConfigManager().getSuddenDeathDragonIntervalSeconds();
    }

    /**
     * Spawn d'un dragon à un bord aléatoire de la zone, volant vers le
     * centre. Un dragon sur trois cible un joueur vivant au hasard.
     */
    private void spawnDragon() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }

        // Point de départ : bord aléatoire de la zone, en hauteur.
        var random = java.util.concurrent.ThreadLocalRandom.current();
        int half = zone.getSize() / 2;
        double x = zone.getCenterX() + random.nextInt(-half, half + 1);
        double z = zone.getCenterZ()
                + (random.nextBoolean() ? -half : half); // bord nord ou sud
        if (random.nextBoolean()) {
            z = zone.getCenterZ() + random.nextInt(-half, half + 1);
            x = zone.getCenterX()
                    + (random.nextBoolean() ? -half : half); // ou bord est/ouest
        }
        Location spawnLocation = new Location(zone.getWorld(), x,
                zone.getWorld().getHighestBlockYAt((int) x, (int) z) + 30.0, z);

        EnderDragon dragon = zone.getWorld().spawn(spawnLocation,
                EnderDragon.class, spawned -> {
                    // Le dragon n'a pas de barre de boss "battle" vanilla :
                    // il agit comme un mob sauvage qui détruit la map.
                    spawned.setCustomNameVisible(false);
                });

        // Ciblage occasionnel d'un joueur (spec §26 : "occasionnellement").
        dragonsSpawned++;
        if (dragonsSpawned % 3 == 0) {
            Player target = pickRandomAlivePlayer();
            if (target != null) {
                try {
                    dragon.setTarget(target);
                } catch (Exception ignored) {
                    // API variable selon versions : best-effort assumé.
                }
            }
        }
    }

    /** Choisit un joueur vivant et non spectateur au hasard. */
    private Player pickRandomAlivePlayer() {
        var candidates = plugin.getGameManager().getState() != null
                ? org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(player -> {
                    var data = plugin.getPlayerManager().getData(player);
                    return data.isAlive() && !data.isEliminated() && !data.isSpectator();
                })
                .toList()
                : java.util.List.of();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(candidates.size()));
    }
}
