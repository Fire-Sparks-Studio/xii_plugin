package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.item.TeamSelectorItem;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comportement SPECTATEUR personnalisé (spec §16).
 *
 * Choix d'implémentation (documenté) :
 * le joueur RESTE EN SURVIE pour permettre une hotbar personnalisée
 * (boussole de ciblage), et obtient les propriétés spectateur via :
 * - invulnérabilité (setInvulnerable) ;
 * - invisibilité (effet infini) ;
 * - vol autorisé ;
 * - annulation de toutes ses interactions par les listeners.
 *
 * LIMITATION CONNUE : le "noclip" complet n'est pas réalisable en
 * survie sans packets ; si un jour il devient prioritaire, il suffira
 * de passer le joueur en GameMode.SPECTATOR dans enter()/exit().
 */
public class SpectatorService {

    /** Slot hotbar de la boussole de ciblage. */
    private static final int COMPASS_SLOT = 4;
    /** Type interne PDC de la boussole. */
    private static final String COMPASS_TYPE = "spectator_compass";

    private final XiiPlugin plugin;

    public SpectatorService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Entrée / sortie
    // -----------------------------------------------------------------

    /**
     * Fait entrer un joueur en mode spectateur TEMPORAIRE
     * (mort en attente de respawn, spec §29).
     */
    public void enter(Player player) {
        applySpectatorState(player);
        var data = plugin.getPlayerManager().getData(player);
        data.setSpectator(true);
    }

    /**
     * Fait entrer un joueur en mode spectateur PERMANENT
     * (sans équipe au lancement §15, élimination définitive §29).
     */
    public void enterPermanent(Player player) {
        enter(player);
        plugin.getPlayerManager().getData(player).setEliminated(true);
    }

    /**
     * Fait sortir un joueur du mode spectateur : état normal restauré.
     */
    public void exit(Player player) {
        if (!plugin.getPlayerManager().getData(player).isSpectator()) {
            return; // pas spectateur => rien à faire
        }
        restoreNormalState(player);
        plugin.getPlayerManager().getData(player).setSpectator(false);
    }

    /** Sort tout le monde du mode spectateur (arrêt / fin de partie). */
    public void exitAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            exit(player);
        }
    }

    public boolean isSpectator(UUID uuid) {
        return plugin.getPlayerManager().getData(uuid).isSpectator();
    }

    // -----------------------------------------------------------------
    // État Bukkit
    // -----------------------------------------------------------------

    private void applySpectatorState(Player player) {
        player.setInvulnerable(true);                       // invulnérable
        player.setAllowFlight(true);                        // peut voler
        player.setFlying(true);                             // vole directement
        player.addPotionEffect(new PotionEffect(            // invisible
                PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION,
                0,
                true,
                false));
        giveCompass(player);                                // hotbar spectateur
    }

    private void restoreNormalState(Player player) {
        player.setInvulnerable(false);
        com.mceteams.xii.util.PlayerUtil.removeInvisibility(player);
        player.setFlying(false);
        player.setAllowFlight(false);
        removeCompass(player);
    }

    // -----------------------------------------------------------------
    // Boussole de ciblage (sélection d'un joueur à suivre, spec §16)
    // -----------------------------------------------------------------

    private void giveCompass(Player player) {
        ItemStack compass = ItemUtil.tag(
                ItemUtil.buildNamedItem(Material.COMPASS,
                        "§bSpectateur",
                        List.of("§7Clic droit : joueur suivant.",
                                "§7Clic gauche : joueur précédent.")),
                COMPASS_TYPE);
        player.getInventory().setItem(COMPASS_SLOT, compass);
        player.updateInventory();
    }

    private void removeCompass(Player player) {
        var inventory = player.getInventory();
        if (ItemUtil.isType(inventory.getItem(COMPASS_SLOT), COMPASS_TYPE)) {
            inventory.setItem(COMPASS_SLOT, null);
            player.updateInventory();
        }
    }

    /** L'item cliqué est-il la boussole spectateur ? */
    public boolean isSpectatorCompass(ItemStack item) {
        return ItemUtil.isType(item, COMPASS_TYPE);
    }

    /**
     * Cycle vers le joueur vivant SUIVANT/PRÉCÉDENT et téléporte le
     * spectateur près de lui (comportement type serveur compétitif).
     *
     * @param forward true = suivant, false = précédent.
     */
    public void cycleTarget(Player spectator, boolean forward) {
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(spectator)) {
                continue;
            }
            var data = plugin.getPlayerManager().getData(online);
            if (data.isAlive() && !data.isEliminated() && !data.isSpectator()) {
                targets.add(online);
            }
        }
        if (targets.isEmpty()) {
            com.mceteams.xii.util.MessageUtil.send(spectator,
                    "§7Aucun joueur à observer.");
            return;
        }

        // Ordre stable pour un cycle déterministe.
        targets.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int index = nearestIndex(targets, spectator.getLocation());
        index = forward ? (index + 1) % targets.size()
                : (index - 1 + targets.size()) % targets.size();

        Player target = targets.get(index);
        spectator.teleport(target.getLocation());
        com.mceteams.xii.util.MessageUtil.sendActionBar(spectator,
                "§bObservation : §f" + target.getName());
    }

    /** Index de la cible la plus proche (point de départ du cycle). */
    private int nearestIndex(List<Player> targets, org.bukkit.Location from) {
        double bestDistance = Double.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < targets.size(); i++) {
            double distance = from.getWorld() == targets.get(i).getWorld()
                    ? from.distance(targets.get(i).getLocation())
                    : Double.MAX_VALUE;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
