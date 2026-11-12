import 'package:rh_mobile_app/core/libelles/libelles.dart';

/// Réponse type d'un `GET libelles` (format `Libelles` snake_case, ADR lot 3 § 7), pour les tests.
/// Données d'essai uniquement : l'application n'embarque aucune liste de secours.
Map<String, dynamic> libellesReferentielJson({String version = 'W/"v1"'}) => {
      'version': version,
      'referentiels': {
        'TYPE_CONGE': [
          {'code': 'ANNUEL', 'libelle': 'Congé annuel', 'ordre': 1, 'actif': true, 'piece_jointe_obligatoire': false},
          {'code': 'MALADIE', 'libelle': 'Congé maladie', 'ordre': 2, 'actif': true, 'piece_jointe_obligatoire': true},
          {'code': 'MARIAGE', 'libelle': 'Congé de mariage', 'ordre': 3, 'actif': true, 'piece_jointe_obligatoire': false},
        ],
        'TYPE_DOCUMENT': [
          {'code': 'ATTESTATION_TRAVAIL', 'libelle': 'Attestation de travail', 'ordre': 1, 'actif': true, 'sla_heures': 24},
          {'code': 'BULLETIN_PAIE', 'libelle': 'Bulletin de paie', 'ordre': 2, 'actif': true},
        ],
        'STATUT_DEMANDE': [
          {'code': 'SOUMISE', 'libelle': 'Soumise', 'ordre': 0, 'actif': true},
          {'code': 'EN_VALIDATION_SUPERIEUR', 'libelle': 'En attente du responsable', 'ordre': 1, 'actif': true},
          {'code': 'EN_VALIDATION_RRH', 'libelle': 'En attente de validation RH', 'ordre': 2, 'actif': true},
          {'code': 'APPROUVEE', 'libelle': 'Approuvée', 'ordre': 3, 'actif': true},
          {'code': 'REFUSEE', 'libelle': 'Refusée', 'ordre': 3, 'actif': true},
          {'code': 'ANNULEE', 'libelle': 'Annulée', 'ordre': 4, 'actif': true},
        ],
        'TYPE_DEMANDE': [
          {'code': 'CONGE', 'libelle': 'Congé', 'ordre': 1, 'actif': true},
          {'code': 'ORDRE_MISSION', 'libelle': 'Ordre de mission', 'ordre': 2, 'actif': true},
        ],
        'STATUT_DOCUMENT': [
          {'code': 'EN_ATTENTE_FILE', 'libelle': 'En file d’attente', 'ordre': 1, 'actif': true},
          {'code': 'EN_TRAITEMENT_RH', 'libelle': 'En traitement par la RH', 'ordre': 2, 'actif': true},
          {'code': 'DISPONIBLE', 'libelle': 'Disponible', 'ordre': 3, 'actif': true},
          {'code': 'REJETEE', 'libelle': 'Refusée', 'ordre': 4, 'actif': true},
        ],
        'STATUT_FORMATION': [
          {'code': 'EN_VALIDATION_RRH', 'libelle': 'En attente de validation RH', 'ordre': 1, 'actif': true},
          {'code': 'INTEGREE_PLAN', 'libelle': 'Intégrée au plan', 'ordre': 2, 'actif': true},
          {'code': 'REFUSEE', 'libelle': 'Refusée', 'ordre': 3, 'actif': true},
          {'code': 'ANNULEE', 'libelle': 'Annulée', 'ordre': 4, 'actif': true},
        ],
        'STATUT_PLAINTE': [
          {'code': 'NOUVEAU', 'libelle': 'Nouveau', 'ordre': 0, 'actif': true},
          {'code': 'EN_ANALYSE', 'libelle': 'En analyse', 'ordre': 1, 'actif': true},
          {'code': 'EN_TRAITEMENT', 'libelle': 'En traitement', 'ordre': 2, 'actif': true},
          {'code': 'RESOLU', 'libelle': 'Résolu', 'ordre': 3, 'actif': true},
          {'code': 'FERME', 'libelle': 'Fermé', 'ordre': 4, 'actif': true},
        ],
        'PROFIL_ACCES': [
          {'code': 'RH', 'libelle': 'Ressources humaines', 'ordre': 1, 'actif': true},
          {'code': 'DIRECTION', 'libelle': 'Direction (consultation)', 'ordre': 2, 'actif': true},
        ],
        'FAMILLE_METIER': [
          {'code': 'DEV_BACKEND', 'libelle': 'Développement backend', 'ordre': 1, 'actif': true},
          {'code': 'DEV_LOGICIEL', 'libelle': 'Développement logiciel', 'ordre': 2, 'actif': true},
        ],
        'NIVEAU_SENIORITE': [
          {'code': 'CONFIRME', 'libelle': 'Confirmé', 'ordre': 1, 'actif': true},
          {'code': 'SENIOR', 'libelle': 'Sénior', 'ordre': 2, 'actif': true},
          {'code': 'TEAM_LEAD', 'libelle': "Chef d'équipe", 'ordre': 3, 'actif': true},
        ],
      },
    };

Map<String, dynamic> libellesEvaluationJson({String version = 'W/"e1"'}) => {
      'version': version,
      'referentiels': {
        'STATUT_EVALUATION': [
          // Codes réels servis par svc-evaluation (ValeursInitialesEvaluation).
          {'code': 'EN_ATTENTE_VALIDATION_CROISEE', 'libelle': 'En cours', 'ordre': 1, 'actif': true},
          {'code': 'VALIDEE_COLLABORATEUR', 'libelle': 'Validée par le collaborateur', 'ordre': 2, 'actif': true},
          {'code': 'VALIDEE_SUPERIEUR', 'libelle': 'Validée par le responsable', 'ordre': 3, 'actif': true},
          {'code': 'VALIDEE', 'libelle': 'Validée', 'ordre': 4, 'actif': true},
          {'code': 'ARCHIVEE', 'libelle': 'Archivée', 'ordre': 5, 'actif': true},
        ],
        'ECHELLE_ETOILES': [
          {'code': '1', 'libelle': 'Débutant', 'ordre': 1, 'actif': true},
          {'code': '2', 'libelle': 'Notions', 'ordre': 2, 'actif': true},
          {'code': '3', 'libelle': 'Autonome', 'ordre': 3, 'actif': true},
          {'code': '4', 'libelle': 'Confirmé', 'ordre': 4, 'actif': true},
          {'code': '5', 'libelle': 'Expert', 'ordre': 5, 'actif': true},
        ],
        'APPRECIATION': [
          {'code': 'INSUFFISANT', 'libelle': 'Insuffisant', 'ordre': 1, 'actif': true},
          {'code': 'A_AMELIORER', 'libelle': 'À améliorer', 'ordre': 2, 'actif': true},
          {'code': 'SATISFAISANT', 'libelle': 'Satisfaisant', 'ordre': 3, 'actif': true},
          {'code': 'POSITIF', 'libelle': 'Positif', 'ordre': 4, 'actif': true},
          {'code': 'EXCELLENT', 'libelle': 'Excellent', 'ordre': 5, 'actif': true},
        ],
      },
    };

/// Charge les deux jeux d'essai dans [Libelles] (à appeler dans `setUp`, et `Libelles.reinitialiser` dans `tearDown`).
void chargerLibellesDeTest() {
  Libelles.charger(ParametresReponse.depuisJson(libellesReferentielJson()).referentiels);
  Libelles.charger(ParametresReponse.depuisJson(libellesEvaluationJson()).referentiels);
}
