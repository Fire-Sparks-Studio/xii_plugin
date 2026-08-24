package com.mceteams.xii.system;

/**
 * Systèmes activables/désactivables INDÉPENDAMMENT des phases
 * (spec §2 et §33).
 *
 * PRINCIPE CLÉ : un listener ne doit JAMAIS tester lui-même la phase
 * courante ("if phase == PREPARATION"). Il doit demander au système :
 *
 *     if (!gameSystems.isMiningListenerEnabled()) { return; }
 *
 * Avantages :
 * - recherche rapide d'un système avec Ctrl+F ;
 * - désactivation temporaire d'un système sans toucher aux phases ;
 * - interruption indépendante de certains mécanismes ;
 * - listeners découplés des phases ;
 * - debug facile.
 *
 * Les flags sont normalement pilotés par {@link SystemController}
 * selon l'état du jeu, mais restent modifiables à la main.
 */
public class GameSystems {

    // --- Protection / inventaire -----------------------------------
    /** Restrictions "lobby" : pas de casser/poser/pvp/drop/pickup. */
    private boolean protectionEnabled;
    /** Surveillance de l'inventaire (items spéciaux, lignes verrouillées). */
    private boolean inventoryEnabled;
    /** Item sélecteur d'équipe actif (clic droit => GUI). */
    private boolean teamItemsEnabled;
    /** Item admin actif (opérateurs, lobby uniquement). */
    private boolean adminItemsEnabled;

    // --- Gameplay ----------------------------------------------------
    /** Points de minage actifs. */
    private boolean miningEnabled;
    /** Suivi anti-abus du placement de blocs actif. */
    private boolean blockPlaceEnabled;
    /** Combat (PvP, dégâts, kills) actif. */
    private boolean combatEnabled;
    /** Mort/respawn actif. */
    private boolean deathEnabled;
    /** Exploration (chunks) active. */
    private boolean explorationEnabled;
    /** Colis (packages) actifs. */
    private boolean packageEnabled;
    /** Cœurs destructibles. */
    private boolean coreEnabled;
    /** Météorites actives. */
    private boolean meteoriteEnabled;

    // --- Divers -------------------------------------------------------
    /** Sélection de classe active (GUI ouvrable). */
    private boolean classSelectionEnabled;
    /** Mode spectateur custom géré par SpectatorService. */
    private boolean spectatorEnabled;

    /**
     * Désactive tous les systèmes (serveur normal, spec §9).
     */
    public void reset() {
        protectionEnabled = false;
        inventoryEnabled = false;
        teamItemsEnabled = false;
        adminItemsEnabled = false;
        miningEnabled = false;
        blockPlaceEnabled = false;
        combatEnabled = false;
        deathEnabled = false;
        explorationEnabled = false;
        packageEnabled = false;
        coreEnabled = false;
        meteoriteEnabled = false;
        classSelectionEnabled = false;
        spectatorEnabled = false;
    }

    // --- Getters/Setters (nommés pour être retrouvés au Ctrl+F) ------

    public boolean isProtectionListenerEnabled() {
        return protectionEnabled;
    }

    public void setProtectionListenerEnabled(boolean enabled) {
        this.protectionEnabled = enabled;
    }

    public boolean isInventoryListenerEnabled() {
        return inventoryEnabled;
    }

    public void setInventoryListenerEnabled(boolean enabled) {
        this.inventoryEnabled = enabled;
    }

    public boolean isTeamItemsEnabled() {
        return teamItemsEnabled;
    }

    public void setTeamItemsEnabled(boolean enabled) {
        this.teamItemsEnabled = enabled;
    }

    public boolean isAdminItemsEnabled() {
        return adminItemsEnabled;
    }

    public void setAdminItemsEnabled(boolean enabled) {
        this.adminItemsEnabled = enabled;
    }

    public boolean isMiningListenerEnabled() {
        return miningEnabled;
    }

    public void setMiningListenerEnabled(boolean enabled) {
        this.miningEnabled = enabled;
    }

    public boolean isBlockPlaceListenerEnabled() {
        return blockPlaceEnabled;
    }

    public void setBlockPlaceListenerEnabled(boolean enabled) {
        this.blockPlaceEnabled = enabled;
    }

    public boolean isCombatListenerEnabled() {
        return combatEnabled;
    }

    public void setCombatListenerEnabled(boolean enabled) {
        this.combatEnabled = enabled;
    }

    public boolean isDeathListenerEnabled() {
        return deathEnabled;
    }

    public void setDeathListenerEnabled(boolean enabled) {
        this.deathEnabled = enabled;
    }

    public boolean isExplorationListenerEnabled() {
        return explorationEnabled;
    }

    public void setExplorationListenerEnabled(boolean enabled) {
        this.explorationEnabled = enabled;
    }

    public boolean isPackageListenerEnabled() {
        return packageEnabled;
    }

    public void setPackageListenerEnabled(boolean enabled) {
        this.packageEnabled = enabled;
    }

    public boolean isCoreListenerEnabled() {
        return coreEnabled;
    }

    public void setCoreListenerEnabled(boolean enabled) {
        this.coreEnabled = enabled;
    }

    public boolean isMeteoriteListenerEnabled() {
        return meteoriteEnabled;
    }

    public void setMeteoriteListenerEnabled(boolean enabled) {
        this.meteoriteEnabled = enabled;
    }

    public boolean isClassSelectionEnabled() {
        return classSelectionEnabled;
    }

    public void setClassSelectionEnabled(boolean enabled) {
        this.classSelectionEnabled = enabled;
    }

    public boolean isSpectatorSystemEnabled() {
        return spectatorEnabled;
    }

    public void setSpectatorSystemEnabled(boolean enabled) {
        this.spectatorEnabled = enabled;
    }
}
