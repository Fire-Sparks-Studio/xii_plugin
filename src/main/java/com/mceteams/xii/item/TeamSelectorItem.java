package com.mceteams.xii.item;

import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Fabrique de l'item "Sélection d'équipe" (spec §5).
 *
 * Apparence :
 * - aucune équipe sélectionnée : BARRIER
 * - équipe sélectionnée : laine de la couleur (BLUE_WOOL etc.)
 *
 * L'item porte un identifiant PDC interne ("team_selector") pour être
 * reconnu SANS dépendre du matériau ni du nom affiché.
 */
public class TeamSelectorItem {

    /** Type interne enregistré dans le PDC. */
    public static final String INTERNAL_TYPE = "team_selector";

    /**
     * Construit l'item selon l'équipe actuellement sélectionnée.
     *
     * @param selectedColor équipe choisie, ou null si aucune.
     */
    public static ItemStack build(TeamColor selectedColor) {
        Material material = selectedColor == null
                ? Material.BARRIER
                : com.mceteams.xii.util.TeamUtil.woolOf(selectedColor);

        String name = selectedColor == null
                ? "§fSélection d'équipe"
                : "§fÉquipe : " + selectedColor.getColoredName();

        java.util.List<String> lore = java.util.List.of(
                "§7Clic droit pour ouvrir",
                "§7le menu des équipes.");

        // Tag PDC => identification fiable par InventoryListener/InteractionListener.
        return ItemUtil.tag(
                ItemUtil.buildNamedItem(material, name, lore),
                INTERNAL_TYPE);
    }
}
