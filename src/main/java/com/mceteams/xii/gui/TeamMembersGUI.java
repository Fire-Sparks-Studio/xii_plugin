package com.mceteams.xii.gui;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.model.GameTeam;
import com.mceteams.xii.util.ItemUtil;
import com.mceteams.xii.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUI des MEMBRES d'une équipe (spec §5 : "les joueurs").
 *
 * DEUX VUES dans le même inventaire (bascule par bouton) :
 *
 * 1. VUE MEMBRES : têtes des membres.
 *    - clic sur une tête -> confirmation d'EXPULSION (kick) ;
 *    - bouton Ajouter -> bascule vers la vue d'ajout ;
 *    - bouton Options -> TeamOptionsGUI ; Retour -> TeamManagementGUI.
 *
 * 2. VUE AJOUT : tous les joueurs EN LIGNE qui ne sont pas déjà dans
 *    cette équipe ; un clic les ajoute immédiatement à l'équipe.
 */
public class TeamMembersGUI implements InventoryHolder {

    private final XiiPlugin plugin;
    private final Player player;
    private final TeamColor color;
    private Inventory inventory;

    /** true = vue d'ajout de joueurs, false = vue des membres. */
    private boolean addMode = false;

    public TeamMembersGUI(XiiPlugin plugin, Player player, TeamColor color) {
        this.plugin = plugin;
        this.player = player;
        this.color = color;
    }

    public void open() {
        this.inventory = Bukkit.createInventory(this, 54,
                color.getColoredName() + " §8- membres");
        rebuild();
        player.openInventory(inventory);
    }

    // -----------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------

    /** Reconstruit l'inventaire selon la vue courante. */
    private void rebuild() {
        inventory.clear();
        if (addMode) {
            buildAddView();
        } else {
            buildMembersView();
        }
    }

    /** Vue 1 : liste des membres + actions. */
    private void buildMembersView() {
        GameTeam team = plugin.getTeamManager().getTeam(color);
        if (team == null) {
            player.closeInventory();
            return;
        }
        int slot = 0;
        memberSlots.clear();
        for (var memberUuid : team.getPlayers()) {
            var offlinePlayer = Bukkit.getOfflinePlayer(memberUuid);
            String name = offlinePlayer.getName() != null
                    ? offlinePlayer.getName()
                    : memberUuid.toString().substring(0, 8);

            ItemStack head = ItemUtil.buildNamedItem(
                    Material.PLAYER_HEAD,
                    "§f" + name,
                    java.util.List.of(
                            "§7Vivant : " + (plugin.getPlayerManager()
                                    .getData(memberUuid).isAlive()
                                    ? "§aoui" : "§cnon"),
                            "",
                            "§cClique pour EXPULSER de l'équipe."));

            if (slot < 45) { // garde 3 rangées du bas pour les boutons
                inventory.setItem(slot, head);
                memberSlots.put(slot, memberUuid);
                slot++;
            }
        }

        inventory.setItem(48, ItemUtil.buildNamedItem(
                Material.COMPARATOR, "§bOptions",
                java.util.List.of("§7Taille, suppression...")));
        inventory.setItem(49, ItemUtil.buildNamedItem(
                Material.ARROW, "§7Retour", null));
        inventory.setItem(50, ItemUtil.buildNamedItem(
                Material.LIME_DYE, "§aAjouter un joueur",
                java.util.List.of("§7Ouvre la liste des joueurs en ligne.")));
    }

    /** Correspondance slot -> uuid membre (pour le kick). */
    private final java.util.Map<Integer, java.util.UUID> memberSlots =
            new java.util.HashMap<>();

