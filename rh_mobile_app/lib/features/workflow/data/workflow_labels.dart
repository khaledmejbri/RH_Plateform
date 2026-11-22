import '../../../core/format/formats.dart';
import 'workflow_models.dart';

const String _inconnu = 'Valeur non reconnue';

String _lire(Map<String, String> table, String? code) {
  final c = code?.trim() ?? '';
  if (c.isEmpty) return kValeurAbsente;
  return table[c] ?? _inconnu;
}

const Map<String, String> _actionsDecision = {
  'APPROUVER': 'Approuver',
  'REFUSER': 'Refuser',
  'RENVOYER': 'Renvoyer',
  'FAIT': 'Marquer comme fait',
};

const Map<String, String> _originesCandidat = {
  'TITULAIRE': 'Titulaire',
  'DELEGUE': 'Délégué',
  'DELEGUE_AUTO': 'Délégué automatique',
  'ESCALADE': 'Escalade',
  'REAFFECTATION': 'Réaffectation',
};

const Map<String, String> _statutsEtapeSuivi = {
  'ACTIVE': 'En cours',
  'TERMINEE': 'Terminée',
  'SAUTEE': 'Non applicable',
  'ANNULEE': 'Annulée',
  'A_VENIR': 'À venir',
};

const Map<String, String> _resultatsEtape = {
  'APPROUVEE': 'Approuvée',
  'REFUSEE': 'Refusée',
  'RENVOYEE': 'Complément demandé',
  'FAITE': 'Faite',
  'EXPIREE': 'Expirée',
};

const Map<String, String> _raisonsSaut = {
  'CONDITION': 'Condition non remplie',
  'AUCUN_INTERVENANT': 'Aucun intervenant trouvé',
};

const Map<String, String> _statutsInstance = {
  'EN_COURS': 'En cours',
  'BLOQUEE': 'Bloquée',
  'APPROUVEE': 'Approuvée',
  'REFUSEE': 'Refusée',
  'ANNULEE': 'Annulée',
  'EXPIREE': 'Expirée',
  'ERREUR': 'En erreur',
};

const Map<String, String> _statutsTache = {
  'A_TRAITER': 'À traiter',
  'TRAITEE': 'Traitée',
  'EXPIREE': 'Expirée',
  'ANNULEE': 'Annulée',
};

const Map<String, String> _objetsTypes = {
  'CONGE': 'Congé',
  'AUTORISATION_SORTIE': 'Autorisation de sortie',
  'ORDRE_MISSION': 'Ordre de mission',
  'DOCUMENT': 'Document administratif',
  'FORMATION': 'Formation',
  'PLAINTE': 'Plainte / réclamation',
  'POINTAGE_MANUEL': 'Pointage manuel',
  'HEURES_SUP': 'Heures supplémentaires',
};

const Map<String, String> _statutsObjetWorkflow = {
  'SOUMISE': 'Soumise',
  'EN_VALIDATION_SUPERIEUR': 'Validation manager',
  'EN_VALIDATION_RRH': 'Validation RH',
  'EN_ATTENTE_FILE': 'En attente RH',
  'EN_TRAITEMENT_RH': 'En traitement RH',
  'EN_ATTENTE': 'En attente',
  'EN_COURS': 'En cours de validation',
  'A_COMPLETER': 'Complément demandé',
  'APPROUVEE': 'Approuvée',
  'VALIDEE': 'Validée',
  'DISPONIBLE': 'Disponible',
  'INTEGREE_PLAN': 'Intégrée au plan',
  'REFUSEE': 'Refusée',
  'REJETEE': 'Rejetée',
  'ANNULEE': 'Annulée',
  'EXPIREE': 'Expirée',
};

const Map<String, String> _clesResume = {
  'affichage_periode': 'Période',
  'affichage_duree': 'Durée',
  'affichage_type': 'Type',
  'affichage_heure': 'Heure',
  'affichage_signaux': 'Signaux',
  'affichage_sla': 'Échéance prévue',
  'affichage_motif': 'Motif',
  'affichage_destination': 'Destination',
  'affichage_cout': 'Coût estimé',
  'affichage_dates': 'Dates',
  'affichage_intitule': 'Intitulé',
  'affichage_beneficiaires': 'Bénéficiaires',
};

const List<String> typesObjetDelegables = [
  'CONGE',
  'AUTORISATION_SORTIE',
  'ORDRE_MISSION',
  'DOCUMENT',
  'FORMATION',
  'POINTAGE_MANUEL',
  'HEURES_SUP',
];

