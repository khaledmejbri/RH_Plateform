class PlainteItem {
  PlainteItem({
    required this.id,
    required this.titre,
    required this.statut,
    required this.typePlainte,
    this.description,
  });

  final String id;
  final String titre;
  final String statut;
  final String typePlainte;
  final String? description;

  factory PlainteItem.fromJson(Map<String, dynamic> j) {
    return PlainteItem(
      id: j['identifiant']?.toString() ?? '',
      titre: j['titre'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      typePlainte: j['type_plainte'] as String? ?? '',
      description: j['description'] as String?,
    );
  }
}
