package com.mceteams.xii.util;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.ItemRarity;
import com.mceteams.xii.enums.PlayerUpgrade;
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

    /**
     * Clé PDC secondaire : "xii:item_data". Stocke des métadonnées
     * additionnelles, ex : "upgrade:vitalite" pour les items upgrade.
     */
    private static NamespacedKey itemDataKey;

    /**
     * Clé PDC "xii:rare_claimed" (BYTE) : présente uniquement sur les
     * objets RARES/LÉGENDAIRES générés par le loot. Valeur :
     * 0 = pas encore réclamé, 1 = réclamé par un joueur.
     */
    private static NamespacedKey rareClaimKey;

    /** Type interne pour les items UPGRADE consommables. */
    public static final String TYPE_UPGRADE = "upgrade_item";

    private ItemUtil() {
        // Classe utilitaire : pas d'instance.
    }

    /**
     * Initialise les clés PDC (nécessite l'instance du plugin).
     * Appelé une seule fois par XiiPlugin au démarrage.
     */
    public static void init(XiiPlugin plugin) {
        itemTypeKey = new NamespacedKey(plugin, "item_type");
        itemDataKey = new NamespacedKey(plugin, "item_data");
        rareClaimKey = new NamespacedKey(plugin, "rare_claimed");
    }

    /**
     * Attache des métadonnées arbitraires à un item (ex : la clé
     * d'upgrade). Retourne l'item pour le chaînage.
     */
    public static ItemStack setItemData(ItemStack item, String data) {
        if (item == null || itemDataKey == null || data == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer()
                    .set(itemDataKey, PersistentDataType.STRING, data);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * @return les métadonnées attachées à cet item, ou null.
     */
    public static String getItemData(ItemStack item) {
        if (item == null || !item.hasItemMeta() || itemDataKey == null) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(itemDataKey, PersistentDataType.STRING);
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
     * Construit une TÊTE DE JOUEUR avec le visage réel du joueur.
     * - joueur EN LIGNE : son profil courant (textures garanties) ;
     * - hors ligne : texture mémorisée au dernier passage en ligne, ou
     *   fetch session (best effort) si inconnue.
     * NB : la texture est CACHÉE par UUID à la première rencontre en ligne :
     * les têtes restent donc exactes même après déconnexion (et même sur un
     * serveur offline-mode, où le fetch session ne fonctionne pas).
     */
    public static ItemStack buildPlayerHead(java.util.UUID playerUuid,
                                            String name,
                                            List<String> lore) {
        ItemStack item = buildNamedItem(Material.PLAYER_HEAD, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
            var profile = resolveHeadProfile(playerUuid, name);
            if (profile != null) {
                skullMeta.setPlayerProfile(profile);
            }
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    /**
     * Cache des textures de peau par UUID (propriété "textures").
     */
    private static final java.util.concurrent.ConcurrentHashMap<
            java.util.UUID, com.destroystokyo.paper.profile.ProfileProperty>
            HEAD_TEXTURES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @return la propriété "textures" d'un profil Paper, ou null.
     */
    private static com.destroystokyo.paper.profile.ProfileProperty findTexture(
            com.destroystokyo.paper.profile.PlayerProfile profile) {
        for (com.destroystokyo.paper.profile.ProfileProperty property :
                profile.getProperties()) {
            if (property.getName().equals("textures")) {
                return property;
            }
        }
        return null;
    }

    /**
     * Profil "tête" pour un UUID :
     * 1. joueur EN LIGNE : profil courant, on en mémorise les textures ;
     * 2. HORS LIGNE avec texture mémorisée : on la rejoue directement ;
     * 3. HORS LIGNE sans mémoire : fetch session Mojang (best effort).
     */
    private static com.destroystokyo.paper.profile.PlayerProfile resolveHeadProfile(
            java.util.UUID playerUuid, String name) {
        var online = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (online != null) {
            com.destroystokyo.paper.profile.PlayerProfile live =
                    online.getPlayerProfile();
            com.destroystokyo.paper.profile.ProfileProperty tex = findTexture(live);
            if (tex != null) {
                HEAD_TEXTURES.put(playerUuid, tex);
            }
            return live;
        }

        com.destroystokyo.paper.profile.PlayerProfile profile;
        try {
            profile = (name == null || name.isBlank())
                    ? org.bukkit.Bukkit.createProfile(playerUuid)
                    : org.bukkit.Bukkit.createProfile(playerUuid, name);
        } catch (IllegalArgumentException e) {
            return null; // UUID illégal : tête sans profil (Steve par défaut).
        }

        com.destroystokyo.paper.profile.ProfileProperty cached =
                HEAD_TEXTURES.get(playerUuid);
        if (cached != null && cached.getValue() != null) {
            profile.setProperty(cached);
            return profile;
        }
        try {
            profile.complete(true); // fetch textures (session Mojang)
            com.destroystokyo.paper.profile.ProfileProperty tex = findTexture(profile);
            if (tex != null) {
                HEAD_TEXTURES.put(playerUuid, tex);
            }
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // Profil incomplet (serveur offline-mode, textures indisponibles) :
            // on garde le profil vide -> tête "Steve" côté client.
        }
        return profile;
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

    // -----------------------------------------------------------------
    // Objets RARES / LÉGENDAIRES (loot de colis)
    // -----------------------------------------------------------------

    public static final String TYPE_RARE = "rare_item";
    public static final String TYPE_LEGENDARY = "legendary_item";

    /**
     * L'item est-il un objet RARE ou LÉGENDAIRE ? Ces objets sont DROPpés
     * à la mort du porteur (le reste de l'inventaire est conservé).
     *
     * Deux sources :
     * - tag direct TYPE_RARE / TYPE_LEGENDARY ;
     * - items UPGRADE dont la rareté (via la clé) est Rare/Épique/Légendaire
     *   (les upgrades COMMUNES restent sur le joueur à sa mort).
     */
    public static boolean isRareOrLegendary(ItemStack item) {
        String type = getInternalType(item);
        if (TYPE_RARE.equals(type) || TYPE_LEGENDARY.equals(type)) {
            return true;
        }
        return isUpgradeItem(item) && upgradeRarity(item) != null;
    }

    /**
     * L'item est-il un objet d'UPGRADE consommable (via le PDC "upgrade:") ?
     * Couvre les 12 upgrades graduées ET le Totem de Résurrection
     * (TOTEM_RESURRECTION), quelle que soit leur rareté.
     */
    public static boolean isUpgradeItem(ItemStack item) {
        String data = getItemData(item);
        return data != null && data.startsWith("upgrade:");
    }

    /**
     * L'upgrade portée par un item (si "upgrade:<clé>"), sinon null.
     */
    public static PlayerUpgrade upgradeOf(ItemStack item) {
        String data = getItemData(item);
        if (data != null && data.startsWith("upgrade:")) {
            return PlayerUpgrade.fromKey(data.substring("upgrade:".length()));
        }
        return null;
    }

    /** Rareté d'une upgrade portée par l'item, sinon null. */
    private static ItemRarity upgradeRarity(ItemStack item) {
        return upgradeOf(item) != null ? upgradeOf(item).getRarity() : null;
    }

    // -----------------------------------------------------------------
    // Suivi de RÉCLAMATION des objets rares (PDC rare_claimed : 0/1)
    // -----------------------------------------------------------------

    /**
     * Rareté de l'item si celui-ci est identifiable (tag direct ou
     * upgrade), sinon null. Sert à distinguer l'annonce RARE vs
     * LÉGENDAIRE à la récupération.
     */
    public static com.mceteams.xii.enums.ItemRarity rarityOf(ItemStack item) {
        String type = getInternalType(item);
        if (TYPE_LEGENDARY.equals(type)) {
            return com.mceteams.xii.enums.ItemRarity.LEGENDARY;
        }
        if (TYPE_RARE.equals(type)) {
            return com.mceteams.xii.enums.ItemRarity.RARE;
        }
        String data = getItemData(item);
        if (data != null && data.startsWith("upgrade:")) {
            var upgrade = com.mceteams.xii.enums.PlayerUpgrade
                    .fromKey(data.substring("upgrade:".length()));
            return upgrade != null ? upgrade.getRarity() : null;
        }
        return null;
    }

    /**
     * Marque un objet RARE/LÉGENDAIRE comme "pas encore réclamé"
     * (rare_claimed = 0). No-op si l'item n'est pas rare.
     */
    public static ItemStack markUnclaimedRare(ItemStack item) {
        if (item == null || !isRareOrLegendary(item)) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer()
                    .set(rareClaimKey, PersistentDataType.BYTE, (byte) 0);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Cet item est-il un objet rare généré par le loot PAS ENCORE
     * réclamé par un joueur ?
     */
    public static boolean isUnclaimedRare(ItemStack item) {
        if (item == null || !item.hasItemMeta() || rareClaimKey == null) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer()
                .get(rareClaimKey, PersistentDataType.BYTE);
        return value != null && value == 0;
    }

    /** Enregistre la réclamation d'un objet rare (rare_claimed = 1). */
    public static void markClaimed(ItemStack item) {
        if (item == null || !item.hasItemMeta() || rareClaimKey == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer()
                    .set(rareClaimKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
    }
}
