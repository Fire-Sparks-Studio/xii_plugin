package com.mceteams.xii.enums;

public enum Messages {

    // ===== General =====
    NO_PERMISSION("§cVous n'avez pas la permission d'effectuer cette action.", "§cYou don't have permission to perform this action."),
    NOT_PLAYER("§cCette commande est réservée aux joueurs.", "§cThis command can only be used by players."),
    UNKNOWN_COMMAND("§cCommande inconnue : §f{0}", "§cUnknown command: §f{0}"),
    USAGE("§cUsage : §7{0}", "§cUsage: §7{0}"),
    INVALID_NUMBER("§cNombre invalide.", "§cInvalid number."),

    // ===== Join / Leave =====
    JOIN_TEAM_CLOSED("§cLes équipes sont actuellement fermées.", "§cTeams are currently closed."),
    JOIN_GAME_STARTED("§cLa partie a déjà commencé.", "§cThe game has already started."),
    JOIN_ALREADY_IN_TEAM("§cVous êtes déjà dans une équipe.", "§cYou're already in a team."),
    JOIN_UNKNOWN_COLOR("§cCouleur inconnue : §f{0}", "§cUnknown color: §f{0}"),
    JOIN_TEAM_NOT_EXIST("§cCette équipe n'existe pas.", "§cThis team doesn't exist."),
    JOIN_SUCCESS("§aVous avez rejoint l'équipe §f{0}§a.", "§aYou joined team §f{0}§a."),
    JOIN_TEAM_FULL("§cCette équipe est complète.", "§cThis team is full."),
    LEAVE_DISABLED("§cLe départ des équipes est actuellement désactivé.", "§cLeaving teams is currently disabled."),
    LEAVE_GAME_STARTED("§cLa partie a déjà commencé.", "§cThe game has already started."),
    LEAVE_NOT_IN_TEAM("§cVous n'êtes dans aucune équipe.", "§cYou're not in any team."),
    LEAVE_SUCCESS("§aVous avez quitté votre équipe.", "§aYou left your team."),

    // ===== Teams =====
    TEAMS_USAGE("§cUsage : §7/xii teams <create|delete|add|remove|heart|eliminate|revive|tpbase|options>", "§cUsage: §7/xii teams <create|delete|add|remove|heart|eliminate|revive|tpbase|options>"),
    TEAMS_UNKNOWN_SUB("§cSous-commande inconnue : §f{0}", "§cUnknown subcommand: §f{0}"),
    TEAM_CREATE_USAGE("§cUsage : §7/xii teams create <couleur>", "§cUsage: §7/xii teams create <color>"),
    TEAM_ALREADY_EXISTS("§cCette équipe existe déjà.", "§cThis team already exists."),
    TEAM_CREATED("§aÉquipe §f{0} §acréée.", "§aTeam §f{0} §acreated."),
    TEAM_DELETE_USAGE("§cUsage : §7/xii teams delete <couleur>", "§cUsage: §7/xii teams delete <color>"),
    TEAM_DELETED("§aÉquipe §f{0} §asupprimée.", "§aTeam §f{0} §adeleted."),
    TEAM_ADD_USAGE("§cUsage : §7/xii teams add <joueur> <couleur>", "§cUsage: §7/xii teams add <player> <color>"),
    PLAYER_NOT_FOUND("§cJoueur introuvable : §f{0}", "§cPlayer not found: §f{0}"),
    TEAM_PLAYER_ADDED("§f{0} §aa rejoint l'équipe §f{1}§a.", "§f{0} §ahas joined team §f{1}§a."),
    TEAM_REMOVE_USAGE("§cUsage : §7/xii teams remove <joueur>", "§cUsage: §7/xii teams remove <player>"),
    TEAM_PLAYER_REMOVED("§f{0} §aa été retiré de son équipe.", "§f{0} §ahas been removed from their team."),
    TEAM_OPTIONS_UNKNOWN("§cOption inconnue : §f{0}", "§cUnknown option: §f{0}"),

