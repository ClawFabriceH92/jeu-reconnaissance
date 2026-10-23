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
import fr.jeu.reconnaissance.visages.core.GenerateurQuestions
import fr.jeu.reconnaissance.visages.core.Genre
import fr.jeu.reconnaissance.visages.core.PackInvalide
import fr.jeu.reconnaissance.visages.core.Personne
import fr.jeu.reconnaissance.visages.core.Progression
import fr.jeu.reconnaissance.visages.core.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class Ecran { ACCUEIL, JEU, GALERIE, FICHE }

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

class JeuViewModel(app: Application) : AndroidViewModel(app) {
    private val depot = Depot(app.filesDir)
    private val generateur = GenerateurQuestions()

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

    init {
        travail(null) { null }
    }

    fun fichierPhoto(p: Personne): File = depot.fichierPhoto(p)

    fun nomFichierExport() = "jeu-reconnaissance-${LocalDate.now()}.zip"

    // --- Navigation ---

    fun ouvrirGalerie() {
        ecran = Ecran.GALERIE
    }

    fun ouvrirFiche(p: Personne?) {
        fiche = p
        ecran = Ecran.FICHE
    }

    fun retour() {
        ecran = if (ecran == Ecran.FICHE && fiche != null) Ecran.GALERIE else Ecran.ACCUEIL
    }

    // --- Jeu ---

    fun jouer() {
        ecran = Ecran.JEU
        nouvelleQuestion()
    }

    fun nouvelleQuestion() {
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
        if (suite.etape == Etape.RESULTAT) {
            enregistrer(Difficulte.apresReponse(progression, suite.question.personne.id, suite.reussi))
        }
    }

    fun basculerConnu(p: Personne) {
        val connus = if (p.id in progression.connus) progression.connus - p.id else progression.connus + p.id
        enregistrer(progression.copy(connus = connus))
    }

    private fun enregistrer(p: Progression) {
        progression = p
        viewModelScope.launch(Dispatchers.IO) { depot.enregistrerProgression(p) }
    }

    // --- Import / export ZIP ---

    fun importer(uri: Uri) = travail("Import du ZIP…") {
        val rapport = getApplication<Application>().contentResolver.openInputStream(uri)?.use { depot.importer(it) }
            ?: throw Refus("Impossible d'ouvrir le fichier")
        buildString {
            append("${rapport.importees} personne(s) importée(s)")
            if (rapport.sansPhoto > 0) append(" · ${rapport.sansPhoto} ignorée(s) : photo absente du ZIP")
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
            if (existante == null) {
                depot.ajouter(prenom, nom, genre, ecrire ?: throw Refus("Choisissez une photo"))
                "${listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ")} ajouté(e)"
            } else {
                depot.modifier(existante.copy(prenom = prenom, nom = nom, genre = genre), ecrire)
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
                    depot.personnes() to depot.progression()
                } catch (e: Exception) {
                    null
                }
                Triple(m, etat, ok)
            }
            resultat.second?.let { (l, p) ->
                personnes = l
                progression = p
            }
            enCours = null
            message = resultat.first
            if (resultat.third) apres()
        }
    }
}
