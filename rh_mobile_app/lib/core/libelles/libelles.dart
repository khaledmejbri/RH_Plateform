/// Table unique code -> libellé français de RH Connect (D-L01.b, règles R3, R5, R6).
///
/// Lot 3 (D-R02, D-R03) : les libellés des listes paramétrables (types de congé et de document,
/// statuts, familles, niveaux, échelle des étoiles, appréciations) viennent du serveur
/// (voir `LibellesService`) et sont chargés avec [charger]. Il n'existe AUCUNE liste de secours :
/// tant qu'une liste n'est pas chargée, ou si le code n'y figure pas, le résultat est
/// [Libelles.valeurNonReconnue], jamais le code brut ni un statut par défaut.
/// Quand l'API fournit déjà un libellé (ex. `famille_metier_libelle`), il est prioritaire :
/// voir [Libelles.avecRepli]. Seuls les libellés non paramétrables (étapes de suivi, actions
/// d'historique, types de nœud, état du temps de travail) restent dans ce fichier.
library;

import 'package:flutter/foundation.dart';

/// Noms des référentiels servis par les `GET libelles` des trois services (ADR lot 3, § 7).
class Referentiel {
  Referentiel._();

  static const typeDemande = 'TYPE_DEMANDE';
  static const typeConge = 'TYPE_CONGE';
  static const statutDemande = 'STATUT_DEMANDE';
  static const typeDocument = 'TYPE_DOCUMENT';
  static const statutDocument = 'STATUT_DOCUMENT';
  static const statutFormation = 'STATUT_FORMATION';
  static const statutPlainte = 'STATUT_PLAINTE';
  static const typePlainte = 'TYPE_PLAINTE';
  static const profilAcces = 'PROFIL_ACCES';
  static const familleMetier = 'FAMILLE_METIER';
  static const niveauSeniorite = 'NIVEAU_SENIORITE';
  static const statutEvaluation = 'STATUT_EVALUATION';
  static const echelleEtoiles = 'ECHELLE_ETOILES';
  static const appreciation = 'APPRECIATION';
}

/// Une valeur d'un référentiel paramétré, telle que reçue de l'API (snake_case).
@immutable
class ParametreValeur {
  const ParametreValeur({
    required this.code,
    required this.libelle,
    this.description,
    this.couleur,
    this.ordre = 0,
    this.actif = true,
    this.slaHeures,
    this.pieceJointeObligatoire = false,
  });

  final String code;
  final String libelle;
  final String? description;
  final String? couleur;
  final int ordre;
  final bool actif;
  final int? slaHeures;

  /// Types de congé : le serveur exige une pièce jointe (le bouton d'envoi la réclame aussi).
  final bool pieceJointeObligatoire;

  /// Lit une valeur ; renvoie null si `code` ou `libelle` manque (valeur ignorée, jamais devinée).
  static ParametreValeur? depuisJson(Object? brut) {
    if (brut is! Map) return null;
    final code = brut['code']?.toString().trim() ?? '';
    final libelle = brut['libelle']?.toString().trim() ?? '';
    if (code.isEmpty || libelle.isEmpty) return null;
    int entier(Object? v) => v is num ? v.toInt() : (int.tryParse('${v ?? ''}') ?? 0);
    final sla = brut['sla_heures'];
    return ParametreValeur(
      code: code,
      libelle: libelle,
      description: brut['description']?.toString(),
      couleur: brut['couleur']?.toString(),
      ordre: entier(brut['ordre']),
      actif: brut['actif'] is bool ? brut['actif'] as bool : true,
      slaHeures: sla is num ? sla.toInt() : null,
      pieceJointeObligatoire: brut['piece_jointe_obligatoire'] == true,
    );
  }
}

/// Réponse d'un `GET libelles` : version (ETag faible) et valeurs par référentiel.
class ParametresReponse {
  const ParametresReponse({required this.version, required this.referentiels});

  final String? version;
  final Map<String, List<ParametreValeur>> referentiels;

  /// Lit `{ version, referentiels: { NOM: [valeur…] } }`. Une charge utile qui n'est pas un objet
  /// ou sans `referentiels` est une erreur (FormatException) : on ne remplace pas une table par du vide.
  static ParametresReponse depuisJson(Object? brut) {
    if (brut is! Map || brut['referentiels'] is! Map) {
      throw const FormatException('Réponse des libellés illisible.');
    }
    final res = <String, List<ParametreValeur>>{};
    (brut['referentiels'] as Map).forEach((nom, liste) {
      if (liste is! List) return;
      final valeurs = <ParametreValeur>[];
      for (final brutV in liste) {
        final v = ParametreValeur.depuisJson(brutV);
        if (v != null) valeurs.add(v);
      }
      valeurs.sort((a, b) => a.ordre.compareTo(b.ordre));
      res['$nom'.toUpperCase()] = valeurs;
    });
    return ParametresReponse(version: brut['version']?.toString(), referentiels: res);
  }
}