    // ===== Heart =====
    HEART_USAGE("§cUsage : §7/xii teams heart <couleur> <destroy|restore>", "§cUsage: §7/xii teams heart <color> <destroy|restore>"),
    HEART_DESTROYED("§lCOEUR DÉTRUIT ! > {0}§7 a été détruit.", "§lHEART DESTROYED! > {0}§7 was destroyed."),
    HEART_DESTROYED_BY("§lCOEUR DÉTRUIT ! > {0}§7 a été détruit par {1}", "§lHEART DESTROYED! > {0}§7 was destroyed by {1}"),
    HEART_RESTORED("§lCOEUR RESTAURÉ ! > {0}§7 a été restauré.", "§lHEART RESTORED! > {0}§7 was restored."),
    HEART_BROADCAST("§lCOEUR DÉTRUIT ! > {0}§7 a été détruit par {1}", "§lHEART DESTROYED! > {0}§7 was destroyed by {1}"),
    HEART_TITLE("§c§lCOEUR DÉTRUIT !", "§c§lHEART DESTROYED!"),
    HEART_SUBTITLE("§7Vous ne réapparaîtrez plus.", "§7You will no longer respawn."),

    // ===== Eliminate / Revive =====
    ELIMINATE_USAGE("§cUsage : §7/xii teams eliminate <couleur>", "§cUsage: §7/xii teams eliminate <color>"),
    ELIMINATED_VICTIM("§c§lVOUS AVEZ ÉTÉ ÉLIMINÉ", "§c§lYOU HAVE BEEN ELIMINATED"),
    ELIMINATED_TEAM("§lÉQUIPE ÉLIMINÉE > {0}§c a été éliminée !", "§lTEAM ELIMINATED > {0}§c has been eliminated!"),
    REVIVE_USAGE("§cUsage : §7/xii teams revive <couleur>", "§cUsage: §7/xii teams revive <color>"),
    TEAM_REVIVED("§lÉQUIPE RÉANIMÉE > {0}§a a été réanimée !", "§lTEAM REVIVED > {0}§a has been revived!"),

    // ===== TP Base =====
    TPBASE_USAGE("§cUsage : §7/xii teams tpbase <couleur|@a|joueur>", "§cUsage: §7/xii teams tpbase <color|@a|player>"),
    TPBASE_ALL_DONE("§aTous les joueurs ont été téléportés à leur base.", "§aAll players have been teleported to their base."),
    TPBASE_PLAYER_NO_TEAM("§c§f{0} §cn'est dans aucune équipe.", "§c§f{0} §cis not in any team."),
    TPBASE_SPAWN_NOT_SET("§cLe point de réapparition de cette équipe n'est pas défini.", "§cThis team's spawn point has not been set."),
    TPBASE_PLAYER_DONE("§f{0} §aa été téléporté à sa base.", "§f{0} §ahas been teleported to their base."),
    TPBASE_TEAM_DONE("§aLes joueurs de l'équipe §f{0} §aont été téléportés.", "§aTeam §f{0}§a's players have been teleported."),

    // ===== Options =====
    OPTIONS_USAGE("§cUsage : §7/xii teams options <allow|<couleur>>", "§cUsage: §7/xii teams options <allow|<color>>"),
    OPTIONS_ALLOW_USAGE("§cUsage : §7/xii teams options allow <join|leave> <true|false>", "§cUsage: §7/xii teams options allow <join|leave> <true|false>"),
    OPTIONS_ALLOW_TOGGLED("§aAutorisation de rejoindre une équipe mise à jour.", "§aTeam joining permission updated."),
    OPTIONS_LEAVE_TOGGLED("§aAutorisation de quitter une équipe mise à jour.", "§aTeam leaving permission updated."),
    OPTIONS_ALLOW_UNKNOWN("§cUsage : §7/xii teams options allow ...", "§cUsage: §7/xii teams options allow ..."),
    OPTIONS_MAX_MEMBERS_USAGE("§cUsage : §7/xii teams options {0} maxmembers <nombre>", "§cUsage: §7/xii teams options {0} maxmembers <number>"),
    OPTIONS_LIMIT_SET("§aLimite de l'équipe §f{0} §adéfinie à §f{1}§a.", "§aTeam §f{0}§a's limit has been set to §f{1}§a."),

