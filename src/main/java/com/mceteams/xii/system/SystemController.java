package com.mceteams.xii.system;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GamePhase;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PreparationSubPhase;

/**
 * Le SystemController applique l'état des systèmes selon l'état du jeu
 * (spec §2 et §33).
 *
 * MATRICE CONCEPTUELLE (spec §33) - les listeners ne testent jamais la
 * phase eux-mêmes, ils interrogent GameSystems :
 *
 *  WAITING          : protection, inventory, teamItems, adminItems
 *  COUNTDOWN        : protection, inventory
 *                     (teamItems/adminItems désactivés dès le début du countdown,
 *                      spec §13)
 *  CLASS_SELECTION  : protection, inventory, classSelection
 *  PREPARATION      : protection, mining, blockPlace, combat, death,
 *                     exploration (+ packages selon sous-phase,
 *                     core selon règles de combat)
 *  COMBAT           : combat, death, exploration, core, meteorites,
 *                     packages (selon sous-phase)
 *  ENDING / NONE    : rien (ou minimal)
 *
 * Le contrôleur est le SEUL endroit qui traduit phases -> flags.
 */
public class SystemController {

    private final XiiPlugin plugin;

    public SystemController(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Recalcule et applique l'état des systèmes à partir de l'état
     * courant du jeu. À appeler après CHAQUE changement d'état ou de
     * sous-phase.
     */
    public void refresh() {
        SystemState state = computeCurrentState();
        apply(state);

        // Les items de lobby dépendent aussi de l'état : on les resynchronise.
        plugin.getLobbyItemManager().refreshAllOnline();
    }

    /**
     * Construit le SystemState correspondant à l'état courant.
     * Méthode purement déclarative : chaque ligne = une règle de la matrice.
     */
    private SystemState computeCurrentState() {
        GameState gameState = plugin.getGameManager().getState();
        if (gameState == null) {
            return SystemState.allDisabled();
        }

        return switch (gameState) {
            // Serveur normal : aucun système actif (spec §3/§9).
            case NONE -> SystemState.allDisabled();

            // Attente : protections + items de sélection et d'admin (§12).
            case WAITING -> new SystemState(
                    true,   // protection (invincibilité, pas de casser/poser/pvp...)
                    true,   // inventory (items protégés)
                    true,   // teamItems (sélecteur d'équipe actif)
                    true,   // adminItems (item admin actif pour les ops)
                    false, false, false, false,
                    false, false, false, false,
                    false,  // classSelection
                    true);  // spectator (système prêt)

            // Countdown : on retire immédiatement sélecteur et admin (§13).
            case COUNTDOWN -> new SystemState(
                    true, true,
                    false, false,
                    false, false, false, false,
                    false, false, false, false,
                    false, true);

            // Sélection de classe : GUI ouvrable, toujours protégé (§14).
            case CLASS_SELECTION -> new SystemState(
                    true, true,
                    false, false,
                    false, false, false, false,
                    false, false, false, false,
                    true, true);

            // Préparation : gameplay actif selon les règles (§18).
            case PREPARATION -> preparationState();

            // Combat : PvP global, météorites, cœurs... (§20-§26).
            case COMBAT -> combatState();

            // Fin de partie : plus de gameplay actif.
            case ENDING -> new SystemState(
                    true, true,
                    false, false,
                    false, false, false, false,
                    false, false, false, false,
                    false, true);
        };
    }

    /** État des systèmes pendant la PRÉPARATION (spec §17/§18).
     * NB : l'accessibilité du loot des donjons n'est pas un flag système,
     * elle est suivie par DungeonManager. */
    private SystemState preparationState() {
        PreparationSubPhase sub = plugin.getPhaseManager().getPreparationSubPhase();

        // Les colis apparaissent dès PACKAGES, puis davantage en PACKAGE_UPGRADE :
        // c'est le PackageTask qui module la fréquence, ici on active le système.
        boolean packagesActive = sub != null
                && (sub == PreparationSubPhase.PACKAGES
                || sub == PreparationSubPhase.POINT_UPGRADES
                || sub == PreparationSubPhase.PACKAGE_UPGRADE
                || sub == PreparationSubPhase.DUNGEON_RESTOCK);

        return new SystemState(
                true,           // protection (bases protégées, donjons protégés)
                true,           // inventory (items spéciaux surveillés)
                false, false,   // items lobby éteints
                true,           // mining (points de minage actifs)
                true,           // blockPlace (suivi anti-duplication)
                true,           // combat (PvP selon règles de bases)
                true,           // death (morts + respawn)
                true,           // exploration
                packagesActive,
                false,          // core : les cœurs sont intouchables en préparation
                false,          // météorites : uniquement en combat
                false,          // classSelection terminée
                true);          // spectator (morts temporaires)
    }

    /** État des systèmes pendant le COMBAT (spec §20 à §26). */
    private SystemState combatState() {
        CombatSubPhase sub = plugin.getPhaseManager().getCombatSubPhase();

        // Les météorites commencent à METEORITES et restent ensuite.
        boolean meteorsActive = sub == CombatSubPhase.METEORITES
                || sub == CombatSubPhase.MORE_DAMAGE
                || sub == CombatSubPhase.ALL_CORE_DESTRUCTION
                || sub == CombatSubPhase.MORE_METEORITES
                || sub == CombatSubPhase.SUDDEN_DEATH;

        return new SystemState(
                true,           // protection (règles de bases adaptées au combat)
                true,           // inventory
                false, false,   // items lobby éteints définitivement
                true,           // mining
                true,           // blockPlace
                true,           // combat (PvP global)
                true,           // death
                true,           // exploration
                false,          // packages : mécanique de préparation uniquement
                true,           // core destructible en combat
                meteorsActive,
                false,          // classSelection
                true);          // spectator
    }

    /**
     * Applique un SystemState au GameSystems (copie flag par flag).
     */
    public void apply(SystemState state) {
        GameSystems systems = plugin.getGameSystems();
        systems.setProtectionListenerEnabled(state.protection());
        systems.setInventoryListenerEnabled(state.inventory());
        systems.setTeamItemsEnabled(state.teamItems());
        systems.setAdminItemsEnabled(state.adminItems());
        systems.setMiningListenerEnabled(state.mining());
        systems.setBlockPlaceListenerEnabled(state.blockPlace());
        systems.setCombatListenerEnabled(state.combat());
        systems.setDeathListenerEnabled(state.death());
        systems.setExplorationListenerEnabled(state.exploration());
        systems.setPackageListenerEnabled(state.packages());
        systems.setCoreListenerEnabled(state.core());
        systems.setMeteoriteListenerEnabled(state.meteorites());
        systems.setClassSelectionEnabled(state.classSelection());
        systems.setSpectatorSystemEnabled(state.spectator());

        plugin.getLogger().info("[SystemController] Systèmes appliqués : " + describe(state));
    }

    /** Description compacte pour les logs de debug. */
    private String describe(SystemState state) {
        StringBuilder sb = new StringBuilder();
        if (state.protection()) sb.append("protection ");
        if (state.mining()) sb.append("mining ");
        if (state.combat()) sb.append("combat ");
        if (state.death()) sb.append("death ");
        if (state.exploration()) sb.append("exploration ");
        if (state.packages()) sb.append("packages ");
        if (state.core()) sb.append("core ");
        if (state.meteorites()) sb.append("meteorites ");
        if (state.classSelection()) sb.append("classSelection ");
        if (sb.isEmpty()) {
            sb.append("(aucun)");
        }
        return sb.toString().trim();
    }
}
