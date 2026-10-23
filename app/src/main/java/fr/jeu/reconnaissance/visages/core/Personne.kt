package fr.jeu.reconnaissance.visages.core

enum class Genre(val code: String) {
    HOMME("H"),
    FEMME("F"),
    INCONNU("");

    companion object {
        private val HOMMES = setOf("h", "m", "homme", "masculin", "male", "man", "garcon", "garçon")
        private val FEMMES = setOf("f", "femme", "feminin", "féminin", "female", "woman", "fille")

        fun depuis(valeur: String?): Genre {
            val v = valeur?.trim()?.lowercase().orEmpty()
            return when (v) {
                in HOMMES -> HOMME
                in FEMMES -> FEMME
                else -> INCONNU
            }
        }
    }
}

/** Une personne à reconnaître. [photo] est un chemin relatif au dossier de la base (« photos/… »). */
data class Personne(
    val id: String,
    val prenom: String,
    val nom: String,
    val genre: Genre,
    val photo: String,
) {
    val nomComplet: String
        get() = listOf(prenom, nom).filter { it.isNotBlank() }.joinToString(" ")
}

/** Progression du joueur : personnes cochées « connues » et niveau de difficulté. */
data class Progression(
    val connus: Set<String> = emptySet(),
    val niveau: Int = 1,
    val serie: Int = 0,
)
