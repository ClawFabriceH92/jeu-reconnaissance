# Jeu de reconnaissance

Application Android pour apprendre à reconnaître des visages : une photo s'affiche,
on choisit le prénom puis le nom parmi des propositions.

Kotlin · Jetpack Compose · minSdk 26 · identifiant `fr.jeu.reconnaissance.visages` · nom affiché « Reconnaissance ».

## Fonctionnalités

- **Entraînement du jour** (10 min par défaut, 5 à 30 au choix) : partie avec chrono qui ne compte
  que le temps de jeu actif (pause après 60 s sans réponse ou app en arrière-plan). Objectif atteint →
  bravo + bilan, et série de jours consécutifs. Rappel quotidien par notification (un toucher sur la carte
  de l'accueil l'active) ; il ne s'affiche pas si l'entraînement du jour est déjà fait, et le toucher lance la séance.
- **Photo aléatoire (libre)** : prénom puis nom, sans chrono. Les prénoms proposés sont du même sexe que la personne
  (photo de femme → uniquement des prénoms de femmes de la liste). Pour une personne dont le genre
  n'est pas renseigné, les propositions sont mélangées : l'accueil propose « Préciser le genre ».
- **Genre déduit du prénom** : à l'import et à l'ajout, le genre est rempli automatiquement pour les prénoms
  non mixtes (≥ 95 % des naissances d'un même sexe, ≥ 20 naissances) d'après le
  [Fichier des prénoms de l'Insee](https://www.insee.fr/fr/statistiques/7633685) (naissances en France 1900-2022).
  Les prénoms mixtes (Camille, Dominique, Claude…) restent à préciser. La liste
  `app/src/main/assets/prenoms_genre.tsv` est générée par `scripts/generer_prenoms.py`
  (workflow « Générer la liste des prénoms »).
  On tire en priorité les personnes pas encore connues.
- **Difficulté** : 3 propositions au niveau 1, jusqu'à 7 au niveau 5.
  3 bonnes réponses d'affilée font monter d'un niveau, une erreur fait descendre d'un niveau.
  Une bonne réponse complète (prénom + nom) coche la personne comme « connue ».
- **Voir tout** : galerie avec progression (cases à cocher modifiables).
  Toucher une photo permet de la modifier ou de la supprimer.
- **Ajouter une personne** : photo de la galerie, prénom, nom, genre.
- **Importer ZIP** : remplace toute la liste par le contenu du ZIP. Si le ZIP est une sauvegarde
  (il contient `progression.json` / `parametres.json`), progression et réglages sont repris ;
  sinon la progression repart de zéro.
- **Exporter ZIP** : enregistre le pack complet (liste, photos, progression, réglages).
- **Sauvegarde automatique** : à chaque sortie de l'app (si quelque chose a changé), le pack complet est écrit dans
  `Téléchargements/Reconnaissance/reconnaissance-sauvegarde.zip`. Ce dossier survit à une désinstallation :
  pour tout récupérer, « Importer ZIP » puis choisir ce fichier.
- **Rappels d'entraînement** (⚙ en haut de l'accueil) : désactivé, tous les jours, du lundi au vendredi
  ou une fois par semaine (jour au choix), à l'heure choisie. Toucher la notification lance une partie.
- **Partager ZIP** : envoie le pack par une autre application (mail, messagerie…).

Les données restent sur le téléphone. Le seul accès réseau sert à la mise à jour.

## Mise à jour automatique

Même système que [notes-de-frais](https://github.com/ClawFabriceH92/notes-de-frais) :

- À l'ouverture (puis toutes les 30 min, au plus une requête par heure), l'app lit
  `version.json` sur la release `latest` (repli : API GitHub). Le dépôt doit rester **public**.
- Nouvelle version : en Wi-Fi elle est téléchargée d'avance, sinon bouton « Télécharger ».
  « Installer » fait d'abord une sauvegarde complète, puis lance l'installeur Android.
- Réglages › « Vérifier les mises à jour » pour forcer la vérification.
- La CI publie `app-release.apk`, `reconnaissance.apk` et `version.json` sur la release `latest`.

**Clé de signature** : Android n'installe une mise à jour par-dessus (en gardant les données) que si
l'APK est signé avec la même clé. Lancer une fois `scripts/push-secrets.sh` (crée la clé dans
`~/.secrets/keystores-android/` et publie les secrets `RECONNAISSANCE_*`). Sans ces secrets, chaque build
a une clé jetable : l'app détecte la différence et explique la marche à suivre (désinstaller, installer,
restaurer la sauvegarde).

## Format du ZIP

**ZIP de photos seules** : accepté. Une personne est créée par image (tous dossiers confondus) ;
prénom et nom sont tirés du nom du fichier (`photo_01_Marie_Dupont.jpg` → Marie / Dupont,
`Jean-Pierre Martin.jpg` → Jean-Pierre / Martin). Le genre est à préciser dans « Voir tout ».

**ZIP complet** (créé par « Exporter ZIP ») :

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
`RECONNAISSANCE_KEYSTORE_PASSWORD`, `RECONNAISSANCE_KEY_ALIAS`, `RECONNAISSANCE_KEY_PASSWORD`
(publiés par `scripts/push-secrets.sh`) ;
sans ces secrets, elle est signée avec la clé de debug du runner, qui change à chaque build :
il faut alors désinstaller l'app avant d'installer un nouvel APK (et l'APK n'est pas publiable sur le Play Store).

APK le plus récent : https://github.com/ClawFabriceH92/jeu-reconnaissance/releases/download/latest/reconnaissance.apk
(publié par un lancement manuel du workflow, ou par un push sur la branche principale).
