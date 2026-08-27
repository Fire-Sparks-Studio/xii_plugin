package com.mceteams.xii.scoreboard.tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Colonne d'informations INDÉPENDANTE des joueurs : une liste de lignes
 * legacy affichées sous forme de fausses entrées du TAB.
 *
 * C'est la brique générique du layout : aucune dépendance au gameplay
 * XII Days (c'est TabManager qui construit le contenu de la colonne).
 * Le haut de la colonne = la première ligne ajoutée.
 */
public class TabColumn {

    /** Lignes de la colonne, dans l'ordre d'affichage (haut -> bas). */
    private final List<String> rows = new ArrayList<>();

    public TabColumn add(String value) {
        rows.add(value);
        return this;
    }

    public List<String> rows() {
        return Collections.unmodifiableList(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}