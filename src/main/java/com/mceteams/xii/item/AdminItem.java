package com.mceteams.xii.item;

import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Fabrique de l'item d'administration (spec §5) : TRIPWIRE.
 *
 * NOTE VERSION 26.2 : la spécification demande TRIPWIRE, mais depuis
 * les versions récentes ce matériau n'existe PLUS sous forme d'item
 * (bloc seul). On utilise donc STRING ("ficelle"), qui est l'objet
 * historique associé au fil déclencheur/tripwire, tout en conservant
 * l'identifiant PDC interne "admin" et le comportement attendu.
 *
 * Donné uniquement aux OPÉRATEURS pendant l'attente. Ouvre le GUI
 * d'administration (lancement, équipes, joueurs, paramètres).
 */
public class AdminItem {

    /** Type interne enregistré dans le PDC. */
    public static final String INTERNAL_TYPE = "admin";

    /**
     * Matériau résolu au chargement de la classe :
     * TRIPWIRE s'il redevient un item un jour, sinon STRING.
     */
    public static final Material MATERIAL =
            Material.TRIPWIRE.isItem() ? Material.TRIPWIRE : Material.STRING;

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
