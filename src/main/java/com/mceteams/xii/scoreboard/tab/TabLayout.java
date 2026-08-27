package com.mceteams.xii.scoreboard.tab;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Layout du TAB : header + footer + colonnes d'infos.
 *
 * Le client Minecraft trie TOUTES les entrées par ordre de liste
 * DÉCROISSANT (listOrder élevé = en haut à gauche) puis les range en
 * colonnes de 20 lignes maximum. Concrètement :
 *
 *   total &lt;= 20              -> 1 colonne (tout à la verticale)
 *   21 &lt;= total &lt;= 40        -> 2 colonnes, remplissage COLONNE PAR COLONNE
 *   (les total/2 premières entrées à gauche, le reste à droite)
 *
 * Pour que la colonne d'infos apparaisse À DROITE des joueurs (et non pas
 * en dessous), on complète la grille à une hauteur cible h :
 *
 *   - bande [haut] : les JOUEURS réels (ordres PLAYER_ORDER_BASE - i) ;
 *   - bande suivante : lignes VIDE (fausses entrées invisibles) qui
 *     terminent la colonne de gauche ;
 *   - bande suivante : la colonne d'infos (droite, en haut) ;
 *   - bande [bas]   : lignes VIDE qui terminent la colonne de droite.
 *
 * T = 2*h entrées => le client fait exactement 2 colonnes de h lignes.
 * h est au minimum MIN_HEIGHT_FOR_COLUMNS pour garantir 2 colonnes même
 * avec très peu de joueurs.
 */
public class TabLayout {

    /** Bande d'ordres des joueurs réels (>= 0 : passe par l'API Bukkit). */
    public static final int PLAYER_ORDER_BASE = 1_000_000;
    /** Bande d'ordres des lignes vides de la colonne de gauche. */
    public static final int FILLER_LEFT_BASE = 900_000;
    /** Bande d'ordres des lignes d'infos (colonne de droite). */
    public static final int INFO_BASE = 800_000;
    /** Bande d'ordres des lignes vides de la colonne de droite. */
    public static final int FILLER_RIGHT_BASE = 700_000;

    /** Hauteur minimum pour forcer 2 colonnes (2*h > 20). */
    public static final int MIN_HEIGHT_FOR_COLUMNS = 11;
    /** Lignes maximum par colonne (limite du client). */
    public static final int MAX_ROWS_PER_COLUMN = 20;

    private final List<String> headerLines = new ArrayList<>();
    private final List<String> footerLines = new ArrayList<>();
    private final List<TabColumn> columns = new ArrayList<>();

    /** Nombre de joueurs réels affichés à côté de la colonne d'infos. */
    private int playerSlots = 0;

    public TabLayout withPlayerSlots(int playerSlots) {
        this.playerSlots = Math.max(0, playerSlots);
        return this;
    }

    public TabLayout header(String... lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                headerLines.add(line);
            }
        }
        return this;
    }

    public TabLayout footer(String... lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                footerLines.add(line);
            }
        }
        return this;
    }

    public TabLayout column(TabColumn column) {
        if (column != null && !column.isEmpty()) {
            columns.add(column);
        }
        return this;
    }

    public List<String> headerLines() {
        return Collections.unmodifiableList(headerLines);
    }

    public List<String> footerLines() {
        return Collections.unmodifiableList(footerLines);
    }

    private int infoRowCount() {
        int count = 0;
        for (TabColumn column : columns) {
            count += column.rows().size();
        }
        return count;
    }

    /**
     * Toutes les fausses entrées (infos + vides) qui complètent la grille
     * à côté des joueurs réels. UUID DÉTERMINISTES : le diff par
     * spectateur peut ajouter / mettre à jour / retirer proprement.
     * Retourne une liste vide quand aucune colonne d'infos (lobby).
     */
    public List<FakeTabEntry> fakeEntries() {
        int info = infoRowCount();
        if (info == 0) {
            return List.of(); // lobby : liste des joueurs seule, verticale.
        }

        int height = Math.min(MAX_ROWS_PER_COLUMN,
                Math.max(MIN_HEIGHT_FOR_COLUMNS, Math.max(playerSlots, info)));
        int leftFillers = Math.max(0, height - playerSlots);
        int rightFillers = Math.max(0, height - info);

        List<FakeTabEntry> entries = new ArrayList<>();
        for (int i = 0; i < leftFillers; i++) {
            entries.add(filler("lg", i, FILLER_LEFT_BASE - i));
        }
        int infoIndex = 0;
        for (TabColumn column : columns) {
            for (String row : column.rows()) {
                entries.add(new FakeTabEntry(
                        uuidOf("nf", infoIndex),
                        "XII Info",
                        row,
                        INFO_BASE - infoIndex));
                infoIndex++;
            }
        }
        for (int i = 0; i < rightFillers; i++) {
            entries.add(filler("rd", i, FILLER_RIGHT_BASE - i));
        }
        return entries;
    }

    /** Ligne vide (un espace) qui bouche une colonne de la grille. */
    private static FakeTabEntry filler(String ns, int index, int order) {
        return new FakeTabEntry(uuidOf(ns, index), "XII F", " ", order,
                GRAY_BLOCK_PROFILE);
    }

    /**
     * Profil "bloc gris" : peau intégralement grise (data-URI), affichée
     * par le client comme une petite tête de bloc grise à la place de la
     * tête de joueur par défaut (Steve) sur les entrées vides du TAB.
     */
    private static final GameProfile GRAY_BLOCK_PROFILE = buildGrayBlockProfile();

    private static GameProfile buildGrayBlockProfile() {
        // NB : PropertyMap (authlib 9) COPIE toute multimap passée en
        // constructeur dans une ImmutableMultimap : put() y lève
        // UnsupportedOperationException. On insère donc la texture AVANT
        // l'encapsulation.
        //
        // Peau 64x64 entièrement grise générée hors ligne et embarquée en
        // "data:image/png;base64,..." : quelle que soit la zone que le
        // client recadre pour l'icône du TAB, elle est grise dense.
        String textureValue = "{\"textures\":{\"SKIN\":{\"url\":\"data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAACuSURBVHhe7daxDcQgDAXQTMwqbMRqd6IgOsFZbkl4xW+whaUnF75KKZ+Tc80Pp+UGqLX+TWtteXtSPQoAAAAAAPgF6B+dFhsAAAAAAAAAuANsAAAAAJaGkWzA7vUoAAC4A2wAAAAAAAAA4A6wAQAAAACwNIxkA3avRwEwA/SPTosNAAAAAAAAANwBNgAAAABLw0g2YPd6FAAAAAAAAAAAgBsgSh8wvz2pniUFeHu+I0SGsqT3mS8AAAAASUVORK5CYII=\"}}}";
        Multimap<String, Property> mutable = HashMultimap.create();
        mutable.put("textures", new Property("textures", textureValue));
        return new GameProfile(
                UUID.nameUUIDFromBytes(
                        "xii:grey-block".getBytes(StandardCharsets.UTF_8)),
                "XII Grey Block",
                new PropertyMap(mutable));
    }

    private static UUID uuidOf(String namespace, int index) {
        return UUID.nameUUIDFromBytes(
                ("xii:" + namespace + ":" + index).getBytes(StandardCharsets.UTF_8));
    }
}