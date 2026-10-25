#!/usr/bin/env bash
# Publie les quatre secrets de signature de jeu-reconnaissance (même principe que notes-de-frais).
# Crée la clé au premier lancement. Les mots de passe sont lus dans le fichier local et jamais affichés.
# Prérequis : keytool (JDK) et gh connecté (gh auth login) avec accès au dépôt.
#
# GARDEZ une copie de la clé (reconnaissance-release.keystore + .env) : la perdre, c'est ne plus
# pouvoir publier de mise à jour installable par-dessus.
set -euo pipefail
REPO="ClawFabriceH92/jeu-reconnaissance"
DIR="${SECRETS_DIR:-$HOME/.secrets/keystores-android}"
KS="$DIR/reconnaissance-release.keystore"
ENVF="$DIR/reconnaissance-keystore.env"

if [ ! -f "$KS" ]; then
  mkdir -p "$DIR" && chmod 700 "$DIR"
  PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias reconnaissance \
    -keyalg RSA -keysize 4096 -validity 10000 \
    -storepass "$PASS" -keypass "$PASS" \
    -dname "CN=Reconnaissance, O=jeu-reconnaissance, C=FR" >/dev/null
  printf 'STORE_PASSWORD=%s\nKEY_ALIAS=reconnaissance\n' "$PASS" > "$ENVF"
  chmod 600 "$KS" "$ENVF"
  echo "Clé créée : $KS"
fi

PASS=$(grep '^STORE_PASSWORD=' "$ENVF" | cut -d= -f2-)
ALIAS=$(grep '^KEY_ALIAS=' "$ENVF" | cut -d= -f2-)

gh secret set RECONNAISSANCE_KEYSTORE_B64 --repo "$REPO" --body "$(base64 -w0 "$KS")"
gh secret set RECONNAISSANCE_KEYSTORE_PASSWORD --repo "$REPO" --body "$PASS"
gh secret set RECONNAISSANCE_KEY_PASSWORD --repo "$REPO" --body "$PASS"
gh secret set RECONNAISSANCE_KEY_ALIAS --repo "$REPO" --body "$ALIAS"

gh secret list --repo "$REPO"
