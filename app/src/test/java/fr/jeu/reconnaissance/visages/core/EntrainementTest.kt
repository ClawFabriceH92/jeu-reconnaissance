package fr.jeu.reconnaissance.visages.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntrainementTest {
    private val lundi = LocalDate.of(2025, 10, 20)

    @Test
    fun objectifEtSerie() {
        var e = Entrainement()
        e = e.ajouterTemps(lundi, 9 * 60, 10)
        assertFalse(e.faitLe(lundi))
        assertEquals(0, e.serie(lundi))
        e = e.ajouterTemps(lundi, 60, 10)
        assertTrue(e.faitLe(lundi))
        assertEquals(1, e.serie(lundi))

        // Mardi : compteur du jour remis à zéro, la série d'hier compte encore.
        val mardi = lundi.plusDays(1)
        assertEquals(0, e.secondesDu(mardi))
        assertEquals(1, e.serie(mardi))
        e = e.ajouterTemps(mardi, 600, 10)
        assertEquals(2, e.serie(mardi))

        // Jeudi sans mercredi : série cassée.
        val jeudi = lundi.plusDays(3)
        assertEquals(0, e.serie(jeudi))
        e = e.ajouterTemps(jeudi, 600, 10)
        assertEquals(1, e.serie(jeudi))
    }

    @Test
    fun reponsesDuJourEtJson() {
        var e = Entrainement().ajouterReponse(lundi, true).ajouterReponse(lundi, false).ajouterTemps(lundi, 700, 10)
        assertEquals(2, e.questions)
        assertEquals(1, e.reussites)
        assertEquals(e, Entrainement.depuisJson(e.versJson()))
        e = e.ajouterReponse(lundi.plusDays(1), true)
        assertEquals(1, e.questions)
        assertEquals(Entrainement(), Entrainement.depuisJson("??"))
    }
}
