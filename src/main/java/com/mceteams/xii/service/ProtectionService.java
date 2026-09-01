package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.model.GameBase;
import com.mceteams.xii.model.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Logique métier de PROTECTION (spec §12/§18).
 *
 * Centralise les réponses aux questions "peut-il faire X ?" :
 * - ProtectionListener : casser/poser/pvp/drop/pickup ;
 * - CombatListener : PvP selon les bases et les phases ;
 * - InteractionListener : coffres de donjons verrouillés.
 *
 * Règles PvP (ajustées au retour gameplay) :
 * - WAITING/COUNTDOWN/CLASS_SELECTION/ENDING : aucun PvP.
 * - PRÉPARATION : PvP AUTORISÉ partout, SAUF :
 *     . dans les BASES (aucun combat à l'intérieur, §18) ;
 *     . dans les DONJONS tant que leurs loots ne sont pas accessibles
 *       (zones "verrouillées" avant la sous-phase DUNGEONS).
 * - COMBAT : PvP global (§21), sauf entre coéquipiers (jamais).
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
        org.bukkit.Location victimLocation = victim.getLocation();

        // 1. Dans une base : SEULS les défenseurs (équipe propriétaire)
        //    peuvent frapper un intrus. Tout le reste est interdit
        //    (intrus entre eux, attaquant extérieur, etc.).
        GameBase victimBase = plugin.getBaseManager().baseAt(victimLocation);
        if (victimBase != null) {
            return attackerTeam != null
                    && attackerTeam.getColor() == victimBase.getColor();
        }

        // 2. Interdiction dans les donjons tant que le loot est fermé.
        if (!canOpenDungeonChest()
                && plugin.getDungeonManager().isInDungeonArea(victimLocation)) {
            return false;
        }
        // Partout ailleurs en préparation : PvP autorisé.
        return true;
    }

    /**
     * Droits de MODIFICATION de bloc (casser/poser) pendant le gameplay.
     *
     * RÈGLES DES BASES (pose/casse "propres dès le début", cf. retour
     * utilisateur) :
     * - les blocs de STRUCTURE (posés par le .nbt) sont INVIOLABLES par
     *   tous (incassables + irremplaçables) ;
     * - les CHAMPS (crops, farmland) sont toujours protégés ;
     * - chaque équipe peut poser des blocs dans SA base et casser les
     *   blocs QU'ELLE a posés (les autres blocs y sont intouchables) ;
     * - les CRISTAUX (sea_lantern) sont laissés à CoreListener (ordre
     *   HIGH), qui gère leur destruction de façon contrôlée ;
     * - hors base : modification libre (PRÉPARATION et COMBAT).
     */
    public boolean canModifyBlock(Player player, Block block) {
        GameState state = plugin.getGameManager().getState();
        if (state != GameState.PREPARATION && state != GameState.COMBAT) {
            return false;
        }
        if (isSpectator(player)) {
            return false; // un spectateur ne modifie jamais le monde
        }

        // Cristaux : leur logique de casse appartient à CoreListener /
        // WorldListener (pas de protection de base ici).
        if (plugin.getCoreService().isCrystalBlock(block)) {
            return true;
        }

        GameBase base = plugin.getBaseManager()
                .baseContainingBlock(block.getLocation());
        if (base == null) {
            return true; // hors base : libre
        }

        Material type = block.getType();
        boolean purpleGlass = type == Material.PURPLE_STAINED_GLASS
                || type == Material.PURPLE_STAINED_GLASS_PANE;

        // RÈGLE UTILISATEUR : le VERRE VIOLET (deux formes) est destructible
        // SEULEMENT À PARTIR DU JOUR 7 (COMBAT) et SEULEMENT par les AUTRES
        // équipes (jamais par le propriétaire de la base), à l'image de la
        // protection du CŒUR. Pendant la PRÉPARATION il est inviolable.
        if (purpleGlass
                && plugin.getPhaseManager().getPhase()
                != com.mceteams.xii.enums.GamePhase.COMBAT) {
            return false;
        }

        // 1. Blocs de structure : inviolables par tous (en COMBAT, le verre
        //    violet est géré ci-dessus et échappe à cette règle).
        if (!purpleGlass && plugin.getStructureManager()
                .isStructureBlock(block.getLocation())) {
            return false;
        }

        boolean owner = playerTeamOf(player) == base.getColor();

        // 2. VERRE VIOLET (jour 7+) : cassable PAR LES AUTRES équipes,
        //    jamais par son propriétaire.
        if (purpleGlass) {
            return !owner;
        }

        // 2. Champs : toujours protégés.
        if (isProtectedTerrain(block.getType())) {
            return false;
        }

        // 3. Bloc posé par l'équipe : cassable par l'équipe seule.
        if (base.isOwnedBlock(block.getLocation())) {
            return owner;
        }

        // 4. Tout autre bloc de la base : l'équipe propriétaire seule
        //    (le terrain naturel reste modifiable par ses propriétaires).
        return owner;
    }

    /** @return la couleur de l'équipe du joueur, ou null s'il n'en a pas. */
    private com.mceteams.xii.enums.TeamColor playerTeamOf(Player player) {
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        return team == null ? null : team.getColor();
    }

    /** Les blocs "culture/terre" à protéger en toutes circonstances. */
    private static final Set<Material> PROTECTED_TERRAIN = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.FARMLAND,
            Material.MELON, Material.PUMPKIN,
            Material.MELON_STEM, Material.PUMPKIN_STEM,
            Material.ATTACHED_MELON_STEM, Material.ATTACHED_PUMPKIN_STEM,
            Material.SUGAR_CANE);

    /** Ce matériau est-il un champ protégé ? */
    public boolean isProtectedTerrain(Material material) {
        return PROTECTED_TERRAIN.contains(material);
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
