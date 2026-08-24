package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.manager.BaseManager;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.PlayerData;
import org.bukkit.entity.Player;

/**
 * Logique métier de PROTECTION (spec §12/§18).
 *
 * Centralise les réponses aux questions "peut-il faire X ?" :
 * - ProtectionListener : casser/poser/pvp/drop/pickup ;
 * - CombatListener : PvP selon les bases et les phases ;
 * - InteractionListener : coffres de donjons verrouillés.
 *
 * Règles PvP (interprétation fidèle de la spec) :
 * - WAITING/COUNTDOWN/CLASS_SELECTION/ENDING : aucun PvP.
 * - PRÉPARATION : PvP interdit, SAUF contre un intrus situé dans une
 *   base ennemie ("un joueur adverse qui entre dans la base peut
 *   être attaqué", §18).
 * - COMBAT : PvP global (les restrictions de base changent, §21),
 *   sauf entre coéquipiers (jamais).
 */
public class ProtectionService {

    private final XiiPlugin plugin;

    public ProtectionService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // PvP
    // -----------------------------------------------------------------

    /**
     * Le coup est-il autorisé ?
     * (attacker/victim jamais null ; appelé après filtrage des morts)
     */
    public boolean isPvpAllowed(Player attacker, Player victim) {
        GameState state = plugin.getGameManager().getState();

        // Hors gameplay : aucun PvP (lobby, countdown, classes, fin).
        if (state != GameState.PREPARATION && state != GameState.COMBAT) {
            return false;
        }

        var attackerTeam = plugin.getTeamManager().getTeamOf(attacker.getUniqueId());
        var victimTeam = plugin.getTeamManager().getTeamOf(victim.getUniqueId());

        // Jamais de dégâts entre coéquipiers (toutes phases).
        if (attackerTeam != null && attackerTeam.hasPlayer(victim.getUniqueId())) {
            return false;
        }

        if (state == GameState.COMBAT) {
            return true; // combat : PvP global (§21)
        }

        // --- PRÉPARATION ---------------------------------------------
        BaseManager baseManager = plugin.getBaseManager();

        // La victime se trouve-t-elle dans une base qui n'est PAS la sienne ?
        GameBase victimBase = baseManager.baseAt(victim.getLocation());
        boolean victimIsIntruder = victimBase != null
                && (victimTeam == null || !victimBase.getColor().equals(victimTeam.getColor()));

        // Intrus dans une base ennemie => il peut être attaqué (§18).
        return victimIsIntruder;
    }

    // -----------------------------------------------------------------
    // Monde (casser / poser / ramasser / jeter)
    // -----------------------------------------------------------------

    /**
     * La casse/pose/interaction "monde" doit-elle être bloquée ?
     * (états lobby + fin de partie). Pendant le gameplay, les règles
     * fines sont gérées par MiningService/CoreService/etc.
     */
    public boolean shouldBlockWorldInteraction(Player player) {
        GameState state = plugin.getGameManager().getState();
        return state == GameState.WAITING
                || state == GameState.COUNTDOWN
                || state == GameState.CLASS_SELECTION
                || state == GameState.ENDING;
    }

    /** Ramasser un objet au sol doit-il être interdit ? */
    public boolean shouldBlockPickup(Player player) {
        return shouldBlockWorldInteraction(player);
    }

    /** Jeter un objet doit-il être interdit ? */
    public boolean shouldBlockDrop(Player player) {
        return shouldBlockWorldInteraction(player);
    }

    /**
     * Ouvrir un coffre de donjon est-il permis ?
     * Verrouillé tant que la sous-phase DUNGEONS n'a pas débuté (§17).
     */
    public boolean canOpenDungeonChest() {
        return plugin.getDungeonManager().isLootAccessible();
    }

    /**
     * Un joueur peut-il endommager le monde ? Les spectateurs ne peuvent
     * rien faire (spec §16), quel que soit l'état.
     */
    public boolean isSpectator(Player player) {
        PlayerData data = plugin.getPlayerManager().getData(player);
        return data.isSpectator();
    }
}
