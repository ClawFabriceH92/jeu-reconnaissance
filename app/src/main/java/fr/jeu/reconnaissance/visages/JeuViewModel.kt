package fr.jeu.reconnaissance.visages

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jeu.reconnaissance.visages.core.Depot
import fr.jeu.reconnaissance.visages.core.Difficulte
import fr.jeu.reconnaissance.visages.core.Entrainement
import fr.jeu.reconnaissance.visages.core.Frequence
import fr.jeu.reconnaissance.visages.core.GenerateurQuestions
import fr.jeu.reconnaissance.visages.core.GenresPrenoms
import fr.jeu.reconnaissance.visages.core.Genre
import fr.jeu.reconnaissance.visages.core.PackInvalide
import fr.jeu.reconnaissance.visages.core.Parametres
import fr.jeu.reconnaissance.visages.core.Personne
import fr.jeu.reconnaissance.visages.core.Progression
import fr.jeu.reconnaissance.visages.core.Question
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

enum class Ecran { ACCUEIL, JEU, GALERIE, FICHE, PARAMETRES, GENRES }

enum class Etape { PRENOM, NOM, RESULTAT }

data class Partie(
    val question: Question,
    val etape: Etape,
    val prenomChoisi: String? = null,
    val nomChoisi: String? = null,
) {
    val reussi: Boolean
        get() = (prenomChoisi == null || prenomChoisi == question.personne.prenom) &&
            (nomChoisi == null || nomChoisi == question.personne.nom)
}

private class Refus(message: String) : Exception(message)