    // ===== Day =====
    DAY_USAGE("§cUsage : §7/xii day <start|stop|set>", "§cUsage: §7/xii day <start|stop|set>"),
    DAY_NOT_STARTED("§cLa partie n'a pas commencé.", "§cThe game hasn't started."),
    DAY_SET_USAGE("§cUsage : §7/xii day set <1-12>", "§cUsage: §7/xii day set <1-12>"),
    DAY_INVALID("§cLe jour doit être compris entre §f1 §cet §f12§c.", "§cDay must be between §f1 §cand §f12§c."),
    DAY_MIN_TEAMS("§cAu moins §f2 §céquipes sont nécessaires.", "§cAt least §f2 §cteams are required."),
    DAY_GAME_STARTED("§cLa partie a déjà commencé.", "§cThe game has already started."),
    DAY_GAME_STOPPED("§cLa partie n'a pas commencé.", "§cThe game hasn't started."),

    // ===== Allow (item/block) =====
    ALLOW_USAGE("§cUsage : §7/xii allow <item|block> <true|false>", "§cUsage: §7/xii allow <item|block> <true|false>"),
    ALLOW_NO_ITEM("§cVous ne tenez aucun objet.", "§cYou're not holding an item."),
    ALLOW_ITEM_ADDED("§c§f{0} §ca été ajouté à la liste noire.", "§c§f{0} §chas been added to the blacklist."),
    ALLOW_ITEM_REMOVED("§a§f{0} §aa été retiré de la liste noire.", "§a§f{0} §ahas been removed from the blacklist."),
    ALLOW_NO_BLOCK("§cVous ne visez aucun bloc.", "§cYou're not targeting a block."),
    ALLOW_BLOCK_ADDED("§c§f{0} §ca été ajouté à la liste noire.", "§c§f{0} §chas been added to the blacklist."),
    ALLOW_BLOCK_REMOVED("§a§f{0} §aa été retiré de la liste noire.", "§a§f{0} §ahas been removed from the blacklist."),
    ALLOW_UNKNOWN("§cUsage : §7/xii allow ...", "§cUsage: §7/xii allow ..."),

    // ===== Points =====
    POINTS_USAGE("§cUsage : §7/xii points <set|reset> <joueur|couleur> [valeur]", "§cUsage: §7/xii points <set|reset> <player|color> [value]"),
    POINTS_UNKNOWN_SUB("§cSous-commande inconnue : §f{0}", "§cUnknown subcommand: §f{0}"),
    POINTS_SET_USAGE("§cUsage : §7/xii points set <joueur|couleur> <points>", "§cUsage: §7/xii points set <player|color> <points>"),
    POINTS_TEAM_SET("§aPoints de l'équipe §f{0} §adéfinis à §f{1}§a.", "§aTeam §f{0}§a's points have been set to §f{1}§a."),
    POINTS_PLAYER_SET("§aPoints de §f{0} §adéfinis à §f{1}§a.", "§a§f{0}§a's points have been set to §f{1}§a."),
    POINTS_RESET_USAGE("§cUsage : §7/xii points reset <joueur|couleur>", "§cUsage: §7/xii points reset <player|color>"),
    POINTS_TEAM_RESET("§aPoints de l'équipe §f{0} §aréinitialisés.", "§aTeam §f{0}§a's points have been reset."),
    POINTS_PLAYER_RESET("§aPoints de §f{0} §aréinitialisés.", "§a§f{0}§a's points have been reset."),

    // ===== Admin =====
    ADMIN_USAGE("§cUsage : §7/xii admin <setup|quit>", "§cUsage: §7/xii admin <setup|quit>"),

    // ===== End of Game =====
    VICTORY_ANNOUNCE("§6§lVICTOIRE !", "§6§lVICTORY!"),
    DEFEAT_ANNOUNCE("§c§lGAME OVER !", "§c§lGAME OVER!"),
    TIE_ANNOUNCE("§7§lEgalité !", "§7§lTie!"),

