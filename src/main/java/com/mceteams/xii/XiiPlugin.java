package com.mceteams.xii;

import com.mceteams.xii.command.PartyCommand;
import com.mceteams.xii.command.TeamCommand;
import com.mceteams.xii.command.ZoneCommand;
import com.mceteams.xii.config.ConfigManager;
import com.mceteams.xii.config.DataManager;
import com.mceteams.xii.config.FileManager;
import com.mceteams.xii.item.LobbyItemManager;
import com.mceteams.xii.listener.AchievementsListener;
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
import com.mceteams.xii.listener.RarePickupListener;
import com.mceteams.xii.listener.TeamListener;
import com.mceteams.xii.listener.WorldListener;
import com.mceteams.xii.manager.BaseManager;
import com.mceteams.xii.manager.ClassManager;
import com.mceteams.xii.manager.DungeonManager;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.manager.LootManager;
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
import com.mceteams.xii.service.LootService;
import com.mceteams.xii.service.MeteoriteService;
import com.mceteams.xii.service.MiningService;
import com.mceteams.xii.service.PackageService;
import com.mceteams.xii.service.PointFeedService;
import com.mceteams.xii.service.PointService;
import com.mceteams.xii.service.ProtectionService;
import com.mceteams.xii.service.SpectatorService;
import com.mceteams.xii.service.UpgradeService;
import com.mceteams.xii.system.GameSystems;
import com.mceteams.xii.system.SystemController;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée du plugin XII-Days.
 *
 * Responsabilité UNIQUE : initialiser et relier les composants dans le
 * bon ordre (spec §38). Aucune logique de gameplay ici.
 *
 * Séquence au démarrage :
 * 1. chargement configuration ;
 * 2. chargement données persistantes (zone) ;
 * 3. toute zone d'une session précédente est PURGÉE : le serveur
 *    repart en mode normal, /zone set est obligatoire pour jouer.
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

    // --- Système de loot -----------------------------------------------
    private LootManager lootManager;
    private LootService lootService;

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
    private UpgradeService upgradeService;
    private PointFeedService pointFeedService;

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
        com.mceteams.xii.util.LocationUtil.init(this);

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
        // Nouvelle mécanique : items d'upgrade + totem de résurrection.
        this.upgradeService = new UpgradeService(this);
        // Barre d'action des points CUMULÉS (fenêtre courte mutualisée).
        this.pointFeedService = new PointFeedService(this);

        // 7bis) SYSTÈME DE LOOT : manager (tables) puis service (génération).
        this.lootManager = new LootManager();
        this.lootService = new LootService(this, lootManager);

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

        // 11) Purge d'une éventuelle zone de session précédente.
        restoreZoneState();

        // 12) Barre d'action des points : tick toutes les 200 ms (fenêtre
        // de mutualisation de 3 s, cf. PointFeedService).
        Bukkit.getScheduler().runTaskTimer(this,
                () -> pointFeedService.tick(), 20L, 4L);

        getLogger().info("===[READY]===");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("===[DISABLED]===");
    }

    // -----------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------

    /** Enregistre les 14 listeners du plugin. */
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
        pm.registerEvents(new RarePickupListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new AchievementsListener(this), this);
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
     * RÈGLE UTILISATEUR (remplace spec §9) : au démarrage, AUCUNE reprise
     * automatique. Une zone persistée d'une session précédente est
     * IGNORÉE et purgée : le serveur repart en mode Minecraft normal,
     * et l'opérateur doit refaire /zone set pour relancer le jeu.
     */
    private void restoreZoneState() {
        var persistedZone = dataManager.loadZone();
        if (persistedZone == null) {
            return; // serveur normal tant que pas de /zone set
        }
        // Zone d'une session précédente => purgée volontairement.
        dataManager.clearZone();
        getLogger().info("[Zone] Zone précédente ignorée (redémarrage). "
                + "Faites /zone set pour reconfigurer la zone de jeu.");
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
    public UpgradeService getUpgradeService() { return upgradeService; }
    public PointFeedService getPointFeedService() { return pointFeedService; }
    public LootManager getLootManager() { return lootManager; }
    public LootService getLootService() { return lootService; }

    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public TabManager getTabManager() { return tabManager; }
    public LobbyItemManager getLobbyItemManager() { return lobbyItemManager; }
}