class Libelles {
  Libelles._();

  static const String valeurNonReconnue = 'Valeur non reconnue';

  /// Valeurs chargées depuis l'API : référentiel -> valeurs (ordre serveur).
  static final Map<String, List<ParametreValeur>> _tables = {};

  /// Incrémenté à chaque changement de table (les écrans qui veulent se redessiner peuvent l'écouter).
  static final ValueNotifier<int> version = ValueNotifier<int>(0);

  /// Remplace les valeurs de [referentiels] (celles reçues d'un service). Les autres restent.
  static void charger(Map<String, List<ParametreValeur>> referentiels) {
    referentiels.forEach((nom, valeurs) {
      _tables[nom.toUpperCase()] = List<ParametreValeur>.unmodifiable(valeurs);
    });
    version.value++;
  }

  /// Vide toutes les tables (tests, déconnexion).
  static void reinitialiser() {
    _tables.clear();
    version.value++;
  }

  /// Valeurs actives d'un référentiel, dans l'ordre du serveur ; vide si non chargé.
  static List<ParametreValeur> valeurs(String referentiel) =>
      [for (final v in _tables[referentiel.toUpperCase()] ?? const <ParametreValeur>[]) if (v.actif) v];

  /// Une valeur par son code ; null si le référentiel n'est pas chargé ou le code absent.
  static ParametreValeur? valeur(String referentiel, String? code) {
    if (code == null) return null;
    final c = code.trim();
    if (c.isEmpty) return null;
    final liste = _tables[referentiel.toUpperCase()];
    if (liste == null) return null;
    for (final v in liste) {
      if (v.code == c) return v;
    }
    for (final v in liste) {
      if (v.code.toUpperCase() == c.toUpperCase()) return v;
    }
    return null;
  }

  /// Libellé d'une table fixe (non paramétrable) ; [valeurNonReconnue] si le code est absent.
  static String _fixe(Map<String, String> table, String? code) {
    final c = code?.trim() ?? '';
    if (c.isEmpty) return valeurNonReconnue;
    return table[c] ?? table[c.toUpperCase()] ?? valeurNonReconnue;
  }

  /// Libellé d'un référentiel chargé ; [valeurNonReconnue] si le code est absent.
  static String _de(String referentiel, String? code) =>
      valeur(referentiel, code)?.libelle ?? valeurNonReconnue;

  /// Libellé fourni par l'API s'il est lisible, sinon celui de la table, sinon [valeurNonReconnue].
  static String avecRepli(String? libelleApi, String? code, String Function(String?) parTable) {
    final l = libelleApi?.trim();
    if (l != null && l.isNotEmpty && !ressembleAUnCode(l)) return l;
    return parTable(code);
  }

  /// Vrai si le texte est un code brut (`DEV_BACKEND`, `EN_ANALYSE`) ou un identifiant technique.
  static bool ressembleAUnCode(String texte) {
    final t = texte.trim();
    if (t.isEmpty) return false;
    return RegExp(r'^[A-Z0-9]+(_[A-Z0-9]+)+$').hasMatch(t) ||
        RegExp(r'^[A-Z]{3,}$').hasMatch(t) ||
        RegExp(r'^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$').hasMatch(t);
  }

  /// Remplace les sigles internes d'un texte écrit par le serveur : « RRH » -> « RH », « RO » -> « responsable ».
  static String assainir(String texte) {
    return texte
        .replaceAllMapped(RegExp(r'\bRRH\b'), (_) => 'RH')
        .replaceAllMapped(RegExp(r'\bRO\b'), (_) => 'responsable');
  }

  // ---------------------------------------------------------------- Demandes M01

  static String typeDemande(String? code) => _de(Referentiel.typeDemande, code);

  static String typeConge(String? code) => _de(Referentiel.typeConge, code);

  /// Titre d'une demande : « Congé annuel » pour un congé dont le type est connu, sinon le type de demande.
  /// Le libellé du type de congé servi avec la demande ([typeCongeLibelleApi]) est prioritaire : il reste
  /// juste pour un type désactivé depuis (absent du référentiel chargé).
  static String titreDemande(String? typeDemandeCode, {String? typeCongeCode, String? typeCongeLibelleApi}) {
    if (typeDemandeCode == 'CONGE') {
      final aCode = typeCongeCode != null && typeCongeCode.trim().isNotEmpty;
      final aLibelle = typeCongeLibelleApi != null && typeCongeLibelleApi.trim().isNotEmpty;
      if (aCode || aLibelle) return avecRepli(typeCongeLibelleApi, typeCongeCode, typeConge);
    }
    return typeDemande(typeDemandeCode);
  }

