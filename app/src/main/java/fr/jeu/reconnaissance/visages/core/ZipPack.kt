package fr.jeu.reconnaissance.visages.core

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PackInvalide(message: String) : Exception(message)

/**
 * Pack ZIP échangé entre appareils :
 *   liste.json
 *   photos/photo_01_Prenom_Nom.jpg
 *   …
 */
object ZipPack {
    const val LISTE = "liste.json"
    const val PHOTOS = "photos"
    const val PROGRESSION = "progression.json"
    const val PARAMETRES = "parametres.json"

    val EXTENSIONS_IMAGES = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    private const val MAX_FICHIER = 50L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTREES = 20_000

    /**
     * Extrait le ZIP [fichier] dans [dest] en refusant les chemins qui sortent du dossier.
     * Les noms de fichiers sont lus en UTF-8, ou à défaut dans l'encodage des ZIP de
     * l'explorateur Windows (CP850), sinon en ISO-8859-1. Renvoie le dossier d'extraction.
     */
    fun extraire(fichier: File, dest: File): File {
        val encodages = listOf("UTF-8", "IBM850", "ISO-8859-1").filter { Charset.isSupported(it) }
        for ((i, nom) in encodages.withIndex()) {
            dest.deleteRecursively()
            try {
                fichier.inputStream().use { return extraire(it, dest, Charset.forName(nom)) }
            } catch (e: IllegalArgumentException) {
                // Nom de fichier mal encodé : on essaie l'encodage suivant.
                if (i == encodages.lastIndex) throw PackInvalide("ZIP : noms de fichiers illisibles")
            }
        }
        throw PackInvalide("ZIP illisible")
    }

    fun extraire(entree: InputStream, dest: File, encodage: Charset = Charsets.UTF_8): File {
        dest.mkdirs()
        val racine = dest.canonicalFile
        var total = 0L
        var entrees = 0
        ZipInputStream(BufferedInputStream(entree), encodage).use { zip ->
            val tampon = ByteArray(64 * 1024)
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entrees > MAX_ENTREES) throw PackInvalide("ZIP : trop de fichiers")
                // Selon la version d'Android/Java, un nom mal décodé lève une exception ou contient U+FFFD.
                if ('\uFFFD' in e.name) throw IllegalArgumentException("nom mal encodé")
                val nom = e.name.replace('\\', '/')
                if (e.isDirectory || ignorer(nom)) continue

                val cible = File(racine, nom).canonicalFile
                if (!cible.path.startsWith(racine.path + File.separator)) {
                    throw PackInvalide("ZIP : chemin interdit ($nom)")
                }
                cible.parentFile?.mkdirs()
                var taille = 0L
                cible.outputStream().use { out ->
                    while (true) {
                        val lu = zip.read(tampon)
                        if (lu < 0) break
                        taille += lu
                        total += lu
                        if (taille > MAX_FICHIER) throw PackInvalide("ZIP : fichier trop volumineux ($nom)")
                        if (total > MAX_TOTAL) throw PackInvalide("ZIP : contenu trop volumineux")
                        out.write(tampon, 0, lu)
                    }
                }
            }
        }
        return racine
    }

    /**
     * ZIP sans liste.json : une personne par image trouvée (tous dossiers confondus).
     * Prénom et nom sont déduits du nom du fichier, le genre reste à préciser.
     */
    fun personnesDepuisPhotos(racine: File): List<Personne> {
        val base = racine.canonicalFile
        return base.walkTopDown()
            .filter { it.isFile && estImage(it.name) }
            .map { it.relativeTo(base).invariantSeparatorsPath }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .mapIndexed { i, chemin ->
                val (prenom, nom) = nomsDepuisFichier(chemin.substringAfterLast('/'))
                Personne((i + 1).toString().padStart(2, '0'), prenom, nom, Genre.INCONNU, chemin)
            }
            .toList()
    }

    /**
     * « photo_01_Marie_Dupont.jpg » → (Marie, Dupont) ; « Jean-Pierre Martin Durand.png » →
     * (Jean-Pierre, Martin Durand). Sans mot exploitable, le nom du fichier sert de prénom.
     */
    fun nomsDepuisFichier(fichier: String): Pair<String, String> {
        val base = fichier.substringBeforeLast('.')
        val mots = base.split(Regex("[_\\s]+")).filter { it.isNotBlank() }.toMutableList()
        if (mots.firstOrNull()?.lowercase() in setOf("photo", "img", "image")) mots.removeAt(0)
        mots.removeAll { mot -> mot.all { it.isDigit() } }
        if (mots.isEmpty()) return base.trim() to ""
        return mots.first() to mots.drop(1).joinToString(" ")
    }

    /** Retrouve le fichier photo d'une personne dans un pack extrait, ou null. */
    fun photoDans(dossierListe: File, racine: File, chemin: String): File? {
        val nomFichier = chemin.substringAfterLast('/')
        val candidats = listOf(File(dossierListe, chemin), File(dossierListe, "$PHOTOS/$nomFichier"))
        val r = racine.canonicalFile.path + File.separator
        return candidats
            .map { it.canonicalFile }
            .firstOrNull { it.isFile && it.path.startsWith(r) && estImage(it.name) }
    }

    /**
     * Écrit le pack : liste.json, [annexes] (nom → contenu, ex. progression.json) puis photos/…
     * ([base] est le dossier qui contient photos/).
     */
    fun ecrire(personnes: List<Personne>, base: File, sortie: OutputStream, annexes: Map<String, String> = emptyMap()) {
        ZipOutputStream(sortie).use { zip ->
            (mapOf(LISTE to ListeJson.ecrire(personnes)) + annexes).forEach { (nom, texte) ->
                zip.putNextEntry(ZipEntry(nom))
                zip.write(texte.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("$PHOTOS/"))
            zip.closeEntry()
            personnes.forEach { p ->
                val fichier = File(base, p.photo)
                if (!fichier.isFile) return@forEach
                zip.putNextEntry(ZipEntry(p.photo))
                fichier.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun estImage(nom: String) = nom.substringAfterLast('.', "").lowercase() in EXTENSIONS_IMAGES

    private fun ignorer(nom: String): Boolean {
        val segments = nom.split('/')
        return nom.startsWith("__MACOSX/") ||
            segments.any { it.startsWith(".") && it != "." && it != ".." } ||
            segments.last().equals("Thumbs.db", ignoreCase = true) ||
            segments.last().equals("desktop.ini", ignoreCase = true)
    }

    /** Dossier qui contient liste.json (racine du ZIP ou sous-dossier unique), ou null. */
    fun trouverListe(racine: File): File? {
        if (File(racine, LISTE).isFile) return racine
        val sousDossiers = racine.listFiles { f -> f.isDirectory && File(f, LISTE).isFile }.orEmpty()
        return sousDossiers.singleOrNull()
    }
}
