package com.mceteams.xii.system;

/**
 * Instantané immuable de l'état souhaité des systèmes.
 *
 * Le SystemController calcule un SystemState à partir de l'état du
 * jeu (GameState + phase/sous-phase), puis l'applique au GameSystems.
 * Ce découplage permet de :
 * - comparer avant/après ;
 * - logger les changements ;
 * - tester la matrice d'activation sans serveur.
 */
public class SystemState {

    private final boolean protection;
    private final boolean inventory;
    private final boolean teamItems;
    private final boolean adminItems;
    private final boolean mining;
    private final boolean blockPlace;
    private final boolean combat;
    private final boolean death;
    private final boolean exploration;
    private final boolean packages;
    private final boolean core;
    private final boolean meteorites;
    private final boolean classSelection;
    private final boolean spectator;

    public SystemState(boolean protection, boolean inventory,
                       boolean teamItems, boolean adminItems,
                       boolean mining, boolean blockPlace,
                       boolean combat, boolean death,
                       boolean exploration, boolean packages,
                       boolean core, boolean meteorites,
                       boolean classSelection, boolean spectator) {
        this.protection = protection;
        this.inventory = inventory;
        this.teamItems = teamItems;
        this.adminItems = adminItems;
        this.mining = mining;
        this.blockPlace = blockPlace;
        this.combat = combat;
        this.death = death;
        this.exploration = exploration;
        this.packages = packages;
        this.core = core;
        this.meteorites = meteorites;
        this.classSelection = classSelection;
        this.spectator = spectator;
    }

    /** État "tout éteint" : serveur Minecraft normal. */
    public static SystemState allDisabled() {
        return new SystemState(
                false, false, false, false,
                false, false, false, false,
                false, false, false, false,
                false, false);
    }

    // --- Getters ------------------------------------------------------
    public boolean protection() { return protection; }
    public boolean inventory() { return inventory; }
    public boolean teamItems() { return teamItems; }
    public boolean adminItems() { return adminItems; }
    public boolean mining() { return mining; }
    public boolean blockPlace() { return blockPlace; }
    public boolean combat() { return combat; }
    public boolean death() { return death; }
    public boolean exploration() { return exploration; }
    public boolean packages() { return packages; }
    public boolean core() { return core; }
    public boolean meteorites() { return meteorites; }
    public boolean classSelection() { return classSelection; }
    public boolean spectator() { return spectator; }
}