/** Portée indépendante de l'écran : la sauvegarde automatique continue si l'activité se ferme. */
private val porteeSauvegarde = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class JeuViewModel(app: Application) : AndroidViewModel(app) {
    private val depot = Depot(app.filesDir)
    private val generateur = GenerateurQuestions()
    /** Prénoms non mixtes d'après l'Insee (lu à la première utilisation, hors thread UI). */
    private val genres: GenresPrenoms by lazy {
        try {
            app.assets.open("prenoms_genre.tsv").bufferedReader().use { GenresPrenoms.lire(it.readText()) }
        } catch (e: Exception) {
            GenresPrenoms.VIDE
        }
    }

    var personnes by mutableStateOf<List<Personne>>(emptyList())
        private set
    var progression by mutableStateOf(Progression())
        private set
    var ecran by mutableStateOf(Ecran.ACCUEIL)
        private set
    var partie by mutableStateOf<Partie?>(null)
        private set
    /** Personne éditée sur l'écran FICHE (null = ajout). */
    var fiche by mutableStateOf<Personne?>(null)
        private set
    /** Libellé de l'opération longue en cours (import, export…). */
    var enCours by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var parametres by mutableStateOf(Parametres())
        private set
    var prochainRappel by mutableStateOf<LocalDateTime?>(null)
        private set
    var derniereSauvegarde by mutableStateOf(0L)
        private set

    /** Temps et réponses du jour, jours où l'objectif a été atteint. */
    var entrainement by mutableStateOf(Entrainement())
        private set
    /** Partie lancée en mode « Entraînement du jour » (chrono affiché). */
    var seance by mutableStateOf(false)
        private set
    /** L'objectif vient d'être atteint pendant la partie : afficher le bravo une fois. */
    var objectifAtteint by mutableStateOf(false)
    private var derniereAction = 0L
    private var ticsNonEnregistres = 0

    /** Données modifiées depuis la dernière sauvegarde automatique. */
    @Volatile
    private var modifie = false
    /** Ouverture depuis la notification : lancer le jeu dès que la liste est chargée. */
    private var jouerApresChargement = false

    init {
        travail(null) {
            if (depot.completerGenres(genres) > 0) modifie = true
            null
        }
    }

    fun ouvertureDepuisRappel() {
        if (personnes.size >= 2) commencerEntrainement() else jouerApresChargement = true
    }

    fun fichierPhoto(p: Personne): File = depot.fichierPhoto(p)

    fun nomFichierExport() = "jeu-reconnaissance-${LocalDate.now()}.zip"

    // --- Navigation ---

    fun ouvrirGalerie() {
        ecran = Ecran.GALERIE
    }

    fun ouvrirParametres() {
        ecran = Ecran.PARAMETRES
    }

    fun ouvrirGenres() {
        ecran = Ecran.GENRES
    }

    /** Indique le genre d'une personne (écran « Préciser le genre » ou pendant le jeu). */
    fun definirGenre(p: Personne, genre: Genre) {
        partie?.let { pa ->
            if (pa.question.personne.id == p.id) {
                partie = pa.copy(question = pa.question.copy(personne = pa.question.personne.copy(genre = genre)))
            }
        }
        personnes = personnes.map { if (it.id == p.id) it.copy(genre = genre) else it }
        travail(null) {
            depot.changerGenre(p.id, genre)
            modifie = true
            null
        }
    }

    fun ouvrirFiche(p: Personne?) {
        fiche = p
        ecran = Ecran.FICHE
    }

    fun retour() {
        if (ecran == Ecran.JEU) {
            enregistrerEntrainement()
            seance = false
        }
        ecran = if (ecran == Ecran.FICHE && fiche != null) Ecran.GALERIE else Ecran.ACCUEIL
    }

    // --- Jeu ---

    fun jouer() {
        seance = false
        ecran = Ecran.JEU
        nouvelleQuestion()
    }

    // --- Entraînement du jour ---

    fun commencerEntrainement() {
        seance = true
        ecran = Ecran.JEU
        nouvelleQuestion()
    }

    /** Secondes sans réponse au-delà desquelles le chrono se met en pause. */
    val pauseApres = 60

    fun enPause() = System.currentTimeMillis() - derniereAction > pauseApres * 1000L

    /** Appelé chaque seconde tant que l'écran de jeu est visible. Ne compte que le temps de jeu actif. */
    fun tic() {
        if (enPause()) return
        val aujourdhui = LocalDate.now()
        val avant = entrainement.faitLe(aujourdhui)
        entrainement = entrainement.ajouterTemps(aujourdhui, 1, parametres.minutesParJour)
        if (!avant && entrainement.faitLe(aujourdhui)) {
            objectifAtteint = true
            enregistrerEntrainement()
        } else if (++ticsNonEnregistres >= 15) {
            enregistrerEntrainement()
        }
    }

    fun enregistrerEntrainement() {
        ticsNonEnregistres = 0
        val e = entrainement
        modifie = true
        viewModelScope.launch(Dispatchers.IO) { depot.enregistrerEntrainement(e) }
    }

    fun activerRappelQuotidien() {
        changerParametres(parametres.copy(frequence = Frequence.QUOTIDIENNE))
    }

    fun nouvelleQuestion() {
        derniereAction = System.currentTimeMillis()
        val q = generateur.suivante(personnes, progression, partie?.question?.personne?.id)
        partie = q?.let { Partie(it, if (it.choixPrenoms.isNotEmpty()) Etape.PRENOM else Etape.NOM) }
    }

    fun repondre(choix: String) {
        val p = partie ?: return
        val suite = when (p.etape) {
            Etape.PRENOM -> p.copy(
                prenomChoisi = choix,
                etape = if (p.question.choixNoms.isNotEmpty()) Etape.NOM else Etape.RESULTAT,
            )
            Etape.NOM -> p.copy(nomChoisi = choix, etape = Etape.RESULTAT)
            Etape.RESULTAT -> return
        }
        partie = suite
        derniereAction = System.currentTimeMillis()
        if (suite.etape == Etape.RESULTAT) {
            entrainement = entrainement.ajouterReponse(LocalDate.now(), suite.reussi)
            enregistrer(Difficulte.apresReponse(progression, suite.question.personne.id, suite.reussi))
        }
    }

    fun basculerConnu(p: Personne) {
        val connus = if (p.id in progression.connus) progression.connus - p.id else progression.connus + p.id
        enregistrer(progression.copy(connus = connus))
    }

    private fun enregistrer(p: Progression) {
        progression = p
        modifie = true
        viewModelScope.launch(Dispatchers.IO) { depot.enregistrerProgression(p) }
    }

    // --- Réglages : rappel et sauvegarde ---

    fun changerParametres(p: Parametres) {
        parametres = p
        modifie = true
        viewModelScope.launch {
            prochainRappel = withContext(Dispatchers.IO) {
                depot.enregistrerParametres(p)
                Rappels.planifier(getApplication<Application>(), p)
            }
        }
    }

    fun sauvegarderMaintenant() = travail("Sauvegarde…") {
        if (!Sauvegarde.ecrire(getApplication<Application>(), depot)) {
            throw Refus("Sauvegarde impossible (liste vide ou stockage inaccessible)")
        }
        modifie = false
        "Sauvegarde enregistrée dans Téléchargements/${Sauvegarde.DOSSIER}"
    }

    /**
     * Sauvegarde d'abord, puis passe l'APK à l'installeur (comme notes-de-frais) :
     * une mise à jour est justement le moment où les données sont en jeu.
     */
    fun sauvegarderPuisInstaller(installer: () -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            enCours = "Sauvegarde avant mise à jour…"
            withContext(Dispatchers.IO) {
                try {
                    if (Sauvegarde.ecrire(app, depot)) modifie = false
                } catch (e: Exception) {
                    // L'installation reste possible ; la sauvegarde automatique précédente existe.
                }
            }
            derniereSauvegarde = Sauvegarde.derniere(app)
            enCours = null
            installer()
        }
    }

    /** Appelé quand l'app passe en arrière-plan. */
    fun sauvegardeAutomatique() {
        if (!modifie || !parametres.sauvegardeAuto || personnes.isEmpty()) return
        modifie = false
        val app = getApplication<Application>()
        porteeSauvegarde.launch {
            try {
                if (!Sauvegarde.ecrire(app, depot)) modifie = true
            } catch (e: Exception) {
                modifie = true
            }
        }
    }

    // --- Import / export ZIP ---

    fun importer(uri: Uri) = travail("Import du ZIP…") {
        val rapport = getApplication<Application>().contentResolver.openInputStream(uri)?.use { depot.importer(it) }
            ?: throw Refus("Impossible d'ouvrir le fichier")
        val genresTrouves = depot.completerGenres(genres)
        val aPreciser = depot.personnes().count { it.genre == Genre.INCONNU }
        buildString {
            append("${rapport.importees} personne(s) importée(s)")
            if (genresTrouves > 0) append(" · genre déduit du prénom pour $genresTrouves")
            if (aPreciser > 0) append(" · $aPreciser genre(s) à préciser")
            if (rapport.sansPhoto > 0) append(" · ${rapport.sansPhoto} ignorée(s) : photo absente du ZIP")
            if (rapport.progressionRestauree) append(" · progression et réglages restaurés")
            if (rapport.depuisPhotos) append(" · noms tirés des noms de fichiers : vérifiez-les dans « Voir tout »")
        }
    }

    fun exporter(uri: Uri) = travail("Export du ZIP…") {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { depot.exporter(it) }
            ?: throw Refus("Impossible d'écrire le fichier")
        "ZIP exporté (${personnes.size} personne(s))"
    }

    /** Prépare le ZIP dans le cache puis renvoie son Uri partageable. */
    fun preparerPartage(pret: (Uri) -> Unit) {
        var uri: Uri? = null
        travail("Préparation du ZIP…", apres = { uri?.let(pret) }) {
            val app = getApplication<Application>()
            val dossier = File(app.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
            val fichier = File(dossier, nomFichierExport())
            fichier.outputStream().use { depot.exporter(it) }
            uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", fichier)
            null
        }
    }

    // --- Ajout / modification ---

    fun enregistrerFiche(prenom: String, nom: String, genre: Genre, photo: Uri?) {
        val existante = fiche
        travail("Enregistrement…", apres = { ecran = if (existante != null) Ecran.GALERIE else Ecran.ACCUEIL }) {
            val app = getApplication<Application>()
            val ecrire: ((java.io.OutputStream) -> Unit)? = photo?.let { u -> { out: java.io.OutputStream -> Images.versJpeg(app, u, out) } }
            // Genre non choisi : on le déduit du prénom quand c'est possible.
            val g = if (genre == Genre.INCONNU) genres.genre(prenom) else genre
            if (existante == null) {
                depot.ajouter(prenom, nom, g, ecrire ?: throw Refus("Choisissez une photo"))
                "${listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ")} ajouté(e)"
            } else {
                depot.modifier(existante.copy(prenom = prenom, nom = nom, genre = g), ecrire)
                "Fiche enregistrée"
            }
        }
    }

    fun supprimer(p: Personne) = travail("Suppression…", apres = { ecran = Ecran.GALERIE }) {
        depot.supprimer(p.id)
        "${p.nomComplet} supprimé(e)"
    }

    /** Exécute [action] hors du thread UI, affiche son message, puis recharge la base. */
    private fun travail(libelle: String?, apres: () -> Unit = {}, action: () -> String?) {
        viewModelScope.launch {
            enCours = libelle
            var ok = false
            val resultat = withContext(Dispatchers.IO) {
                val m = try {
                    action().also { ok = true }
                } catch (e: PackInvalide) {
                    e.message
                } catch (e: Refus) {
                    e.message
                } catch (e: Exception) {
                    "Erreur : ${e.message ?: e.javaClass.simpleName}"
                }
                val etat = try {
                    val reglages = depot.parametres()
                    Etat(
                        depot.personnes(), depot.progression(), reglages,
                        Rappels.planifier(getApplication<Application>(), reglages), depot.entrainement(),
                    )
                } catch (e: Exception) {
                    null
                }
                Triple(m, etat, ok)
            }
            resultat.second?.let {
                personnes = it.personnes
                progression = it.progression
                parametres = it.parametres
                prochainRappel = it.prochainRappel
                // Pendant une partie, le compteur en mémoire est plus récent que le fichier.
                if (ecran != Ecran.JEU) entrainement = it.entrainement
            }
            derniereSauvegarde = Sauvegarde.derniere(getApplication<Application>())
            // Données présentes mais jamais sauvegardées (ex. première ouverture après mise à jour).
            if (derniereSauvegarde == 0L && personnes.isNotEmpty()) modifie = true
            enCours = null
            message = resultat.first
            if (resultat.third) {
                if (libelle != null && libelle != "Sauvegarde…") modifie = true
                apres()
            }
            if (jouerApresChargement && personnes.size >= 2) {
                jouerApresChargement = false
                commencerEntrainement()
            }
        }
    }
}

private data class Etat(
    val personnes: List<Personne>,
    val progression: Progression,
    val parametres: Parametres,
    val prochainRappel: LocalDateTime?,
    val entrainement: Entrainement,
)