  static String statutDemande(String? code) => _de(Referentiel.statutDemande, code);

  static const Map<String, String> _etapesSuivi = {
    'DEPOT': 'Dépôt',
    'RO': 'Validation par le responsable',
    'RRH': 'Validation RH',
    'CLOTURE': 'Clôture',
    'TRANSMISE_RRH': 'Transmission aux RH',
    'FILE_ATTENTE': 'File d’attente',
    'TRAITEMENT_RH': 'Traitement par les RH',
  };

  /// Libellé d'une étape de suivi. Le libellé de l'API (« Validation — Omar Belhadj ») est gardé
  /// s'il est lisible, après remplacement des sigles ; le code n'est jamais affiché.
  /// Pour l'étape `RRH`, le libellé court de la table (« Validation RH ») prime sur « Approbation RRH ».
  static String etapeSuivi(String? code, [String? libelleApi]) {
    final l = libelleApi?.trim();
    if (code == 'RRH' || code == 'DEPOT') {
      return _fixe(_etapesSuivi, code);
    }
    if (l != null && l.isNotEmpty && !ressembleAUnCode(l)) {
      return assainir(l);
    }
    return _fixe(_etapesSuivi, code);
  }

  static const Map<String, String> _actionsHistorique = {
    'CREATION_DEMANDE': 'Demande créée',
    'SOUMISE_A_RO': 'Soumise au responsable',
    'VALIDATION_RO': 'Validée par le responsable',
    'REFUS_RO': 'Refusée par le responsable',
    'VALIDATION_RRH': 'Validée par la RH',
    'APPROBATION_RRH': 'Approuvée par la RH',
    'REFUS_RRH': 'Refusée par la RH',
    'ANNULATION_DEMANDEUR': 'Annulée par le demandeur',
    'CLOTUREE': 'Clôturée',
    'SOUMISE_A_RH': 'Soumise à la RH',
    'NOTIFICATION_ENVOYEE': 'Notification envoyée',
    'INTEGRATION_PLAN': 'Intégrée au plan de formation',
    'EVALUATION_CHAUDE_ENVOYEE': 'Évaluation à chaud envoyée',
    'EVALUATION_CHAUDE_RECUE': 'Évaluation à chaud reçue',
    'EVALUATION_FROIDE_ENVOYEE': 'Évaluation à froid envoyée',
    'EVALUATION_FROIDE_RECUE': 'Évaluation à froid reçue',
    'FORMATION_TERMINEE': 'Formation terminée',
  };

  static String actionHistorique(String? code) => _fixe(_actionsHistorique, code);

  /// Codes à masquer dans un commentaire : `MOT_MOT`, `CONGE` et les sigles internes (RRH, RO).
  /// Les autres sigles (CNSS, DRH…) restent visibles.
  static final RegExp _codeDansCommentaire =
      RegExp(r'\b[A-Z0-9]+(?:_[A-Z0-9]+)+\b|\b(?:CONGE|RRH|RO)\b');

  /// Commentaire d'historique écrit par le serveur. Un ancien commentaire stocké qui contient un
  /// code (« Demande CONGE créée », « Approbation RRH ») n'est pas affiché (null) : l'action
  /// traduite suffit. Un commentaire lisible est gardé après remplacement des sigles.
  static String? commentaireHistorique(String? commentaire) {
    final c = commentaire?.trim();
    if (c == null || c.isEmpty) return null;
    if (_codeDansCommentaire.hasMatch(c)) return null;
    return assainir(c);
  }

  // ---------------------------------------------------------------- Documents

  static String typeDocument(String? code) => _de(Referentiel.typeDocument, code);

  static String statutDocument(String? code) => _de(Referentiel.statutDocument, code);

  // ---------------------------------------------------------------- Formations

  static String statutFormation(String? code) => _de(Referentiel.statutFormation, code);

  // ---------------------------------------------------------------- Plaintes

  static String statutPlainte(String? code) => _de(Referentiel.statutPlainte, code);

  static String typePlainte(String? code) => _de(Referentiel.typePlainte, code);

  // ---------------------------------------------------------------- Évaluations

