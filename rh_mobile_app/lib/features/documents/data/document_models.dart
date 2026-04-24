class DocumentDemandeItem {
  const DocumentDemandeItem({
    required this.id,
    required this.typeDocument,
    required this.statut,
    this.rangDansFile,
    this.reglePriorite,
    this.enRetard,
    this.dateCreation,
    this.motifRejet,
  });

  final String id;
  final String typeDocument;
  final String statut;
  final int? rangDansFile;
  final String? reglePriorite;
  final bool? enRetard;
  final DateTime? dateCreation;
  final String? motifRejet;

  static DocumentDemandeItem fromJson(Map<String, dynamic> j) {
    final rang = j['rang_dans_file'];
    final dateRaw = j['date_creation'] as String?;
    return DocumentDemandeItem(
      id: '${j['identifiant'] ?? ''}',
      typeDocument: j['type_document'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      rangDansFile: rang is int ? rang : (rang is num ? rang.toInt() : null),
      reglePriorite: j['regle_priorite'] as String?,
      enRetard: j['en_retard'] as bool?,
      dateCreation: dateRaw != null ? DateTime.tryParse(dateRaw) : null,
      motifRejet: j['motif_rejet'] as String?,
    );
  }
}

class DocumentSuivi {
  const DocumentSuivi({
    required this.reglePriorite,
    required this.rangDansFile,
    required this.nombreDevant,
    required this.enRetard,
    required this.etapes,
  });

  final String reglePriorite;
  final int? rangDansFile;
  final int? nombreDevant;
  final bool enRetard;
  final List<Map<String, dynamic>> etapes;

  static DocumentSuivi fromJson(Map<String, dynamic> j) {
    final raw = j['etapes'] as List<dynamic>? ?? [];
    final rd = j['rang_dans_file'];
    final nd = j['nombre_en_attente_devant'];
    return DocumentSuivi(
      reglePriorite: j['regle_priorite'] as String? ?? '',
      rangDansFile: rd is int ? rd : (rd is num ? rd.toInt() : null),
      nombreDevant: nd is int ? nd : (nd is num ? nd.toInt() : null),
      enRetard: j['en_retard'] as bool? ?? false,
      etapes: raw.map((e) => Map<String, dynamic>.from(e as Map)).toList(),
    );
  }
}
