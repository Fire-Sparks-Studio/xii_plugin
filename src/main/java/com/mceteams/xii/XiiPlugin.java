package com.mceteams.xii;

import com.mceteams.xii.command.PartyCommand;
import com.mceteams.xii.command.TeamCommand;
import com.mceteams.xii.command.ZoneCommand;
import com.mceteams.xii.config.ConfigManager;
import com.mceteams.xii.config.DataManager;
import com.mceteams.xii.config.FileManager;
import com.mceteams.xii.item.LobbyItemManager;
import com.mceteams.xii.listener.BlockPlaceListener;
import com.mceteams.xii.listener.CombatListener;
import com.mceteams.xii.listener.ConnectionListener;
import com.mceteams.xii.listener.CoreListener;
import com.mceteams.xii.listener.DeathListener;
import com.mceteams.xii.listener.ExplorationListener;
import com.mceteams.xii.listener.InteractionListener;
import com.mceteams.xii.listener.InventoryListener;
import com.mceteams.xii.listener.MiningListener;
import com.mceteams.xii.listener.PackageListener;
import com.mceteams.xii.listener.ProtectionListener;
import com.mceteams.xii.listener.TeamListener;
import com.mceteams.xii.listener.WorldListener;
import com.mceteams.xii.manager.BaseManager;
import com.mceteams.xii.manager.ClassManager;
import com.mceteams.xii.manager.DungeonManager;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.PackageManager;
import com.mceteams.xii.manager.PhaseManager;
import com.mceteams.xii.manager.PlayerManager;
import com.mceteams.xii.manager.RespawnManager;
import com.mceteams.xii.manager.StructureManager;
import com.mceteams.xii.manager.TeamManager;
import com.mceteams.xii.manager.ZoneManager;
import com.mceteams.xii.scoreboard.ScoreboardManager;
import com.mceteams.xii.scoreboard.TabManager;
import com.mceteams.xii.service.ClassService;
import com.mceteams.xii.service.CombatService;
import com.mceteams.xii.service.CoreService;
import com.mceteams.xii.service.DeathService;
import com.mceteams.xii.service.ExplorationService;
import com.mceteams.xii.service.MeteoriteService;
import com.mceteams.xii.service.MiningService;
import com.mceteams.xii.service.PackageService;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.ProtectionService;
import com.mceteams.xii.service.SpectatorService;
import com.mceteams.xii.system.GameSystems;
import com.mceteams.xii.system.SystemController;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée du plugin XII-Days.
 *
 * Responsabilité UNIQUE : initialiser et relier les composants dans le
 * bon ordre (spec §38). Aucune logique de gameplay ici.
 *
 * Séquence au démarrage (spec §9) :
 * 1. chargement configuration ;
 * 2. chargement données persistantes (zone) ;
 * 3. vérification zone + monde associé ;
 * 4. zone valide => WAITING ; monde disparu => zone invalidée,
 *    serveur normal ; aucune partie jamais reprise.
 */
public class XiiPlugin extends JavaPlugin {

    // --- Configuration -------------------------------------------------
    private FileManager fileManager;
    private ConfigManager configManager;
    private DataManager dataManager;

    // --- Systèmes --------------------------------------------------------
    private GameSystems gameSystems;
    private SystemController systemController;

    // --- Managers ---------------------------------------------------------
    private ZoneManager zoneManager;
    private PlayerManager playerManager;
    private TeamManager teamManager;
    private PhaseManager phaseManager;
    private StructureManager structureManager;
    private BaseManager baseManager;
    private DungeonManager dungeonManager;
    private ClassManager classManager;
    private RespawnManager respawnManager;
    private PackageManager packageManager;
    private GameManager gameManager;

    // --- Services -----------------------------------------------------------
    private PointService pointService;
    private CombatService combatService;
    private MiningService miningService;
    private ExplorationService explorationService;
    private SpectatorService spectatorService;
    private ClassService classService;
    private DeathService deathService;
    private CoreService coreService;
    private PackageService packageService;
    private MeteoriteService meteoriteService;
    private ProtectionService protectionService;

    // --- Affichage / items ----------------------------------------------------
    private ScoreboardManager scoreboardManager;
    private TabManager tabManager;
    private LobbyItemManager lobbyItemManager;

