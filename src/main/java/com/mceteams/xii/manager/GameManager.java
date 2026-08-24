package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.CombatSubPhase;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.task.CountdownTask;
import com.mceteams.xii.task.MeteoriteTask;
import com.mceteams.xii.task.PackageTask;
import com.mceteams.xii.task.PhaseTask;
import com.mceteams.xii.task.RespawnTask;
import com.mceteams.xii.task.SuddenDeathTask;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * Gère l'état GLOBAL de la partie et orchestre les transitions
 * (spec §2 : le GameManager possède l'état, les autres exécutent).
 *
 * Cycle complet (spec §41) :
 *   NONE -> (zone set) WAITING -> COUNTDOWN -> CLASS_SELECTION
 *        -> PREPARATION (60 min) -> COMBAT (60 min) -> ENDING -> WAITING.
 *
 * Une partie en cours n'est JAMAIS reprise après redémarrage (§9) :
 * au démarrage avec une zone valide, on revient à WAITING.
 */
public class GameManager {

    private final XiiPlugin plugin;

    /** État courant du jeu (NONE = serveur normal). */
    private GameState state = GameState.NONE;

    // --- Tasks actives (une seule instance chacune à la fois) --------
    private CountdownTask activeCountdownTask;
    private PhaseTask phaseTask;
    private RespawnTask respawnTask;
    private PackageTask packageTask;
    private MeteoriteTask meteoriteTask;
    private SuddenDeathTask suddenDeathTask;

    public GameManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState newState) {
        this.state = newState;
        plugin.getSystemController().refresh();
        plugin.getScoreboardManager().updateAll();
        plugin.getTabManager().updateAll();
    }

    /** La partie est-elle en cours (préparation ou combat) ? */
    public boolean isRunning() {
        return state == GameState.PREPARATION || state == GameState.COMBAT;
    }

    // -----------------------------------------------------------------
    // Démarrage du serveur (spec §9)
    // -----------------------------------------------------------------

    /**
     * Reprend le mode WAITING après un redémarrage (zone valide).
     * Les parties ne sont jamais reprises : on repart de WAITING.
     */
    public void resumeAfterRestart() {
        applyGameRules();
        // Les structures sont déjà dans le monde ; on reconstruit
        // uniquement les MODELS (bases, donjons, cœurs, coffres).
        rebuildModels();
        enterWaiting(true);
        plugin.getLogger().info("[Game] Zone détectée : retour en WAITING.");
    }

    // -----------------------------------------------------------------
    // /zone set (spec §10)
    // -----------------------------------------------------------------

    /**
     * Une zone vient d'être définie : génération complète puis WAITING.
     */
    public void setupZone() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        applyGameRules();

        MessageUtil.broadcast("§aGénération de la zone de jeu...");
        // 6/7/8 : zone d'attente, bases, quatre donjons.
        plugin.getStructureManager().placeWaitingLobby(zone);
        plugin.getBaseManager().buildBases(zone);
        plugin.getDungeonManager().buildDungeons(zone);

        // 9/11 : passage en WAITING + restrictions + téléportations.
        enterWaiting(false);
        MessageUtil.broadcast("§aZone prête ! La partie attend son lancement.");
    }

    // -----------------------------------------------------------------
    // /zone delete (spec §11)
    // -----------------------------------------------------------------

    /**
     * Supprime la zone configurée : plus aucune interférence du plugin,
     * le serveur redevient un serveur Minecraft normal.
     */
    public void deleteZone() {
        cancelAllTasks();
        plugin.getPackageManager().removeAllBlocks();
        plugin.getSpectatorService().exitAll();
        plugin.getLobbyItemManager().clearAllOnline();
        plugin.getBaseManager().clearAll();
        plugin.getDungeonManager().clearAll();
        plugin.getStructureManager().clearHistory();
        plugin.getTeamManager().resetTransientState();
        plugin.getRespawnManager().clearAll();
        plugin.getPhaseManager().reset();
        resetAllPlayerData();

        state = GameState.NONE;
        plugin.getSystemController().refresh();
        MessageUtil.broadcast("§7Zone supprimée : serveur Minecraft normal.");
    }

    // -----------------------------------------------------------------
    // WAITING (spec §12)
    // -----------------------------------------------------------------

    /**
     * Passe en WAITING : téléportations vers la zone d'attente,
     * items de lobby, systèmes d'attente.
     *
     * @param silent true pour ne pas annoncer (démarrage du serveur).
     */
    private void enterWaiting(boolean silent) {
        state = GameState.WAITING;
        plugin.getSystemController().refresh();

        Location lobbySpawn = getLobbySpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendToLobby(player, lobbySpawn);
        }
        if (!silent) {
            MessageUtil.broadcast("§bLes joueurs ont rejoint la zone d'attente.");
        }
    }

    /**
     * Envoie un joueur dans la zone d'attente (état propre).
     */
    public void sendToLobby(Player player, Location lobbySpawn) {
        plugin.getSpectatorService().exit(player);
        com.mceteams.xii.util.PlayerUtil.reset(player);
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn);
        }
        // Items de lobby selon l'état (WAITING => sélecteur + item admin si op).
        plugin.getLobbyItemManager().giveLobbyItems(player);
    }

    /**
     * Point d'apparition dans la zone d'attente : centre de la zone +
     * hauteur configurée (+2 pour poser les pieds sur la structure).
     */
    public Location getLobbySpawn() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return null;
        }
        return new Location(
                zone.getWorld(),
                zone.getCenterX(),
                zone.getCenterY() + plugin.getConfigManager().getWaitingLobbyHeight() + 2,
                zone.getCenterZ());
    }

    // -----------------------------------------------------------------
    // Lancement de la partie (spec §13)
    // -----------------------------------------------------------------

    /**
     * /party start : lance le compte à rebours de 5 secondes.
     */
    public void startParty() {
        if (state != GameState.WAITING) {
            return;
        }
        state = GameState.COUNTDOWN;
        plugin.getSystemController().refresh(); // retire sélecteur + item admin
        MessageUtil.broadcast("§eLancement de la partie dans §f"
                + plugin.getConfigManager().getCountdownSeconds() + " secondes§e !");

        int seconds = plugin.getConfigManager().getCountdownSeconds();
        activeCountdownTask = new CountdownTask(plugin, seconds,
                () -> beginClassSelection());
        activeCountdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Annule le compte à rebours (/party stop ou GUI admin, spec §13).
     */
    public void cancelCountdown() {
        if (activeCountdownTask != null) {
            activeCountdownTask.cancelExternally();
            activeCountdownTask = null;
            state = GameState.WAITING;
            plugin.getSystemController().refresh();
            MessageUtil.broadcast("§cLancement annulé. Retour à l'attente.");
        }
    }

    // -----------------------------------------------------------------
    // Sélection des classes (spec §14)
    // -----------------------------------------------------------------

    /** Après le countdown : CLASS_SELECTION pendant 30 secondes. */
    private void beginClassSelection() {
        activeCountdownTask = null;
        state = GameState.CLASS_SELECTION;
        plugin.getSystemController().refresh();
        plugin.getClassManager().openSelectionForAll();

        int seconds = plugin.getConfigManager().getClassSelectionSeconds();
        activeCountdownTask = new CountdownTask(plugin, seconds,
                () -> {
                    plugin.getClassManager().assignRandomMissing();
                    beginPreparation();
                });
        activeCountdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    // -----------------------------------------------------------------
    // Début officiel de la partie (spec §15)
    // -----------------------------------------------------------------

    /**
     * Fin de la sélection : répartition des joueurs puis PREPARATION.
     */
    private void beginPreparation() {
        activeCountdownTask = null;

        // 3 : inventaires nettoyés ; 5/6 : items et systèmes de lobby off.
        for (Player player : Bukkit.getOnlinePlayers()) {
            com.mceteams.xii.util.PlayerUtil.reset(player);
            plugin.getLobbyItemManager().removeAll(player);
        }

        // 4 : téléportation à la base de chaque équipe.
        for (Player player : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getData(player);
            var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());

            if (team != null && team.getSpawn() != null) {
                data.setAlive(true);
                data.setEliminated(false);
                player.teleport(team.getSpawn());
                plugin.getClassService().applyPassives(player, data);
            } else {
                // 2 : sans équipe => spectateur permanent (spec §16).
                data.setEliminated(true);
                plugin.getSpectatorService().enterPermanent(player);
            }
        }

        // 7 : passage officiel en PREPARATION (phase + sous-phase START).
        state = GameState.PREPARATION;
        plugin.getPhaseManager().startPreparation();
        setState(state); // déclenche refresh + scoreboards

        startGameplayTasks();
        MessageUtil.broadcast("§6=== PRÉPARATION === §760 minutes avant le combat !");
    }

    // -----------------------------------------------------------------
    // Horloge de jeu (appelée par PhaseTask chaque seconde)
    // -----------------------------------------------------------------

    /**
     * Avance l'horloge des phases, applique les transitions et met à
     * jour l'affichage.
     */
    public void onSecondTick() {
        if (!isRunning()) {
            return;
        }
        var result = plugin.getPhaseManager()
                .tickSecond(plugin.getConfigManager().getSubPhaseDurationSeconds());

        switch (result) {
            case CONTINUING -> { /* rien à faire ce tick */ }
            case ADVANCED_SUB_PHASE -> plugin.getSystemController().refresh();
            case ENTERED_COMBAT -> onCombatEntered();
            case GAME_OVER -> endGame("Temps écoulé - mort subite terminée");
            case INACTIVE -> { /* hors gameplay */ }
        }

        // Affichage + restriction Mineur (ligne verrouillée) chaque seconde.
        plugin.getScoreboardManager().updateAll();
        plugin.getClassService().sweepMinerLockedRow();
    }

    /** Transition PREPARATION -> COMBAT (les hooks ont déjà annoncé). */
    private void onCombatEntered() {
        plugin.getSystemController().refresh();
        MessageUtil.broadcast("§4=== COMBAT === §7Le PvP fait désormais rage !");
    }

    /**
     * Hook appelé par PhaseManager à CHAQUE début de sous-phase.
     * Déclenche les mécaniques correspondantes (colis, météorites...).
     */
    public void handleSubPhaseStart(Object subPhase) {
        if (subPhase instanceof com.mceteams.xii.enums.PreparationSubPhase prep) {
            switch (prep) {
                case START -> MessageUtil.broadcast(
                        "§7Préparation : §fcollectez des ressources§7 !");
                case PACKAGES -> {
                    startPackageTask();
                    MessageUtil.broadcast("§eDes colis commencent à apparaître§7 !");
                }
                case DUNGEONS -> plugin.getDungeonManager().unlockLoot();
                case POINT_UPGRADES -> MessageUtil.broadcast(
                        "§bPOINTS x2 §7pendant toute la sous-phase !");
                case PACKAGE_UPGRADE -> MessageUtil.broadcast(
                        "§eDavantage de colis apparaissent§7 !");
                case DUNGEON_RESTOCK -> plugin.getDungeonManager().restockAll();
            }
            return;
        }
        if (subPhase instanceof com.mceteams.xii.enums.CombatSubPhase combat) {
            switch (combat) {
                case START -> MessageUtil.broadcast(
                        "§cLe PvP est désormais §4global§c !");
                case METEORITES -> {
                    startMeteoriteTask();
                    MessageUtil.broadcast("§6Des météorites s'écrasent sur la map§7 !");
                }
                case MORE_DAMAGE -> MessageUtil.broadcast(
                        "§4DÉGÂTS x2 §7pendant toute la sous-phase !");
                case ALL_CORE_DESTRUCTION -> plugin.getCoreService().destroyAllCores();
                case MORE_METEORITES -> MessageUtil.broadcast(
                        "§6Météorites x2 §7et points terrain doublés !");
                case SUDDEN_DEATH -> {
                    startSuddenDeathTask();
                    MessageUtil.broadcast("§4MORT SUBITE §7- les dragons arrivent !");
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Victoire / fin de partie (spec §27)
    // -----------------------------------------------------------------

    /**
     * Vérifie les conditions de victoire anticipée (appelé après chaque
     * mort/élimination) : il ne reste qu'une équipe vivante.
     */
    public void checkVictoryConditions() {
        if (!isRunning()) {
            return;
        }
        long aliveTeams = plugin.getTeamManager().all().stream()
                .filter(team -> !team.isEliminated())
                .filter(team -> plugin.getTeamManager().aliveCount(team) > 0)
                .count();
        if (aliveTeams <= 1) {
            endGame("Dernière équipe en vie");
        }
    }

    /**
     * Termine la partie : classement par joueurs vivants PUIS points
     * (spec §27), état ENDING, retour automatique en WAITING.
     */
    public void endGame(String reason) {
        if (!isRunning()) {
            return;
        }
        cancelGameplayTasks();

        List<GameTeam> ranking = plugin.getTeamManager().all().stream()
                .sorted(Comparator
                        .comparingInt((GameTeam t) ->
                                plugin.getTeamManager().aliveCount(t)).reversed()
                        .thenComparingInt(t -> t.getScore().getTotal()).reversed())
                .toList();

        MessageUtil.broadcast("§8========================================");
        MessageUtil.broadcast("§bFIN DE PARTIE §7(" + reason + ")");
        int place = 1;
        for (GameTeam team : ranking) {
            String line = "§7#" + place + " " + team.getColor().getColoredName()
                    + " §7- §f" + plugin.getTeamManager().aliveCount(team)
                    + " vivant(s) §7- §e" + team.getScore().getTotal() + " pts";
            MessageUtil.broadcast(place == 1 ? "§6★ " + line : line);
            place++;
        }
        if (!ranking.isEmpty()) {
            MessageUtil.broadcast("§6Vainqueur : "
                    + ranking.get(0).getColor().getColoredName());
        }
        MessageUtil.broadcast("§8========================================");

        state = GameState.ENDING;
        plugin.getSystemController().refresh();

        // Retour automatique en WAITING après la durée d'affichage.
        int endingTicks = plugin.getConfigManager().getEndingSeconds() * 20;
        Bukkit.getScheduler().runTaskLater(plugin, this::returnToWaiting, endingTicks);
    }

    // -----------------------------------------------------------------
    // /party stop (spec §35)
    // -----------------------------------------------------------------

    /**
     * Arrête tout et revient proprement à WAITING.
     */
    public void stopParty() {
        cancelAllTasks();

        // Nettoyage du monde : colis retirés, structures reposées.
        plugin.getPackageManager().removeAllBlocks();
        plugin.getStructureManager().regenerateAll();
        rebuildModels();

        // Réinitialisation des états temporaires.
        plugin.getTeamManager().resetTransientState();
        plugin.getRespawnManager().clearAll();
        plugin.getClassManager().resetAll();
        plugin.getDungeonManager().resetAccess();
        plugin.getCombatService().resetMatchState();
        plugin.getMiningService().resetMatchState();
        plugin.getExplorationService().resetMatchState();
        plugin.getCoreService().resetAll();
        plugin.getPhaseManager().reset();
        plugin.getSpectatorService().exitAll();
        resetAllPlayerData();

        enterWaiting(false);
        MessageUtil.broadcast("§cPartie arrêtée. Retour à l'attente.");
    }

    /**
     * Retour automatique en WAITING après ENDING.
     */
    public void returnToWaiting() {
        if (state != GameState.ENDING) {
            return;
        }
        // Même nettoyage qu'un /party stop mais sans double annonce :
        // on réutilise la logique complète pour garantir un état neuf.
        cancelAllTasks();
        plugin.getPackageManager().removeAllBlocks();
        plugin.getStructureManager().regenerateAll();
        rebuildModels();
        plugin.getTeamManager().resetTransientState();
        plugin.getRespawnManager().clearAll();
        plugin.getClassManager().resetAll();
        plugin.getDungeonManager().resetAccess();
        plugin.getCombatService().resetMatchState();
        plugin.getMiningService().resetMatchState();
        plugin.getExplorationService().resetMatchState();
        plugin.getCoreService().resetAll();
        plugin.getPhaseManager().reset();
        plugin.getSpectatorService().exitAll();
        resetAllPlayerData();
        enterWaiting(false);
    }

    /**
     * Saut direct à un jour (1..12) via /party set <jour>.
     *
     * @return message d'erreur, ou null si succès.
     */
    public String skipToDay(int day) {
        if (!isRunning()) {
            return "Aucune partie en cours.";
        }
        boolean ok = plugin.getPhaseManager().skipToDay(day);
        if (!ok) {
            return "Jour invalide (1 à 12).";
        }
        // Le hook a déjà déclenché les mécaniques du jour : on synchronise
        // les tasks (ex : sauter directement aux météorites).
        restartPeriodicTasksForCurrentSubPhase();
        plugin.getSystemController().refresh();
        return null;
    }

    // -----------------------------------------------------------------
    // Tasks internes
    // -----------------------------------------------------------------

    private void startGameplayTasks() {
        phaseTask = new PhaseTask(plugin);
        phaseTask.runTaskTimer(plugin, 20L, 20L);

        respawnTask = new RespawnTask(plugin);
        respawnTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startPackageTask() {
        if (packageTask != null) {
            return; // déjà actif
        }
        packageTask = new PackageTask(plugin);
        packageTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startMeteoriteTask() {
        if (meteoriteTask != null) {
            return;
        }
        meteoriteTask = new MeteoriteTask(plugin);
        meteoriteTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startSuddenDeathTask() {
        if (suddenDeathTask != null) {
            return;
        }
        suddenDeathTask = new SuddenDeathTask(plugin);
        suddenDeathTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Après un saut de jour : relance les tasks périodiques qui doivent
     * être actives pour la sous-phase COURANTE.
     */
    private void restartPeriodicTasksForCurrentSubPhase() {
        var phaseManager = plugin.getPhaseManager();
        if (phaseManager.getPhase() == com.mceteams.xii.enums.GamePhase.PREPARATION) {
            var sub = phaseManager.getPreparationSubPhase();
            if (sub != null && sub.ordinal() >= com.mceteams.xii.enums.PreparationSubPhase.PACKAGES.ordinal()) {
                startPackageTask();
            }
        } else if (phaseManager.getPhase() == com.mceteams.xii.enums.GamePhase.COMBAT) {
            var sub = phaseManager.getCombatSubPhase();
            if (sub != null && sub.ordinal() >= CombatSubPhase.METEORITES.ordinal()
                    && sub != CombatSubPhase.SUDDEN_DEATH) {
                startMeteoriteTask();
            }
            if (sub == CombatSubPhase.SUDDEN_DEATH) {
                startSuddenDeathTask();
            }
        }
    }

    /** Arrête toutes les tasks de gameplay + countdown. */
    public void cancelAllTasks() {
        cancelGameplayTasks();
        if (activeCountdownTask != null) {
            try {
                activeCountdownTask.cancelExternally();
            } catch (IllegalStateException ignored) {
                // Task déjà annulée.
            }
            activeCountdownTask = null;
        }
    }

    /** Arrête uniquement les tasks liées au gameplay en cours. */
    public void cancelGameplayTasks() {
        safeCancel(phaseTask);          phaseTask = null;
        safeCancel(respawnTask);        respawnTask = null;
        safeCancel(packageTask);        packageTask = null;
        safeCancel(meteoriteTask);      meteoriteTask = null;
        safeCancel(suddenDeathTask);    suddenDeathTask = null;
    }

    private void safeCancel(org.bukkit.scheduler.BukkitRunnable task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
                // Pas encore schedulée ou déjà annulée.
            }
        }
    }

    /** Arrêt propre du plugin (onDisable) : on coupe tout, on sauvegarde. */
    public void shutdown() {
        cancelAllTasks();
        plugin.getDataManager().saveZone(plugin.getZoneManager().getZone());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Applique les gamerules "compétition" à l'activation de la zone. */
    private void applyGameRules() {
        World world = plugin.getZoneManager().getZone().getWorld();
        if (world == null) {
            return;
        }
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);   // pas d'incendie infini
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false); // mobs contrôlés
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);   // pas de drop à la mort
        world.setTime(1000L); // jour permanent
        world.setStorm(false);
    }

    /** Reconstruit les MODELS (bases/donjons/cœurs/coffres). */
    private void rebuildModels() {
        var zone = plugin.getZoneManager().getZone();
        if (zone == null || zone.getWorld() == null) {
            return;
        }
        plugin.getBaseManager().buildBases(zone);
        plugin.getDungeonManager().buildDungeons(zone);
    }

    /**
     * Remet toutes les données joueurs à neuf (nouvelle partie / arrêt).
     */
    private void resetAllPlayerData() {
        for (var data : plugin.getPlayerManager().all()) {
            data.setAlive(true);
            data.setEliminated(false);
            data.setSpectator(false);
            data.setDisconnected(false);
            data.setDeathCause(null);
            data.getScore().reset();
            data.clearLastDamage();
        }
    }
}
