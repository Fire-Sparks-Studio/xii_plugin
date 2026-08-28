package com.mceteams.xii.scoreboard;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.GameState;
import com.mceteams.xii.enums.PlayerUpgrade;
import com.mceteams.xii.enums.PointCategory;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.PlayerData;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.scoreboard.tab.FakeTabEntry;
import com.mceteams.xii.scoreboard.tab.PlayerInfoPackets;
import com.mceteams.xii.scoreboard.tab.TabColumn;
import com.mceteams.xii.scoreboard.tab.TabLayout;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Affichage TAB (liste des joueurs) - grille "Joueurs | Infos" (SANS
 * ProtocolLib, NMS Mojang-mapped du dev bundle paperweight).
 *
 * Le client trie les entrées par ordre de liste décroissant puis les
 * range en colonnes de 20 lignes max (colonne par colonne). TabLayout
 * complète donc la grille avec de fausses entrées pour obtenir DEUX
 * colonnes côte à côte :
 *
 *   COLONNE GAUCHE : les joueurs réels (équipes triées Bleu > Jaune >
 *   Rouge > Vert, puis sans-équipe, puis spectateurs) suivis de lignes
 *   vides jusqu'à la hauteur cible.
 *   COLONNE DROITE  : le panel d'infos PERSONNEL (VOS STATS) : kills,
 *   points apportés et upgrades du joueur qui consulte le TAB.
 *
 * HEADER (en haut) : "XII DAYS".
 * FOOTER (en bas)  : statut de la partie.
 *
 * Les envois réseau sont DIFFÉRENTIELS par spectateur : seuls les
 * paquets nécessaires (ajout / mise à jour / retrait) sont émis, même si
 * updateAll() est appelé chaque seconde en partie (GameManager).
 */
public class TabManager {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private final XiiPlugin plugin;

    /** Fausses entrées déjà poussées par spectateur (uuid viewer -> entrée). */
    private final Map<UUID, Map<UUID, FakeTabEntry>> pushed = new HashMap<>();
    /** Dernier nom de liste appliqué par joueur (diff). */
    private final Map<UUID, String> lastPlayerName = new HashMap<>();
    /** Dernier ordre appliqué par joueur (diff). */
    private final Map<UUID, Integer> lastPlayerOrder = new HashMap<>();
    /** Dernier header/footer appliqué par joueur (diff). */
    private final Map<UUID, String> lastHeaderFooter = new HashMap<>();

