package fr.jeu.reconnaissance.visages.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.jeu.reconnaissance.visages.Ecran
import fr.jeu.reconnaissance.visages.Etape
import fr.jeu.reconnaissance.visages.Images
import fr.jeu.reconnaissance.visages.JeuViewModel
import androidx.core.content.ContextCompat
import fr.jeu.reconnaissance.visages.Sauvegarde
import fr.jeu.reconnaissance.visages.core.Difficulte
import fr.jeu.reconnaissance.visages.core.Frequence
import fr.jeu.reconnaissance.visages.core.Parametres
import fr.jeu.reconnaissance.visages.core.Genre
import fr.jeu.reconnaissance.visages.core.Personne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TYPES_ZIP = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-zip",
    "multipart/x-zip",
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppReconnaissance(vm: JeuViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = vm.ecran != Ecran.ACCUEIL) { vm.retour() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (vm.ecran) {
                            Ecran.ACCUEIL -> "Reconnaissance"
                            Ecran.JEU -> "Photo aléatoire"
                            Ecran.GALERIE -> "Voir tout"
                            Ecran.FICHE -> if (vm.fiche == null) "Ajouter une personne" else "Modifier"
                            Ecran.PARAMETRES -> "Rappels et sauvegarde"
                        }
                    )
                },
                actions = {
                    if (vm.ecran == Ecran.ACCUEIL) {
                        IconButton(onClick = vm::ouvrirParametres) {
                            Icon(Icons.Filled.Settings, contentDescription = "Rappels et sauvegarde")
                        }
                    }
                },
                navigationIcon = {
                    if (vm.ecran != Ecran.ACCUEIL) {
                        IconButton(onClick = vm::retour) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { marges ->
        Box(Modifier.padding(marges).fillMaxSize()) {
            when (vm.ecran) {
                Ecran.ACCUEIL -> Accueil(vm)
                Ecran.JEU -> EcranJeu(vm)
                Ecran.GALERIE -> Galerie(vm)
                Ecran.FICHE -> Fiche(vm)
                Ecran.PARAMETRES -> EcranParametres(vm)
            }
        }
    }

    vm.enCours?.let { libelle ->
        Dialog(onDismissRequest = {}) {
            Card {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(libelle)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Accueil

@Composable
private fun Accueil(vm: JeuViewModel) {
    val context = LocalContext.current
    var importEnAttente by remember { mutableStateOf<Uri?>(null) }

    val choisirZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (vm.personnes.isEmpty()) vm.importer(uri) else importEnAttente = uri
        }
    }
    val creerZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exporter(uri)
    }

    val total = vm.personnes.size
    val connus = vm.personnes.count { it.id in vm.progression.connus }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (total == 0) {
            Text(
                "La liste est vide.\nImportez un ZIP (photos seules, ou liste.json + photos/) ou ajoutez des personnes.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Pour restaurer une sauvegarde : Importer ZIP › Téléchargements › ${Sauvegarde.DOSSIER} › " +
                    Sauvegarde.NOM.substringBefore(".zip") + " (le plus récent).",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("$total personne(s) · $connus connue(s)", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { connus.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text(
                "Niveau ${vm.progression.niveau} / ${Difficulte.NIVEAU_MAX} · ${Difficulte.nbChoix(vm.progression.niveau)} propositions",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))

        BoutonAccueil("Photo aléatoire", Icons.Filled.Shuffle, actif = total >= 2, principal = true, onClick = vm::jouer)
        BoutonAccueil("Voir tout", Icons.Filled.GridView, actif = total > 0, onClick = vm::ouvrirGalerie)
        BoutonAccueil("Ajouter une personne", Icons.Filled.PersonAdd) { vm.ouvrirFiche(null) }
        BoutonAccueil("Rappels et sauvegarde", Icons.Filled.Settings, onClick = vm::ouvrirParametres)

        Spacer(Modifier.height(8.dp))
        Text("Échange de données", style = MaterialTheme.typography.titleSmall)
        BoutonAccueil("Importer ZIP", Icons.Filled.FileDownload) { choisirZip.launch(TYPES_ZIP) }
        BoutonAccueil("Exporter ZIP", Icons.Filled.FileUpload, actif = total > 0) { creerZip.launch(vm.nomFichierExport()) }
        BoutonAccueil("Partager ZIP", Icons.Filled.Share, actif = total > 0) {
            vm.preparerPartage { uri ->
                val envoi = Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(envoi, "Partager le ZIP"))
            }
        }
        Text(
            "Un ZIP de photos seules suffit : prénom et nom sont tirés des noms de fichiers " +
                "(ex. photo_01_Marie_Dupont.jpg). L'export contient aussi la liste, la progression et les réglages. " +
                "Importer remplace toute la liste actuelle.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    importEnAttente?.let { uri ->
        AlertDialog(
            onDismissRequest = { importEnAttente = null },
            title = { Text("Remplacer la liste ?") },
            text = {
                Text(
                    "Les ${vm.personnes.size} personne(s) actuelles seront remplacées par le contenu du ZIP. " +
                        "Si c'est une sauvegarde, la progression et les réglages sont repris ; sinon la progression repart de zéro."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    importEnAttente = null
                    vm.importer(uri)
                }) { Text("Importer") }
            },
            dismissButton = { TextButton(onClick = { importEnAttente = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun BoutonAccueil(
    texte: String,
    icone: ImageVector,
    actif: Boolean = true,
    principal: Boolean = false,
    onClick: () -> Unit,
) {
    val contenu: @Composable RowScope.() -> Unit = {
        Icon(icone, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Text(texte, Modifier.weight(1f))
    }
    val modifier = Modifier.fillMaxWidth().height(52.dp)
    if (principal) {
        Button(onClick = onClick, enabled = actif, modifier = modifier) { contenu() }
    } else {
        FilledTonalButton(onClick = onClick, enabled = actif, modifier = modifier) { contenu() }
    }
}

// ---------------------------------------------------------------- Jeu

@Composable
private fun EcranJeu(vm: JeuViewModel) {
    val partie = vm.partie
    if (partie == null) {
        Text("Ajoutez au moins deux personnes pour jouer.", Modifier.padding(20.dp))
        return
    }
    val q = partie.question
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Niveau ${vm.progression.niveau} / ${Difficulte.NIVEAU_MAX} · série ${vm.progression.serie}",
            style = MaterialTheme.typography.labelLarge,
        )
        Photo(
            vm.fichierPhoto(q.personne),
            Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)),
            taille = 900,
        )
        if (q.choixPrenoms.isNotEmpty()) {
            Choix("Prénom ?", q.choixPrenoms, q.personne.prenom, partie.prenomChoisi, vm::repondre)
        }
        if (partie.etape != Etape.PRENOM && q.choixNoms.isNotEmpty()) {
            Choix("Nom ?", q.choixNoms, q.personne.nom, partie.nomChoisi, vm::repondre)
        }
        if (partie.etape == Etape.RESULTAT) {
            Text(
                if (partie.reussi) "Bravo !" else "C'était ${q.personne.nomComplet}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (partie.reussi) Vert else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(onClick = vm::nouvelleQuestion, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Photo suivante")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choix(titre: String, options: List<String>, correct: String, choisi: String?, repondre: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titre, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val couleur = when {
                    choisi == null -> null
                    option == correct -> Vert
                    option == choisi -> MaterialTheme.colorScheme.error
                    else -> null
                }
                if (couleur != null) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = couleur, contentColor = MaterialTheme.colorScheme.onPrimary),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) { Text(option) }
                } else {
                    OutlinedButton(
                        onClick = { repondre(option) },
                        enabled = choisi == null,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) { Text(option) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Galerie

@Composable
private fun Galerie(vm: JeuViewModel) {
    val total = vm.personnes.size
    val connus = vm.personnes.count { it.id in vm.progression.connus }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Progression : $connus / $total", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else connus.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Touchez une photo pour corriger le nom, le genre, ou la supprimer.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(vm.personnes, key = { it.id }) { p ->
            Card(Modifier.clickable { vm.ouvrirFiche(p) }) {
                Photo(vm.fichierPhoto(p), Modifier.fillMaxWidth().aspectRatio(3f / 4f), taille = 450)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.id in vm.progression.connus, onCheckedChange = { vm.basculerConnu(p) })
                    Column(Modifier.padding(end = 8.dp)) {
                        Text(
                            p.nomComplet,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (p.genre == Genre.INCONNU) {
                            Text(
                                "Genre à préciser",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Fiche (ajout / modification)

@Composable
private fun Fiche(vm: JeuViewModel) {
    val existante = vm.fiche
    val context = LocalContext.current
    var prenom by rememberSaveable(existante?.id) { mutableStateOf(existante?.prenom.orEmpty()) }
    var nom by rememberSaveable(existante?.id) { mutableStateOf(existante?.nom.orEmpty()) }
    var genre by rememberSaveable(existante?.id) { mutableStateOf(existante?.genre ?: Genre.INCONNU) }
    var photo by rememberSaveable(existante?.id) { mutableStateOf<Uri?>(null) }
    var confirmerSuppression by remember { mutableStateOf(false) }

    val choisirPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = uri
    }

    val apercu by produceState<ImageBitmap?>(null, photo, existante?.photo) {
        value = withContext(Dispatchers.IO) {
            val p = photo
            when {
                p != null -> Images.chargerUri(context, p, 900)
                existante != null -> Images.charger(vm.fichierPhoto(existante), 900)
                else -> null
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    choisirPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            val image = apercu
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("Choisir une photo")
                }
            }
        }
        OutlinedTextField(prenom, { prenom = it }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nom, { nom = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Genre :")
            listOf(Genre.FEMME to "Femme", Genre.HOMME to "Homme").forEach { (g, libelle) ->
                RadioButton(selected = genre == g, onClick = { genre = g })
                Text(libelle, Modifier.clickable { genre = g }.padding(end = 12.dp))
            }
        }
        if (genre == Genre.INCONNU) {
            Text(
                "Sans genre, les propositions de prénoms mélangent hommes et femmes.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { vm.enregistrerFiche(prenom, nom, genre, photo) },
            enabled = (prenom.isNotBlank() || nom.isNotBlank()) && (photo != null || existante != null),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Enregistrer") }
        if (existante != null) {
            OutlinedButton(
                onClick = { confirmerSuppression = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Supprimer")
            }
        }
    }

    if (confirmerSuppression && existante != null) {
        AlertDialog(
            onDismissRequest = { confirmerSuppression = false },
            title = { Text("Supprimer ${existante.nomComplet} ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmerSuppression = false
                    vm.supprimer(existante)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text("Annuler") } },
        )
    }
}

// ---------------------------------------------------------------- Rappels et sauvegarde

private val FORMAT_RAPPEL = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRENCH)
private val FORMAT_SAUVEGARDE = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EcranParametres(vm: JeuViewModel) {
    val context = LocalContext.current
    val p = vm.parametres
    val permissionNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        if (!accordee) vm.message = "Notifications refusées : le rappel ne pourra pas s'afficher"
    }
    val changer = { n: Parametres ->
        if (n.frequence != Frequence.AUCUNE && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.changerParametres(n)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Rappel d'entraînement", style = MaterialTheme.typography.titleMedium)
        Frequence.entries.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clickable { changer(p.copy(frequence = f)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = p.frequence == f, onClick = { changer(p.copy(frequence = f)) })
                Text(f.libelle)
            }
        }
        if (p.frequence == Frequence.HEBDOMADAIRE) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { j ->
                    FilterChip(
                        selected = p.jour == j,
                        onClick = { changer(p.copy(jour = j)) },
                        label = { Text(j.getDisplayName(TextStyle.SHORT, Locale.FRENCH)) },
                    )
                }
            }
        }
        if (p.frequence != Frequence.AUCUNE) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> changer(p.copy(heure = h, minute = m)) }, p.heure, p.minute, true).show()
            }) {
                Text("Heure : %02d:%02d".format(p.heure, p.minute))
            }
            vm.prochainRappel?.let {
                Text("Prochain rappel : ${it.format(FORMAT_RAPPEL)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Le rappel peut arriver avec quelques minutes de retard si le téléphone est en veille. " +
                    "Toucher la notification lance directement une partie.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Sauvegarde", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sauvegarde automatique")
                Text(
                    "Liste, photos, progression et réglages, enregistrés dans " +
                        "Téléchargements/${Sauvegarde.DOSSIER}/${Sauvegarde.NOM} quand vous quittez l'app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = p.sauvegardeAuto, onCheckedChange = { vm.changerParametres(p.copy(sauvegardeAuto = it)) })
        }
        Text(
            if (vm.derniereSauvegarde == 0L) {
                "Aucune sauvegarde pour l'instant"
            } else {
                "Dernière sauvegarde : " +
                    Instant.ofEpochMilli(vm.derniereSauvegarde).atZone(ZoneId.systemDefault()).format(FORMAT_SAUVEGARDE)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = vm::sauvegarderMaintenant, enabled = vm.personnes.isNotEmpty()) {
            Text("Sauvegarder maintenant")
        }
        Text(
            "Si l'app doit être désinstallée puis réinstallée : « Importer ZIP » et choisir ce fichier " +
                "(Téléchargements › ${Sauvegarde.DOSSIER}). Tout est restauré. Après une réinstallation, " +
                "les nouvelles sauvegardes s'appellent « … (1).zip » : prenez la plus récente.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------- Photo

@Composable
private fun Photo(fichier: File, modifier: Modifier, taille: Int) {
    val image by produceState<ImageBitmap?>(null, fichier.path, fichier.lastModified(), taille) {
        value = withContext(Dispatchers.IO) { Images.charger(fichier, taille) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
