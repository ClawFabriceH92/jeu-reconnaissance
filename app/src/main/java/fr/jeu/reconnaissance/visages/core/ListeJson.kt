package fr.jeu.reconnaissance.visages.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lecture / écriture de liste.json.
 *
 * Format écrit :
 * {
 *   "format": "jeu-reconnaissance", "version": 1,
 *   "personnes": [
 *     { "id": "01", "prenom": "Marie", "nom": "Dupont", "genre": "F",
 *       "photo": "photos/photo_01_Marie_Dupont.jpg" }
 *   ]
 * }
 *
 * En lecture, un tableau seul est aussi accepté, ainsi que quelques variantes de clés
 * (prénom, firstName, sexe, fichier…).
 */
object ListeJson {
    const val FORMAT = "jeu-reconnaissance"
    const val VERSION = 1

    fun lire(texte: String): List<Personne> {
        val t = texte.trimStart('﻿').trim()
        val tableau = try {
            if (t.startsWith("[")) {
                JSONArray(t)
            } else {
                val o = JSONObject(t)
                o.optJSONArray("personnes") ?: o.optJSONArray("liste") ?: o.optJSONArray("people")
                    ?: throw PackInvalide("liste.json : tableau « personnes » introuvable")
            }
        } catch (e: org.json.JSONException) {
            throw PackInvalide("liste.json illisible : ${e.message}")
        }

        val resultat = mutableListOf<Personne>()
        val ids = mutableSetOf<String>()
        for (i in 0 until tableau.length()) {
            val o = tableau.optJSONObject(i) ?: continue
            val prenom = o.texte("prenom", "prénom", "firstName", "first_name")
            val nom = o.texte("nom", "lastName", "last_name")
            val photo = o.texte("photo", "fichier", "image", "file")
            if (photo.isBlank() || (prenom.isBlank() && nom.isBlank())) continue

            val base = o.texte("id").ifBlank { (i + 1).toString().padStart(2, '0') }
            var id = base
            var n = 2
            while (!ids.add(id)) id = "$base-${n++}"

            resultat += Personne(
                id = id,
                prenom = prenom,
                nom = nom,
                genre = Genre.depuis(o.texte("genre", "sexe", "gender")),
                photo = photo.replace('\\', '/'),
            )
        }
        return resultat
    }

    fun ecrire(personnes: List<Personne>): String {
        val tableau = JSONArray()
        personnes.forEach { p ->
            tableau.put(
                JSONObject()
                    .put("id", p.id)
                    .put("prenom", p.prenom)
                    .put("nom", p.nom)
                    .put("genre", p.genre.code)
                    .put("photo", p.photo)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("personnes", tableau)
            .toString(2)
    }

    private fun JSONObject.texte(vararg cles: String): String {
        for (cle in cles) {
            val v = opt(cle)
            if (v != null && v != JSONObject.NULL) return v.toString().trim()
        }
        return ""
    }
}
