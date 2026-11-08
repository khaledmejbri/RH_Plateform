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
  final type = n.objetType;
  if (type != null && type.isNotEmpty) {
    final id = n.objetId;
    final idValide = id != null && _uuid.hasMatch(id);
    switch (type) {
      case 'DEMANDE':
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
      case 'HEURES_SUP':
        return '/heures-sup/moi';
      default:
        // ALERTE_ECART et types inconnus : pas d'écran mobile (alertes réservées à la Plateforme RH).
        return null;
    }
  }
  return _routeHeritee(n);
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
