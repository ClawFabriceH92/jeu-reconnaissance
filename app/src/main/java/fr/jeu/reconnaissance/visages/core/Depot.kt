package fr.jeu.reconnaissance.visages.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer

/**
 * [depuisPhotos] : ZIP sans liste.json, noms déduits des noms de fichiers.
 * [progressionRestauree] : le ZIP était une sauvegarde complète (progression et réglages repris).
 */
data class RapportImport(
    val importees: Int,
    val sansPhoto: Int,
    val depuisPhotos: Boolean = false,
    val progressionRestauree: Boolean = false,
)

/**
 * Stockage local sur l'appareil :
 *   [racine]/base/liste.json
 *   [racine]/base/photos/…
 *   [racine]/progression.json
 *   [racine]/parametres.json
 *   [racine]/entrainement.json
 */
class Depot(private val racine: File) {
    val dossierBase = File(racine, "base")
    private val fichierListe = File(dossierBase, ZipPack.LISTE)
    private val dossierPhotos = File(dossierBase, ZipPack.PHOTOS)
    private val fichierProgression = File(racine, ZipPack.PROGRESSION)
    private val fichierParametres = File(racine, ZipPack.PARAMETRES)
    private val fichierEntrainement = File(racine, ZipPack.ENTRAINEMENT)

    fun fichierPhoto(p: Personne) = File(dossierBase, p.photo)

    @Synchronized
    fun personnes(): List<Personne> =
        if (fichierListe.isFile) ListeJson.lire(fichierListe.readText()) else emptyList()

    /**
     * Remplace toute la base par le contenu du ZIP. La progression et les réglages sont repris
     * s'ils sont dans le ZIP (sauvegarde), sinon la progression est réinitialisée.
     */
    @Synchronized
    fun importer(entree: InputStream): RapportImport {
        val tmp = File(racine, "import_tmp")
        val nouvelle = File(racine, "base_import")
        val zip = File(racine, "import.zip")
        tmp.deleteRecursively()
        nouvelle.deleteRecursively()
        try {
            zip.outputStream().use { entree.copyTo(it) }
            val extrait = ZipPack.extraire(zip, tmp)
            val avecListe = ZipPack.trouverListe(extrait)
            val dossierListe = avecListe ?: extrait
            val lues = if (avecListe != null) {
                ListeJson.lire(File(avecListe, ZipPack.LISTE).readText())
            } else {
                ZipPack.personnesDepuisPhotos(extrait).ifEmpty {
                    throw PackInvalide("Ce fichier ne contient ni liste.json ni photos (ZIP valide ?)")
                }
            }
            val photos = File(nouvelle, ZipPack.PHOTOS).apply { mkdirs() }
            val noms = mutableSetOf<String>()
            val gardees = mutableListOf<Personne>()
            var sansPhoto = 0
            for (p in lues) {
                val source = ZipPack.photoDans(dossierListe, tmp, p.photo)
                if (source == null) {
                    sansPhoto++
                    continue
                }
                val nom = nomPhoto(p, source.extension, noms)
                source.copyTo(File(photos, nom))
                gardees += p.copy(photo = "${ZipPack.PHOTOS}/$nom")
            }
            if (gardees.isEmpty()) {
                throw PackInvalide(
                    if (lues.isEmpty()) "liste.json ne contient aucune personne"
                    else "Aucune photo de liste.json n'a été trouvée dans le ZIP"
                )
            }
            File(nouvelle, ZipPack.LISTE).writeText(ListeJson.ecrire(gardees))

            dossierBase.deleteRecursively()
            if (!nouvelle.renameTo(dossierBase)) nouvelle.copyRecursively(dossierBase, overwrite = true)
            val ids = gardees.map { it.id }.toSet()
            val progression = avecListe?.let { File(it, ZipPack.PROGRESSION) }?.takeIf { it.isFile }
                ?.let { progressionDepuisJson(it.readText()) }
            enregistrerProgression(progression?.let { it.copy(connus = it.connus intersect ids) } ?: Progression())
            avecListe?.let { File(it, ZipPack.PARAMETRES) }?.takeIf { it.isFile }
                ?.let { enregistrerParametres(Parametres.depuisJson(it.readText())) }
            avecListe?.let { File(it, ZipPack.ENTRAINEMENT) }?.takeIf { it.isFile }
                ?.let { enregistrerEntrainement(Entrainement.depuisJson(it.readText())) }
            return RapportImport(
                gardees.size,
                sansPhoto,
                depuisPhotos = avecListe == null,
                progressionRestauree = progression != null,
            )
        } finally {
            zip.delete()
            tmp.deleteRecursively()
            nouvelle.deleteRecursively()
        }
    }

