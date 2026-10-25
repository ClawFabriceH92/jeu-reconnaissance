package fr.jeu.reconnaissance.visages.update

import android.util.Log

/** Trace des mises à jour dans logcat (équivalent minimal du Diag de notes-de-frais). */
internal object Journal {
    private const val TAG = "Reconnaissance"

    fun note(sujet: String, message: String) {
        Log.i(TAG, "$sujet : $message")
    }

    fun note(sujet: String, erreur: Throwable) {
        Log.w(TAG, sujet, erreur)
    }
}