String libelleActionDecision(String? code) => _lire(_actionsDecision, code);
String libelleOrigineCandidat(String? code) => _lire(_originesCandidat, code);
String libelleOrigineTache(String? code) => libelleOrigineCandidat(code);
String libelleStatutEtapeSuivi(String? code) => _lire(_statutsEtapeSuivi, code);
String libelleResultatEtape(String? code) => _lire(_resultatsEtape, code);
String libelleRaisonSaut(String? code) => _lire(_raisonsSaut, code);
String libelleStatutInstance(String? code) => _lire(_statutsInstance, code);
String libelleStatutTache(String? code) => _lire(_statutsTache, code);
String libelleObjetType(String? code) {
  final c = code?.trim() ?? '';
  if (c.isEmpty) return kValeurAbsente;
  return _objetsTypes[c] ?? _inconnu;
}

String libelleStatutObjetWorkflow(String? code) => _lire(_statutsObjetWorkflow, code);

String libelleCleResume(String cle) {
  if (_clesResume.containsKey(cle)) return _clesResume[cle]!;
  final sansPrefixe = cle.startsWith('affichage_') ? cle.substring('affichage_'.length) : cle;
  final nettoye = sansPrefixe.replaceAll('_', ' ').trim();
  if (nettoye.isEmpty) return kValeurAbsente;
  return nettoye[0].toUpperCase() + nettoye.substring(1);
}

String libelleStatutDelegation(StatutDelegation s) {
  switch (s) {
    case StatutDelegation.active:
      return 'Active';
    case StatutDelegation.planifiee:
      return 'Planifiée';
    case StatutDelegation.expiree:
      return 'Expirée';
    case StatutDelegation.annulee:
      return 'Annulée';
  }
}

String libelleOrigineDelegation(String? origine) {
  switch (origine?.trim()) {
    case 'MANUELLE':
      return 'Manuelle';
    case 'AUTO_CONGE':
      return 'Automatique (congé)';
    case '':
    case null:
      return kValeurAbsente;
    default:
      return _inconnu;
  }
}

/// Règle de saisie d'un commentaire sur décision.
class RegleCommentaireDecision {
  const RegleCommentaireDecision({
    required this.obligatoire,
    required this.minLongueur,
    required this.maxLongueur,
  });

  final bool obligatoire;
  final int minLongueur;
  final int maxLongueur;
}

/// Retourne la règle de commentaire pour une [action] donnée et [commentaireObligatoireSi].
RegleCommentaireDecision regleCommentairePourAction(
  String action, {
  bool commentaireObligatoireSi = false,
}) {
  if (action == 'REFUSER' || action == 'RENVOYER') {
    return const RegleCommentaireDecision(
      obligatoire: true,
      minLongueur: 5,
      maxLongueur: 500,
    );
  }
  if (action == 'APPROUVER' && commentaireObligatoireSi) {
    return const RegleCommentaireDecision(
      obligatoire: true,
      minLongueur: 10,
      maxLongueur: 500,
    );
  }
  return const RegleCommentaireDecision(
    obligatoire: false,
    minLongueur: 0,
    maxLongueur: 500,
  );
}

/// Route mobile associée à un objet métier du workflow, ou null si non navigable.
String? routePourObjetWorkflow(String? objetType, String? objetId) {
  final type = objetType?.trim().toUpperCase() ?? '';
  final id = objetId?.trim() ?? '';
  final aId = id.isNotEmpty;
  switch (type) {
    case 'DEMANDE':
    case 'CONGE':
    case 'AUTORISATION_SORTIE':
    case 'ORDRE_MISSION':
      return aId ? '/demandes-admin/$id' : '/demandes-admin';
    case 'DOCUMENT':
      return aId ? '/documents/$id' : '/documents';
    case 'PLAINTE':
      return aId ? '/plaintes/$id' : '/plaintes';
    case 'FORMATION':
      return aId ? '/formations/$id' : '/formations';
    case 'POINTAGE_MANUEL':
      return aId ? '/pointage/manuels/$id' : '/pointage/manuel';
    case 'HEURES_SUP':
      return '/heures-sup/moi';
    default:
      return null;
  }
}

/// Extrait les entrées lisibles (`affichage_*`) d'un résumé JSON serveur.
List<MapEntry<String, String>> entreesResumeAffichables(Map<String, dynamic> resume) {
  final entrees = <MapEntry<String, String>>[];
  for (final e in resume.entries) {
    if (!e.key.startsWith('affichage_')) continue;
    final val = e.value?.toString().trim() ?? '';
    if (val.isEmpty) continue;
    entrees.add(MapEntry(libelleCleResume(e.key), val));
  }
  if (entrees.isNotEmpty) return entrees;
  for (final e in resume.entries) {
    final val = e.value?.toString().trim() ?? '';
    if (val.isEmpty) continue;
    entrees.add(MapEntry(libelleCleResume(e.key), val));
  }
  return entrees;
}
