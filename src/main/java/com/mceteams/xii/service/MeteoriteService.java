package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameZone;
import com.mceteams.xii.model.Meteorite;
import com.mceteams.xii.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Logique métier des MÉTÉORITES (spec §22/§25).
 *
 * Implémentation : une LargeFireball spawnée en altitude avec une
 * vitesse dirigée vers le sol. À l'impact :
 * - l'explosion vanilla détruit la zone de blocs (et les constructions) ;
 * - WorldListener détecte l'explosion (tag "xii_meteorite") et demande
 *   ICI l'application des dégâts joueurs : 35% à 50% de la vie max.
 *
 * La fréquence est gérée par MeteoriteTask ; en MORE_METEORITES le
 * facteur de config double la fréquence, et PointService double les
 * points "terrain" (minage/exploration).
 */
public class MeteoriteService {

    /** Scoreboard tag identifiant nos boules de feu météorites. */
    public static final String METEORITE_TAG = "xii_meteorite";

    private final XiiPlugin plugin;
    private final Random random = new Random();

    public MeteoriteService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Lancement
    // -----------------------------------------------------------------

    /**
     * Lance une météorite vers un point aléatoire de la zone.
     *
     * ASYNCHRONE : le chunk cible est chargé via getChunkAtAsync avant
     * toute lecture de terrain (sinon gel du serveur).
     */
    public void strike() {
        GameZone zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }

        // La boule de feu est lancée une fois la position résolue.
        LocationUtil.randomDrySurfaceInAsync(zone, target -> {
            if (target == null
                    || plugin.getGameManager().getState()
                    != com.mceteams.xii.enums.GameState.COMBAT) {
                return; // monde absent ou combat terminé entre-temps
            }
            launchFireball(target);
        });
    }

    /** Lance la boule de feu météorite vers le point cible. */
    private void launchFireball(Location target) {
        double power = plugin.getConfigManager().getMeteoritePower();
        int radius = plugin.getConfigManager().getMeteoriteRadius();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double percent = rng.nextDouble(
                plugin.getConfigManager().getMeteoriteDamageMinPercent(),
                plugin.getConfigManager().getMeteoriteDamageMaxPercent());

        // Model conservé pour traçabilité/logs.
        Meteorite meteorite = new Meteorite(target, power, radius, percent);

        // Spawn de la boule de feu à 40 blocs au-dessus de la cible.
        Location launchPoint = LocationUtil.highAbove(target, 40);
        var world = target.getWorld();
        world.spawn(launchPoint, LargeFireball.class,
                fb -> {
                    fb.setVelocity(new Vector(0, -1.8, 0));   // chute verticale
                    fb.setYield((float) power);               // rayon d'explosion
                    fb.setIsIncendiary(false);                // pas d'incendie
                    fb.addScoreboardTag(METEORITE_TAG);       // identification
                });

        plugin.getLogger().fine("[Météorite] Impact prévu à "
                + target.getBlockX() + "/" + target.getBlockZ()
                + " (" + Math.round(percent * 100) + "% dégâts)");
    }

    // -----------------------------------------------------------------
    // Dégâts à l'impact
    // -----------------------------------------------------------------

    /**
     * Applique les dégâts de l'impact aux joueurs proches :
     * entre 35% et 50% de leur vie maximale selon la config.
     * Appelé par WorldListener juste après l'explosion.
     */
    public void applyImpactDamage(Location impactCenter, Entity sourceEntity) {
        int radius = plugin.getConfigManager().getMeteoriteRadius();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (Player player : impactCenter.getWorld().getNearbyEntities(
                impactCenter, radius, radius, radius)
                .stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .toList()) {

            // Les spectateurs ne subissent rien (invulnérables).
            if (player.isInvulnerable()) {
                continue;
            }

            // Fraction aléatoire dans [35%, 50%] pour chaque joueur touché.
            double percent = rng.nextDouble(
                    plugin.getConfigManager().getMeteoriteDamageMinPercent(),
                    plugin.getConfigManager().getMeteoriteDamageMaxPercent());

            var maxHealthAttribute = player.getAttribute(
                    org.bukkit.attribute.Attribute.MAX_HEALTH);
            double maxHealth = maxHealthAttribute != null
                    ? maxHealthAttribute.getValue() : 20.0;
            double damage = maxHealth * percent;

            double newHealth = player.getHealth() - damage;
            if (newHealth <= 0) {
                // Mort par météorite : cause OTHER (gérée par DeathListener).
                player.damage(1000.0);
            } else {
                // Réduction directe : garantit exactement 35-50% de vie perdue
                // indépendamment de l'armure (choix de gameplay documenté).
                player.setHealth(newHealth);
            }
        }
    }

    /**
     * Intervalle avant la prochaine météorite ; divisé par le facteur en
     * MORE_METEORITES (fréquence doublée, spec §25).
     */
    public int nextStrikeDelaySeconds() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int min = plugin.getConfigManager().getMeteoriteMinIntervalSeconds();
        int max = plugin.getConfigManager().getMeteoriteMaxIntervalSeconds();

        boolean upgraded = plugin.getPhaseManager().getCombatSubPhase()
                == com.mceteams.xii.enums.CombatSubPhase.MORE_METEORITES;
        if (upgraded) {
            double factor = plugin.getConfigManager().getMeteoriteUpgradeFactor();
            min = (int) Math.max(1, min / factor);
            max = (int) Math.max(min + 1, max / factor);
        }
        return min + rng.nextInt(Math.max(1, max - min));
    }
}
