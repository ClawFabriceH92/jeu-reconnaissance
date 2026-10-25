package fr.jeu.reconnaissance.visages.core

import java.text.Normalizer

/**
 * Genre déduit du prénom, d'après le Fichier des prénoms de l'Insee (assets/prenoms_genre.tsv,
 * généré par scripts/generer_prenoms.py). Seuls les prénoms nettement non mixtes y figurent :
 * pour les autres (Camille, Dominique…), le genre reste à préciser.
 */
class GenresPrenoms(private val table: Map<String, Genre>) {

    /** Genre du prénom, ou INCONNU. « Jean-Pierre » est cherché tel quel puis par « Jean ». */
    fun genre(prenom: String): Genre {
        val c = cle(prenom)
        if (c.isEmpty()) return Genre.INCONNU
        table[c]?.let { return it }
        val premier = c.split('-', ' ').firstOrNull { it.isNotEmpty() } ?: return Genre.INCONNU
        return table[premier] ?: Genre.INCONNU
    }

    val taille get() = table.size

    companion object {
        val VIDE = GenresPrenoms(emptyMap())

        fun lire(texte: String): GenresPrenoms {
            val table = HashMap<String, Genre>()
            texte.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { ligne ->
                    val (prenom, g) = ligne.split('\t').takeIf { it.size >= 2 } ?: return@forEach
                    val genre = Genre.depuis(g)
                    if (genre != Genre.INCONNU) table[cle(prenom)] = genre
                }
            return GenresPrenoms(table)
        }

        fun cle(prenom: String): String =
            Normalizer.normalize(prenom.trim().uppercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\s+"), " ")
    }
}
