# Jeu de reconnaissance

Application Android pour apprendre à reconnaître des visages : une photo s'affiche,
on choisit le prénom puis le nom parmi des propositions.

Kotlin · Jetpack Compose · minSdk 26 · identifiant `fr.jeu.reconnaissance.visages` · nom affiché « Reconnaissance ».

## Fonctionnalités

- **Photo aléatoire** : prénom puis nom. Les propositions sont du même genre que la personne.
  On tire en priorité les personnes pas encore connues.
- **Difficulté** : 3 propositions au niveau 1, jusqu'à 7 au niveau 5.
  3 bonnes réponses d'affilée font monter d'un niveau, une erreur fait descendre d'un niveau.
  Une bonne réponse complète (prénom + nom) coche la personne comme « connue ».
- **Voir tout** : galerie avec progression (cases à cocher modifiables).
  Toucher une photo permet de la modifier ou de la supprimer.
- **Ajouter une personne** : photo de la galerie, prénom, nom, genre.
- **Importer ZIP** : remplace toute la liste par le contenu du ZIP et réinitialise la progression.
- **Exporter ZIP** : enregistre le pack (Drive, Téléchargements…) pour sauvegarde ou transfert.
- **Partager ZIP** : envoie le pack par une autre application (mail, messagerie…).

Toutes les données restent sur le téléphone ; aucun accès réseau n'est demandé.

## Format du ZIP

```
liste.json
photos/
  photo_01_Marie_Dupont.jpg
  photo_02_Eric_Martin.jpg
  …
```

`liste.json` :

```json
{
  "format": "jeu-reconnaissance",
  "version": 1,
  "personnes": [
    { "id": "01", "prenom": "Marie", "nom": "Dupont", "genre": "F",
      "photo": "photos/photo_01_Marie_Dupont.jpg" }
  ]
}
```

- `genre` : `F` ou `H` (accepte aussi `femme`, `homme`, `M`…). Vide = inconnu.
- `photo` : chemin relatif à `liste.json`. Si le fichier n'y est pas, il est cherché dans `photos/`.
- À l'import, un tableau seul (`[ {...}, ... ]`) est aussi accepté, ainsi que les clés
  `prénom`, `sexe`, `fichier`. Le ZIP peut contenir un dossier racine unique.
- Les personnes dont la photo est absente du ZIP sont ignorées (l'app indique combien).
- Formats d'image : jpg, jpeg, png, webp, gif, bmp, heic, heif.

## Compiler

Ouvrir le dossier dans Android Studio puis Run (▶), ou :

```
./gradlew testDebugUnitTest   # tests unitaires
./gradlew assembleDebug       # APK de debug
```

La CI GitHub (`.github/workflows/build.yml`) lance les tests et construit l'APK à chaque push.
La release est signée avec le keystore fourni par les secrets `RECONNAISSANCE_KEYSTORE_B64`,
`RECONNAISSANCE_KEYSTORE_PASSWORD`, `RECONNAISSANCE_KEY_ALIAS`, `RECONNAISSANCE_KEY_PASSWORD` ;
sans ces secrets, elle est signée avec la clé de debug (installable, mais pas publiable sur le Play Store).
