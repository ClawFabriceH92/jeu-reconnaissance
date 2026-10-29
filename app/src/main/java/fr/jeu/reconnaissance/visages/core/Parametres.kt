package fr.jeu.reconnaissance.visages.core

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

enum class Frequence(val libelle: String) {
    AUCUNE("Désactivé"),
    QUOTIDIENNE("Tous les jours"),
    JOURS_OUVRES("Du lundi au vendredi"),
    HEBDOMADAIRE("Une fois par semaine"),
}

/** Réglages de l'app : rappel d'entraînement et sauvegarde automatique. */
data class Parametres(
    val frequence: Frequence = Frequence.AUCUNE,
    val heure: Int = 18,
    val minute: Int = 0,
    val jour: DayOfWeek = DayOfWeek.MONDAY,
    val sauvegardeAuto: Boolean = true,
    /** Objectif de l'entraînement quotidien, en minutes de jeu. */
    val minutesParJour: Int = 10,
) {
    /** Prochaine date de rappel strictement après [maintenant], ou null si désactivé. */
    fun prochainRappel(maintenant: LocalDateTime): LocalDateTime? {
        if (frequence == Frequence.AUCUNE) return null
        for (decalage in 0L..7L) {
            val date = maintenant.toLocalDate().plusDays(decalage)
            val moment = date.atTime(heure, minute)
            if (moment.isAfter(maintenant) && jourValide(date.dayOfWeek)) return moment
        }
        return null
    }

    private fun jourValide(j: DayOfWeek) = when (frequence) {
        Frequence.AUCUNE -> false
        Frequence.QUOTIDIENNE -> true
        Frequence.JOURS_OUVRES -> j != DayOfWeek.SATURDAY && j != DayOfWeek.SUNDAY
        Frequence.HEBDOMADAIRE -> j == jour
    }

    fun versJson(): String = JSONObject()
        .put("frequence", frequence.name)
        .put("heure", heure)
        .put("minute", minute)
        .put("jour", jour.name)
        .put("sauvegardeAuto", sauvegardeAuto)
        .put("minutesParJour", minutesParJour)
        .toString(2)

    companion object {
        fun depuisJson(texte: String): Parametres = try {
            val o = JSONObject(texte)
            Parametres(
                frequence = Frequence.entries.firstOrNull { it.name == o.optString("frequence") } ?: Frequence.AUCUNE,
                heure = o.optInt("heure", 18).coerceIn(0, 23),
                minute = o.optInt("minute", 0).coerceIn(0, 59),
                jour = DayOfWeek.entries.firstOrNull { it.name == o.optString("jour") } ?: DayOfWeek.MONDAY,
                sauvegardeAuto = o.optBoolean("sauvegardeAuto", true),
                minutesParJour = o.optInt("minutesParJour", 10).coerceIn(1, 120),
            )
        } catch (e: org.json.JSONException) {
            Parametres()
        }
    }
}
