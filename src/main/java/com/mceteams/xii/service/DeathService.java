package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.DeathCause;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.model.PlayerData;
import com.mceteams.xii.util.MessageUtil;
import com.mceteams.xii.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Logique métier des MORTS (spec §19/§29/§30).
 *
 * Séquence officielle d'une mort (spec §19) :
 * 1. la mort est enregistrée (alive=false, DeathCause) ;
 * 2. des points peuvent être retirés (pénalité) ;
 * 3. le kill streak de l'équipe de la victime est réinitialisé ;
 *    (fait par CombatService.registerKill)
 * 4. le joueur part en spectateur temporaire ;
 * 5. il reçoit son titre de mort ;
 * 6. son délai de respawn est programmé (RespawnManager).
 */
public class DeathService {

    private final XiiPlugin plugin;

    public DeathService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Traite la mort d'un joueur EN LIGNE.
     *
     * @param victim joueur mort
     * @param cause  cause qualifiée (PLAYER/DISCONNECT/OTHER)
     * @param killer tueur éventuel (null si environnemental).
     */
    public void handleDeath(Player victim, DeathCause cause, Player killer) {
        PlayerData data = plugin.getPlayerManager().getData(victim);
        if (!data.isAlive()) {
            // Déjà mort (spectateur qui re-meurt : chute dans le vide
            // pendant la mort subite...) => on ré-applique proprement
            // l'état spectateur au lieu de le laisser se faire rendre
            // par la vanilla à un spawn aléatoire.
            plugin.getSpectatorService().reapply(victim);
            return;
        }

        // 1. Enregistrement.
        data.setAlive(false);
        data.setDeathCause(cause);

        // 1bis. DROP SPÉCIFIQUE : les produits de MINERAI (fondus ou non)
        // et les objets RARES/LÉGENDAIRES tombent au sol à l'endroit de
        // la mort. TOUT le reste de l'inventaire est conservé
        // (keepInventory). Les items spéciaux du plugin (barrières
        // Mineur, items lobby) ne sont jamais droppés.
        dropLootItems(victim);

        // Kills / streaks / points du tueur (spec §18 : surveillance combat).
        plugin.getCombatService().registerKill(killer, victim);

        // 2. Pénalité de points.
        int penalty = plugin.getConfigManager().getDeathPenalty();
        plugin.getPointService().remove(victim, penalty, "mort");

        // 4. Spectateur temporaire.
        plugin.getSpectatorService().enter(victim);

        // 5. Titre de mort + délai annoncé (texte FRANÇAIS, spec §29).
        int delay = plugin.getRespawnManager().schedule(victim.getUniqueId());
        if (delay > 0) {
            // PRÉPARATION : respawn minuté classique.
            MessageUtil.sendTitle(victim,
                    "§cTU ES MORT",
                    "§7Réapparition dans §e" + delay + " seconde(s)",
                    10, 60, 10);
        } else {
            // COMBAT (jour 7+) : pas de timer, retour au début de la
            // prochaine sous-phase.
            MessageUtil.sendTitle(victim,
                    "§cTU ES MORT",
                    "§7Réapparition au début de la §eprochaine sous-phase",
                    10, 60, 10);
        }
        SoundUtil.playDeath(victim);

        // Annonce à tous + vérification victoire anticipée (style Hypixel).

        // KILL FINAL : UNIQUEMENT si le coeur de l'équipe de la victime
        // est détruit (chaque kill est alors définitif).
        var victimTeam = plugin.getTeamManager().getTeamOf(victim.getUniqueId());
        boolean finalKill = victimTeam != null && !victimTeam.isHeartAlive();

        // Si le coeur est déjà mort et que la victime était le dernier
        // debout : l'équipe passe ELIMINEE immédiatement (annonce incluse),
        // sans attendre le traitement du respawn.
        if (victimTeam != null && !victimTeam.isHeartAlive()) {
            plugin.getTeamManager().updateElimination(victimTeam);
        }

        String finalSuffix = (finalKill ? " §b§lKILL FINAL" : "") + ".";
        if (killer != null) {
            MessageUtil.broadcast("\n§c☠ §f" + victim.getName()
                    + " §7a été tué par §c" + killer.getName()
                    + finalSuffix + "\n");
        } else {
            MessageUtil.broadcast("\n§c☠ §f" + victim.getName()
                    + " §7est mort"
                    + finalSuffix + "\n");
        }
        plugin.getGameManager().checkVictoryConditions();
    }

