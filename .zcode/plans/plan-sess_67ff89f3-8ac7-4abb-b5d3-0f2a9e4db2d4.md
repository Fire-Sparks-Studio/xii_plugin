## Objectif
World "plaines douces" conforme à ta spec (aucune montagne, pas d'océan du tout — donc pas en 0,0,0 — biomes type plaine/désert/forêts/neigé, relief léger), + correction des erreurs serveur détectées. **Tu fais les push toi-même** : je te préviens quand tout est prêt localement.

## Étape 1 — Finaliser le datapack local (`xii-datapack/`)
- `noise_settings/xii:plaines` : le fichier actuel est déjà presque parfait (relief de collines douces y≈52→76 via gradient+jagged noise ±12 blocs, `sea_level=-63` + aquifers désactivés ⇒ **zéro océan/lac possible**, aucune spline donc **zéro montagne possible**). Je le garde et j'enrichis sa `surface_rule` avec des conditions par biome :
  - désert → sable en surface + sandstone dessous ;
  - snowy_plains / snowy_taiga → grass_block `snowy=true` ;
  - défaut → herbe + terre (comme actuellement).
- `world_preset/xii:plaines` (**le vrai bug : il ne référencait même pas xii:plaines !**) :
  - overworld → `settings: xii:plaines` + biome_source multi_noise explicite : `plains, sunflower_plains, forest, birch_forest, flower_forest, dark_forest, taiga, snowy_plains, snowy_taiga, desert, savanna` (aucun biome montagne/océan/rivière, points de paramètres couvrant tout ⇒ remplissage garanti par biomes terrestres uniquement) ;
  - rajout de la dimension **Nether** (vanilla) qui manquait + End conservé tel quel.

## Étape 2 — Vérifier/reconstruire les zips locaux
- Lister le contenu du `resource-pack.zip` local : s'assurer qu'il embarque bien le nouveau `pack.mcmeta` (format 88) et les 10 textures scoreboard. ⚠️ S'il n'y a aucun json de définition `font/` pour ces textures dans le pack, je te le signale avant d'aller plus loin (les pngs ne serviraient à rien sans provider).
- Reconstruire `xii-plaines.zip` avec TOUS les fichiers du dossier (mcmeta + preset + noise_settings + dimension).

## Étape 3 — 🟡 PAUSE : je te dis "prêt pour le push"
Tu commites et pushe sur `master` de `Fire-Sparks-Studio/xii_plugin` à ta convenance (resource-pack.zip, pack.mcmeta, datapack, et les modifs Java déjà revues ensemble).

## Étape 4 — Après ton push : sync serveur `\\ws-dylan\srv\HOST\minecraft\dev-xiidays`
- Quand tu me dis que c'est poussé : je télécharge le zip servi par l'URL GitHub, calcule son SHA-1 et corrige `server.properties` (`resource-pack-sha1=`, + remplissage de `resource-pack-prompt=` avec l'ancien message sympa).
- Dépôt du nouveau `xii-plaines.zip` dans `datapacks/`.

## Étape 5 — Régénération du monde (serveur arrêté)
Séquence à coordonner avec toi : ① tu arrêtes le serveur → ② je sauvegarde puis supprime `world`, `world_nether`, `world_the_end` → ③ démarrage par toi via `run.bat` → ④ je vérifie dans `logs/latest.log` que `xii-plaines` se charge sans erreur, puis contrôle visuel de ton côté (ex. `/tp 0 100 0`).

## Trade-off à connaître
Le générateur minimal choisi (garantie anti-montagne/anti-eau) ne produit ni grottes creusées ni rivières — les minerais normaux restent présents (features vanilla), seulement les grandes filons/veines sont absents. Si tu veux des grottes plus tard, on complexifiera le density function.