    /** Vue 2 : joueurs en ligne ajoutables. */
    private void buildAddView() {
        int slot = 0;
        candidateSlots.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            var current = plugin.getTeamManager().getTeamOf(online.getUniqueId());
            // Déjà dans CETTE équipe => non proposé.
            if (current != null && current.getColor() == color) {
                continue;
            }
            if (slot >= 45) {
                break; // inventaire plein
            }
            String teamInfo = current != null
                    ? "§7Actuellement : " + current.getColor().getColoredName()
                    : "§7Sans équipe";
            ItemStack head = ItemUtil.buildNamedItem(
                    Material.PLAYER_HEAD,
                    "§f" + online.getName(),
                    java.util.List.of(teamInfo,
                            "",
                            "§aClique pour AJOUTER à l'équipe."));
            inventory.setItem(slot, head);
            candidateSlots.put(slot, online.getUniqueId());
            slot++;
        }
        inventory.setItem(49, ItemUtil.buildNamedItem(
                Material.ARROW, "§7Retour aux membres", null));
    }

    /** Correspondance slot -> uuid candidat (pour l'ajout). */
    private final java.util.Map<Integer, java.util.UUID> candidateSlots =
            new java.util.HashMap<>();

    // -----------------------------------------------------------------
    // Clics
    // -----------------------------------------------------------------

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player clicker)
                || !clicker.equals(player)) {
            return;
        }
        int slot = event.getSlot();

        if (addMode) {
            handleAddClick(slot);
        } else {
            handleMemberClick(slot);
        }
    }

    /** Clics en vue MEMBRES. */
    private void handleMemberClick(int slot) {
        // Kick d'un membre via sa tête.
        if (memberSlots.containsKey(slot)) {
            java.util.UUID targetUuid = memberSlots.get(slot);
            var offlinePlayer = Bukkit.getOfflinePlayer(targetUuid);
            String name = offlinePlayer.getName() != null
                    ? offlinePlayer.getName() : targetUuid.toString();

            Bukkit.getScheduler().runTask(plugin, () -> new ConfirmGUI(
                    plugin,
                    player,
                    "§cExpulser §f" + name + " §cde l'équipe "
                            + color.getColoredName() + "§c ?",
                    () -> {
                        boolean removed =
                                plugin.getTeamManager().removePlayer(targetUuid);
                        MessageUtil.send(player, removed
                                ? "§a✔ §f" + name + " §7expulsé de l'équipe."
                                : "§cJoueur introuvable dans l'équipe.");
                        Player target = Bukkit.getPlayer(targetUuid);
                        if (removed && target != null && target.isOnline()) {
                            MessageUtil.send(target,
                                    "§c✘ Vous avez été expulsé de votre équipe.");
                            plugin.getLobbyItemManager().giveLobbyItems(target);
                        }
                        open(); // rafraîchit la liste
                    },
                    this::open
            ).open());
            return;
        }

        switch (slot) {
            case 48 -> {
                var optionsGui = new TeamOptionsGUI(plugin, player, color);
                Bukkit.getScheduler().runTask(plugin, optionsGui::open);
            }
            case 49 -> {
                var managementGui = new TeamManagementGUI(plugin, player);
                Bukkit.getScheduler().runTask(plugin, managementGui::open);
            }
            case 50 -> {
                addMode = true;
                rebuild(); // simple changement de contenu, pas de réouverture
                player.updateInventory();
            }
            default -> { /* tête sans action / vide */ }
        }
    }

    /** Clics en vue AJOUT. */
    private void handleAddClick(int slot) {
        if (slot == 49) {
            addMode = false;
            rebuild();
            player.updateInventory();
            return;
        }
        java.util.UUID candidate = candidateSlots.get(slot);
        if (candidate == null) {
            return;
        }
        var result = plugin.getTeamManager().addPlayer(candidate, color);
        switch (result) {
            case OK -> {
                Player added = Bukkit.getPlayer(candidate);
                MessageUtil.send(player, "§a✔ §f"
                        + (added != null ? added.getName() : candidate)
                        + " §7rejoint l'équipe " + color.getColoredName());
                if (added != null && added.isOnline()) {
                    MessageUtil.send(added, "§7Vous rejoignez l'équipe "
                            + color.getColoredName());
                    // En attente : rafraîchit son sélecteur (laine couleur).
                    if (plugin.getGameManager().getState()
                            == com.mceteams.xii.enums.GameState.WAITING) {
                        plugin.getLobbyItemManager().giveLobbyItems(added);
                    } else if (plugin.getSpectatorService().isSpectator(candidate)) {
                        // Recruté en pleine partie : sort du mode spectateur
                        // et retourne au jeu comme les autres respawn.
                        plugin.getSpectatorService().exit(added);
                        var data = plugin.getPlayerManager().getData(candidate);
                        data.setEliminated(false);
                        data.setAlive(true);
                        var base = plugin.getBaseManager().getBase(color);
                        if (base != null) {
                            added.teleport(base.getSpawn());
                        }
                        plugin.getClassService().applyPassives(added, data);
                        MessageUtil.send(added, "§aVous réintégrez la partie !");
                    }
                }
                addMode = false;
                rebuild();
                player.updateInventory();
            }
            case FULL -> MessageUtil.send(player,
                    "§cÉquipe pleine ! Augmentez la taille dans Options.");
            default -> MessageUtil.send(player, "§cAjout impossible.");
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