    // ===== Broadcasts =====
    GAME_STARTED("§6§lXII DAYS §7a commencé !", "§6§lXII DAYS §7has started!"),
    GAME_STOPPED("§c§lXII DAYS §7a été arrêté.", "§c§lXII DAYS §7has been stopped."),
    DAY_ANNOUNCE("§6§lJOUR §f§l{0}", "§6§lDAY §f§l{0}"),
    COUNTDOWN_TICK("§e§l{0}...", "§e§l{0}..."),
    SETUP_INITIALIZED("§a§lXII DAYS §7a été initialisé.", "§a§lXII DAYS §7has been initialized."),
    SETUP_RESET("§e§lXII DAYS §7a été réinitialisé.", "§e§lXII DAYS §7has been reset."),
    WAITING_FOR_START("§e§lEn attente du début...", "§e§lWaiting for start..."),

    // ===== Misc =====
    SPECTATOR_NO_TEAM("§cVous n'êtes dans aucune équipe. §7Mode spectateur activé.", "§cYou're not in any team. §7Spectator mode enabled."),
    CANCELLED("§cAction annulée.", "§cAction cancelled."),
    MAX_MEMBERS_INVALID("§cNombre invalide.", "§cInvalid number."),
    MAX_MEMBERS_SET("§aLimite définie à §f{0}§a.", "§aLimit set to §f{0}§a."),
    TEAM_GUI_FULL("§c§lCOMPLET", "§c§lFULL"),
    TEAM_GUI_PLAYERS("§7{0}§8/§7{1} joueurs", "§7{0}§8/§7{1} players"),
    PLAYER_NOT_FOUND_SHORT("§cJoueur introuvable.", "§cPlayer not found."),
    PLAYER_ADDED_TO_TEAM("§a§f{0} §aa rejoint l'équipe.", "§a§f{0} §ahas joined the team."),
    PLAYER_REMOVED_SHORT("§a§f{0} §aa été retiré.", "§a§f{0} §ahas been removed."),
    TP_SPAWN_NOT_SET("§cLe point de réapparition de cette équipe n'est pas défini.", "§cThis team's spawn point has not been set."),
    TP_ALL_DONE("§aLes joueurs de l'équipe §f{0} §aont été téléportés.", "§aAll players of team §f{0} §ahave been teleported."),
    TP_PLAYER_DONE("§a§f{0} §aa été téléporté à la base.", "§a§f{0} §ahas been teleported to base."),
    LANG_CHANGED_FR("§aLangue définie sur §6Français§a.", "§aLanguage set to §6French§a."),
    LANG_CHANGED_EN("§aLangue définie sur §6English§a.", "§aLanguage set to §6English§a."),
    ALREADY_IN_TEAM("§cVous êtes déjà dans l'équipe §f{0}§c.", "§cYou're already in team §f{0}§c."),

    // ===== GUI Titles =====
    GUI_ADMIN("§6§lAdministration", "§6§lAdministration"),
    GUI_TEAM_MANAGEMENT("§6§lGestion des équipes", "§6§lTeam Management"),
    GUI_GAME_MANAGEMENT("§e§lGestion de la partie", "§e§lGame Management"),
    GUI_TEAM_SELECTOR("§6Choisir une équipe", "§6Choose a team"),
    GUI_TEAM_CREATE("§a§lCréer une équipe", "§a§lCreate a team"),
    GUI_LANGUAGE("§6§lLangue", "§6§lLanguage"),