    public TabManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /** Met à jour la liste (noms, ordre, infos, header/footer) de tous. */
    public void updateAll() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        List<Player> sorted = sortPlayers(online);
        Map<UUID, Integer> orders = assignOrders(sorted);
        for (Player viewer : online) {
            TabLayout layout = buildLayout(viewer, sorted.size());
            syncViewer(viewer, layout, layout.fakeEntries(), orders);
        }
    }

    /**
     * Prépare le TAB après une connexion (recalcul complet, décalé d'un
     * tick pour laisser la connexion se stabiliser).
     */
    public void onJoin(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Hors partie (NONE) : le plugin n'interfère pas avec le serveur.
            if (plugin.getGameManager().getState() == GameState.NONE) {
                return;
            }
            // Un joueur qui ARRIVE (première connexion OU reconnection)
            // est d'abord diffusé par la vanilla avec l'ordre 0 => il
            // apparaît TOUT EN BAS du TAB chez les autres. On force sa
            // re-publication immédiate avec le bon nom + le bon ordre
            // (paquet au niveau de chaque spectateur, idempotent), puis
            // updateAll recalcule le reste au tick suivant.
            readdJoinedPlayer(player);
            updateAll();
        });
    }

    /**
     * Re-publie le joueur qui vient de se connecter AUPRÈS DE TOUS les
     * autres spectateurs avec sa position calculée + son nom de liste,
     * et pose aussi son ordre global côté serveur. Corrige le bug de
     * "joueur reco tout en bas du TAB" (ordre vanilla 0 le temps du tick).
     */
    private void readdJoinedPlayer(Player joined) {
        if (joined == null || !joined.isOnline()) {
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> sorted = sortPlayers(online);
        int index = Math.max(0, sorted.indexOf(joined));
        int order = TabLayout.PLAYER_ORDER_BASE - index;
        String name = formattedName(joined);

        for (Player viewer : online) {
            if (viewer.equals(joined) || !viewer.isOnline()) {
                continue;
            }
            PlayerInfoPackets.readd(viewer, joined,
                    CraftChatMessage.fromStringOrNull(name), order);
        }

        // Garde l'état de diff cohérent avec le sync qui va suivre.
        lastPlayerName.put(joined.getUniqueId(), name);
        lastPlayerOrder.put(joined.getUniqueId(), order);
        if (joined.isOnline()) {
            joined.playerListName(LEGACY.deserialize(name));
            joined.setPlayerListOrder(order);
        }
    }

    /** Oublie l'état poussé à un joueur qui se déconnecte. */
    public void onQuit(UUID uuid) {
        pushed.remove(uuid);
        lastPlayerName.remove(uuid);
        lastPlayerOrder.remove(uuid);
        lastHeaderFooter.remove(uuid);
        // Les ordres des joueurs restants changent : on recalcule au tick
        // suivant (les envois restent différentiels, donc légers).
        Bukkit.getScheduler().runTask(plugin, this::updateAll);
    }

    /** Retire toutes les fausses entrées et oublie les états (retour lobby). */
    public void resetAll() {
        for (Player viewer : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            Map<UUID, FakeTabEntry> sent = pushed.remove(viewer.getUniqueId());
            if (sent != null && !sent.isEmpty()) {
                PlayerInfoPackets.remove(viewer, sent.keySet());
            }
        }
        pushed.clear();
        lastPlayerName.clear();
        lastPlayerOrder.clear();
        lastHeaderFooter.clear();
    }

    /**
     * Re-publie dans le TAB d'un viewer un joueur qui vient d'être caché
     * via hidePlayer : le hide retire aussi l'entrée du TAB, on la remet
     * (paquet) pour le garder affiché dans la liste tout en restant
     * invisible dans le monde. Utilise l'état déjà appliqué pour rester
     * cohérent avec le sync régulier.
     */
    public void restoreHiddenPlayer(Player viewer, Player hidden) {
        if (viewer == null || hidden == null || viewer.equals(hidden)) {
            return;
        }
        UUID uuid = hidden.getUniqueId();
        String name = lastPlayerName.get(uuid);
        if (name == null) {
            name = formattedName(hidden);
            lastPlayerName.put(uuid, name);
        }
        int order = lastPlayerOrder.getOrDefault(
                uuid, TabLayout.PLAYER_ORDER_BASE - sortPosition(hidden));
        if (!lastPlayerOrder.containsKey(uuid)) {
            lastPlayerOrder.put(uuid, order);
        }
        if (viewer.isOnline() && hidden.isOnline()) {
            PlayerInfoPackets.readd(viewer, hidden,
                    CraftChatMessage.fromStringOrNull(name), order);
        }
    }

    /** Position du joueur dans l'ordre de tri courant (pour l'ordre TAB). */
    private int sortPosition(Player player) {
        List<Player> sorted = sortPlayers(new ArrayList<>(Bukkit.getOnlinePlayers()));
        return Math.max(0, sorted.indexOf(player));
    }

    // -----------------------------------------------------------------
    // Synchronisation par spectateur (différentielle)
    // -----------------------------------------------------------------

    private void syncViewer(Player viewer, TabLayout layout,
                            List<FakeTabEntry> fakes, Map<UUID, Integer> orders) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        syncFakeEntries(viewer, fakes);
        syncRealPlayer(viewer,
                orders.getOrDefault(viewer.getUniqueId(), TabLayout.PLAYER_ORDER_BASE));
        syncHeaderFooter(viewer, layout);
    }

    /** Ajoute / met à jour / retire les fausses entrées du spectateur. */
    private void syncFakeEntries(Player viewer, List<FakeTabEntry> target) {
        UUID viewerId = viewer.getUniqueId();
        Map<UUID, FakeTabEntry> sent = pushed.computeIfAbsent(
                viewerId, k -> new HashMap<>());

        List<FakeTabEntry> toAdd = new ArrayList<>();
        List<FakeTabEntry> toUpdate = new ArrayList<>();
        List<UUID> toRemove = new ArrayList<>();

        for (FakeTabEntry entry : target) {
            FakeTabEntry previous = sent.get(entry.uuid());
            if (previous == null) {
                toAdd.add(entry);
            } else if (!previous.equals(entry)) {
                toUpdate.add(entry);
            }
        }
        for (UUID id : sent.keySet()) {
            if (!containsUuid(target, id)) {
                toRemove.add(id);
            }
        }

        if (!toRemove.isEmpty()) {
            PlayerInfoPackets.remove(viewer, toRemove);
        }
        if (!toAdd.isEmpty()) {
            PlayerInfoPackets.add(viewer, toAdd);
        }
        if (!toUpdate.isEmpty()) {
            PlayerInfoPackets.update(viewer, toUpdate);
        }

        for (UUID id : toRemove) {
            sent.remove(id);
        }
        for (FakeTabEntry entry : toAdd) {
            sent.put(entry.uuid(), entry);
        }
        for (FakeTabEntry entry : toUpdate) {
            sent.put(entry.uuid(), entry);
        }
    }

    /** Nom + ordre de tri du joueur réel (colonne joueurs). */
    private void syncRealPlayer(Player player, int order) {
        UUID uuid = player.getUniqueId();

        String name = formattedName(player);
        if (!name.equals(lastPlayerName.get(uuid))) {
            lastPlayerName.put(uuid, name);
            player.playerListName(LEGACY.deserialize(name));
        }
        if (order != lastPlayerOrder.getOrDefault(uuid, Integer.MIN_VALUE)) {
            lastPlayerOrder.put(uuid, order);
            player.setPlayerListOrder(order);
        }
    }

    /** Nom d'affichage d'un joueur dans le TAB (spectateur grisé sinon). */
    private String formattedName(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        if (data != null && data.isSpectator()) {
            return "§5[Spectateur] §7" + player.getName();
        }
        if (team != null) {
            TeamColor color = team.getColor();
            return color.getColorCode() + "§l" + color.getLetter()
                    + "§r " + color.getColorCode() + player.getName();
        }
        return "§7" + player.getName();
    }

    private void syncHeaderFooter(Player viewer, TabLayout layout) {
        UUID uuid = viewer.getUniqueId();
        String header = String.join("\n", layout.headerLines());
        String footer = String.join("\n", layout.footerLines());
        String joined = header + "\n\n" + footer;
        if (joined.equals(lastHeaderFooter.get(uuid))) {
            return;
        }
        lastHeaderFooter.put(uuid, joined);
        viewer.sendPlayerListHeaderAndFooter(
                LEGACY.deserialize(header),
                LEGACY.deserialize(footer));
    }

    private static boolean containsUuid(List<FakeTabEntry> entries, UUID uuid) {
        for (FakeTabEntry entry : entries) {
            if (entry.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Tri des joueurs réels (colonne de gauche)
    // -----------------------------------------------------------------

    /**
     * Joueurs triés équipe d'abord (Bleu > Jaune > Rouge > Vert), puis
     * sans-équipe, puis spectateurs ; nom pour départager.
     */
    private List<Player> sortPlayers(List<Player> online) {
        return online.stream()
                .sorted(Comparator.comparingInt((Player p) -> -teamRank(p))
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Rang de tri d'un joueur : 3 (Bleu) .. 0 (Vert), -1, -2 (spectateur). */
    private int teamRank(Player player) {
        var data = plugin.getPlayerManager().getData(player);
        if (data != null && data.isSpectator()) {
            return -2;
        }
        var team = plugin.getTeamManager().getTeamOf(player.getUniqueId());
        return team != null ? 3 - team.getColor().ordinal() : -1;
    }

    /** Ordres de liste STRICTEMENT décroissants, dans l'ordre du tri. */
    private Map<UUID, Integer> assignOrders(List<Player> sorted) {
        Map<UUID, Integer> orders = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            orders.put(sorted.get(i).getUniqueId(),
                    TabLayout.PLAYER_ORDER_BASE - i);
        }
        return orders;
    }

    // -----------------------------------------------------------------
    // Construction du layout
    // -----------------------------------------------------------------

    private TabLayout buildLayout(Player viewer, int playerSlots) {
        GameState state = plugin.getGameManager().getState();
        TabLayout layout = new TabLayout();

        layout.header("§b§lXII DAYS");

        int day = plugin.getPhaseManager().currentDay();
        layout.footer(footerText(state, day));

        switch (state) {
            case PREPARATION, COMBAT -> appendStats(layout, viewer);
            case ENDING -> appendRanking(layout);
            default -> { /* lobby : liste des joueurs seule. */ }
        }
        return layout.withPlayerSlots(playerSlots);
    }

    /** Footer : phase de la partie en bas de la liste TAB. */
    private String footerText(GameState state, int day) {
        return switch (state) {
            case PREPARATION -> "§ePhase : §7Préparation - Jour §b"
                    + day + "§8/12";
            case COMBAT -> "§ePhase : §7Combat - Jour §b"
                    + day + "§8/12";
            case ENDING -> "§ePhase : §6Fin de partie";
            default -> "§ePhase : §7En attente";
        };
    }

    /**
     * Panel d'infos PERSONNEL du spectateur : ses kills, ses points et
     * ses upgrades.
     *
     * Chaque joueur reçoit ses propres paquets, donc on peut construire
     * un contenu différent par spectateur (contrairement au scoreboard
     * latéral qui est global).
     */
    private void appendStats(TabLayout layout, Player viewer) {
        PlayerData data = plugin.getPlayerManager().getData(viewer);

        List<String> lines = new ArrayList<>();
        lines.add(" §6§l-- §7VOS STATS §6§l--");
        lines.add(" §7Kills : §e" + data.getScore().get(PointCategory.KILL));
        lines.add(" §7Points apportés : §e" + data.getScore().getTotal());
        lines.add(" §7Upgrades :");

        List<String> upgrades = new ArrayList<>();
        for (PlayerUpgrade upgrade : PlayerUpgrade.values()) {
            int level = data.getUpgradeLevel(upgrade);
            if (level > 0) {
                upgrades.add(" §7" + upgrade.getDisplayName()
                        + " : §eNiv " + PlayerUpgrade.roman(level));
            }
        }
        if (upgrades.isEmpty()) {
            upgrades.add(" §7Aucun");
        }
        // LA COLONNE D'INFOS DOIT tenir dans 20 lignes max (sinon le
        // client casse la grille en 3 colonnes). Budget : ligne de titre,
        // kills, points, "Upgrades :" = 4 lignes fixes + les 13 upgrades
        // du jeu = 17 lignes <= 20 => TOUTES les upgrades s'affichent.
        int maxUpgradeLines = PlayerUpgrade.values().length;
        if (upgrades.size() > maxUpgradeLines) {
            upgrades = new ArrayList<>(upgrades.subList(0, maxUpgradeLines));
        }
        lines.addAll(upgrades);

        TabColumn column = new TabColumn();
        for (String line : lines) {
            column.add(line);
        }
        layout.column(column);
    }

    /** Classement par points (utilisé en ENDING). */
    private void appendRanking(TabLayout layout) {
        var ranking = plugin.getTeamManager().all().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getScore().getTotal(), a.getScore().getTotal()))
                .limit(4)
                .toList();
        TabColumn column = new TabColumn();
        column.add(" §6§l-- §7CLASSEMENT §6§l--");
        for (GameTeam team : ranking) {
            column.add(" " + team.getColor().getColoredName()
                    + " §e" + team.getScore().getTotal());
        }
        layout.column(column);
    }
}