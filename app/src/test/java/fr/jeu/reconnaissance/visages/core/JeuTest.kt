package fr.jeu.reconnaissance.visages.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JeuTest {
    private fun p(id: String, prenom: String, nom: String, g: Genre) = Personne(id, prenom, nom, g, "photos/$id.jpg")

    private val personnes = listOf(
        p("1", "Marie", "Dupont", Genre.FEMME),
        p("2", "Julie", "Martin", Genre.FEMME),
        p("3", "Anne", "Petit", Genre.FEMME),
        p("4", "Paul", "Durand", Genre.HOMME),
        p("5", "Luc", "Moreau", Genre.HOMME),
        p("6", "marie", "Bernard", Genre.FEMME),
    )

    @Test
    fun propositionsDuMemeGenreSansDoublon() {
        val g = GenerateurQuestions(Random(1))
        repeat(50) {
            val q = g.question(personnes[0], personnes, nbChoix = 7)
            assertTrue("Marie" in q.choixPrenoms)
            assertTrue(q.choixPrenoms.none { it in setOf("Paul", "Luc", "marie") })
            assertEquals(3, q.choixPrenoms.size) // Marie, Julie, Anne
            // Noms : d'abord ceux des femmes, complétés par ceux des hommes.
            assertEquals(6, q.choixNoms.size)
            assertTrue("Dupont" in q.choixNoms)
        }
        val q = g.question(personnes[3], personnes, nbChoix = 3)
        assertEquals(setOf("Paul", "Luc"), q.choixPrenoms.toSet())
        assertEquals(3, q.choixNoms.size)
    }

    @Test
    fun difficulteMonteEtDescend() {
        var p = Progression()
        assertEquals(3, Difficulte.nbChoix(p.niveau))
        repeat(3) { p = Difficulte.apresReponse(p, "1", true) }
        assertEquals(2, p.niveau)
        assertEquals(setOf("1"), p.connus)
        repeat(30) { p = Difficulte.apresReponse(p, "2", true) }
        assertEquals(Difficulte.NIVEAU_MAX, p.niveau)
        assertEquals(7, Difficulte.nbChoix(p.niveau))
        p = Difficulte.apresReponse(p, "3", false)
        assertEquals(Difficulte.NIVEAU_MAX - 1, p.niveau)
        assertEquals(0, p.serie)
    }

    @Test
    fun tiragePrivilegieLesInconnus() {
        val g = GenerateurQuestions(Random(2))
        val prog = Progression(connus = setOf("1", "2", "3", "4", "5"))
        repeat(20) { assertEquals("6", g.suivante(personnes, prog, "1")!!.personne.id) }
        repeat(20) { assertTrue(g.suivante(personnes, Progression(), "1")!!.personne.id != "1") }
    }
}
