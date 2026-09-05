package com.mceteams.xii.manager;

import com.mceteams.xii.XiiPlugin;
import com.mceteams.xii.model.GameBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Traitement des REPÈRES (blocs de laine) posés manuellement par le
 * développeur dans l'empreinte d'une base, pour paramétrer le placement.
 *
 * PROTOCOLE REPÈRES (RÈGLE UTILISATEUR) :
 * - Laine ROUGE      : SURFACE de la base (sol) → après la pose de la
 *                      structure, ce bloc est remplacé par un grass_block.
 * - Laine BLEU CIEL  : marque un PORTILLON spruce à ouvrir à l'approche
 *                      d'un joueur de l'équipe (fermé pour les adversaires)
 *                      → le bloc est transformé en spruce_fence_gate et
 *                      enregistré dans le GateManager.
 * - Laine VERTE      : emplacement du PNJ "Upgrades d'équipe" (stoké par
 *                      NpcManager, config PNJ plus tard).
 * - Laine JAUNE      : emplacement du PNJ "Objets/items" (idem).
 *
 * NB : les repères sont appliqués APRÈS l'écriture de la structure,
 * sinon la pose des blocs du .nbt écraserait ces transformations.
 */
public class MarkerManager {

    /** Amplitude verticale scrutée AU-DESSUS del'ancre (repères au sol /
     *  portillons) ; l'ancre étant 9 blocs sous le sol, +25 couvre le
     *  haut de la structure et au-delà. */
    private static final int SCAN_ABOVE_ANCHOR = 25;

    private final XiiPlugin plugin;

    public MarkerManager(XiiPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Analyse l'empreinte d'une base et applique les repères trouvés.
     * Doit être appelé UNE FOIS la structure écrite (thread principal).
     *
     * @param color  équipe propriétaire de la base
     * @param anchor coin d'origine (bas) de l'empreinte 35x35 de la base
     */
    public void processBase(com.mceteams.xii.enums.TeamColor color,
                            Location anchor) {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int ax = anchor.getBlockX();
        int az = anchor.getBlockZ();
        int baseY = anchor.getBlockY();

        int grass = 0;
        int gates = 0;
        int npcUpgrade = 0;
        int npcItem = 0;
        int voidCleared = 0;

        // Boîte identique à l'empreinte contenue par GameBase (+1 de marge).
        for (int x = ax - 1; x <= ax + GameBase.SIZE; x++) {
            for (int z = az - 1; z <= az + GameBase.SIZE; z++) {
                for (int y = baseY; y <= baseY + SCAN_ABOVE_ANCHOR; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    switch (block.getType()) {
                        case RED_WOOL -> {
                            block.setType(Material.GRASS_BLOCK, false);
                            grass++;
                        }
                        case LIGHT_BLUE_WOOL -> {
                            block.setType(Material.SPRUCE_FENCE_GATE, false);
                            plugin.getGateManager().registerGate(color,
                                    block.getLocation());
                            gates++;
                        }
                        case GREEN_WOOL -> {
                            plugin.getNpcManager()
                                    .setUpgradeNpc(color, block.getLocation());
                            npcUpgrade++;
                        }
                        case YELLOW_WOOL -> {
                            plugin.getNpcManager()
                                    .setItemNpc(color, block.getLocation());
                            npcItem++;
                        }
                        // RÈGLE UTILISATEUR : les structure_void posés par la
                        // structure (voie du poseur d'API) ne doivent JAMAIS
                        // remplacer le monde. On les remet à AIR ici (dans le
                        // couloir devant la base, l'empreinte reste vide).
                        // C'est AUSSI le moment d'enregistrer cet emplacement :
                        // le ProtectionService INTERDIT d'y poser (l'espace
                        // vide du gabarit doit rester vide).
                        case STRUCTURE_VOID -> {
                            var base = plugin.getBaseManager().getBase(color);
                            if (base != null) {
                                base.addVoidSlot(block.getLocation());
                            }
                            block.setType(Material.AIR, false);
                            voidCleared++;
                        }
                        default -> {
                            // aucun repère
                        }
                    }
                }
            }
        }

        if (grass + gates + npcUpgrade + npcItem + voidCleared > 0) {
            plugin.getLogger().info("[Repères] " + color.getColoredName()
                    + " : " + grass + " sol(s) (→herbe), " + gates
                    + " portillon(s) (→spruce, ouverts), " + npcUpgrade
                    + " PNJ upgrade(s), " + npcItem + " PNJ objets, "
                    + voidCleared + " structure_void nettoyé(s).");
        }
    }
}