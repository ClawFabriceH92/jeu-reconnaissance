package fr.jeu.reconnaissance.visages.core

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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

    val EXTENSIONS_IMAGES = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    private const val MAX_FICHIER = 50L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTREES = 20_000

    /**
     * Extrait le ZIP dans [dest] en refusant les chemins qui sortent du dossier.
     * Renvoie le dossier qui contient liste.json (racine du ZIP ou un sous-dossier unique).
     */
    fun extraire(entree: InputStream, dest: File): File {
        dest.mkdirs()
        val racine = dest.canonicalFile
        var total = 0L
        var entrees = 0
        ZipInputStream(BufferedInputStream(entree)).use { zip ->
            val tampon = ByteArray(64 * 1024)
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entrees > MAX_ENTREES) throw PackInvalide("ZIP : trop de fichiers")
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
        return trouverListe(racine)
            ?: throw PackInvalide("Ce fichier n'est pas un pack valide : liste.json introuvable")
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

    /** Écrit le pack : liste.json puis photos/… ([base] est le dossier qui contient photos/). */
    fun ecrire(personnes: List<Personne>, base: File, sortie: OutputStream) {
        ZipOutputStream(sortie).use { zip ->
            zip.putNextEntry(ZipEntry(LISTE))
            zip.write(ListeJson.ecrire(personnes).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
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

    private fun trouverListe(racine: File): File? {
        if (File(racine, LISTE).isFile) return racine
        val sousDossiers = racine.listFiles { f -> f.isDirectory && File(f, LISTE).isFile }.orEmpty()
        return sousDossiers.singleOrNull()
    }
}
