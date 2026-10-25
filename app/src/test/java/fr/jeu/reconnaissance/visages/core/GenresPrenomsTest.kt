package fr.jeu.reconnaissance.visages.core

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenresPrenomsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val genres = GenresPrenoms.lire(
        """
        # commentaire
        HELENE	F
        JEAN	H
        JEAN-PIERRE	H
        MARIE	F
        """.trimIndent()
    )

    @Test
    fun recherche() {
        assertEquals(Genre.FEMME, genres.genre("Hélène"))
        assertEquals(Genre.FEMME, genres.genre(" marie "))
        assertEquals(Genre.HOMME, genres.genre("Jean-Pierre"))
        assertEquals(Genre.HOMME, genres.genre("Jean-Luc")) // par le premier prénom
        assertEquals(Genre.FEMME, genres.genre("Marie-Camille"))
        assertEquals(Genre.INCONNU, genres.genre("Camille"))
        assertEquals(Genre.INCONNU, genres.genre(""))
    }

    @Test
    fun completerNeModifiePasLesGenresChoisis() {
        val depot = Depot(tmp.newFolder("app"))
        depot.ajouter("Marie", "A", Genre.INCONNU) { it.write(1) }
        depot.ajouter("Jean", "B", Genre.FEMME) { it.write(2) } // choix manuel conservé
        depot.ajouter("Camille", "C", Genre.INCONNU) { it.write(3) }
        assertEquals(1, depot.completerGenres(genres))
        assertEquals(listOf(Genre.FEMME, Genre.FEMME, Genre.INCONNU), depot.personnes().map { it.genre })
        depot.changerGenre(depot.personnes()[2].id, Genre.FEMME)
        assertEquals(Genre.FEMME, depot.personnes()[2].genre)
    }
}