    @Synchronized
    fun exporter(sortie: OutputStream) {
        val annexes = mapOf(
            ZipPack.PROGRESSION to progressionVersJson(progression()),
            ZipPack.PARAMETRES to parametres().versJson(),
            ZipPack.ENTRAINEMENT to entrainement().versJson(),
        )
        ZipPack.ecrire(personnes(), dossierBase, sortie, annexes)
    }

    /** Ajoute une personne ; [ecrirePhoto] écrit l'image JPEG. */
    @Synchronized
    fun ajouter(prenom: String, nom: String, genre: Genre, ecrirePhoto: (OutputStream) -> Unit): Personne {
        val liste = personnes()
        val ids = liste.map { it.id }.toSet()
        var numero = (liste.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1
        while (numero.toString().padStart(2, '0') in ids) numero++
        val brouillon = Personne(numero.toString().padStart(2, '0'), prenom.trim(), nom.trim(), genre, "")
        val fichier = nomPhoto(brouillon, "jpg", nomsPhotos(liste))
        ecrireFichierPhoto(fichier, ecrirePhoto)
        val personne = brouillon.copy(photo = "${ZipPack.PHOTOS}/$fichier")
        ecrireListe(liste + personne)
        return personne
    }

    /** Met à jour prénom / nom / genre et, si [ecrirePhoto] est fourni, la photo. */
    @Synchronized
    fun modifier(maj: Personne, ecrirePhoto: ((OutputStream) -> Unit)?): Personne {
        val liste = personnes()
        val ancienne = liste.firstOrNull { it.id == maj.id } ?: return maj
        val autres = nomsPhotos(liste.filter { it.id != maj.id })
        val ancienFichier = fichierPhoto(ancienne)
        val extension = if (ecrirePhoto != null) "jpg" else ancienFichier.extension
        val nouveauNom = nomPhoto(maj, extension, autres)
        val nouveauFichier = File(dossierPhotos, nouveauNom)
        if (ecrirePhoto != null) {
            ecrireFichierPhoto(nouveauNom, ecrirePhoto)
        } else if (nouveauFichier != ancienFichier) {
            ancienFichier.copyTo(nouveauFichier, overwrite = true)
        }
        if (nouveauFichier.canonicalPath != ancienFichier.canonicalPath) ancienFichier.delete()
        val resultat = maj.copy(prenom = maj.prenom.trim(), nom = maj.nom.trim(), photo = "${ZipPack.PHOTOS}/$nouveauNom")
        ecrireListe(liste.map { if (it.id == maj.id) resultat else it })
        return resultat
    }

    /** Renseigne le genre des personnes « à préciser » quand le prénom l'indique ; renvoie le nombre de fiches complétées. */
    @Synchronized
    fun completerGenres(genres: GenresPrenoms): Int {
        val liste = personnes()
        var n = 0
        val maj = liste.map { p ->
            if (p.genre != Genre.INCONNU) return@map p
            val g = genres.genre(p.prenom)
            if (g == Genre.INCONNU) p else p.copy(genre = g).also { n++ }
        }
        if (n > 0) ecrireListe(maj)
        return n
    }

    /** Change seulement le genre (sans toucher à la photo). */
    @Synchronized
    fun changerGenre(id: String, genre: Genre) {
        ecrireListe(personnes().map { if (it.id == id) it.copy(genre = genre) else it })
    }

    @Synchronized
    fun supprimer(id: String) {
        val liste = personnes()
        liste.firstOrNull { it.id == id }?.let { fichierPhoto(it).delete() }
        ecrireListe(liste.filter { it.id != id })
        val p = progression()
        enregistrerProgression(p.copy(connus = p.connus - id))
    }

    @Synchronized
    fun progression(): Progression =
        if (fichierProgression.isFile) progressionDepuisJson(fichierProgression.readText()) else Progression()

    @Synchronized
    fun enregistrerProgression(p: Progression) {
        ecrireAtomique(fichierProgression, progressionVersJson(p))
    }

    @Synchronized
    fun parametres(): Parametres =
        if (fichierParametres.isFile) Parametres.depuisJson(fichierParametres.readText()) else Parametres()

    @Synchronized
    fun enregistrerParametres(p: Parametres) {
        ecrireAtomique(fichierParametres, p.versJson())
    }

    @Synchronized
    fun entrainement(): Entrainement =
        if (fichierEntrainement.isFile) Entrainement.depuisJson(fichierEntrainement.readText()) else Entrainement()

    @Synchronized
    fun enregistrerEntrainement(e: Entrainement) {
        ecrireAtomique(fichierEntrainement, e.versJson())
    }

    private fun ecrireListe(liste: List<Personne>) {
        dossierPhotos.mkdirs()
        ecrireAtomique(fichierListe, ListeJson.ecrire(liste))
    }

    private fun ecrireFichierPhoto(nom: String, ecrire: (OutputStream) -> Unit) {
        dossierPhotos.mkdirs()
        val tmp = File(dossierPhotos, "$nom.tmp")
        tmp.outputStream().use(ecrire)
        val cible = File(dossierPhotos, nom)
        cible.delete()
        if (!tmp.renameTo(cible)) {
            tmp.copyTo(cible, overwrite = true)
            tmp.delete()
        }
    }

    private fun ecrireAtomique(fichier: File, texte: String) {
        fichier.parentFile?.mkdirs()
        val tmp = File(fichier.path + ".tmp")
        tmp.writeText(texte)
        fichier.delete()
        if (!tmp.renameTo(fichier)) {
            tmp.copyTo(fichier, overwrite = true)
            tmp.delete()
        }
    }

    private fun nomsPhotos(liste: List<Personne>) =
        liste.map { it.photo.substringAfterLast('/').lowercase() }.toMutableSet()

    companion object {
        fun progressionDepuisJson(texte: String): Progression = try {
            val o = JSONObject(texte)
            val connus = o.optJSONArray("connus") ?: JSONArray()
            Progression(
                connus = (0 until connus.length()).map { connus.getString(it) }.toSet(),
                niveau = o.optInt("niveau", 1).coerceIn(1, Difficulte.NIVEAU_MAX),
                serie = o.optInt("serie", 0).coerceAtLeast(0),
            )
        } catch (e: org.json.JSONException) {
            Progression()
        }

        fun progressionVersJson(p: Progression): String = JSONObject()
            .put("connus", JSONArray(p.connus.sorted()))
            .put("niveau", p.niveau)
            .put("serie", p.serie)
            .toString(2)

        /** « photo_01_Marie_Dupont.jpg », unique dans [pris] (comparaison sans casse). */
        fun nomPhoto(p: Personne, extension: String, pris: MutableSet<String>): String {
            val ext = extension.lowercase().ifBlank { "jpg" }
            val morceaux = listOf(p.id, p.prenom, p.nom).map(::slug).filter { it.isNotEmpty() }
            val base = (listOf("photo") + morceaux).joinToString("_")
            var nom = "$base.$ext"
            var n = 2
            while (!pris.add(nom.lowercase())) nom = "${base}_${n++}.$ext"
            return nom
        }

        fun slug(texte: String): String =
            Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
                .take(40)
    }
}
