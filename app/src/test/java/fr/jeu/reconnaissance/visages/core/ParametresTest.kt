package fr.jeu.reconnaissance.visages.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ParametresTest {
    // Jeudi 23 octobre 2025, 17 h 00
    private val jeudi17h = LocalDateTime.of(2025, 10, 23, 17, 0)

    @Test
    fun prochainRappel() {
        val p = Parametres(Frequence.QUOTIDIENNE, heure = 18, minute = 0)
        assertEquals(LocalDateTime.of(2025, 10, 23, 18, 0), p.prochainRappel(jeudi17h))
        assertEquals(LocalDateTime.of(2025, 10, 24, 18, 0), p.prochainRappel(jeudi17h.withHour(18)))

        val ouvres = p.copy(frequence = Frequence.JOURS_OUVRES)
        // Vendredi 19 h -> lundi 18 h
        assertEquals(LocalDateTime.of(2025, 10, 27, 18, 0), ouvres.prochainRappel(LocalDateTime.of(2025, 10, 24, 19, 0)))

        val hebdo = p.copy(frequence = Frequence.HEBDOMADAIRE, jour = DayOfWeek.THURSDAY)
        assertEquals(LocalDateTime.of(2025, 10, 23, 18, 0), hebdo.prochainRappel(jeudi17h))
        assertEquals(LocalDateTime.of(2025, 10, 30, 18, 0), hebdo.prochainRappel(jeudi17h.withHour(19)))

        assertNull(Parametres().prochainRappel(jeudi17h))
    }

    @Test
    fun json() {
        val p = Parametres(Frequence.JOURS_OUVRES, 7, 45, DayOfWeek.SUNDAY, sauvegardeAuto = false)
        assertEquals(p, Parametres.depuisJson(p.versJson()))
        assertEquals(Parametres(), Parametres.depuisJson("pas du json"))
    }
}
