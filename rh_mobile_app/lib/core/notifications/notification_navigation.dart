import '../../features/evaluations/data/evaluation_models.dart';
import 'notification_model.dart';

final RegExp _uuid = RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
);

/// Route à ouvrir quand on touche une notification, ou `null` si elle ne mène à aucune page.
///
/// Priorité aux champs structurés `objet_type` / `objet_id` (ticket 2F). Sans type (anciennes
/// notifications), repli sur la déduction par mots-clés historique. L'identifiant doit être un UUID :
/// sinon on retombe sur la liste du module, jamais sur un chemin construit à partir d'un texte libre.
String? routePourNotification(AppNotification n) {
  final ev = n.evenement?.trim().toUpperCase();
  if (ev != null && ev.isNotEmpty) {
    switch (ev) {
      case 'WF_TACHE_CREEE':
      case 'WF_RELANCE':
      case 'WF_ESCALADE':
      case 'WF_TACHE_A_TRAITER':
        return '/taches';
      case 'WF_DELEGATION':
        return '/delegations';
      case 'WF_BLOQUEE':
        // Supervision back-office web uniquement (D-W8-07).
        return null;
      case 'WF_DECISION':
      case 'WF_FIN':
      case 'WF_INFORMATION':
        return _routeParObjetType(n) ?? _routeHeritee(n);
    }
  }
  return _routeParObjetType(n) ?? _routeHeritee(n);
}

String? _routeParObjetType(AppNotification n) {
  final type = n.objetType;
  if (type != null && type.isNotEmpty) {
    final id = n.objetId;
    final idValide = id != null && _uuid.hasMatch(id);
    switch (type) {
      case 'DEMANDE':
      case 'CONGE':
      case 'AUTORISATION_SORTIE':
      case 'ORDRE_MISSION':
        return idValide ? '/demandes-admin/$id' : '/demandes-admin';
      case 'DOCUMENT':
        return idValide ? '/documents/$id' : '/documents';
      case 'PLAINTE':
        return idValide ? '/plaintes/$id' : '/plaintes';
      case 'FORMATION':
        return idValide ? '/formations/$id' : '/formations';
      case 'EVALUATION':
        return idValide ? '/evaluations/$id' : '/evaluations';
      case 'POINTAGE_MANUEL':
        return idValide ? '/pointage/manuels/$id' : '/pointage/manuel';
      case 'SOLDE_CONGE':
        // Ajustement du solde : « Mon solde de congé » (objet_id nul).
        return '/conges/solde';
      case 'HEURES_SUP':
        return '/heures-sup/moi';
      case 'COLLABORATEUR':
        // Alerte d'échecs de pointage (D-P20) : le responsable ouvre la fiche d'équipe du collaborateur.
        return idValide ? '/equipe/$id' : '/equipe';
      default:
        // ALERTE_ECART et types inconnus : pas d'écran mobile (alertes réservées à la Plateforme RH).
        return null;
    }
  }
  return null;
}

String? _routeHeritee(AppNotification n) {
  final lien = EvaluationNotificationLink.tryParse(subject: n.subject, content: n.content);
  if (lien != null) {
    final id = lien.evaluationId;
    return (lien.hasDeepLink && id != null && _uuid.hasMatch(id)) ? '/evaluations/$id' : '/evaluations';
  }
  final texte = '${n.subject} ${n.content}'.toLowerCase();
  if (texte.contains('document')) return '/documents';
  if (texte.contains('plainte') || texte.contains('réclamation')) return '/plaintes';
  if (texte.contains('congé') || texte.contains('autorisation') || texte.contains('demande')) {
    return '/demandes-admin';
  }
  return null;
}