    // ===== GUI Items =====
    GUI_TEAM_MGMT("§d§lGestion des équipes", "§d§lTeam Management"),
    GUI_TEAM_MGMT_LORE("§7Créer, supprimer et gérer les équipes.", "§7Create, delete and manage teams."),
    GUI_GAME_MGMT("§e§lGestion de la partie", "§e§lGame Management"),
    GUI_GAME_MGMT_LORE("§7Démarrer, arrêter et modifier le jour.", "§7Start, stop and change the day."),
    GUI_ALLOW_JOIN("§e§lAutoriser à rejoindre", "§e§lAllow Join"),
    GUI_ALLOW_JOIN_STATE("§7État : {0}", "§7State: {0}"),
    GUI_ALLOW_LEAVE("§e§lAutoriser à quitter", "§e§lAllow Leave"),
    GUI_ALLOW_LEAVE_STATE("§7État : {0}", "§7State: {0}"),
    GUI_ENABLED("§aActivé", "§aEnabled"),
    GUI_DISABLED("§cDésactivé", "§cDisabled"),
    GUI_TOGGLE("§7Cliquez pour modifier.", "§7Click to toggle."),
    GUI_BACK("§c§lRetour", "§c§lBack"),
    GUI_CREATE_TEAM("§a§lCréer une équipe", "§a§lCreate a team"),
    GUI_CLICK_TO_MANAGE("§7Cliquez pour gérer.", "§7Click to manage."),
    GUI_HEART_STATUS("§7Cœur : {0}", "§7Heart: {0}"),
    GUI_CLICK_TO_CREATE("§7Cliquez pour créer.", "§7Click to create."),
    GUI_ALREADY_CREATED("§cCette équipe existe déjà.", "§cThis team already exists."),
    GUI_FULL("§c§lCOMPLET", "§c§lFULL"),
    GUI_SPECTATOR("§7Mode spectateur", "§7Spectator Mode"),
    GUI_CLICK_TO_ADD("§7Cliquez pour ajouter.", "§7Click to add."),
    GUI_CLICK_TO_REMOVE("§7Cliquez pour retirer.", "§7Click to remove."),
    GUI_STOP("§c§lArrêter", "§c§lStop"),
    GUI_START("§a§lDémarrer", "§a§lStart"),
    GUI_STOP_LORE("§7Arrêter la partie.", "§7Stop the game."),
    GUI_START_LORE("§7Démarrer la partie.", "§7Start the game."),
    GUI_SET_DAY("§e§lModifier le jour", "§e§lSet Day"),
    GUI_CURRENT_DAY("§7Jour actuel : §f{0}", "§7Current day: §f{0}"),
    GUI_CLICK_TO_CHANGE("§7Cliquez pour modifier. §8(1-12)", "§7Click to change. §8(1-12)"),
    GUI_DESTROY_HEART("§c§lDétruire le cœur", "§c§lDestroy Heart"),
    GUI_DESTROY_HEART_LORE("§7Détruire le cœur de l'équipe.", "§7Destroy the team's heart."),
    GUI_RESTORE_HEART("§a§lRestaurer le cœur", "§a§lRestore Heart"),
    GUI_RESTORE_HEART_LORE("§7Restaurer le cœur de l'équipe.", "§7Restore the team's heart."),
    GUI_ELIMINATE("§4§lÉliminer", "§4§lEliminate"),
    GUI_ELIMINATE_LORE("§7Éliminer toute l'équipe.", "§7Eliminate the entire team."),
    GUI_REVIVE("§6§lRéanimer", "§6§lRevive"),
    GUI_REVIVE_LORE("§7Réanimer l'équipe.", "§7Revive the team."),
    GUI_TP_BASE("§b§lTéléporter à la base", "§b§lTP Base"),
    GUI_TP_BASE_LORE("§7Téléporter l'équipe à sa base.", "§7Teleport the team to their base."),
    GUI_MAX_MEMBERS("§f§lMembres maximum", "§f§lMax Members"),
    GUI_MAX_MEMBERS_LORE("§7Limite actuelle : §f{0}", "§7Current limit: §f{0}"),
    GUI_CLICK_TO_CHANGE2("§7Cliquez pour modifier.", "§7Click to change."),
    GUI_ADD_PLAYER("§a§lAjouter un joueur", "§a§lAdd a player"),
    GUI_ADD_PLAYER_LORE("§7Cliquez pour sélectionner un joueur.", "§7Click to select a player."),
    GUI_REMOVE_PLAYER("§c§lRetirer un joueur", "§c§lRemove a player"),
    GUI_REMOVE_PLAYER_LORE("§7Cliquez pour sélectionner un joueur.", "§7Click to select a player."),
    GUI_DELETE_TEAM("§4§lSupprimer l'équipe", "§4§lDelete team"),
    GUI_DELETE_TEAM_LORE("§7Supprimer définitivement cette équipe.", "§7Permanently delete this team."),
    GUI_WHOLE_TEAM("§b§lToute l'équipe", "§b§lWhole Team"),
    GUI_WHOLE_TEAM_LORE("§7Téléporter tous les joueurs de l'équipe.", "§7Teleport all players on the team."),
    GUI_TP_CLICK("§7Cliquez pour téléporter.", "§7Click to teleport."),
    GUI_TEAM_FULL("§cCette équipe est complète.", "§cThis team is full."),
    GUI_ADD_PLAYER_TITLE("§a§lAjouter un joueur", "§a§lAdd a Player"),
    GUI_REMOVE_PLAYER_TITLE("§c§lRetirer un joueur", "§c§lRemove a Player"),

