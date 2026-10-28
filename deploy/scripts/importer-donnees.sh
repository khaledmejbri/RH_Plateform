#!/usr/bin/env bash
# À lancer sur le serveur, dans deploy/ : remplace les 4 bases par les exports de deploy/donnees/.
# Les services Java sont arrêtés pendant l'import puis relancés. Opération destructive pour les bases visées.
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICES_JAVA="svc-identite-acces svc-referentiel-rh svc-evaluation svc-presence svc-notification gateway"
DB_USER="$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2 || true)"
DB_USER="${DB_USER:-sirh}"

for base in authentication_db referentiel_rh_db evaluation_rh_db presence_db; do
  [ -f "donnees/$base.sql" ] || { echo "Fichier manquant : donnees/$base.sql"; exit 1; }
done

read -r -p "Les bases actuelles seront remplacées. Continuer ? (oui/non) " reponse
[ "$reponse" = "oui" ] || { echo "Annulé."; exit 0; }

docker compose stop $SERVICES_JAVA
for base in authentication_db referentiel_rh_db evaluation_rh_db presence_db; do
  echo "Import de $base…"
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d postgres \
    -c "DROP DATABASE IF EXISTS $base WITH (FORCE);" -c "CREATE DATABASE $base;"
  # Fichier envoyé par l'entrée standard : aucun chemin à traduire (Linux ou Git Bash sous Windows)
  docker compose exec -T postgres psql -q -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$base" < "donnees/$base.sql"
done
docker compose start $SERVICES_JAVA
echo "Import terminé. Les services redémarrent (1 à 2 minutes)."
