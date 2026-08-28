package com.mceteams.xii.service;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.ItemRarity;
import com.mceteams.xii.enums.PlayerUpgrade;
import com.mceteams.xii.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des ITEMS d'UPGRADE et du TOTEM DE RESURRECTION.
 *
 * PRINCIPE : un item = une upgrade ; le joueur le CONSOMME (clic droit)
 * pour monter son niveau personnel (ex : 3 x Vitalité => Vitalité III),
 * plafonné au maximum de l'upgrade. Les niveaux vivent dans PlayerData,
 * l'ITEM ne porte que sa clé (même tête quel que soit le niveau).
 *
 * APPARENCE : chaque upgrade est une icône MARÉRIEL VANILLA distincte
 * (PlayerUpgrade.getIcon()), avec un CustomModelData qui sélectionne sa
 * texture personnalisée dans le resource pack (assets/minecraft/items).
 * L'item reste consommable (pas de slot dédié).
 *
 * EFFETS (répartis dans les services spécialisés) :
 * cf. documentation de l'enum PlayerUpgrade.
 *
 * NB : ce service est créé hors de l'arborescence initiale de la spec -
 * justification : nouvelle mécanique d'items sans fichier dédié existant.
 */
public class UpgradeService {

    private final XiiPlugin plugin;

