import '../../../core/format/formats.dart';

/// Solde de congé annuel du collaborateur connecté (A1) : acquis, réservé par les demandes en cours, disponible.
class SoldeConge {
  const SoldeConge({required this.acquis, required this.reserve, required this.disponible});

  final double acquis;
  final double reserve;
  final double disponible;

  factory SoldeConge.fromJson(Map<String, dynamic> j) => SoldeConge(
        acquis: lireDecimal(j['acquis']) ?? 0,
        reserve: lireDecimal(j['reserve']) ?? 0,
        disponible: lireDecimal(j['disponible']) ?? 0,
      );
}

/// Codes de type de mouvement (filtre de A2). Les libellés affichés viennent de l'API (`type_libelle`).
class TypeMouvement {
  const TypeMouvement._();

  static const String acquisition = 'ACQUISITION';
  static const String congePris = 'CONGE_PRIS';
  static const String recredit = 'RECREDIT_ANNULATION';
  static const String ajustement = 'AJUSTEMENT';

  static const List<String> tous = [acquisition, congePris, ajustement, recredit];
}

/// Une ligne de l'historique des mouvements (A2), des plus récents aux plus anciens.
class MouvementSolde {
  const MouvementSolde({
    required this.type,
    required this.typeLibelle,
    required this.nombre,
    required this.soldeApres,
    this.creeLe,
    this.auteurNom,
    this.motif,
    this.demandeId,
    this.demandeNumero,
    this.moisAcquisitionLibelle,
  });

  final String type;
  final String typeLibelle;
  final double nombre;
  final double? soldeApres;
  final String? creeLe;
  final String? auteurNom;
  final String? motif;
  final String? demandeId;
  final String? demandeNumero;
  final String? moisAcquisitionLibelle;

  factory MouvementSolde.fromJson(Map<String, dynamic> j) {
    String? texte(String cle) {
      final v = j[cle]?.toString().trim();
      return (v == null || v.isEmpty) ? null : v;
    }

    return MouvementSolde(
      type: texte('type') ?? '',
      typeLibelle: texte('type_libelle') ?? 'Mouvement',
      nombre: lireDecimal(j['nombre']) ?? 0,
      soldeApres: lireDecimal(j['solde_apres']),
      creeLe: texte('cree_le'),
      auteurNom: texte('auteur_nom'),
      motif: texte('motif'),
      demandeId: texte('demande_id'),
      demandeNumero: texte('demande_numero'),
      moisAcquisitionLibelle: texte('mois_acquisition_libelle'),
    );
  }

  /// Intitulé : « Acquisition décembre 2026 », « Congé pris DEM-202612-0001 », « Ajustement ».
  String get intitule {
    final suffixe = type == TypeMouvement.acquisition
        ? moisAcquisitionLibelle
        : (type == TypeMouvement.congePris || type == TypeMouvement.recredit)
            ? demandeNumero
            : null;
    return suffixe == null ? typeLibelle : '$typeLibelle $suffixe';
  }

  /// Détails sous l'intitulé : motif puis auteur, séparés par « — ».
  String? get details {
    final parts = [if (motif != null) motif!, if (auteurNom != null) auteurNom!];
    return parts.isEmpty ? null : parts.join(' — ');
  }

  /// Quantité signée accordée : « +1,5 jour », « −5 jours ».
  String get nombreAffiche => formatJoursSigne(nombre);
}

/// Une page de mouvements (A2).
class PageMouvements {
  const PageMouvements({
    required this.contenu,
    required this.page,
    required this.totalPages,
    required this.totalElements,
  });

  final List<MouvementSolde> contenu;
  final int page;
  final int totalPages;
  final int totalElements;

  bool get aSuite => page + 1 < totalPages;

  factory PageMouvements.fromJson(Map<String, dynamic> j) {
    final brut = j['contenu'] as List<dynamic>? ?? const [];
    return PageMouvements(
      contenu: brut.map((e) => MouvementSolde.fromJson(Map<String, dynamic>.from(e as Map))).toList(),
      page: (j['page'] as num?)?.toInt() ?? 0,
      totalPages: (j['total_pages'] as num?)?.toInt() ?? 0,
      totalElements: (j['total_elements'] as num?)?.toInt() ?? 0,
    );
  }
}

/// Période d'une journée de congé (A.1) : codes envoyés au serveur.
class PeriodeJour {
  const PeriodeJour._();

  static const String journee = 'JOURNEE';
  static const String matin = 'MATIN';
  static const String apresMidi = 'APRES_MIDI';

  /// Libellé du menu pour un code de période.
  static String libelle(String? code) => switch (code) {
        journee => 'Journée entière',
        matin => 'Matin',
        apresMidi => 'Après-midi',
        _ => kValeurAbsente,
      };

  /// Choix du premier jour : « Journée entière » ou « Après-midi » ; un seul jour : les trois.
  static List<String> premierJour({required bool unSeulJour}) =>
      unSeulJour ? const [journee, matin, apresMidi] : const [journee, apresMidi];

  /// Choix du dernier jour (période différente du premier jour) : « Journée entière » ou « Matin ».
  static const List<String> dernierJour = [journee, matin];
}

/// Blocage d'une simulation (A3) : le libellé est affiché tel quel.
class BlocageSimulation {
  const BlocageSimulation({required this.code, required this.libelle});

  final String code;
  final String libelle;
}

/// Résultat de la simulation d'un congé annuel (A3), calculé par le serveur.
class SimulationConge {
  const SimulationConge({
    required this.joursDecomptes,
    required this.disponible,
    required this.disponibleApres,
    this.blocage,
  });

  final double joursDecomptes;
  final double disponible;
  final double disponibleApres;
  final BlocageSimulation? blocage;

  bool get bloquee => blocage != null;

  /// « 2 jours ouvrés » / « 1 jour ouvré » (accord D-S14, jour ouvré au singulier sous 2).
  String get joursOuvres => '${formatDecimal(joursDecomptes)} ${joursDecomptes.abs() < 2 ? 'jour ouvré' : 'jours ouvrés'}';

  factory SimulationConge.fromJson(Map<String, dynamic> j) {
    final b = j['blocage'];
    BlocageSimulation? blocage;
    if (b is Map) {
      final libelle = b['libelle']?.toString().trim() ?? '';
      blocage = BlocageSimulation(
        code: b['code']?.toString() ?? '',
        libelle: libelle.isEmpty ? 'Cette période ne peut pas être demandée.' : libelle,
      );
    }
    return SimulationConge(
      joursDecomptes: lireDecimal(j['jours_decomptes']) ?? 0,
      disponible: lireDecimal(j['disponible']) ?? 0,
      disponibleApres: lireDecimal(j['disponible_apres']) ?? 0,
      blocage: blocage,
    );
  }
}
