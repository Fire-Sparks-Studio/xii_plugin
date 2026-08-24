package com.mceteams.xii.structure;

import com.mceteams.xii.XiiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Chargement des fichiers .nbt (spec §36).
 *
 * Méthode compatible Paper 26.2 :
 * 1. le fichier est extrait du jar vers un dossier de cache local ;
 * 2. il est copié dans "<monde principal>/generated/structures/
 *    <namespace>/<nom>.nbt" (emplacement standard des templates
 *    vanilla, utilisé par les blocs structure en jeu) ;
 * 3. il est chargé via l'API officielle :
 *    Bukkit.getStructureManager().loadStructure(NamespacedKey).
 *
 * On n'utilise aucune classe inventée : uniquement l'API réelle
 * org.bukkit.structure.Structure / StructureManager (disponible et
 * stable sur Paper).
 */
public class StructureLoader {

    /** Namespace utilisé pour enregistrer les structures du plugin. */
    public static final String NAMESPACE = "xii";

    private final XiiPlugin plugin;
    /** Dossier de cache local : plugins/XII-Days/cache/. */
    private final File cacheDir;

    public StructureLoader(XiiPlugin plugin) {
        this.plugin = plugin;
        this.cacheDir = new File(plugin.getDataFolder(), "cache");
    }

    /**
     * Charge une structure depuis les ressources du jar.
     *
     * @param resourcePath chemin interne, ex : "structures/bases/base_blue.nbt"
     * @param structureName nom d'enregistrement, ex : "base_blue"
     * @return la structure chargée, ou null si absente/invalide
     *         (les .nbt sont fournies par le développeur : une absence
     *         ne doit jamais faire planter le serveur).
     */
    public Structure load(String resourcePath, String structureName) {
        // 1) Extraction depuis le jar vers le cache si nécessaire.
        File cached = new File(cacheDir, structureName + ".nbt");
        if (!cached.exists() && !extractFromJar(resourcePath, cached)) {
            plugin.getLogger().warning("[Structures] Ressource introuvable : "
                    + resourcePath
                    + ". Placez le fichier .nbt dans src/main/resources/"
                    + resourcePath);
            return null;
        }

        // 2) Copie vers <monde>/generated/structures/xii/<nom>.nbt
        if (!installToWorld(cached, structureName)) {
            return null;
        }

        // 3) Chargement via l'API officielle.
        try {
            NamespacedKey key = new NamespacedKey(NAMESPACE, structureName.toLowerCase());
            Structure structure = Bukkit.getStructureManager().loadStructure(key);
            if (structure == null) {
                plugin.getLogger().warning("[Structures] Impossible de charger '"
                        + structureName + "' (fichier .nbt invalide ?).");
            }
            return structure;
        } catch (Exception exception) {
            plugin.getLogger().severe("[Structures] Erreur lors du chargement de '"
                    + structureName + "' : " + exception.getMessage());
            return null;
        }
    }

    /** Extrait la ressource du jar vers le fichier de cache. */
    private boolean extractFromJar(String resourcePath, File target) {
        InputStream input = plugin.getResource(resourcePath);
        if (input == null) {
            return false;
        }
        try {
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("[Structures] Extraction impossible ("
                    + resourcePath + ") : " + exception.getMessage());
            return false;
        } finally {
            try {
                input.close();
            } catch (Exception ignored) {
                // Rien à faire : fermeture best-effort.
            }
        }
    }

    /**
     * Installe le fichier dans le dossier "generated" du monde principal
     * pour que le StructureManager puisse le lire par NamespacedKey.
     */
    private boolean installToWorld(File source, String structureName) {
        World mainWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            plugin.getLogger().severe("[Structures] Aucun monde disponible.");
            return false;
        }
        Path targetDir = mainWorld.getWorldFolder().toPath()
                .resolve("generated")
                .resolve("structures")
                .resolve(NAMESPACE);
        try {
            Files.createDirectories(targetDir);
            // Toujours écraser : le développeur peut mettre à jour ses .nbt,
            // le cache doit suivre.
            Files.copy(source.toPath(),
                    targetDir.resolve(structureName.toLowerCase() + ".nbt"),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("[Structures] Installation impossible ("
                    + structureName + ") : " + exception.getMessage());
            return false;
        }
    }
}