    // ===== New Game States =====
    SETUP_ALREADY_DONE("§cXII Days est déjà configuré.", "§cXII Days is already configured."),
    SETUP_REQUIRED("§cLe setup n'a pas été lancé. Utilise /xii admin setup", "§cSetup not done. Use /xii admin setup"),
    SETUP_IN_PROGRESS("§eConfiguration en cours...", "§eSetup in progress..."),
    SETUP_FAILED("§cErreur lors de la configuration.", "§cError during setup."),
    WORLD_MISSING("§cLe monde XII Days n'existe plus. Reconfiguration nécessaire.", "§cXII Days world no longer exists. Reconfiguration needed."),
    WORLD_RESET("§eLe monde XII Days a été supprimé. Serveur en mode classique.", "§eXII Days world deleted. Server in classic mode."),

    // ===== Countdown =====
    COUNTDOWN_START("§e§lLe compte à rebours commence !", "§e§lThe countdown has started!"),
    COUNTDOWN_CANCELLED("§e§lCompte à rebours annulé.", "§e§lCountdown cancelled."),
    COUNTDOWN_GO("§a§lC'EST PARTI !", "§a§lGO!"),

    // ===== Death / Respawn =====
    YOU_DIED("§c§lVOUS AVEZ MOURU", "§c§lYOU DIED"),
    RESPAWN_IN("§7Réapparition dans §f{0} §7secondes", "§7Respawn in §f{0} §7seconds"),
    PLAYER_LEFT_GAME("§e{0} §ca quitté la partie.", "§e{0} §chas left the game."),
    PLAYER_RECONNECTED("§a{0} §7est revenu dans la partie.", "§a{0} §7has returned to the game."),

    // ===== Game Phase =====
    PREPARATION_INFO("§6§lPRÉPARATION §7- Jour §f{0}", "§6§lPREPARATION §7- Day §f{0}"),
    COMBAT_INFO("§c§lCOMBAT §7- Jour §f{0}", "§c§lCOMBAT §7- Day §f{0}"),

    // ===== Region Protection =====
    BASE_PROTECTED("§cVous êtes dans une zone protégée.", "§cYou are in a protected zone."),
    DUNGEON_PROTECTED("§cVous êtes dans un donjon protégé.", "§cYou are in a protected dungeon."),

    // ===== Admin Stop =====
    ADMIN_STOP("§c§lXII DAYS §7a été complètement arrêté.", "§c§lXII DAYS §7has been fully stopped."),
    ADMIN_STOP_NO_SETUP("§cXII Days n'est pas configuré.", "§cXII Days is not configured.");

    private final String fr;
    private final String en;

    Messages(String fr, String en) {
        this.fr = fr;
        this.en = en;
    }

    public String get(Lang lang) {
        return Lang.FR.equals(lang) ? fr : en;
    }

    public String get(Lang lang, Object... args) {
        String template = get(lang);
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }
}