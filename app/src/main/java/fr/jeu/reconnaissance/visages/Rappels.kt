package fr.jeu.reconnaissance.visages

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.jeu.reconnaissance.visages.core.Depot
import fr.jeu.reconnaissance.visages.core.Parametres
import java.time.LocalDateTime
import java.time.ZoneId

/** Rappels d'entraînement : une alarme à la fois, reprogrammée après chaque rappel. */
object Rappels {
    private const val CANAL = "rappels"
    private const val ACTION = "fr.jeu.reconnaissance.visages.RAPPEL"
    const val EXTRA_JOUER = "jouer"

    /** Programme la prochaine alarme (ou l'annule) ; renvoie la date programmée. */
    fun planifier(context: Context, p: Parametres): LocalDateTime? {
        val alarmes = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RappelReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmes.cancel(intent)
        val prochain = p.prochainRappel(LocalDateTime.now()) ?: return null
        val millis = prochain.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        return prochain
    }

    fun notifier(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val gestionnaire = context.getSystemService(NotificationManager::class.java)
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL, "Rappels d'entraînement", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val ouvrir = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_JOUER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("C'est l'heure de s'entraîner")
            .setContentText("Quelques photos pour garder les visages en mémoire.")
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }
}

/** Reçoit l'alarme (affiche le rappel) et le redémarrage / la mise à jour (reprogramme). */
class RappelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val resultat = goAsync()
        Thread {
            try {
                val parametres = Depot(context.filesDir).parametres()
                if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
                    Rappels.notifier(context)
                }
                Rappels.planifier(context, parametres)
            } finally {
                resultat.finish()
            }
        }.start()
    }
}