    public UpgradeService(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------
    // Création des items
    // -----------------------------------------------------------------

    /**
     * Construit l'item physique d'une upgrade :
     * - nom coloré selon la RARETÉ de l'objet ;
     * - lore = description + rappel d'usage ;
     * - composant ITEM_MODEL -> item definition du resource pack
     *   (assets/xii/items/upgrades/<clé>.json) : texture custom garantie,
     *   rendu vanilla de repli (icône du matériau) sans resource pack ;
     * - PDC "item_data=upgrade:<clé>".
     */
    public ItemStack createItem(PlayerUpgrade type) {
        // Matériau vanilla de repli (sans resource pack) : icône dédiée.
        Material material = type.getIcon();

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            ItemRarity rarity = type.getRarity();
            meta.setDisplayName(rarity.getColorCode() + type.getDisplayName());

            // Texture custom : l'item definition du pack remplace
            // intégralement le rendu de l'item (upgrades ET totem).
            meta.setItemModel(new org.bukkit.NamespacedKey(
                    "xii", "upgrades/" + type.getKey()));

            List<String> lore = new ArrayList<>();
            lore.add("§8" + rarity.getColoredName());
            lore.add("");
            for (String line : type.getLore()) {
                lore.add(line);
            }
            if (type != PlayerUpgrade.TOTEM_RESURRECTION) {
                lore.add("");
                lore.add("§7Niveau max : §e"
                        + PlayerUpgrade.roman(type.getMaxLevel()));
                lore.add("§aClic droit pour consommer.");
            } else {
                lore.add("");
                lore.add("§aClic droit pour ressusciter");
                lore.add("§aun coéquipier mort (jour 7+).");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        // Identification : clé d'upgrade dans les données PDC.
        // NB : PAS de tag "item_type" => l'item reste LIBREMENT
        // déplaçable dans l'inventaire (ce n'est pas un item spécial).
        com.mceteams.xii.util.ItemUtil.setItemData(item,
                "upgrade:" + type.getKey());
        return item;
    }

    /** Donne un exemplaire d'une upgrade à un joueur. */
    public void give(Player player, PlayerUpgrade type) {
        player.getInventory().addItem(createItem(type));
        player.updateInventory();
    }

    // -----------------------------------------------------------------
    // Consommation (clic droit)
    // -----------------------------------------------------------------

    /**
     * Utilisation d'un item upgrade par clic droit.
     *
     * @param player joueur consommateur
     * @param held   l'item en main (sera décrémenté si consommé)
     * @param key    clé technique de l'upgrade
     */
    public void handleUse(Player player, ItemStack held, String key) {
        PlayerUpgrade type = PlayerUpgrade.fromKey(key);
        if (type == null || held == null) {
            return;
        }

        // --- TOTEM DE RESURRECTION : cas particulier --------------------
        if (type == PlayerUpgrade.TOTEM_RESURRECTION) {
            boolean consumed = useTotem(player);
            if (consumed) {
                consumeOne(held);
            }
            return;
        }

        // --- UPGRADES à niveaux ------------------------------------------
        var data = plugin.getPlayerManager().getData(player);
        int current = data.getUpgradeLevel(type);

        if (current >= type.getMaxLevel()) {
            com.mceteams.xii.util.MessageUtil.send(player,
                    "§c" + type.getDisplayName()
                            + " est déjà au niveau maximum (§e"
                            + PlayerUpgrade.roman(current) + "§c).");
            return; // PAS de consommation au max
        }

        int newLevel = data.setUpgradeLevel(type, current + 1);
        consumeOne(held);

        com.mceteams.xii.util.MessageUtil.send(player,
                rarityColor(type) + type.getDisplayName()
                        + " §7→ niveau §e" + PlayerUpgrade.roman(newLevel));
        com.mceteams.xii.util.SoundUtil.play(player,
                Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);

        // Ré-application immédiate des passifs (PV/Agilité/Endurance...).
        plugin.getClassService().applyPassives(player, data);
    }

    /** Décrémente l'item consommé (disparaît s'il était unique). */
    private void consumeOne(ItemStack held) {
        if (held.getAmount() <= 1) {
            held.setAmount(0);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    /** Couleur de rareté d'une upgrade (pour les messages). */
    private String rarityColor(PlayerUpgrade type) {
        return type.getRarity().getColorCode() + type.getDisplayName();
    }

    // -----------------------------------------------------------------
    // Totem de Résurrection
    // -----------------------------------------------------------------

    /**
     * Utilise un Totem : ressuscite UN coéquipier MORT.
     *
     * CONDITIONS (règles utilisateur, simplifiées) :
     * 1. journée >= 7 (fin de la phase de préparation -> combat) ;
     * 2. un coéquipier est mort (data.isAlive() == false), qu'il soit en
     *    attente de respawn, au pool "totem" (coeur détruit) ou hors lig
     *    - c'est TOUT - peu importe le mode de mort / le délai restant.
     *
     * @return true si le totem doit être consommé.
     */
    private boolean useTotem(Player user) {
        var team = plugin.getTeamManager().getTeamOf(user.getUniqueId());
        if (team == null) {
            com.mceteams.xii.util.MessageUtil.send(user,
                    "§cVous devez avoir une équipe pour utiliser le totem.");
            return false;
        }

        // Condition d'usage : à partir du jour 7 uniquement.
        if (plugin.getPhaseManager().currentDay() < 7) {
            com.mceteams.xii.util.MessageUtil.send(user,
                    "§cLe totem de résurrection n'est utilisable qu'à partir "
                            + "du §ejour 7§c.");
            return false;
        }

        // Premier coéquipier SANS VIE (mort). Déconnecté et mort, séparé
        // ou en attente : ressuscitable dans tous les cas.
        UUID targetUuid = null;
        for (UUID member : team.getPlayers()) {
            PlayerData data = plugin.getPlayerManager().getData(member);
            if (data != null && !data.isAlive()) {
                targetUuid = member;
                break;
            }
        }

        if (targetUuid == null) {
            com.mceteams.xii.util.MessageUtil.send(user,
                    "§cAucun coéquipier mort à ressusciter actuellement.");
            return false;
        }

        boolean revived = plugin.getRespawnManager().reviveByTotem(targetUuid);
        if (!revived) {
            return false;
        }

        var offlineTarget = Bukkit.getOfflinePlayer(targetUuid);
        String name = offlineTarget.getName() != null
                ? offlineTarget.getName() : "?";
        MessageUtilWrapper.broadcast(" §d★ §f" + user.getName()
                + " §7utilise un §d§lTOTEM DE RÉSURRECTION §7sur §f" + name + "§7 !");
        SoundUtilWrapper.broadcastDragonGrowl(user);
        return true;
    }

    /** Petits wrappers pour éviter des imports verbeux. */
    private static final class MessageUtilWrapper {
        static void broadcast(String message) {
            com.mceteams.xii.util.MessageUtil.broadcast(message);
        }
    }

    private static final class SoundUtilWrapper {
        static void broadcastDragonGrowl(Player around) {
            com.mceteams.xii.util.SoundUtil.play(around,
                    Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    // -----------------------------------------------------------------
    // Effets passifs par seconde (Hâte, Aimant)
    // -----------------------------------------------------------------

    /**
     * Appelé chaque seconde par l'horloge de jeu :
     * - HÂTE      : effet Haste permanent (rafraîchi), tous blocs ;
     * - AIMANT    : attire les items au sol dans le rayon du niveau.
     */
    public void tickSecond() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var data = plugin.getPlayerManager().getData(player);

            // --- Hâte ---------------------------------------------------
            int hasteLevel = data.getUpgradeLevel(PlayerUpgrade.HATE);
            if (hasteLevel > 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE,
                        60,                       // rafraîchi chaque seconde
                        hasteLevel - 1,           // Haste I/II/III
                        true,                     // ambient
                        false));                  // sans particules
            }

            // --- Aimant ---------------------------------------------------
            int magnetLevel = data.getUpgradeLevel(PlayerUpgrade.AIMANT);
            if (magnetLevel > 0) {
                double radius = switch (magnetLevel) {
                    case 1 -> 3.0;
                    case 2 -> 5.0;
                    default -> 8.0;           // niveau III
                };
                var location = player.getLocation();
                for (var entity : location.getWorld()
                        .getNearbyEntities(location, radius, radius, radius)) {
                    if (!(entity instanceof org.bukkit.entity.Item itemEntity)) {
                        continue;
                    }
                    if (itemEntity.getPickupDelay() > 0
                            && itemEntity.getPickupDelay() != Short.MAX_VALUE) {
                        continue; // vient d'être jeté volontairement
                    }
                    var pull = player.getLocation().toVector()
                            .subtract(entity.getLocation().toVector());
                    if (pull.lengthSquared() < 0.3) {
                        continue; // déjà sur le joueur
                    }
                    itemEntity.setVelocity(pull.normalize().multiply(0.5));
                }
            }
        }
    }
}
