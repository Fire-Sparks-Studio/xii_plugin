package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.ItemRarity;
import com.mceteams.xii.enums.LootTableId;
import com.mceteams.xii.enums.LootType;
import com.mceteams.xii.enums.PlayerUpgrade;
import com.mceteams.xii.manager.LootManager;
import com.mceteams.xii.model.LootEntry;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Génération EFFECTIVE du loot (spec : tirage pondéré -> quantité ->
 * construction de l'ItemStack). Aucune décision de phase ici : la
 * table est choisie par LootManager.
 *
 * Types gérés :
 * - RESOURCE  : matériau + quantité aléatoire [min..max] ;
 * - EQUIPMENT : variant enchanté raisonnablement (fer/diamant, arcs...) ;
 * - UPGRADE   : tirage parmi les upgrades de la rareté demandée ;
 * - TOTEM     : légendaire sans niveau ni limite globale, avec
 *               garantie progressive pilotée par LootManager.
 */
public class LootService {

    private final XiiPlugin plugin;
    private final LootManager lootManager;

    public LootService(XiiPlugin plugin, LootManager lootManager) {
        this.plugin = plugin;
        this.lootManager = lootManager;
    }

    /**
     * Génère le contenu complet d'une table : {@code rolls} tirages
     * pondérés, puis fusion des stacks RESOURCE identiques pour un
     * rendu propre dans le conteneur.
     */
    public List<ItemStack> generate(LootTableId tableId) {
        List<LootEntry> entries = lootManager.getEntries(tableId);
        List<ItemStack> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }

        for (int roll = 0; roll < tableId.getRolls(); roll++) {
            ItemStack stack = pickOne(entries, tableId);
            if (stack != null) {
                mergeOrAdd(result, stack);
            }
        }
        return result;
    }

    /** Fusionne les ressources identiques, sinon ajoute le stack. */
    private void mergeOrAdd(List<ItemStack> result, ItemStack stack) {
        if (stack.getType() != Material.PLAYER_HEAD) {
            // Ressources empilables : fusion avec un stack existant.
            for (ItemStack existing : result) {
                if (existing.getType() == stack.getType()
                        && existing.getAmount() + stack.getAmount()
                                <= existing.getMaxStackSize()) {
                    existing.setAmount(existing.getAmount() + stack.getAmount());
                    return;
                }
            }
        }
        result.add(stack);
    }

    /**
     * UN tirage pondéré :
     * 1. garantie Totem si la malchance a atteint le seuil ;
     * 2. sinon tirage classique avec poids du Totem amplifié
     *    progressivement ;
     * 3. comptabilisation de la dry-streak pour LootManager.
     */
    private ItemStack pickOne(List<LootEntry> entries, LootTableId tableId) {
        boolean hasTotem = entries.stream().anyMatch(e ->
                e.getType() == LootType.TOTEM);

        // Garantie absolue : fin de la fenêtre normale de loot.
        if (hasTotem && lootManager.mustGuaranteeTotem()) {
            LootEntry totemEntry = firstOf(entries, LootType.TOTEM);
            if (totemEntry != null) {
                lootManager.notifyTotemGenerated();
                return build(totemEntry);
            }
        }

        // Poids totaux (Totem amplifié par la malchance tant que rien).
        double multiplier = hasTotem
                ? lootManager.getTotemWeightMultiplier() : 1.0;
        double total = 0;
        for (LootEntry entry : entries) {
            total += effectiveWeight(entry, multiplier);
        }

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        LootEntry chosen = null;
        for (LootEntry entry : entries) {
            roll -= effectiveWeight(entry, multiplier);
            if (roll <= 0) {
                chosen = entry;
                break;
            }
        }
        if (chosen == null) {
            chosen = entries.get(entries.size() - 1);
        }

        // Comptabilisation malchance / succès Totem.
        if (hasTotem) {
            if (chosen.getType() == LootType.TOTEM) {
                lootManager.notifyTotemGenerated();
            } else {
                lootManager.notifyNonTotemRoll();
            }
        }

        return build(chosen);
    }

    /** Poids effectif : le Totem profite du multiplicateur de rampe. */
    private double effectiveWeight(LootEntry entry, double totemMultiplier) {
        if (entry.getType() == LootType.TOTEM && totemMultiplier > 1.0) {
            return entry.getWeight() * totemMultiplier;
        }
        return entry.getWeight();
    }

    private LootEntry firstOf(List<LootEntry> entries, LootType type) {
        for (LootEntry entry : entries) {
            if (entry.getType() == type) {
                return entry;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Construction concrète des récompenses
    // -----------------------------------------------------------------

    private ItemStack build(LootEntry entry) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        ItemStack item = switch (entry.getType()) {
            case RESOURCE -> new ItemStack(
                    entry.getMaterial(),
                    entry.getMin() + rng.nextInt(Math.max(1,
                            entry.getMax() - entry.getMin() + 1)));
            case EQUIPMENT -> buildEquipment(entry.getVariant());
            case UPGRADE -> buildRandomUpgrade(entry.getRarityFilter());
            case TOTEM -> plugin.getUpgradeService()
                    .createItem(PlayerUpgrade.TOTEM_RESURRECTION);
        };
        // Suivi de réclamation : tout objet rare/légendaire sorti du loot
        // est taggué "non réclamé" (l'annonce partira à sa RÉCUPÉRATION).
        return com.mceteams.xii.util.ItemUtil.markUnclaimedRare(item);
    }

    /** Tire une upgrade ALÉATOIRE parmi celles de la rareté demandée. */
    private ItemStack buildRandomUpgrade(ItemRarity rarityFilter) {
        List<PlayerUpgrade> candidates = new ArrayList<>();
        for (PlayerUpgrade upgrade : PlayerUpgrade.values()) {
            if (upgrade.getRarity() == rarityFilter) {
                candidates.add(upgrade);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(PlayerUpgrade.VITALITE);
        }
        PlayerUpgrade chosen = candidates.get(
                ThreadLocalRandom.current().nextInt(candidates.size()));
        return plugin.getUpgradeService().createItem(chosen);
    }

    // -----------------------------------------------------------------
    // Équipement enchanté (enchantements volontairement raisonnables)
    // -----------------------------------------------------------------

    private ItemStack buildEquipment(String variant) {
        Material material = switch (variant) {
            case "iron_sword" -> Material.IRON_SWORD;
            case "iron_pickaxe" -> Material.IRON_PICKAXE;
            case "bow" -> Material.BOW;
            case "iron_helmet" -> Material.IRON_HELMET;
            case "iron_chestplate" -> Material.IRON_CHESTPLATE;
            case "iron_leggings" -> Material.IRON_LEGGINGS;
            case "iron_boots" -> Material.IRON_BOOTS;
            case "diamond_sword" -> Material.DIAMOND_SWORD;
            case "diamond_pickaxe" -> Material.DIAMOND_PICKAXE;
            case "diamond_helmet" -> Material.DIAMOND_HELMET;
            case "diamond_chestplate" -> Material.DIAMOND_CHESTPLATE;
            case "diamond_leggings" -> Material.DIAMOND_LEGGINGS;
            case "diamond_boots" -> Material.DIAMOND_BOOTS;
            case "strong_iron_helmet" -> Material.IRON_HELMET;
            case "strong_iron_chestplate" -> Material.IRON_CHESTPLATE;
            case "strong_iron_leggings" -> Material.IRON_LEGGINGS;
            case "strong_iron_boots" -> Material.IRON_BOOTS;
            default -> Material.IRON_SWORD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            switch (variant) {
                case "iron_sword" -> {
                    meta.addEnchant(Enchantment.SHARPNESS, rand(1, 3), false);
                    meta.addEnchant(Enchantment.UNBREAKING, 2, false);
                }
                case "iron_pickaxe" -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, rand(2, 3), false);
                    meta.addEnchant(Enchantment.UNBREAKING, 2, false);
                    meta.addEnchant(Enchantment.FORTUNE, 1, false);
                }
                case "bow" -> {
                    meta.addEnchant(Enchantment.POWER, rand(2, 3), false);
                    meta.addEnchant(Enchantment.UNBREAKING, 2, false);
                }
                case "diamond_sword" -> {
                    meta.addEnchant(Enchantment.SHARPNESS, 3, false);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, false);
                    meta.addEnchant(Enchantment.LOOTING, 2, false);
                }
                case "diamond_pickaxe" -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, 4, false);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, false);
                    meta.addEnchant(Enchantment.FORTUNE, 2, false);
                }
                case "bow_strong" -> { /* réservé futur */ }
                default -> {
                    // Armures : fer simple / fer renforcé / diamant.
                    int protection = switch (variant) {
                        case "iron_helmet", "iron_chestplate",
                             "iron_leggings", "iron_boots" -> rand(1, 2);
                        case "strong_iron_helmet", "strong_iron_chestplate",
                             "strong_iron_leggings", "strong_iron_boots" -> 3;
                        default -> 2; // pièces diamant
                    };
                    meta.addEnchant(Enchantment.PROTECTION, protection, false);
                    meta.addEnchant(Enchantment.UNBREAKING,
                            variant.startsWith("strong") ? 3 : 2, false);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Entier aléatoire inclusif entre min et max. */
    private int rand(int min, int max) {
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }
}
