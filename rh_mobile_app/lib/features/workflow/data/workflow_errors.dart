import 'package:dio/dio.dart';

import '../../../core/widgets/rh_ui.dart';

/// Extrait le code d'erreur fonctionnel serveur (`ErrorResponse.code`), ou null.
String? codeErreurWorkflow(Object erreur) {
  if (erreur is DioException) {
    final data = erreur.response?.data;
    if (data is Map) {
      final code = data['code']?.toString().trim();
      if (code != null && code.isNotEmpty) return code;
    }
  }
  return null;
}

/// Extrait le message serveur exact (`ErrorResponse.message`), ou null.
String? messageServeurWorkflow(Object erreur) {
  if (erreur is DioException) {
    final data = erreur.response?.data;
    if (data is Map) {
      final msg = data['message']?.toString().trim();
      if (msg != null && msg.isNotEmpty) return msg;
    }
  }
  return null;
}

/// Extrait le code HTTP s'il provient de Dio.
int? statutHttpWorkflow(Object erreur) {
  if (erreur is DioException) {
    return erreur.response?.statusCode;
  }
  return null;
}

String _messageOuRepli(Object erreur, String fallback) {
  if (erreur is DioException) {
    return rhErrorMessage(erreur);
  }
  final texte = '$erreur'.replaceFirst('Exception: ', '').trim();
  if (texte.isEmpty || texte == 'Exception') return fallback;
  return texte;
}

/// Message utilisateur pour une erreur lors de la soumission d'une décision (`POST /workflow/taches/{id}/decision`).
///
/// Règles D-W8-04 :
/// - `409` → « Déjà traitée par quelqu'un d'autre »
/// - `422` → message exact du serveur conservé tel quel
/// - `403` / `404` → message explicite français
String messageErreurDecision(Object erreur) {
  final http = statutHttpWorkflow(erreur);
  final code = codeErreurWorkflow(erreur);
  final serveur = messageServeurWorkflow(erreur);

  if (http == 409 ||
      code == 'TACHE_DEJA_TRAITEE' ||
      code == 'MODIFIE_ENTRE_TEMPS' ||
      code == 'CONFLIT_VERSION') {
    return 'Déjà traitée par quelqu’un d’autre.';
  }
  if (http == 422 && serveur != null) {
    return serveur;
  }
  if (http == 403 || code == 'AUTO_DECISION_INTERDITE') {
    return serveur ?? 'Vous n’êtes pas autorisé à décider sur cette tâche.';
  }
  if (http == 404 || code == 'TACHE_INTROUVABLE') {
    return 'Cette tâche est introuvable ou n’existe plus.';
  }
  return _messageOuRepli(erreur, 'Impossible d’enregistrer votre décision.');
}

/// Message utilisateur pour une erreur sur l'envoi d'un complément (`POST .../complement`).
String messageErreurComplement(Object erreur) {
  final http = statutHttpWorkflow(erreur);
  final serveur = messageServeurWorkflow(erreur);
  if (http == 409) {
    return serveur ?? 'Cette demande a déjà été mise à jour.';
  }
  if (http == 422 && serveur != null) {
    return serveur;
  }
  if (http == 403) {
    return serveur ?? 'Seul le demandeur peut compléter cette demande.';
  }
  if (http == 404) {
    return 'La demande associée est introuvable.';
  }
  return _messageOuRepli(erreur, 'Impossible d’envoyer le complément.');
}

/// Message utilisateur pour une erreur de délégation (`POST/DELETE /workflow/delegations/moi`).
String messageErreurDelegation(Object erreur) {
  final http = statutHttpWorkflow(erreur);
  final code = codeErreurWorkflow(erreur);
  final serveur = messageServeurWorkflow(erreur);

  if (http == 409 || code == 'DELEGATION_CHEVAUCHEMENT') {
    return serveur ?? 'Une délégation active ou planifiée chevauche déjà ces dates.';
  }
  if (http == 422 || code == 'DELEGATION_INVALIDE') {
    return serveur ?? 'Les paramètres de la délégation sont invalides.';
  }
  if (http == 403) {
    return serveur ?? 'Action non autorisée sur cette délégation.';
  }
  if (http == 404) {
    return 'Délégation introuvable.';
  }
  return _messageOuRepli(erreur, 'Impossible d’enregistrer la délégation.');
}

/// Message générique de consultation workflow.
String messageErreurWorkflowGenerique(Object erreur, {String fallback = 'Service workflow momentanément indisponible.'}) {
  final http = statutHttpWorkflow(erreur);
  if (http == 403) return 'Accès non autorisé à ce dossier workflow.';
  if (http == 404) return 'Élément introuvable.';
  return _messageOuRepli(erreur, fallback);
}
