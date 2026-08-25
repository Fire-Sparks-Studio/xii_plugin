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
        File cached = ensureCached(resourcePath, structureName);
        if (cached == null) {
            plugin.getLogger().warning("[Structures] Ressource introuvable : "
                    + resourcePath
                    + ". Placez le fichier .nbt dans src/main/resources/"
                    + resourcePath);
            return null;
        }

        // 2) Installation dans TOUTES les racines candidates AVANT le
        // premier chargement (le template manager vanilla met en cache
        // ses échecs : on ne lui laisse qu'une seule chance de lire).
        boolean installed = installToWorld(cached, NAMESPACE, structureName)
                | installToWorld(cached, "minecraft", structureName);
        if (!installed) {
            return null;
        }

        // 3) Chargement via l'API officielle : namespace "xii" puis
        // "minecraft". NB : sur certaines versions de Paper, loadStructure
        // renvoie null même avec un fichier valide et présent - c'est le
        // comportement CONNU qui déclenche le poseur de secours manuel
        // (RawTemplatePlacer) : aucun bruit inutile ici.
        for (String namespace : new String[]{NAMESPACE, "minecraft"}) {
            try {
                NamespacedKey key = new NamespacedKey(namespace,
                        structureName.toLowerCase());
                Structure structure = Bukkit.getStructureManager()
                        .loadStructure(key);
                if (structure == null) {
                    // Dernier recours : déjà présente dans le registre ?
                    structure = Bukkit.getStructureManager().getStructure(key);
                }
                if (structure != null) {
                    plugin.getLogger().info("[Structures] '" + structureName
                            + "' chargée (" + key + ") depuis " + resourcePath);
                    return structure;
                }
            } catch (Throwable exception) {
                plugin.getLogger().severe("[Structures] Erreur de chargement ('"
                        + namespace + ":" + structureName + "') : " + exception);
                exception.printStackTrace();
            }
        }

        // 4) Échec API = chemin NORMAL sur cette version : retour null,
        // l'appelant bascule sur RawTemplatePlacer. Pas d'erreur affichée.
        return null;
    }

    /**
     * Réécrit la balise TAG_Int "DataVersion" du template avec la
     * DataVersion COURANTE du serveur (Bukkit.getUnsafe().getDataVersion()).
     *
     * Le .nbt d'un bloc structure se termine par cette balise ; le motif
     * binaire recherché est : 0x03 0x00 0x0B "DataVersion" + int BE.
     * Non bloquant en cas d'échec (best-effort).
     */
    private void patchDataVersion(File nbtFile) {
        try {
            int serverVersion = org.bukkit.Bukkit.getUnsafe().getDataVersion();

            // Décompression GZip.
            byte[] compressed = java.nio.file.Files.readAllBytes(nbtFile.toPath());
            java.io.ByteArrayInputStream bin =
                    new java.io.ByteArrayInputStream(compressed);
            byte[] data;
            try (var gzIn = new java.util.zip.GZIPInputStream(bin)) {
                data = gzIn.readAllBytes();
            }

            // Motif : TAG_Int + nom de longueur 11 + "DataVersion".
            byte[] pattern = new byte[14];
            pattern[0] = 0x03;
            pattern[1] = 0x00;
            pattern[2] = 0x0B;
            byte[] name = "DataVersion"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(name, 0, pattern, 3, name.length);

            int found = -1;
            for (int i = data.length - pattern.length - 4; i >= 0; i--) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                plugin.getLogger().warning("[Structures] DataVersion non "
                        + "trouvée dans le template (format inattendu ?).");
                return;
            }
            int offset = found + pattern.length;
            int current = ((data[offset] & 0xFF) << 24)
                    | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);
            if (current == serverVersion) {
                return; // déjà alignée
            }
            data[offset] = (byte) ((serverVersion >>> 24) & 0xFF);
            data[offset + 1] = (byte) ((serverVersion >>> 16) & 0xFF);
            data[offset + 2] = (byte) ((serverVersion >>> 8) & 0xFF);
            data[offset + 3] = (byte) (serverVersion & 0xFF);

            // Recompression GZip + écriture.
            java.io.ByteArrayOutputStream bout =
                    new java.io.ByteArrayOutputStream();
            try (var gzOut = new java.util.zip.GZIPOutputStream(bout)) {
                gzOut.write(data);
            }
            java.nio.file.Files.write(nbtFile.toPath(), bout.toByteArray());
            plugin.getLogger().info("[Structures] DataVersion alignée sur "
                    + "le serveur : " + current + " -> " + serverVersion);
        } catch (Throwable exception) {
            plugin.getLogger().warning("[Structures] Alignement DataVersion "
                    + "impossible : " + exception);
        }
    }

    /**
     * Garantit le fichier .nbt en cache local : extraction du jar
     * (toujours écrasée) + alignement DataVersion sur le serveur.
     *
     * @return le fichier de cache, ou null si la ressource est absente.
     */
    public File ensureCached(String resourcePath, String structureName) {
        File cached = new File(cacheDir, structureName + ".nbt");
        if (!extractFromJar(resourcePath, cached)) {
            return null;
        }
        // ALIGNE la DataVersion du template sur celle du SERVEUR :
        // un .nbt exporté depuis une version plus récente que le serveur
        // échoue SILENCIEUSEMENT au chargement (pas de rétrograde dans les
        // DataFixers vanilla). Le format des templates est stable, seule
        // cette balise compte.
        patchDataVersion(cached);
        return cached;
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
        } catch (Throwable exception) {
            plugin.getLogger().severe("[Structures] Extraction impossible ("
                    + resourcePath + ") : " + exception);
            exception.printStackTrace();
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
     * Installe le fichier dans le dossier "generated" d'une racine,
     * au format EXACT attendu par le StructureTemplateManager vanilla :
     * {@code <racine>/generated/<namespace>/<nom>.nbt} (identique à la
     * sauvegarde d'un bloc structure en jeu).
     */
    private boolean installToWorld(File source, String namespace,
                                   String structureName) {
        World mainWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            plugin.getLogger().severe("[Structures] Aucun monde disponible.");
            return false;
        }
        boolean allOk = true;
        for (Path root : generatedRoots(mainWorld)) {
            Path targetDir = root.resolve(namespace);
            try {
                Files.createDirectories(targetDir);
                // Toujours écraser : le développeur peut mettre à jour ses
                // .nbt, les copies doivent suivre.
                Path target = targetDir.resolve(
                        structureName.toLowerCase() + ".nbt");
                Files.copy(source.toPath(), target,
                        StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[Structures] Installée : "
                        + target.toAbsolutePath());
            } catch (Throwable exception) {
                plugin.getLogger().severe("[Structures] Installation impossible ("
                        + structureName + " dans " + root + ") : " + exception);
                exception.printStackTrace();
                allOk = false;
            }
        }
        return allOk;
    }

    /**
     * Racines "generated" candidates selon le layout du serveur :
     * - CLASSIQUE : {@code <monde>/generated} (dossier du monde overworld) ;
     * - NOUVEAU LAYOUT (dimensions séparées) : le template manager résout
     *   sa racine sur le DOSSIER DE STOCKAGE ({@code <server>/world}) alors
     *   que getWorldFolder() renvoie {@code world/dimensions/<ns>/<dim>} :
     *   on remonte donc jusqu'au parent du dossier "dimensions".
     */
    private java.util.List<Path> generatedRoots(World mainWorld) {
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
        if (mainWorld != null) {
            Path worldFolder = mainWorld.getWorldFolder().toPath();
            roots.add(worldFolder.resolve("generated"));
            // Détection du nouveau layout : .../dimensions/<ns>/<dim>
            Path cursor = worldFolder;
            for (int i = 0; i < 4 && cursor.getParent() != null; i++) {
                cursor = cursor.getParent();
                if ("dimensions".equalsIgnoreCase(
                        cursor.getFileName().toString())
                        && cursor.getParent() != null) {
                    roots.add(cursor.getParent().resolve("generated"));
                    break;
                }
            }
        }
        return new java.util.ArrayList<>(roots);
    }
}
