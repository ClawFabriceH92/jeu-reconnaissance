"""Génère app/src/main/assets/prenoms_genre.tsv depuis le Fichier des prénoms de l'Insee.

Source : Insee, « Fichier des prénoms » (état civil, naissances en France depuis 1900),
https://www.insee.fr/fr/statistiques/7633685 (référencé sur data.gouv.fr).

Seuls les prénoms nettement non mixtes sont gardés : au moins MIN_TOTAL naissances
et au moins PART_MIN des naissances d'un même sexe. Les autres (Camille, Dominique…)
restent « genre à préciser » dans l'app.
"""
import csv
import io
import os
import re
import sys
import unicodedata
import urllib.request
import zipfile
from collections import defaultdict

PAGE = "https://www.insee.fr/fr/statistiques/7633685"
SORTIE = "app/src/main/assets/prenoms_genre.tsv"
MIN_TOTAL = 20
PART_MIN = 0.95


def telecharger(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (jeu-reconnaissance)"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def cle(prenom):
    s = unicodedata.normalize("NFD", prenom.strip().upper())
    return "".join(c for c in s if unicodedata.category(c) != "Mn")


def main():
    url = sys.argv[1] if len(sys.argv) > 1 else None
    if url is None:
        html = telecharger(PAGE).decode("utf-8", "replace")
        liens = sorted(set(re.findall(r'/fr/statistiques/fichier/\d+/[^"\'\s]+\.zip', html)))
        print("Liens trouvés :", *liens, sep="\n  ")
        csv_nat = [l for l in liens if "nat" in l.lower() and "csv" in l.lower()]
        if not csv_nat:
            sys.exit("Aucun fichier national CSV trouvé sur la page")
        url = "https://www.insee.fr" + csv_nat[0]
    print("Téléchargement :", url)
    archive = zipfile.ZipFile(io.BytesIO(telecharger(url)))
    nom = next(n for n in archive.namelist() if n.lower().endswith(".csv"))
    brut = archive.read(nom)
    try:
        texte = brut.decode("utf-8-sig")
    except UnicodeDecodeError:
        texte = brut.decode("latin-1")
    lignes = csv.reader(io.StringIO(texte), delimiter=";")
    entete = [c.strip().lower() for c in next(lignes)]
    print("Fichier :", nom, "| colonnes :", entete)

    def col(*noms):
        for n in noms:
            if n in entete:
                return entete.index(n)
        sys.exit(f"Colonne absente parmi {noms}")

    i_sexe = col("sexe")
    i_prenom = col("preusuel", "prenom", "prénom")
    i_nombre = col("nombre", "valeur", "nb")

    comptes = defaultdict(lambda: [0, 0])  # [hommes, femmes]
    for n, ligne in enumerate(lignes):
        if n < 3:
            print("Exemple :", ligne)
        if len(ligne) <= max(i_sexe, i_prenom, i_nombre):
            continue
        prenom = ligne[i_prenom]
        if not prenom or prenom.startswith("_"):
            continue  # _PRENOMS_RARES
        try:
            nombre = int(float(ligne[i_nombre]))
        except ValueError:
            continue
        sexe = ligne[i_sexe].strip()
        if sexe in ("1", "M"):
            comptes[cle(prenom)][0] += nombre
        elif sexe in ("2", "F"):
            comptes[cle(prenom)][1] += nombre

    gardes = []
    for prenom, (h, f) in comptes.items():
        total = h + f
        if total < MIN_TOTAL or len(prenom) < 2:
            continue
        if f / total >= PART_MIN:
            gardes.append((prenom, "F"))
        elif h / total >= PART_MIN:
            gardes.append((prenom, "H"))
    gardes.sort()
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    with open(SORTIE, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"# Source : Insee, Fichier des prénoms ({url}). Part d'un sexe >= {PART_MIN}, >= {MIN_TOTAL} naissances.\n")
        for prenom, g in gardes:
            out.write(f"{prenom}\t{g}\n")
    print(f"{len(comptes)} prénoms lus, {len(gardes)} gardés")
    for test in ("MARIE", "JEAN", "CAMILLE", "DOMINIQUE", "HELENE", "JEAN-PIERRE", "CLAUDE", "SACHA"):
        print(test, comptes.get(test), dict(gardes).get(test, "-"))


if __name__ == "__main__":
    main()
