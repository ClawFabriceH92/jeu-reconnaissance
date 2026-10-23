package fr.jeu.reconnaissance.visages.core

import kotlin.random.Random

/** Une question : la photo d'une personne, les propositions de prénoms puis de noms. */
data class Question(
    val personne: Personne,
    val choixPrenoms: List<String>,
    val choixNoms: List<String>,
)

/**
 * Le nombre de propositions augmente avec le niveau (3 à 7).
 * [REUSSITES_PAR_NIVEAU] bonnes réponses d'affilée font monter d'un niveau ; une erreur le fait baisser.
 */
object Difficulte {
    const val NIVEAU_MAX = 5
    const val REUSSITES_PAR_NIVEAU = 3

    fun nbChoix(niveau: Int) = 2 + niveau.coerceIn(1, NIVEAU_MAX)

    fun apresReponse(p: Progression, personneId: String, reussi: Boolean): Progression {
        if (!reussi) return p.copy(niveau = (p.niveau - 1).coerceAtLeast(1), serie = 0)
        val serie = p.serie + 1
        val monte = serie >= REUSSITES_PAR_NIVEAU && p.niveau < NIVEAU_MAX
        return p.copy(
            connus = p.connus + personneId,
            niveau = if (monte) p.niveau + 1 else p.niveau,
            serie = if (monte) 0 else serie,
        )
    }
}

class GenerateurQuestions(private val random: Random = Random.Default) {

    /** Tire une personne (de préférence pas encore connue, et différente de la précédente). */
    fun suivante(personnes: List<Personne>, progression: Progression, precedenteId: String?): Question? {
        if (personnes.isEmpty()) return null
        val autres = personnes.filter { it.id != precedenteId }.ifEmpty { personnes }
        val aApprendre = autres.filter { it.id !in progression.connus }
        val personne = aApprendre.ifEmpty { autres }.random(random)
        return question(personne, personnes, Difficulte.nbChoix(progression.niveau))
    }

    /** Les propositions ne mélangent pas les genres (sauf genre inconnu). */
    fun question(personne: Personne, personnes: List<Personne>, nbChoix: Int): Question {
        val memeGenre = personnes.filter { personne.genre == Genre.INCONNU || it.genre == personne.genre }
        val prenoms = choix(personne.prenom, nbChoix, memeGenre.map { it.prenom })
        // Un nom de famille n'est pas genré : on complète avec ceux de l'autre genre si besoin.
        val noms = choix(personne.nom, nbChoix, memeGenre.map { it.nom }, personnes.map { it.nom })
        return Question(personne, prenoms, noms)
    }

    /**
     * [correct] + jusqu'à nbChoix-1 leurres distincts, mélangés. Les leurres sont pris dans
     * [groupes] par ordre de priorité. Vide si [correct] est vide.
     */
    fun choix(correct: String, nbChoix: Int, vararg groupes: List<String>): List<String> {
        if (correct.isBlank()) return emptyList()
        val vus = mutableSetOf(cle(correct))
        val leurres = groupes
            .flatMap { groupe -> groupe.filter { it.isNotBlank() }.shuffled(random) }
            .filter { vus.add(cle(it)) }
            .take(nbChoix - 1)
        return (leurres + correct).shuffled(random)
    }

    private fun cle(s: String) = s.trim().lowercase()
}
