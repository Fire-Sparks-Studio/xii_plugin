package com.mceteams.xii.util;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Utilitaire items : création d'items nommés et identification des
 * items spéciaux via un PDC (PersistentDataContainer).
 *
 * Spec §5 : les items de lobby doivent être reconnaissables par un
 * identifiant interne, SANS dépendre du matériau ni du nom affiché.
 */
public final class ItemUtil {

    /**
     * Clé PDC unique : "xii:item_type". La valeur est une chaîne
     * ("team_selector", "admin", "spectator_compass"...).
     */
    private static NamespacedKey itemTypeKey;

    private ItemUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Initialise la clé PDC (nécessite l'instance du plugin).
     * Appelé une seule fois par XiiPlugin au démarrage.
     */
    public static void init(XiiPlugin plugin) {
        itemTypeKey = new NamespacedKey(plugin, "item_type");
    }

    /** Clé interne ou null si non initialisée. */
    public static NamespacedKey getItemTypeKey() {
        return itemTypeKey;
    }

    /**
     * Construit un item avec nom et lore (lignes déjà colorées).
     */
    public static ItemStack buildNamedItem(Material material,
                                           String name,
                                           List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Marque un item avec un type interne (PDC). Ex : tag(item,"team_selector").
     */
    public static ItemStack tag(ItemStack item, String internalType) {
        if (item == null || itemTypeKey == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer()
                    .set(itemTypeKey, PersistentDataType.STRING, internalType);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * @return le type interne de l'item ("team_selector", "admin"...)
     * ou null si ce n'est pas un item spécial.
     */
    public static String getInternalType(ItemStack item) {
        if (item == null || !item.hasItemMeta() || itemTypeKey == null) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(itemTypeKey, PersistentDataType.STRING);
    }

    /**
     * L'item possède-t-il exactement ce type interne ?
     */
    public static boolean isType(ItemStack item, String internalType) {
        return internalType != null
                && internalType.equals(getInternalType(item));
    }

    /**
     * Cet item est-il un item spécial du plugin (quel que soit son type) ?
     * Utilisé par InventoryListener pour interdire tout déplacement/drop.
     */
    public static boolean isSpecialItem(ItemStack item) {
        return getInternalType(item) != null;
    }
}
