package fr.jeu.reconnaissance.visages.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Entraînement quotidien : temps de jeu du jour, réponses du jour, et jours où
 * l'objectif (ex. 10 minutes) a été atteint, pour la série de jours consécutifs.
 */
data class Entrainement(
    val date: LocalDate? = null,
    val secondes: Int = 0,
    val questions: Int = 0,
    val reussites: Int = 0,
    val joursFaits: Set<LocalDate> = emptySet(),
) {
    /** Les compteurs du jour, remis à zéro si [aujourdhui] est un autre jour. */
    fun du(aujourdhui: LocalDate): Entrainement =
        if (date == aujourdhui) this else copy(date = aujourdhui, secondes = 0, questions = 0, reussites = 0)

    fun ajouterTemps(aujourdhui: LocalDate, s: Int, objectifMinutes: Int): Entrainement {
        val e = du(aujourdhui)
        val total = e.secondes + s
        val fait = total >= objectifMinutes * 60
        return e.copy(
            secondes = total,
            joursFaits = if (fait) (e.joursFaits + aujourdhui).filter { it.isAfter(aujourdhui.minusDays(400)) }.toSet()
            else e.joursFaits,
        )
    }

    fun ajouterReponse(aujourdhui: LocalDate, reussi: Boolean): Entrainement {
        val e = du(aujourdhui)
        return e.copy(questions = e.questions + 1, reussites = e.reussites + if (reussi) 1 else 0)
    }

    fun faitLe(jour: LocalDate) = jour in joursFaits

    fun secondesDu(aujourdhui: LocalDate) = du(aujourdhui).secondes

    /** Jours consécutifs avec objectif atteint, jusqu'à aujourd'hui (ou hier si pas encore fait aujourd'hui). */
    fun serie(aujourdhui: LocalDate): Int {
        var jour = if (faitLe(aujourdhui)) aujourdhui else aujourdhui.minusDays(1)
        var n = 0
        while (faitLe(jour)) {
            n++
            jour = jour.minusDays(1)
        }
        return n
    }

    fun versJson(): String = JSONObject()
        .put("date", date?.toString() ?: "")
        .put("secondes", secondes)
        .put("questions", questions)
        .put("reussites", reussites)
        .put("joursFaits", JSONArray(joursFaits.sorted().map { it.toString() }))
        .toString(2)

    companion object {
        fun depuisJson(texte: String): Entrainement = try {
            val o = JSONObject(texte)
            val jours = o.optJSONArray("joursFaits") ?: JSONArray()
            Entrainement(
                date = o.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                secondes = o.optInt("secondes", 0).coerceAtLeast(0),
                questions = o.optInt("questions", 0).coerceAtLeast(0),
                reussites = o.optInt("reussites", 0).coerceAtLeast(0),
                joursFaits = (0 until jours.length()).mapNotNull { runCatching { LocalDate.parse(jours.getString(it)) }.getOrNull() }.toSet(),
            )
        } catch (e: org.json.JSONException) {
            Entrainement()
        }
    }
}
