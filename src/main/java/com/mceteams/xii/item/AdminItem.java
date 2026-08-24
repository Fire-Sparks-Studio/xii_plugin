package com.mceteams.xii.item;

import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Fabrique de l'item d'administration (spec §5) : TRIPWIRE.
 *
 * Donné uniquement aux OPÉRATEURS pendant l'attente. Ouvre le GUI
 * d'administration (lancement, équipes, joueurs, paramètres).
 * Identifiant PDC interne : "admin".
 */
public class AdminItem {

    /** Type interne enregistré dans le PDC. */
    public static final String INTERNAL_TYPE = "admin";

    /** Matériau officiel de l'item admin (spec §5). */
    public static final Material MATERIAL = Material.TRIPWIRE;

    /** Construit l'item d'administration. */
    public static ItemStack build() {
        return ItemUtil.tag(ItemUtil.buildNamedItem(
                        MATERIAL,
                        "§cAdministration",
                        List.of("§7Clic droit pour ouvrir",
                                "§7le menu d'administration.")),
                INTERNAL_TYPE);
    }
}
