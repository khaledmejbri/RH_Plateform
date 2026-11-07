/// Table unique code -> libellé français de RH Connect (D-L01.b, règles R3, R5, R6).
///
/// Tout code d'énumération renvoyé par l'API passe par ici. Un code inconnu donne
/// [Libelles.valeurNonReconnue] : jamais le code brut, jamais un statut par défaut.
/// Quand l'API fournit déjà un libellé (ex. `famille_metier_libelle`), il est prioritaire :
/// voir [Libelles.avecRepli]. Au lot 3, cette table sera alimentée par le paramétrage serveur
/// (voir [Libelles.surcharger]).
library;

class Libelles {
  Libelles._();

  static const String valeurNonReconnue = 'Valeur non reconnue';

  /// Surcharges fournies par l'API (lot 3) : catégorie -> code -> libellé.
  static final Map<String, Map<String, String>> _surcharges = {};

  /// Remplace ou ajoute des libellés d'une catégorie (ex. `statut_demande`) à partir de l'API.
  static void surcharger(String categorie, Map<String, String> libelles) {
    _surcharges[categorie] = Map<String, String>.from(libelles);
  }

  /// Vide les surcharges (tests, déconnexion).
  static void reinitialiser() => _surcharges.clear();

  static String? _chercher(String categorie, String? code, Map<String, String> table) {
    if (code == null) return null;
    final c = code.trim();
    if (c.isEmpty) return null;
    return _surcharges[categorie]?[c] ?? table[c] ?? table[c.toUpperCase()];
  }

  /// Libellé d'une table ; [valeurNonReconnue] si le code est absent de la table.
  static String _de(String categorie, String? code, Map<String, String> table) =>
      _chercher(categorie, code, table) ?? valeurNonReconnue;

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

  static const Map<String, String> _typesDemande = {
    'CONGE': 'Congé',
    'AUTORISATION_SORTIE': 'Autorisation de sortie',
    'ORDRE_MISSION': 'Ordre de mission',
  };

  static String typeDemande(String? code) => _de('type_demande', code, _typesDemande);

  static const Map<String, String> _typesConge = {
    'ANNUEL': 'Congé annuel',
    'MALADIE': 'Congé maladie',
    'MATERNITE': 'Congé maternité',
    'SANS_SOLDE': 'Congé sans solde',
    'AUTRE': 'Autre',
  };

  static String typeConge(String? code) => _de('type_conge', code, _typesConge);

  /// Titre d'une demande : « Congé annuel » pour un congé dont le type est connu, sinon le type de demande.
  static String titreDemande(String? typeDemandeCode, {String? typeCongeCode}) {
    if (typeDemandeCode == 'CONGE' && typeCongeCode != null && typeCongeCode.trim().isNotEmpty) {
      return typeConge(typeCongeCode);
    }
    return typeDemande(typeDemandeCode);
  }

  static const Map<String, String> _statutsDemande = {
    'SOUMISE': 'En attente de validation RH',
    'EN_VALIDATION_SUPERIEUR': 'En attente du responsable',
    'EN_VALIDATION_RRH': 'En attente de validation RH',
    'APPROUVEE': 'Approuvée',
    'REFUSEE': 'Refusée',
    'ANNULEE': 'Annulée',
  };

