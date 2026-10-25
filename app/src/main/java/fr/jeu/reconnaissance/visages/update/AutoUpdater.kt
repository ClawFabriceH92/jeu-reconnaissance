package fr.jeu.reconnaissance.visages.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** The two system hand-offs the update flow needs, plus the signature check. */
object AutoUpdater {

    /**
     * true / false : l'APK téléchargé est signé (ou non) avec la même clé que l'app installée.
     * null : impossible à vérifier (Android < 9 ou certificat illisible) ; on tente l'installation.
     */
    fun memeSignature(context: Context, file: File): Boolean? = try {
        if (Build.VERSION.SDK_INT < 28) {
            null
        } else {
            val pm = context.packageManager
            val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners?.toSet()
            val installee = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.toSet()
            if (archive.isNullOrEmpty() || installee.isNullOrEmpty()) null else archive == installee
        }
    } catch (e: Exception) {
        Journal.note("update/signature", e)
        null
    }

    fun install(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Journal.note("update/install", e)
        }
    }

    fun openInstallSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Journal.note("update/settings", e)
        }
    }
}