  /// Pseudo-statut propre au mobile (M-74) : le serveur ne le sert pas, c'est la vue du responsable
  /// quand le collaborateur n'a pas encore validé. Libellé local fixe, hors référentiel.
  static const String statutEnAttenteCollaborateur = 'EN_ATTENTE_COLLABORATEUR';
  static const String _libelleEnAttenteCollaborateur = 'En attente du collaborateur';

  static String statutEvaluation(String? code) {
    if (code?.trim() == statutEnAttenteCollaborateur) return _libelleEnAttenteCollaborateur;
    return _de(Referentiel.statutEvaluation, code);
  }

  static String niveauSeniorite(String? code) => _de(Referentiel.niveauSeniorite, code);

  /// Famille métier : libellé de l'API (prioritaire), sinon référentiel chargé, sinon [valeurNonReconnue].
  static String familleMetier(String? code, [String? libelleApi]) =>
      avecRepli(libelleApi, code, (c) => _de(Referentiel.familleMetier, c));

  /// Nom du niveau d'une note de 1 à 5 (référentiel `ECHELLE_ETOILES`, codes `1` à `5`).
  static String niveauNote(int note) => _de(Referentiel.echelleEtoiles, '$note');

  /// « 3 — Autonome » (R9).
  static String noteAvecNiveau(int note) => '$note — ${niveauNote(note)}';

  // ---------------------------------------------------------------- Temps de travail

  /// Appréciation d'une note sur 20. Les bornes (7, 10, 14, 17) sont des règles de calcul non
  /// paramétrables ; le libellé de chaque code vient du référentiel `APPRECIATION`.
  /// Chaîne vide sans note ; [valeurNonReconnue] si le référentiel n'est pas chargé.
  static String appreciation(int? scoreSur20) {
    if (scoreSur20 == null) return '';
    final code = scoreSur20 <= 7
        ? 'INSUFFISANT'
        : scoreSur20 <= 10
            ? 'A_AMELIORER'
            : scoreSur20 <= 14
                ? 'SATISFAISANT'
                : scoreSur20 <= 17
                    ? 'POSITIF'
                    : 'EXCELLENT';
    return _de(Referentiel.appreciation, code);
  }

  static const Map<String, String> _etatsTempsTravail = {
    'COMPLET': 'Complet',
    'EN_COURS': 'En cours',
    'INCOMPLET': 'Pointage incomplet',
  };

  static String etatTempsTravail(String? code) => _fixe(_etatsTempsTravail, code);

  // ---------------------------------------------------------------- Rôles (D-H06, R7)

  static String profilAcces(String? code) => _de(Referentiel.profilAcces, code);

  /// Normalise un type de nœud (« Direction Générale », `DIRECTION_GENERALE`) en code (`DIRECTION_GENERALE`).
  static String codeTypeNoeud(String? brut) {
    var t = (brut ?? '').trim().toUpperCase();
    const accents = {'É': 'E', 'È': 'E', 'Ê': 'E', 'Ë': 'E', 'À': 'A', 'Â': 'A', 'Î': 'I', 'Ï': 'I', 'Ô': 'O', 'Û': 'U', 'Ù': 'U', 'Ç': 'C'};
    accents.forEach((k, v) => t = t.replaceAll(k, v));
    t = t.replaceAll(RegExp(r'[\s\-]+'), '_');
    if (t == 'DG') return 'DIRECTION_GENERALE';
    return t;
  }

  static const Map<String, String> _typesNoeud = {
    'DIRECTION_GENERALE': 'Direction générale',
    'DIRECTION': 'Direction',
    'DEPARTEMENT': 'Département',
    'SERVICE': 'Service',
  };

  /// Libellé du type d'un nœud d'organigramme ; [valeurNonReconnue] si inconnu.
  static String typeNoeud(String? brut) => _fixe(_typesNoeud, codeTypeNoeud(brut));

  /// Intitulé du responsable d'un nœud (D-H06) : `titre_poste` du nœud s'il est renseigné, sinon
  /// dérivé du type du nœud managé (D-H04). [feminin] accorde « Directrice » quand le genre est connu.
  /// Chaîne vide si le type est inconnu (l'écran affiche alors le poste de la personne).
  static String intituleResponsable(String? typeNoeud, {String? titrePoste, bool? feminin}) {
    final t = titrePoste?.trim();
    if (t != null && t.isNotEmpty) return t;
    final f = feminin == true;
    switch (codeTypeNoeud(typeNoeud)) {
      case 'DIRECTION_GENERALE':
        return f ? 'Directrice générale' : 'Directeur général';
      case 'DIRECTION':
        return f ? 'Directrice' : 'Directeur';
      case 'DEPARTEMENT':
        return 'Chef de département';
      case 'SERVICE':
        return 'Chef de service';
      default:
        return '';
    }
  }
}
