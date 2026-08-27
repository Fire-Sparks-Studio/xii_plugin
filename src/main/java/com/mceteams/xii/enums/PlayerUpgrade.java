package com.mceteams.xii.enums;

import org.bukkit.Material;

/**
 * Les 13 objets du jeu : 12 UPGRADES consommables (un item = un
 * niveau, jusqu'au max) + 1 LÉGENDAIRE unique sans niveau.
 *
 * Chaque entrée porte ses données d'affichage et ses limites ; les
 * EFFETS sont appliqués par les services :
 * - Vitalité/Agilité/Endurance/Frappe rapide -> ClassService.applyPassives
 * - Puissance/Saignement        -> CombatService
 * - Résistance/Pas léger/Garde  -> CombatListener
 * - Hâte/Aimant                 -> UpgradeService.tickSecond
 * - Prospecteur                 -> MiningService
 * - Totem de Résurrection       -> UpgradeService.handleUse (+ RespawnManager)
 */
public enum PlayerUpgrade {

    VITALITE("vitalite", "Vitalité", ItemRarity.COMMON, 3,
            Material.RED_DYE,
            "§7+2 PV par niveau"),
    PUISSANCE("puissance", "Puissance", ItemRarity.COMMON, 3,
            Material.ORANGE_DYE,
            "§7+5% de dégâts par niveau"),
    AGILITE("agilite", "Agilité", ItemRarity.COMMON, 3,
            Material.SUGAR,
            "§7+5% de vitesse par niveau"),
    RESISTANCE("resistance", "Résistance", ItemRarity.RARE, 3,
            Material.SHIELD,
            "§7-5% de dégâts reçus par niveau"),
    PAS_LEGER("pas_leger", "Pas léger", ItemRarity.COMMON, 3,
            Material.FEATHER,
            "§7-25% de dégâts de chute par niveau"),
    HATE("hate", "Hâte", ItemRarity.RARE, 3,
            Material.YELLOW_DYE,
            "§7Haste supplémentaire par niveau",
            "§8(fonctionne sur tous les blocs)"),
    SAIGNEMENT("saignement", "Saignement", ItemRarity.RARE, 1,
            Material.REDSTONE,
            "§7Chance d'appliquer un saignement",
            "§7lors d'une attaque"),
    FRAPPE_RAPIDE("frappe_rapide", "Frappe rapide", ItemRarity.EPIC, 3,
            Material.LIGHT_BLUE_DYE,
            "§7+10% de vitesse d'attaque",
            "§7par niveau"),
    GARDE("garde", "Garde", ItemRarity.EPIC, 3,
            Material.PRISMARINE_CRYSTALS,
            "§7Résistance temporaire après",
            "§7avoir reçu un coup"),
    PROSPECTEUR("prospecteur", "Prospecteur", ItemRarity.RARE, 3,
            Material.DIAMOND,
            "§7Augmente les chances de ressources rares",
            "§8(n'affecte pas les légendaires)"),
    ENDURANCE("endurance", "Endurance", ItemRarity.COMMON, 3,
            Material.GLOWSTONE_DUST,
            "§7Réduit la consommation liée au sprint",
            "§8(adapté : faim gelée => bonus vitesse)"),
    AIMANT("aimant", "Aimant", ItemRarity.RARE, 3,
            Material.IRON_INGOT,
            "§7Rayon d'attraction des objets : §f3/5/8"),

    TOTEM_RESURRECTION("totem_resurrection", "Totem Rez", ItemRarity.LEGENDARY, 1,
            Material.TOTEM_OF_UNDYING,
            "§7Permet la résurrection d'un coéquipier",
            "§7en attente de retour de jeu"); // <-- point-virgule final pour le dernier enum

    /** Clé technique (PDC, commandes). */
    private final String key;
    /** Nom français. */
    private final String displayName;
    /** Rareté de l'OBJET (le niveau n'y change rien). */
    private final ItemRarity rarity;
    /** Niveau maximum atteignable en consommant des exemplaires. */
    private final int maxLevel;
    /** Icône de repli si aucune texture de tête custom n'est configurée. */
    private final Material icon;
    /** Lignes de description (effets). */
    private final String[] lore;

    PlayerUpgrade(String key, String displayName, ItemRarity rarity,
                  int maxLevel, Material icon, String... lore) {
        this.key = key;
        this.displayName = displayName;
        this.rarity = rarity;
        this.maxLevel = maxLevel;
        this.icon = icon;
        this.lore = lore;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemRarity getRarity() {
        return rarity;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Material getIcon() {
        return icon;
    }

    public String[] getLore() {
        return lore;
    }

    /**
     * Recherche par clé technique (insensible à la casse).
     *
     * @return l'upgrade, ou null si inconnue.
     */
    public static PlayerUpgrade fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (PlayerUpgrade upgrade : values()) {
            if (upgrade.key.equalsIgnoreCase(key)) {
                return upgrade;
            }
        }
        return null;
    }

    /** Chiffres romains pour l'affichage des niveaux (1->I, 2->II...). */
    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
}