#!/usr/bin/env bash
# À lancer sur le PC de développement : exporte les 4 bases de recette (comptes, organigramme,
# évaluations, pointage) dans deploy/donnees/, pour les recharger sur le serveur de démonstration.
# Les exports contiennent des données personnelles et des empreintes de mots de passe :
# deploy/donnees/ est ignoré par Git, ne pas le publier.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p donnees

PG_CONTAINER="${PG_CONTAINER:-abonne-postgres}"
PG_HOST="${PG_HOST:-host.docker.internal}"
PG_PORT="${PG_PORT:-5444}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-password}"

for base in authentication_db referentiel_rh_db evaluation_rh_db presence_db; do
  echo "Export de $base…"
  docker exec -e PGPASSWORD="$PG_PASSWORD" "$PG_CONTAINER" \
    pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" --no-owner --no-privileges "$base" \
    > "donnees/$base.sql"
done
ls -lh donnees/
echo "Copier ensuite le dossier sur le serveur : scp -r deploy/donnees ubuntu@<IP>:~/sirh/deploy/"
