class DemandeAdminItem {
  DemandeAdminItem({
    required this.id,
    required this.typeDemande,
    required this.statut,
    this.periodeDebut,
    this.periodeFin,
    this.motifRefus,
  });

  final String id;
  final String typeDemande;
  final String statut;
  final String? periodeDebut;
  final String? periodeFin;
  final String? motifRefus;

  factory DemandeAdminItem.fromJson(Map<String, dynamic> j) {
    return DemandeAdminItem(
      id: j['identifiant']?.toString() ?? '',
      typeDemande: j['type_demande'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      periodeDebut: j['periode_debut'] as String?,
      periodeFin: j['periode_fin'] as String?,
      motifRefus: j['motif_refus'] as String?,
    );
  }
}

class WorkflowEtape {
  WorkflowEtape({required this.code, required this.libelle, required this.terminee, required this.enCours});

  final String code;
  final String libelle;
  final bool terminee;
  final bool enCours;

  factory WorkflowEtape.fromJson(Map<String, dynamic> j) {
    return WorkflowEtape(
      code: j['code'] as String? ?? '',
      libelle: j['libelle'] as String? ?? '',
      terminee: j['terminee'] as bool? ?? false,
      enCours: j['en_cours'] as bool? ?? false,
    );
  }
}

class DemandeAdminSuivi {
  DemandeAdminSuivi({
    required this.id,
    required this.typeDemande,
    required this.statut,
    required this.etapeSuperieurRequise,
    required this.etapes,
  });

  final String id;
  final String typeDemande;
  final String statut;
  final bool etapeSuperieurRequise;
  final List<WorkflowEtape> etapes;

  factory DemandeAdminSuivi.fromJson(Map<String, dynamic> j) {
    final raw = j['etapes'] as List<dynamic>? ?? [];
    return DemandeAdminSuivi(
      id: j['identifiant']?.toString() ?? '',
      typeDemande: j['type_demande'] as String? ?? '',
      statut: j['statut'] as String? ?? '',
      etapeSuperieurRequise: j['etape_superieur_requise'] as bool? ?? false,
      etapes: raw.map((e) => WorkflowEtape.fromJson(Map<String, dynamic>.from(e as Map))).toList(),
    );
  }
}
