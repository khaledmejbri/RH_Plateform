import 'package:dio/dio.dart';

/// Compteur du hub « À valider » : demandes M01 en attente + pointages manuels en attente (ticket 2E).
int compterAValider({required int demandes, required int pointagesManuels}) =>
    (demandes < 0 ? 0 : demandes) + (pointagesManuels < 0 ? 0 : pointagesManuels);

/// Exécute [chargement] ; un refus 403 (pas de file pour ce compte) compte pour une liste vide,
/// toute autre erreur (réseau, serveur) est propagée pour afficher « Réessayer ».
Future<List<T>> listeOuVideSiRefuse<T>(Future<List<T>> Function() chargement) async {
  try {
    return await chargement();
  } on DioException catch (e) {
    if (e.response?.statusCode == 403) return <T>[];
    rethrow;
  }
}
