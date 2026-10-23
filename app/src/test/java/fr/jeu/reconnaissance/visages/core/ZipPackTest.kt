package fr.jeu.reconnaissance.visages.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipPackTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zip(vararg fichiers: Pair<String, ByteArray>): ByteArray {
        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie).use { z ->
            fichiers.forEach { (nom, octets) ->
                z.putNextEntry(ZipEntry(nom))
                z.write(octets)
                z.closeEntry()
            }
        }
        return sortie.toByteArray()
    }

    private val listePc = """
        [
          {"id": 1, "prenom": "Marie", "nom": "Dupont", "genre": "F", "photo": "photos/photo_01_Marie.jpg"},
          {"id": 2, "prénom": "Éric", "nom": "Martin", "sexe": "homme", "fichier": "photo_02_Eric.jpg"},
          {"id": 3, "prenom": "Sans", "nom": "Photo", "genre": "H", "photo": "photos/absente.jpg"}
        ]
    """.trimIndent().toByteArray()

    @Test
    fun importPuisExportPuisReimport() {
        val depot = Depot(tmp.newFolder("app"))
        val rapport = depot.importer(
            ByteArrayInputStream(
                zip(
                    "liste.json" to listePc,
                    "photos/photo_01_Marie.jpg" to byteArrayOf(1, 2, 3),
                    "photos/photo_02_Eric.jpg" to byteArrayOf(4, 5),
                    "__MACOSX/._x" to byteArrayOf(0),
                )
            )
        )
        assertEquals(RapportImport(importees = 2, sansPhoto = 1), rapport)

        val personnes = depot.personnes()
        assertEquals(listOf("Marie", "Éric"), personnes.map { it.prenom })
        assertEquals(Genre.HOMME, personnes[1].genre)
        assertEquals("photos/photo_2_Eric_Martin.jpg", personnes[1].photo)
        assertTrue(personnes.all { depot.fichierPhoto(it).isFile })

        depot.enregistrerProgression(Progression(connus = setOf("1"), niveau = 3))

        val exporte = ByteArrayOutputStream().also { depot.exporter(it) }.toByteArray()
        val entrees = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(exporte)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) entrees[e.name] = z.readBytes()
            }
        }
        assertEquals(
            setOf("liste.json", "photos/photo_1_Marie_Dupont.jpg", "photos/photo_2_Eric_Martin.jpg"),
            entrees.keys,
        )
        assertEquals(listOf<Byte>(1, 2, 3), entrees["photos/photo_1_Marie_Dupont.jpg"]!!.toList())

        val autre = Depot(tmp.newFolder("autre"))
        autre.importer(ByteArrayInputStream(exporte))
        assertEquals(personnes, autre.personnes())

        // Un nouvel import réinitialise la progression.
        depot.importer(ByteArrayInputStream(exporte))
        assertEquals(Progression(), depot.progression())
    }

    @Test
    fun zipAvecDossierRacine() {
        val depot = Depot(tmp.newFolder("app"))
        val r = depot.importer(
            ByteArrayInputStream(
                zip(
                    "jeu/liste.json" to """{"personnes":[{"prenom":"A","nom":"B","photo":"photos/a.png"}]}""".toByteArray(),
                    "jeu/photos/a.png" to byteArrayOf(9),
                )
            )
        )
        assertEquals(1, r.importees)
        assertEquals("01", depot.personnes().single().id)
    }

    @Test
    fun cheminInterditRefuse() {
        val app = tmp.newFolder("app")
        val depot = Depot(app)
        try {
            depot.importer(ByteArrayInputStream(zip("../evil.txt" to byteArrayOf(1), "liste.json" to listePc)))
            fail("PackInvalide attendu")
        } catch (e: PackInvalide) {
            assertTrue(e.message!!.contains("interdit"))
        }
        assertFalse(File(app.parentFile, "evil.txt").exists())
    }

    @Test
    fun importInvalideGardeAncienneBase() {
        val depot = Depot(tmp.newFolder("app"))
        depot.ajouter("Paul", "Durand", Genre.HOMME) { it.write(byteArrayOf(7)) }
        for (contenu in listOf(byteArrayOf(1, 2, 3), zip("liste.json" to listePc))) {
            try {
                depot.importer(ByteArrayInputStream(contenu))
                fail("PackInvalide attendu")
            } catch (_: PackInvalide) {
            }
        }
        assertEquals(listOf("Paul"), depot.personnes().map { it.prenom })
    }

    @Test
    fun ajouterModifierSupprimer() {
        val depot = Depot(tmp.newFolder("app"))
        val p = depot.ajouter(" Léa ", "N'Guyen", Genre.FEMME) { it.write(byteArrayOf(1)) }
        assertEquals("01", p.id)
        assertEquals("photos/photo_01_Lea_N_Guyen.jpg", p.photo)
        val q = depot.ajouter("Léa", "N'Guyen", Genre.FEMME) { it.write(byteArrayOf(2)) }
        assertEquals("02", q.id)

        val m = depot.modifier(p.copy(prenom = "Lina"), null)
        assertEquals("photos/photo_01_Lina_N_Guyen.jpg", m.photo)
        assertEquals(listOf<Byte>(1), depot.fichierPhoto(m).readBytes().toList())
        assertFalse(File(depot.dossierBase, p.photo).exists())

        depot.enregistrerProgression(Progression(connus = setOf("01", "02")))
        depot.supprimer("01")
        assertEquals(listOf("02"), depot.personnes().map { it.id })
        assertEquals(setOf("02"), depot.progression().connus)
        assertFalse(depot.fichierPhoto(m).exists())
    }

    @Test
    fun zipDePhotosSeules() {
        val depot = Depot(tmp.newFolder("app"))
        val r = depot.importer(
            ByteArrayInputStream(
                zip(
                    "Mes photos/photo_01_Marie_Dupont.jpg" to byteArrayOf(1),
                    "Mes photos/Jean-Pierre Martin Durand.PNG" to byteArrayOf(2),
                    "Mes photos/Thumbs.db" to byteArrayOf(3),
                    "Mes photos/notes.txt" to byteArrayOf(4),
                    "IMG_20240101.jpg" to byteArrayOf(5),
                )
            )
        )
        assertEquals(RapportImport(importees = 3, sansPhoto = 0, depuisPhotos = true), r)
        val personnes = depot.personnes()
        assertEquals(
            listOf("IMG_20240101" to "", "Jean-Pierre" to "Martin Durand", "Marie" to "Dupont"),
            personnes.map { it.prenom to it.nom },
        )
        assertTrue(personnes.all { it.genre == Genre.INCONNU && depot.fichierPhoto(it).isFile })
    }

    @Test
    fun nomsAccentuesZipWindows() {
        // L'explorateur Windows encode les noms en CP850 sans le drapeau UTF-8.
        val sortie = ByteArrayOutputStream()
        ZipOutputStream(sortie, java.nio.charset.Charset.forName("IBM850")).use { z ->
            z.putNextEntry(ZipEntry("photo_01_Hélène_Lefèvre.jpg"))
            z.write(byteArrayOf(1))
            z.closeEntry()
        }
        val depot = Depot(tmp.newFolder("app"))
        depot.importer(ByteArrayInputStream(sortie.toByteArray()))
        assertEquals("Hélène" to "Lefèvre", depot.personnes().single().let { it.prenom to it.nom })
    }
}
