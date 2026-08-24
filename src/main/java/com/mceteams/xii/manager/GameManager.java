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

        MessageUtil.broadcast("§a✔ Génération de la zone de jeu...");
        // 6/7/8 : zone d'attente, bases, quatre donjons.
        plugin.getStructureManager().placeWaitingLobby(zone);
        plugin.getBaseManager().buildBases(zone);
        plugin.getDungeonManager().buildDungeons(zone);

        // 9/11 : passage en WAITING + restrictions + téléportations.
        enterWaiting(false);
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        MessageUtil.broadcast(" §a§lZONE PRÊTE !");
        MessageUtil.broadcast(" §7En attente du lancement... §8(/party start)");
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
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
        MessageUtil.broadcast("§c✘ Zone supprimée. §7Serveur Minecraft normal.");
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
        // FIX : rafraîchit scoreboards + tab => efface la sidebar de fin
        // de partie qui restait sinon affichée indéfiniment.
        plugin.getScoreboardManager().updateAll();
        plugin.getTabManager().updateAll();

        Location lobbySpawn = getLobbySpawn();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendToLobby(player, lobbySpawn);
        }
        if (!silent) {
            MessageUtil.broadcast("§7Les joueurs ont rejoint la §bzone d'attente§7.");
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
     *
     * GARDE-FOUS (obligatoires avant tout lancement) :
     * - une zone doit être définie (/zone set) ;
     * - au moins une équipe doit exister (/teams create).
     *
     * @return un message d'erreur FR si le lancement est refusé,
     *         null si le countdown démarre.
     */
    public String startParty() {
        // 1. La zone doit être configurée : sans zone, pas de partie.
        if (!plugin.getZoneManager().hasZone()) {
            return "Aucune zone définie. Utilisez §f/zone set§c d'abord.";
        }
        // 2. L'état doit être WAITING.
        if (state != GameState.WAITING) {
            return "La partie ne peut être lancée que depuis l'attente.";
        }
        // 3. Il faut au moins une équipe.
        if (plugin.getTeamManager().isEmpty()) {
            return "Aucune équipe créée. Utilisez §f/teams create <couleur>§c d'abord.";
        }

        state = GameState.COUNTDOWN;
        plugin.getSystemController().refresh(); // retire sélecteur + item admin
        MessageUtil.broadcast(" ");
        MessageUtil.broadcast("§e§lLa partie démarre dans §f§l"
                + plugin.getConfigManager().getCountdownSeconds() + " secondes§e§l !");

        int seconds = plugin.getConfigManager().getCountdownSeconds();
        // Countdown de lancement : TITLES + pling grave chaque seconde,
        // puis GROWL DE DRAGON quand la partie DÉMARRE réellement
        // (téléportation aux bases dès la fin du countdown).
        activeCountdownTask = new CountdownTask(
                plugin, seconds,
                this::beginPreparation,
                false,                          // mode titles
                null,                           // (pas d'action bar)
                org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f,   // pling grave
                org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f // départ !
        );
        activeCountdownTask.runTaskTimer(plugin, 0L, 20L);
        return null;
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
            MessageUtil.broadcast("§c✖ Lancement annulé. Retour à l'attente.");
        }
    }

    // -----------------------------------------------------------------
    // Sélection des classes (spec §14)
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // Début officiel de la partie (spec §15, ajusté : le jeu démarre
    // DÈS LA FIN DU COUNTDOWN ; la sélection de classe se déroule PENDANT
    // la préparation, sans bloquer le gameplay).
    // -----------------------------------------------------------------

    /**
     * Fin du countdown de 5 secondes : téléportation aux bases, systèmes
     * de jeu actifs, GUI de classe ouverte avec un compte à rebours de
     * 30 secondes en barre d'action. À zéro : fermeture des GUI et
     * classe aléatoire pour ceux qui n'ont pas choisi (spec §14).
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

            if (team == null) {
                // 2 : SANS équipe => spectateur permanent (spec §16).
                data.setEliminated(true);
                plugin.getSpectatorService().enterPermanent(player);
                continue;
            }

            // Spawn de l'équipe, avec repli sur la base si besoin.
            Location spawnPoint = team.getSpawn();
            if (spawnPoint == null) {
                var base = plugin.getBaseManager().getBase(team.getColor());
                spawnPoint = base != null ? base.getSpawn() : null;
            }
            if (spawnPoint == null) {
                // Dernier repli : centre de la zone (jamais en spectateur
                // pour un joueur équipé !).
                spawnPoint = getLobbySpawn();
            }

            data.setAlive(true);
            data.setEliminated(false);
            data.setSpectator(false);
            player.teleport(spawnPoint);
            plugin.getClassService().applyPassives(player, data);
        }

        // 7 : passage officiel en PREPARATION (phase + sous-phase START).
        state = GameState.PREPARATION;
        plugin.getPhaseManager().startPreparation();
        setState(state); // déclenche refresh + scoreboards

        startGameplayTasks();

        // Sélection de classe PENDANT le début de partie : ouverture des
        // GUI + compte à rebours 30 s en ACTION BAR, sans aucun son.
        plugin.getClassManager().openSelectionForAll();
        int classSeconds = plugin.getConfigManager().getClassSelectionSeconds();
        activeCountdownTask = new CountdownTask(
                plugin, classSeconds,
                () -> plugin.getClassManager().finalizeSelection(),
                true,                                   // mode action bar
                "§7Choisissez votre classe §8(§e%s s§8)",
                null, 0f,                               // pas de son de tick
                null, 0f);                              // pas de son de fin
        activeCountdownTask.runTaskTimer(plugin, 0L, 20L);

        MessageUtil.broadcast("§6§lPHASE DE PRÉPARATION");
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        MessageUtil.broadcast(" §7Collectez des ressources et équipez-vous.");
        MessageUtil.broadcast(" §7Le combat commence dans §f30 minutes§7.");
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
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

        // Affichage + restriction Mineur + faim verrouillée chaque seconde.
        plugin.getScoreboardManager().updateAll();
        plugin.getClassService().sweepMinerLockedRow();
        for (Player online : Bukkit.getOnlinePlayers()) {
            com.mceteams.xii.util.PlayerUtil.lockHunger(online);
        }
    }

    /** Transition PREPARATION -> COMBAT (les hooks ont déjà annoncé). */
    private void onCombatEntered() {
        // FIX MAJEUR : synchronise AUSSI GameState sur COMBAT. Sans ça,
        // l'état restait PREPARATION => règles PvP de préparation actives
        // (impossible d'attaquer, flags météorites/coeurs éteints...).
        state = GameState.COMBAT;
        setState(state);
        plugin.getRespawnManager().processSubPhaseStart();
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        MessageUtil.broadcast(" §4§lPHASE DE COMBAT");
        MessageUtil.broadcast(" §cLe PvP est désormais autorisé §4partout§c. Bonne chance.");
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
    }

    /**
     * Hook appelé par PhaseManager à CHAQUE début de sous-phase.
     * Déclenche les mécaniques correspondantes (colis, météorites...).
     */
    public void handleSubPhaseStart(Object subPhase) {
        if (subPhase instanceof com.mceteams.xii.enums.PreparationSubPhase prep) {
            switch (prep) {
                case START -> MessageUtil.broadcast(
                        "§7Bonne collecte à tous !");
                case PACKAGES -> {
                    startPackageTask();
                    MessageUtil.broadcast("§e✦ §fLes premiers colis tombent du ciel§7 !");
                }
                case DUNGEONS -> plugin.getDungeonManager().unlockLoot();
                case POINT_UPGRADES -> MessageUtil.broadcast(
                        "§b✦ §fPOINTS x2 §7jusqu'à la fin de la sous-phase !");
                case PACKAGE_UPGRADE -> MessageUtil.broadcast(
                        "§e✦ Davantage de colis §7apparaissent désormais !");
                case DUNGEON_RESTOCK -> plugin.getDungeonManager().restockAll();
            }
            return;
        }
        if (subPhase instanceof com.mceteams.xii.enums.CombatSubPhase combat) {
            // COMBAT (jour 7+) : tous les morts en attente reviennent au
            // début de CHAQUE sous-phase (sauf équipe sans coeur).
            plugin.getRespawnManager().processSubPhaseStart();

            switch (combat) {
                // Le PvP global est déjà annoncé par le bandeau de phase.
                case START -> { }
                case METEORITES -> {
                    startMeteoriteTask();
                    MessageUtil.broadcast("§6☄ §fDes météorites s'écrasent sur la map§7 !");
                }
                case MORE_DAMAGE -> MessageUtil.broadcast(
                        "§4⚔ §fDÉGÂTS x2 §7pendant toute la sous-phase !");
                case ALL_CORE_DESTRUCTION ->
                        plugin.getCoreService().destroyAllCores();
                case MORE_METEORITES -> {
                    // Jour 11 : TOUS les coeurs restants sont détruits
                    // automatiquement (idempotent si déjà fait au jour 10).
                    plugin.getCoreService().destroyAllCores();
                    MessageUtil.broadcast(
                            "§6☄ Météorites x2 §7- points terrain doublés !");
                }
                case SUDDEN_DEATH -> {
                    startSuddenDeathTask();
                    MessageUtil.broadcast(" ");
                    MessageUtil.broadcast(" §4§lMORT SUBITE");
                    MessageUtil.broadcast(" §7Des dragons §cdévastent la map§7...");
                    MessageUtil.broadcast(" ");
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Victoire / fin de partie (spec §27)
    // -----------------------------------------------------------------

    /**
     * Vérifie les conditions de victoire anticipée.
     *
     * RÈGLE OFFICIELLE (ajustement gameplay) : la victoire n'est
     * prononcée QUE lorsqu'il reste EXACTEMENT UNE équipe debout
     * (non éliminée), peu importe l'état de son coeur.
     *
     * Une équipe n'est éliminée que si son coeur est détruit ET qu'elle
     * n'a plus aucun joueur vivant/ressuscitable. Des membres morts
     * avec un COEUR VIVANT ne comptent pas comme élimination (ils
     * réapparaissent) => plus aucune fin de partie prématurée.
     *
     * Garde-fou : au moins 2 équipes doivent avoir été créées, sinon
     * une partie solo se terminerait instantanément.
     */
    public void checkVictoryConditions() {
        if (!isRunning()) {
            return;
        }
        long totalTeams = plugin.getTeamManager().all().size();
        long standingTeams = plugin.getTeamManager().all().stream()
                .filter(team -> !team.isEliminated())
                .count();

        if (totalTeams >= 2 && standingTeams == 1) {
            endGame("Dernière équipe debout");
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

        // Classement (spec §27) : 1) joueurs vivants décroissants,
        // 2) points décroissants.
        // ATTENTION piège Java : un SEUL .reversed() à la FIN de la
        // chaîne. Deux .reversed() successifs inverseraient aussi le
        // premier critère => "l'équipe éliminée vainqueur" !
        List<GameTeam> ranking = plugin.getTeamManager().all().stream()
                .sorted(Comparator
                        .comparingInt((GameTeam t) ->
                                plugin.getTeamManager().aliveCount(t))
                        .thenComparingInt(t -> t.getScore().getTotal())
                        .reversed())
                .toList();

        MessageUtil.broadcast(" ");
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        MessageUtil.broadcast("  §6§l✶ FIN DE PARTIE §7" + reason);
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        int place = 1;
        for (GameTeam team : ranking) {
            String line = "§7#" + place + " " + team.getColor().getColoredName()
                    + " §8- §f" + plugin.getTeamManager().aliveCount(team)
                    + " vivant(s) §8- §e" + team.getScore().getTotal() + " pts";
            MessageUtil.broadcast(place == 1
                    ? " §6★ " + line
                    : "   " + line);
            place++;
        }
        if (!ranking.isEmpty()) {
            MessageUtil.broadcast(" ");
            MessageUtil.broadcast("  §6§l★ VAINQUEUR : "
                    + ranking.get(0).getColor().getColoredName() + " §6§l★");
        }
        MessageUtil.broadcast(MessageUtil.SEPARATOR);
        MessageUtil.broadcast(" ");

        // Titres de fin : VICTOIRE ! pour l'équipe gagnante,
        // FIN DE PARTIE ! pour tous les autres.
        // On efface d'abord tout titre résiduel (mort, respawn...).
        GameTeam winnerTeam = ranking.isEmpty() ? null : ranking.get(0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.resetTitle();
            var team = plugin.getTeamManager().getTeamOf(online.getUniqueId());
            boolean winner = winnerTeam != null && team == winnerTeam;
            MessageUtil.sendTitle(online,
                    winner ? "§6§lVICTOIRE !" : "§c§lFIN DE PARTIE !",
                    "",
                    10, 100, 20);
        }

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
        MessageUtil.broadcast("§c✖ Partie arrêtée. §7Retour à l'attente.");
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

        // FIX : synchronise l'état global AVANT le saut - le hook du jour
        // cible se déclenche PENDANT phaseManager.skipToDay() et doit
        // trouver le bon GameState (sinon mécaniques à moitié appliquées).
        GameState previousState = this.state;
        this.state = day <= 6 ? GameState.PREPARATION : GameState.COMBAT;

        boolean ok = plugin.getPhaseManager().skipToDay(day);
        if (!ok) {
            this.state = previousState; // jour invalide : aucun changement
            return "Jour invalide (1 à 12).";
        }

        // --- RETOUR EN ARRIÈRE : désactivation des événements postérieurs
        if (day < 7) {
            // Loot des donjons refermé.
            plugin.getDungeonManager().resetAccess();
            // Coeurs restaurés : on repart sur une préparation propre.
            for (var team : plugin.getTeamManager().all()) {
                if (!team.isHeartAlive()) {
                    plugin.getCoreService().restoreCore(team.getColor());
                }
            }
            // ET les JOUEURS sont réanimés (fini les spectateurs oubliés) :
            reviveEveryone();
        } else {
            // En combat, le loot des donjons est forcément ouvert.
            if (!plugin.getDungeonManager().isLootAccessible()) {
                plugin.getDungeonManager().unlockLoot();
            }
        }
        // Dragons restants si l'on quitte la mort subite.
        if (day < 12) {
            for (var world : Bukkit.getWorlds()) {
                world.getEntitiesByClass(org.bukkit.entity.EnderDragon.class)
                        .forEach(org.bukkit.entity.Entity::remove);
            }
        }
        // Météorites déjà en vol si l'on retourne avant leur phase.
        if (day < 8) {
            for (var world : Bukkit.getWorlds()) {
                world.getEntitiesByClass(org.bukkit.entity.LargeFireball.class)
                        .stream()
                        .filter(fireball -> fireball.getScoreboardTags().contains(
                                com.mceteams.xii.service.MeteoriteService.METEORITE_TAG))
                        .forEach(org.bukkit.entity.Entity::remove);
            }
        }

        // --- AVANCE RAPIDE : cumul des mécaniques déjà passées ----------
        boolean prep = this.state == GameState.PREPARATION;
        if (prep && day >= 2) {
            startPackageTask();      // colis disponibles
        }
        if (day >= 3 && !plugin.getDungeonManager().isLootAccessible()) {
            plugin.getDungeonManager().unlockLoot();
        }
        if (day == 6) {
            plugin.getDungeonManager().restockAll();
        }
        if (day >= 8) {
            startMeteoriteTask();    // météorites actives
        }
        if (day >= 10) {
            plugin.getCoreService().destroyAllCores(); // coeurs détruits
        }
        if (day == 12) {
            startSuddenDeathTask();
        }

        // Libère les joueurs morts en attente (nouvelle sous-phase).
        plugin.getRespawnManager().processSubPhaseStart();

        restartPeriodicTasksForCurrentSubPhase();
        setState(this.state); // refresh systèmes + scoreboards + tab
        return null;
    }

    /**
     * Réanime TOUS les membres d'équipes (utilisé au retour en
     * préparation après un /party set antérieur) :
     * sortie du spectateur, vie pleine, retour à la base.
     * Les joueurs SANS équipe restent spectateurs permanents.
     */
    private void reviveEveryone() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getData(online);
            var team = plugin.getTeamManager().getTeamOf(online.getUniqueId());
            if (team == null) {
                continue; // sans équipe : spectateur permanent maintenu
            }
            data.setAlive(true);
            data.setEliminated(false);
            data.setDeathCause(null);
            data.clearLastDamage();
            if (data.isSpectator()) {
                plugin.getSpectatorService().exit(online);
            }
            com.mceteams.xii.util.PlayerUtil.heal(online);

            Location spawnPoint = team.getSpawn();
            if (spawnPoint == null) {
                var base = plugin.getBaseManager().getBase(team.getColor());
                spawnPoint = base != null ? base.getSpawn() : null;
            }
            if (spawnPoint == null) {
                spawnPoint = getLobbySpawn();
            }
            if (spawnPoint != null) {
                online.teleport(spawnPoint);
            }
            plugin.getClassService().applyPassives(online, data);
        }

        // Les données des joueurs HORS LIGNE sont aussi remises à neuf.
        for (var data : plugin.getPlayerManager().all()) {
            if (plugin.getTeamManager().getTeamOf(data.getUuid()) != null) {
                data.setAlive(true);
                data.setEliminated(false);
                data.setDeathCause(null);
                data.clearLastDamage();
            }
        }
        // Files d'attente de respawn purgées (les timers n'ont plus de sens).
        plugin.getRespawnManager().clearPending();
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

    /** Applique les gamerules "compétition" à l'activation de la zone.
     * NB : on utilise org.bukkit.GameRules (API moderne Paper 26.2),
     * les anciennes constantes GameRule.* étant dépréciées. */
    private void applyGameRules() {
        World world = plugin.getZoneManager().getZone().getWorld();
        if (world == null) {
            return;
        }
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);      // temps figé
        world.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);   // météo figée
        // Pas de propagation de feu (les explosions météorites ne brûlent pas la map).
        world.setGameRule(org.bukkit.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);         // mobs contrôlés
        world.setGameRule(org.bukkit.GameRules.KEEP_INVENTORY, true);      // pas de drop à la mort
        world.setGameRule(org.bukkit.GameRules.IMMEDIATE_RESPAWN, true);   // écran de mort instantané
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