  static String statutDemande(String? code) => _de('statut_demande', code, _statutsDemande);

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
      return _de('etape_suivi', code, _etapesSuivi);
    }
    if (l != null && l.isNotEmpty && !ressembleAUnCode(l)) {
      return assainir(l);
    }
    return _de('etape_suivi', code, _etapesSuivi);
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

  static String actionHistorique(String? code) => _de('action_historique', code, _actionsHistorique);

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

  static const Map<String, String> _typesDocument = {
    'ATTESTATION_TRAVAIL': 'Attestation de travail',
    'ATTESTATION_SALAIRE': 'Attestation de salaire',
    'BULLETIN_PAIE': 'Bulletin de paie',
    'ATTESTATION_CNSS': 'Attestation CNSS',
    'FEUILLE_POINTAGE_MENSUELLE': 'Feuille de pointage mensuelle',
    'DOCUMENT_INTERNE': 'Document interne',
    'AUTRE': 'Autre document',
  };

  static String typeDocument(String? code) => _de('type_document', code, _typesDocument);

  static const Map<String, String> _statutsDocument = {
    'EN_ATTENTE_FILE': 'En file d’attente',
    'EN_TRAITEMENT_RH': 'En traitement par la RH',
    'DISPONIBLE': 'Disponible',
    'REJETEE': 'Refusée',
  };

  static String statutDocument(String? code) => _de('statut_document', code, _statutsDocument);

  // ---------------------------------------------------------------- Formations

  static const Map<String, String> _statutsFormation = {
    'EN_VALIDATION_RRH': 'En attente de validation RH',
    'INTEGREE_PLAN': 'Planifiée',
    'REFUSEE': 'Refusée',
    'ANNULEE': 'Annulée',
  };

  static String statutFormation(String? code) => _de('statut_formation', code, _statutsFormation);

  // ---------------------------------------------------------------- Plaintes

  static const Map<String, String> _statutsPlainte = {
    'NOUVEAU': 'Nouvelle',
    'EN_ANALYSE': 'En analyse',
    'EN_TRAITEMENT': 'En traitement',
    'RESOLU': 'Résolue',
    'FERME': 'Fermée',
    // Anciennes valeurs tolérées par l'écran de liste.
    'EN_COURS': 'En cours',
    'EN_ATTENTE': 'En attente',
    'RESOLUE': 'Résolue',
    'TRAITEE': 'Traitée',
    'REJETEE': 'Rejetée',
    'REFUSEE': 'Rejetée',
  };

  static String statutPlainte(String? code) => _de('statut_plainte', code, _statutsPlainte);

  static const Map<String, String> _typesPlainte = {
    'INTERNE': 'Interne',
    'EXTERNE': 'Externe',
  };

  static String typePlainte(String? code) => _de('type_plainte', code, _typesPlainte);

  // ---------------------------------------------------------------- Évaluations

  static const Map<String, String> _statutsEvaluation = {
    'EN_COURS': 'En cours',
    'EN_ATTENTE_COLLABORATEUR': 'En attente du collaborateur',
    'VALIDEE_COLLABORATEUR': 'Validée par le collaborateur',
    'VALIDEE_SUPERIEUR': 'Validée par le responsable',
    'VALIDEE': 'Validée',
    'EN_ATTENTE_VALIDATION_CROISEE': 'En cours',
    'ARCHIVEE': 'Archivée',
  };

  static String statutEvaluation(String? code) => _de('statut_evaluation', code, _statutsEvaluation);

  static const Map<String, String> _niveauxSeniorite = {
    'JUNIOR': 'Junior',
    'CONFIRME': 'Confirmé',
    'CONFIRMED': 'Confirmé',
    'MID': 'Confirmé',
    'SENIOR': 'Sénior',
    'TEAM_LEAD': "Chef d'équipe",
    'EXPERT': 'Expert',
  };

  static String niveauSeniorite(String? code) => _de('niveau_seniorite', code, _niveauxSeniorite);

  /// Familles métier : table de repli quand l'API ne fournit pas `famille_metier_libelle`.
  static const Map<String, String> _famillesMetier = {
    'EXPLOITATION': 'Exploitation et terrain assainissement',
    'GENIE_CIVIL': 'Génie civil et travaux',
    'DEV_LOGICIEL': 'Développement logiciel',
    'DEV_BACKEND': 'Développement backend',
    'DEV_FRONTEND': 'Développement frontend',
    'DEV_MOBILE': 'Développement mobile',
    'SUPPORT_ADMIN': 'Support administratif',
    'MAINTENANCE': 'Maintenance technique',
    'HSE_QUALITE': 'Hygiène, sécurité, environnement et qualité',
  };

  /// Famille métier : libellé de l'API (prioritaire), sinon table de repli, sinon [valeurNonReconnue].
  static String familleMetier(String? code, [String? libelleApi]) =>
      avecRepli(libelleApi, code, (c) => _de('famille_metier', c, _famillesMetier));

  static const Map<int, String> _niveauxNote = {
    1: 'Débutant',
    2: 'Notions',
    3: 'Autonome',
    4: 'Confirmé',
    5: 'Expert',
  };

  /// Nom du niveau d'une note de 1 à 5.
  static String niveauNote(int note) => _niveauxNote[note] ?? valeurNonReconnue;

  /// « 3 — Autonome » (R9).
  static String noteAvecNiveau(int note) => '$note — ${niveauNote(note)}';

  // ---------------------------------------------------------------- Pointage manuel, heures sup

  static const Map<String, String> _statutsDemandeSimple = {
    'EN_ATTENTE': 'En attente',
    'VALIDEE': 'Validée',
    'REFUSEE': 'Refusée',
    'ANNULEE': 'Annulée',
    'EXPIREE': 'Expirée',
  };

  static String statutDemandeSimple(String? code) =>
      _de('statut_demande_simple', code, _statutsDemandeSimple);

  static const Map<String, String> _etatsTempsTravail = {
    'COMPLET': 'Complet',
    'EN_COURS': 'En cours',
    'INCOMPLET': 'Pointage incomplet',
  };

  static String etatTempsTravail(String? code) => _de('etat_temps_travail', code, _etatsTempsTravail);

  // ---------------------------------------------------------------- Rôles (D-H06, R7)

  static const Map<String, String> _profilsAcces = {
    'COLLABORATEUR': 'Collaborateur',
    'RO': 'Responsable opérationnel',
    'RESPONSABLE': 'Responsable',
    'RH': 'Ressources humaines',
    'DIRECTION': 'Direction (consultation)',
    'ADMIN': 'Administrateur',
  };

  static String profilAcces(String? code) => _de('profil_acces', code, _profilsAcces);

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
  static String typeNoeud(String? brut) => _de('type_noeud', codeTypeNoeud(brut), _typesNoeud);

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
