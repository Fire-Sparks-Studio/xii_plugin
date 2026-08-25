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
 * 2. il est copié dans "<monde principal>/generated/<namespace>/<nom>.nbt"
 *    (emplacement EXACT des templates vanilla : c'est là que les blocs
 *    structure sauvegardent en jeu - SANS sous-dossier "structures") ;
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
        // 1) Extraction depuis le jar vers le cache - TOUJOURS écrasée :
        // sans ça, une vieille version en cache masquerait définitivement
        // toute mise à jour du .nbt dans les ressources.
        File cached = new File(cacheDir, structureName + ".nbt");
        if (!extractFromJar(resourcePath, cached)) {
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
                // DIAGNOSTIC : où le fichier a-t-il réellement été écrit,
                // et existe-t-il toujours ? (le template manager vanilla
                // MET EN CACHE les échecs de lecture jusqu'au redémarrage :
                // un essai raté => tous les suivants ratent sans reboot)
                World mainWorld = Bukkit.getWorlds().isEmpty()
                        ? null : Bukkit.getWorlds().get(0);
                Path expected = mainWorld == null ? null
                        : mainWorld.getWorldFolder().toPath()
                                .resolve("generated")
                                .resolve(NAMESPACE)
                                .resolve(structureName.toLowerCase() + ".nbt");
                plugin.getLogger().severe("[Structures] loadStructure('" + key
                        + "') a renvoyé null.");
                plugin.getLogger().severe("[Structures] Chemin attendu : "
                        + (expected == null ? "?" : expected.toAbsolutePath()));
                plugin.getLogger().severe("[Structures] Fichier présent : "
                        + (expected != null && java.nio.file.Files.exists(expected)));
                plugin.getLogger().severe("[Structures] Si le fichier est "
                        + "présent mais non chargé : un essai antérieur de la "
                        + "MÊME session a mis l'échec en cache => REDÉMARREZ "
                        + "le serveur puis refaites /zone set.");
            } else {
                plugin.getLogger().info("[Structures] '" + structureName
                        + "' chargée depuis " + resourcePath);
            }
            return structure;
        } catch (Exception exception) {
            plugin.getLogger().severe("[Structures] Erreur lors du chargement de '"
                    + structureName + "' : " + exception.getMessage());
            exception.printStackTrace();
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
     * Installe le fichier dans le dossier "generated" du monde principal,
     * au format EXACT attendu par le StructureTemplateManager vanilla :
     * {@code <monde>/generated/<namespace>/<nom>.nbt} (identique à la
     * sauvegarde d'un bloc structure en jeu).
     */
    private boolean installToWorld(File source, String structureName) {
        World mainWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            plugin.getLogger().severe("[Structures] Aucun monde disponible.");
            return false;
        }
        Path targetDir = mainWorld.getWorldFolder().toPath()
                .resolve("generated")
                .resolve(NAMESPACE);
        try {
            Files.createDirectories(targetDir);
            // Toujours écraser : le développeur peut mettre à jour ses .nbt,
            // le cache doit suivre.
            Path target = targetDir.resolve(structureName.toLowerCase() + ".nbt");
            Files.copy(source.toPath(), target,
                    StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[Structures] Installée : "
                    + target.toAbsolutePath());
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("[Structures] Installation impossible ("
                    + structureName + ") : " + exception.getMessage());
            return false;
        }
    }
}