    @Override
    public void onLoad() {
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("===[XII Days]=== Démarrage...");

        // 1) Fichiers + clé PDC des items spéciaux.
        this.fileManager = new FileManager(this);
        this.fileManager.setup();
        ItemUtil.init(this);

        // 2) Configuration statique + données persistantes.
        this.configManager = new ConfigManager(this);
        this.dataManager = new DataManager(this, fileManager);

        // 3) État des systèmes (tout éteint au départ).
        this.gameSystems = new GameSystems();
        this.gameSystems.reset();

        // 4) Managers "données" simples.
        this.zoneManager = new ZoneManager(this, dataManager);
        this.playerManager = new PlayerManager();
        this.teamManager = new TeamManager(this,
                configManager.getDefaultMaxPlayersPerTeam());
        this.phaseManager = new PhaseManager();

        // 5) Items de lobby + structures.
        this.lobbyItemManager = new LobbyItemManager(this);
        this.structureManager = new StructureManager(this);

        // 6) Services (logique métier pure).
        this.pointService = new PointService(this);
        this.combatService = new CombatService(this);
        this.miningService = new MiningService(this);
        this.explorationService = new ExplorationService(this);
        this.spectatorService = new SpectatorService(this);
        this.classService = new ClassService(this);
        this.deathService = new DeathService(this);
        this.coreService = new CoreService(this);
        this.packageService = new PackageService(this);
        this.meteoriteService = new MeteoriteService(this);
        this.protectionService = new ProtectionService(this);

        // 7) Managers qui s'appuient sur les services.
        this.respawnManager = new RespawnManager(this);
        this.baseManager = new BaseManager(this);
        this.dungeonManager = new DungeonManager(this);
        this.classManager = new ClassManager(this);
        this.packageManager = new PackageManager(this);

        // 8) Contrôleur système + orchestrateur global.
        this.systemController = new SystemController(this);
        this.gameManager = new GameManager(this);

        // Le PhaseManager prévient le GameManager à chaque sous-phase.
        this.phaseManager.setSubPhaseStartHook(gameManager::handleSubPhaseStart);

        // 9) Affichage.
        this.scoreboardManager = new ScoreboardManager(this);
        this.tabManager = new TabManager(this);

        // 10) Enregistrement des listeners (détection) et commandes.
        registerListeners();
        registerCommands();

        // 11) Reprise d'état après redémarrage (spec §9).
        restoreZoneState();

        getLogger().info("===[READY]===");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("===[DISABLED]===");
    }

    // -----------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------

    /** Enregistre les 13 listeners du plugin. */
    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new InteractionListener(this), this);
        pm.registerEvents(new TeamListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new MiningListener(this), this);
        pm.registerEvents(new BlockPlaceListener(this), this);
        pm.registerEvents(new ExplorationListener(this), this);
        pm.registerEvents(new CoreListener(this), this);
        pm.registerEvents(new PackageListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
    }

    /** Relie les 3 commandes officielles à leurs executors. */
    private void registerCommands() {
        // Les commandes sont déclarées dans plugin.yml ; on les récupère
        // via l'API standard Bukkit.
        var teams = getCommand("teams");
        if (teams != null) {
            teams.setExecutor(new TeamCommand(this));
        }
        var party = getCommand("party");
        if (party != null) {
            party.setExecutor(new PartyCommand(this));
        }
        var zone = getCommand("zone");
        if (zone != null) {
            zone.setExecutor(new ZoneCommand(this));
        }
        var admin = getCommand("admin");
        if (admin != null) {
            admin.setExecutor(new com.mceteams.xii.command.AdminCommand(this));
        }
    }

    /**
     * Vérifie la zone persistée et restaure l'état WAITING si valide
     * (spec §9) ; invalide la zone si son monde a disparu.
     */
    private void restoreZoneState() {
        var persistedZone = dataManager.loadZone();
        if (persistedZone == null) {
            return; // serveur normal tant que pas de /zone set (spec §3)
        }
        if (persistedZone.getWorld() == null) {
            // Monde disparu : zone invalide => données nettoyées, serveur normal.
            dataManager.clearZone();
            getLogger().warning("[Zone] Monde '" + persistedZone.getWorldName()
                    + "' introuvable : données de zone réinitialisées.");
            return;
        }
        // Zone valide : reprise en WAITING (jamais de partie reprise).
        zoneManager.setLoadedZone(persistedZone);
        gameManager.resumeAfterRestart();
    }

    // -----------------------------------------------------------------
    // Accesseurs utilisés par tous les composants (contexte partagé).
    // -----------------------------------------------------------------
    public FileManager getFileManager() { return fileManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public DataManager getDataManager() { return dataManager; }

    public GameSystems getGameSystems() { return gameSystems; }
    public SystemController getSystemController() { return systemController; }

    public ZoneManager getZoneManager() { return zoneManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public PhaseManager getPhaseManager() { return phaseManager; }
    public StructureManager getStructureManager() { return structureManager; }
    public BaseManager getBaseManager() { return baseManager; }
    public DungeonManager getDungeonManager() { return dungeonManager; }
    public ClassManager getClassManager() { return classManager; }
    public RespawnManager getRespawnManager() { return respawnManager; }
    public PackageManager getPackageManager() { return packageManager; }
    public GameManager getGameManager() { return gameManager; }

    public PointService getPointService() { return pointService; }
    public CombatService getCombatService() { return combatService; }
    public MiningService getMiningService() { return miningService; }
    public ExplorationService getExplorationService() { return explorationService; }
    public SpectatorService getSpectatorService() { return spectatorService; }
    public ClassService getClassService() { return classService; }
    public DeathService getDeathService() { return deathService; }
    public CoreService getCoreService() { return coreService; }
    public PackageService getPackageService() { return packageService; }
    public MeteoriteService getMeteoriteService() { return meteoriteService; }
    public ProtectionService getProtectionService() { return protectionService; }

    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public TabManager getTabManager() { return tabManager; }
    public LobbyItemManager getLobbyItemManager() { return lobbyItemManager; }
}
