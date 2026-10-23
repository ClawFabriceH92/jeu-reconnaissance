package fr.jeu.reconnaissance.visages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object Images {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Charge une photo de la base, réduite à ~[taille] px, orientation EXIF appliquée. */
    fun charger(fichier: File, taille: Int): ImageBitmap? {
        val cle = "${fichier.path}:${fichier.lastModified()}:$taille"
        cache.get(cle)?.let { return it }
        val bitmap = decoder(taille, { fichier.inputStream() }) ?: return null
        val image = bitmap.asImageBitmap()
        cache.put(cle, image)
        return image
    }

    fun chargerUri(context: Context, uri: Uri, taille: Int): ImageBitmap? =
        decoder(taille, { context.contentResolver.openInputStream(uri) })?.asImageBitmap()

    /** Convertit l'image choisie en JPEG (côté max 1280 px) pour la stocker dans la base. */
    fun versJpeg(context: Context, uri: Uri, sortie: OutputStream) {
        val bitmap = decoder(1280, { context.contentResolver.openInputStream(uri) })
            ?: throw IllegalArgumentException("Image illisible")
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, sortie)
    }

    private fun decoder(taille: Int, ouvrir: () -> InputStream?): Bitmap? = try {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ouvrir()?.use { BitmapFactory.decodeStream(it, null, bornes) }
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) {
            null
        } else {
            var echantillon = 1
            while (maxOf(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= taille) echantillon *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
            val brut = ouvrir()?.use { BitmapFactory.decodeStream(it, null, options) }
            brut?.let { reduire(orienter(it, ouvrir), taille) }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun orienter(bitmap: Bitmap, ouvrir: () -> InputStream?): Bitmap {
        val orientation = try {
            ouvrir()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            null
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun reduire(bitmap: Bitmap, taille: Int): Bitmap {
        val cote = maxOf(bitmap.width, bitmap.height)
        if (cote <= taille) return bitmap
        val f = taille.toFloat() / cote
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
    }
}
