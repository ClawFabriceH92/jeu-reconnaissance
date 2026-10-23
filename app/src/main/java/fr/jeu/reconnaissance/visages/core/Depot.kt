package fr.jeu.reconnaissance.visages.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer

data class RapportImport(val importees: Int, val sansPhoto: Int)

/**
 * Stockage local sur l'appareil :
 *   [racine]/base/liste.json
 *   [racine]/base/photos/…
 *   [racine]/progression.json
 */
class Depot(private val racine: File) {
    val dossierBase = File(racine, "base")
    private val fichierListe = File(dossierBase, ZipPack.LISTE)
    private val dossierPhotos = File(dossierBase, ZipPack.PHOTOS)
    private val fichierProgression = File(racine, "progression.json")

    fun fichierPhoto(p: Personne) = File(dossierBase, p.photo)

    @Synchronized
    fun personnes(): List<Personne> =
        if (fichierListe.isFile) ListeJson.lire(fichierListe.readText()) else emptyList()

    /** Remplace toute la base par le contenu du ZIP et réinitialise la progression. */
    @Synchronized
    fun importer(entree: InputStream): RapportImport {
        val tmp = File(racine, "import_tmp")
        val nouvelle = File(racine, "base_import")
        tmp.deleteRecursively()
        nouvelle.deleteRecursively()
        try {
            val dossierListe = ZipPack.extraire(entree, tmp)
            val lues = ListeJson.lire(File(dossierListe, ZipPack.LISTE).readText())
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
            enregistrerProgression(Progression())
            return RapportImport(gardees.size, sansPhoto)
        } finally {
            tmp.deleteRecursively()
            nouvelle.deleteRecursively()
        }
    }

    @Synchronized
    fun exporter(sortie: OutputStream) {
        ZipPack.ecrire(personnes(), dossierBase, sortie)
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

    @Synchronized
    fun supprimer(id: String) {
        val liste = personnes()
        liste.firstOrNull { it.id == id }?.let { fichierPhoto(it).delete() }
        ecrireListe(liste.filter { it.id != id })
        val p = progression()
        enregistrerProgression(p.copy(connus = p.connus - id))
    }

    @Synchronized
    fun progression(): Progression {
        if (!fichierProgression.isFile) return Progression()
        return try {
            val o = JSONObject(fichierProgression.readText())
            val connus = o.optJSONArray("connus") ?: JSONArray()
            Progression(
                connus = (0 until connus.length()).map { connus.getString(it) }.toSet(),
                niveau = o.optInt("niveau", 1).coerceIn(1, Difficulte.NIVEAU_MAX),
                serie = o.optInt("serie", 0).coerceAtLeast(0),
            )
        } catch (e: org.json.JSONException) {
            Progression()
        }
    }

    @Synchronized
    fun enregistrerProgression(p: Progression) {
        val o = JSONObject()
            .put("connus", JSONArray(p.connus.sorted()))
            .put("niveau", p.niveau)
            .put("serie", p.serie)
        ecrireAtomique(fichierProgression, o.toString())
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
