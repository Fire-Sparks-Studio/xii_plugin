package com.mceteams.xii.ui;

import com.mceteams.xii.enums.Lang;
import com.mceteams.xii.enums.TeamColor;
import com.mceteams.xii.manager.GameManager;
import com.mceteams.xii.model.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public class TeamOptionsGUI {
    private final GameManager gameManager;

    public TeamOptionsGUI(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    public Inventory create(GameTeam team) {
        TeamColor c = team.getColor();
        Inventory inv = Bukkit.createInventory(null, 27,
                "§6§l" + c.getName(Lang.FR));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.create(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // Row 1: Heart & elimination
        inv.setItem(0, ItemBuilder.create(
                Material.RED_DYE,
                "§c§lDestroy Heart",
                "§7Détruire le cœur de l'équipe"
        ));

        inv.setItem(1, ItemBuilder.create(
                Material.GREEN_DYE,
                "§a§lRestore Heart",
                "§7Restaurer le cœur de l'équipe"
        ));

        inv.setItem(2, ItemBuilder.create(
                Material.BONE,
                "§4§lEliminate",
                "§7Éliminer toute l'équipe"
        ));

        inv.setItem(3, ItemBuilder.create(
                Material.TOTEM_OF_UNDYING,
                "§6§lRevive",
                "§7Réanimer l'équipe"
        ));

        // Row 1 continued: Location
        inv.setItem(5, ItemBuilder.create(
                Material.ENDER_PEARL,
                "§b§lTP Base",
                "§7Téléporter l'équipe à sa base"
        ));

        inv.setItem(6, ItemBuilder.create(
                Material.BEDROCK,
                "§e§lSet Spawn",
                "§7Définir le spawn de l'équipe"
        ));

        inv.setItem(7, ItemBuilder.create(
                Material.REDSTONE_BLOCK,
                "§4§lSet Heart",
                "§7Définir le cœur de l'équipe"
        ));

        inv.setItem(8, ItemBuilder.create(
                Material.PAPER,
                "§f§lMax Members",
                "§7Limite actuelle: §e" + team.getMaxPlayers(),
                "§7Clic pour changer"
        ));

        // Row 2: Player management
        inv.setItem(10, ItemBuilder.create(
                Material.LIME_WOOL,
                "§a§l+ Ajouter un joueur",
                "§7Clic pour sélectionner"
        ));

        inv.setItem(11, ItemBuilder.create(
                Material.RED_WOOL,
                "§c§l- Retirer un joueur",
                "§7Clic pour sélectionner"
        ));

        inv.setItem(15, ItemBuilder.create(
                Material.BARRIER,
                "§4§lSupprimer l'équipe",
                "§7Supprimer définitivement l'équipe"
        ));

        inv.setItem(22, ItemBuilder.create(
                Material.ARROW,
                "§c§lRetour"
        ));

        return inv;
    }
}
