-- Exécuté une seule fois, au premier démarrage du volume PostgreSQL.
-- Les tables sont créées par les services (ddl-auto=update) ; les données de démonstration
-- s'importent ensuite avec deploy/scripts/importer-donnees.sh.
CREATE DATABASE authentication_db;
CREATE DATABASE referentiel_rh_db;
CREATE DATABASE evaluation_rh_db;
CREATE DATABASE presence_db;
