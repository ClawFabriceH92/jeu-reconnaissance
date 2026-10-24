package fr.jeu.reconnaissance.visages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import fr.jeu.reconnaissance.visages.core.Depot
import java.io.File

/**
 * Sauvegarde automatique (liste, photos, progression, réglages) dans
 * Téléchargements/Reconnaissance/reconnaissance-sauvegarde.zip : ce dossier survit à la
 * désinstallation de l'app. Restauration : « Importer ZIP » et choisir ce fichier.
 */
object Sauvegarde {
    const val DOSSIER = "Reconnaissance"
    const val NOM = "reconnaissance-sauvegarde.zip"
    private const val PREFS = "sauvegarde"

    fun derniere(context: Context): Long = prefs(context).getLong("date", 0L)

    /** Écrit la sauvegarde ; renvoie false si l'emplacement n'est pas accessible. */
    fun ecrire(context: Context, depot: Depot): Boolean {
        if (depot.personnes().isEmpty()) return false
        // Le ZIP est d'abord construit en entier, puis copié : une coupure ne laisse pas de sauvegarde tronquée.
        val tmp = File(context.cacheDir, "sauvegarde.zip")
        try {
            tmp.outputStream().use { depot.exporter(it) }
            val ok = if (Build.VERSION.SDK_INT >= 29) ecrireMediaStore(context, tmp) else ecrireAncien(context, tmp)
            if (ok) prefs(context).edit().putLong("date", System.currentTimeMillis()).apply()
            return ok
        } finally {
            tmp.delete()
        }
    }

    private fun ecrireMediaStore(context: Context, source: File): Boolean {
        val resolver = context.contentResolver
        // Fichier créé par cette installation : on le réécrit (après réinstallation, Android ne
        // nous laisse plus le modifier et un nouveau fichier « … (1).zip » est créé).
        prefs(context).getString("uri", null)?.let { Uri.parse(it) }?.let { uri ->
            try {
                resolver.openOutputStream(uri, "wt")?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                    return true
                }
            } catch (e: Exception) {
                // Fichier supprimé ou plus accessible : on en crée un nouveau.
            }
        }
        val valeurs = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, NOM)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOSSIER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valeurs) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException()
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            prefs(context).edit().putString("uri", uri.toString()).apply()
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun ecrireAncien(context: Context, source: File): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val dossier = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOSSIER)
        dossier.mkdirs()
        source.copyTo(File(dossier, NOM), overwrite = true)
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
