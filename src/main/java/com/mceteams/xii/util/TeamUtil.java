package com.mceteams.xii.util;

import com.mceteams.xii.enums.TeamColor;
import org.bukkit.Material;

/**
 * Conversions entre TeamColor (model) et le monde Bukkit : laines,
 * codes couleur, noms d'équipe Bukkit. Centralisé ici pour que les
 * enums et models restent purs (spec §40).
 */
public final class TeamUtil {

    private TeamUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Laine correspondant à la couleur d'équipe (item du sélecteur,
     * spec §5) : BLEU -> BLUE_WOOL, JAUNE -> YELLOW_WOOL, etc.
     */
    public static Material woolOf(TeamColor color) {
        return switch (color) {
            case BLUE -> Material.BLUE_WOOL;
            case YELLOW -> Material.YELLOW_WOOL;
            case RED -> Material.RED_WOOL;
            case GREEN -> Material.GREEN_WOOL;
        };
    }

    /**
     * Nom de l'équipe Bukkit associée. Préfixé pour éviter toute
     * collision avec des teams vanilla existantes.
     */
    public static String bukkitTeamName(TeamColor color) {
        return "xii_" + color.name().toLowerCase();
    }

    /**
     * Parse une couleur depuis un argument de commande
     * (insensible à la casse, accepte "bleu" ou "blue").
     *
     * @return la couleur, ou null si inconnue.
     */
    public static TeamColor parse(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        for (TeamColor color : TeamColor.values()) {
            // Match sur le nom de l'enum (BLUE...) ou le nom français (BLEU...)
            if (color.name().equals(normalized)
                    || color.getDisplayName().toUpperCase().equals(normalized)) {
                return color;
            }
        }
        return null;
    }

    /** Liste des noms français, ex : "Bleu, Jaune, Rouge, Vert". */
    public static String displayNames() {
        StringBuilder sb = new StringBuilder();
        TeamColor[] values = TeamColor.values();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i].getColoredName());
            if (i < values.length - 1) {
                sb.append("§7, ");
            }
        }
        return sb.toString();
    }
}
