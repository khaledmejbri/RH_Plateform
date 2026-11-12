import '../../../core/libelles/libelles.dart';

class PlainteItem {
  PlainteItem({
    required this.id,
    required this.titre,
    required this.statut,
    required this.typePlainte,
    this.description,
    this.numero,
    this.statutLibelleApi,
  });

  final String id;
  final String titre;
  final String statut;
  final String typePlainte;
  final String? description;

  /// Numéro lisible `PLT-AAAAMM-NNNN` si le serveur l'envoie.
  final String? numero;

  /// Libellé du statut servi avec la plainte (`statut_libelle`) ; à défaut, le référentiel chargé.
  final String? statutLibelleApi;

  String get statutLibelle => Libelles.avecRepli(statutLibelleApi, statut, Libelles.statutPlainte);

  factory PlainteItem.fromJson(Map<String, dynamic> j) {
    return PlainteItem(
      id: j['identifiant']?.toString() ?? '',
      titre: j['titre'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      typePlainte: j['type_plainte'] as String? ?? '',
      description: j['description'] as String?,
      numero: j['numero'] as String?,
      statutLibelleApi: j['statut_libelle'] as String?,
    );
  }
}