    /**
     * DROP les items "précieux" à la mort du joueur :
     * - produits de minerai (bruts ou fondus) - cf. MiningService ;
     * - objets RARES / LÉGENDAIRES (tag PDC).
     *
     * Les items spéciaux du plugin (barrières Mineur, boussole...) et
     * tout le RESTE de l'inventaire restent sur le joueur
     * (keepInventory actif).
     */
    private void dropLootItems(Player victim) {
        var inventory = victim.getInventory();
        var location = victim.getLocation();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            // Jamais les items spéciaux du plugin.
            if (com.mceteams.xii.util.ItemUtil.isSpecialItem(item)) {
                continue;
            }

            boolean isOre = com.mceteams.xii.service.MiningService
                    .isOreDrop(item.getType());
            boolean isRare = com.mceteams.xii.util.ItemUtil
                    .isRareOrLegendary(item);

            if (isOre || isRare) {
                inventory.setItem(slot, null);
                victim.getWorld().dropItemNaturally(location, item);
            }
        }
        victim.updateInventory();
    }

    /**
     * Traite une mort SANS joueur en ligne (déconnexion en pleine
     * partie, spec §30).
     *
     * RÈGLE UTILISATEUR : toute déconnexion pendant une partie =
     * UNE VRAIE MORT (même système punitif que les morts en ligne :
     * pénalité de points + reset de kill streak + respawn programmé).
     * Si le joueur avait récemment été touché par un adversaire, ce
     * dernier se voit créditer le kill (comme une mort classique).
     *
     * Pas de titre ni de spectateur : le joueur est hors ligne. Il sera
     * pris en charge à sa reconnexion par ConnectionListener (revenu
     * vivant une fois son délai de respawn écoulé).
     */
    public void handleOfflineDeath(UUID victimUuid, DeathCause cause) {
        PlayerData data = plugin.getPlayerManager().getData(victimUuid);
        if (!data.isAlive()) {
            return;
        }
        data.setAlive(false);
        data.setDeathCause(cause);

        // Pénalité + respawn programmé même hors ligne.
        int penalty = plugin.getConfigManager().getDeathPenalty();
        data.getScore().addPenalty(penalty);

        var team = plugin.getTeamManager().getTeamOf(victimUuid);
        if (team != null) {
            team.resetKillStreak();          // streak réinitialisé
            team.getScore().addPenalty(penalty);
        }

        // Kill crédité au dernier adversaire l'ayant touché (mort jugée).
        // S'il est EN LIGNE il reçoit points/annonces; sinon rien à
        // afficher (le tueur est aussi parti).
        UUID lastDamager = data.getLastDamager();
        if (lastDamager != null) {
            plugin.getCombatService().registerKill(lastDamager, victimUuid);
        }
        data.clearLastDamage();

        plugin.getRespawnManager().schedule(victimUuid);
        plugin.getGameManager().checkVictoryConditions();
    }

    /**
     * Qualifie la cause d'une déconnexion en pleine partie.
     *
     * RÈGLE UTILISATEUR (spec §30 simplifiée) : EN JEU (préparation OU
     * combat), TOUTE déconnexion compte comme une mort classique
     * (DeathCause.DISCONNECT), avec la même punition et le même délai de
     * respawn que les autres morts.
     */
    public DeathCause qualifyDisconnect(PlayerData data) {
        return DeathCause.DISCONNECT;
    }
}
