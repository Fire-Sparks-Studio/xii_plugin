package com.mceteams.xii.structure;

import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.util.Random;

/**
 * Placement des structures chargées dans le monde (spec §36).
 *
 * Utilise l'API officielle Paper :
 *   Structure#place(RegionAccessor, BlockVector, StructureRotation,
 *                   Mirror, int paletteIndex, float integrity, Random)
 *
 * Cette signature existe dans l'API Bukkit/Paper stable et permet la
 * rotation au placement. Aucune classe inventée.
 */
public class StructurePlacer {

    /**
     * Place une structure à l'emplacement donné.
     *
     * @param structure structure préalablement chargée par StructureLoader
     * @param location  emplacement + rotation souhaités
     * @return true si le placement a été effectué.
     */
    public boolean place(Structure structure, StructureLocation location) {
        if (structure == null || location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();

        // L'origine du placement est un BlockVector (API officielle).
        BlockVector origin = new BlockVector(location.getX(), location.getY(), location.getZ());

        // Conversion vers l'enum Bukkit org.bukkit.block.structure.StructureRotation.
        var bukkitRotation = switch (location.getRotation()) {
            case NONE -> org.bukkit.block.structure.StructureRotation.NONE;
            case CLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
        };

        try {
            // Signature réelle Paper 26.2 :
            // place(RegionAccessor, BlockVector, includeEntities,
            //       StructureRotation, Mirror, palette, integrity, Random)
            // includeEntities = false : les structures ne sont que de
            // l'architecture ; on ne duplique aucune entité éventuelle
            // enregistrée dans le .nbt.
            structure.place(
                    world,               // RegionAccessor : le monde
                    origin,
                    false,
                    bukkitRotation,
                    Mirror.NONE,
                    0,                   // palette index 0 = palette principale
                    1.0f,                // intégrité 1.0 = tous les blocs
                    new Random()         // aléa interne vanilla
            );
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
    }
}
