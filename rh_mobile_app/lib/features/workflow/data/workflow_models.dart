import '../../../core/format/formats.dart';
import '../../organigramme/data/organigramme_models.dart';

String? _texteNullable(Object? v) {
  if (v == null) return null;
  final s = v.toString().trim();
  return s.isEmpty ? null : s;
}

int _entier(Object? v, [int defaut = 0]) {
  if (v is int) return v;
  if (v is num) return v.toInt();
  return int.tryParse(v?.toString() ?? '') ?? defaut;
}

Map<String, dynamic> _mapStringDynamic(Object? v) {
  if (v is Map<String, dynamic>) return v;
  if (v is Map) {
    return v.map((k, val) => MapEntry(k.toString(), val));
  }
  return const {};
}

List<String> _listeChaines(Object? v) {
  if (v is List) {
    return v.map((e) => e.toString().trim()).where((s) => s.isNotEmpty).toList();
  }
  return const [];
}

/// Page paginée générique renvoyée par `AdminWorkflowDtos.PageResponse`.
class PageResponse<T> {
  const PageResponse({
    required this.elements,
    required this.page,
    required this.taille,
    required this.total,
  });

  final List<T> elements;
  final int page;
  final int taille;
  final int total;

  bool get aPlus => (page + 1) * taille < total;

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic> item) parseItem,
  ) {
    final bruts = json['elements'];
    final items = <T>[];
    if (bruts is List) {
      for (final e in bruts) {
        if (e is Map) {
          items.add(parseItem(_mapStringDynamic(e)));
        }
      }
    }
    return PageResponse<T>(
      elements: items,
      page: _entier(json['page']),
      taille: _entier(json['taille'], 20),
      total: _entier(json['total']),
    );
  }
}

/// Compteur des tâches en attente de l'utilisateur (`CompteurTachesResponse`).
class CompteurTaches {
  const CompteurTaches({this.aTraiter = 0, this.enRetard = 0});

  final int aTraiter;
  final int enRetard;

  factory CompteurTaches.fromJson(Map<String, dynamic> json) {
    return CompteurTaches(
      aTraiter: _entier(json['a_traiter']),
      enRetard: _entier(json['en_retard']),
    );
  }
}

/// Élément de la liste « Mes tâches » (`TacheListeItemResponse`).
class TacheListeItem {
  const TacheListeItem({
    required this.tacheId,
    required this.instanceId,
    required this.type,
    required this.libelleEtape,
    required this.objetType,
    required this.objetLibelle,
    this.objetNumero,
    required this.objetId,
    this.demandeurNom,
    this.echeanceLe,
    required this.enRetard,
    this.origine,
    this.pourNom,
    required this.actionsPermises,
    required this.version,
    required this.resume,
  });

  final String tacheId;
  final String instanceId;
  final String type;
  final String libelleEtape;
  final String objetType;
  final String objetLibelle;
  final String? objetNumero;
  final String objetId;
  final String? demandeurNom;
  final String? echeanceLe;
  final bool enRetard;
  final String? origine;
  final String? pourNom;
  final List<String> actionsPermises;
  final int version;
  final Map<String, dynamic> resume;

  bool get estComplement => type == 'COMPLEMENT';

  factory TacheListeItem.fromJson(Map<String, dynamic> json) {
    return TacheListeItem(
      tacheId: _texteNullable(json['tache_id']) ?? '',
      instanceId: _texteNullable(json['instance_id']) ?? '',
      type: _texteNullable(json['type']) ?? 'VALIDATION',
      libelleEtape: _texteNullable(json['libelle_etape']) ?? _texteNullable(json['libelle']) ?? '',
      objetType: _texteNullable(json['objet_type']) ?? '',
      objetLibelle: _texteNullable(json['objet_libelle']) ?? _texteNullable(json['objet_type']) ?? '',
      objetNumero: _texteNullable(json['objet_numero']),
      objetId: _texteNullable(json['objet_id']) ?? '',
      demandeurNom: _texteNullable(json['demandeur_nom']),
      echeanceLe: _texteNullable(json['echeance_le']),
      enRetard: json['en_retard'] == true,
      origine: _texteNullable(json['origine']),
      pourNom: _texteNullable(json['pour_nom']),
      actionsPermises: _listeChaines(json['actions_permises']),
      version: _entier(json['version']),
      resume: _mapStringDynamic(json['resume']),
    );
  }
}

/// Étape courante (`EtapeCouranteDto`).
class EtapeCourante {
  const EtapeCourante({
    required this.code,
    required this.libelle,
    this.echeanceLe,
    this.intituleIntervenants,
  });

  final String code;
  final String libelle;
  final String? echeanceLe;
  final String? intituleIntervenants;

  factory EtapeCourante.fromJson(Map<String, dynamic> json) {
    return EtapeCourante(
      code: _texteNullable(json['code']) ?? '',
      libelle: _texteNullable(json['libelle']) ?? '',
      echeanceLe: _texteNullable(json['echeance_le']),
      intituleIntervenants: _texteNullable(json['intitule_intervenants']),
    );
  }
}

/// Étape de la frise de suivi (`EtapeSuiviDto`).
///
/// Conformément à `CA-W8-02` / `D-W26`, la liste des candidats individuels d'un
/// pool n'est volontairement ni stockée ni exposée sur l'interface mobile.
class EtapeSuivi {
  const EtapeSuivi({
    required this.code,
    required this.libelle,
    required this.statut,
    this.raison,
    this.intituleIntervenants,
    this.decideurNom,
    this.pourNom,
    this.resultat,
    this.motif,
    this.echeanceLe,
    this.dateDecision,
  });

  final String code;
  final String libelle;
  final String statut;
  final String? raison;
  final String? intituleIntervenants;
  final String? decideurNom;
  final String? pourNom;
  final String? resultat;
  final String? motif;
  final String? echeanceLe;
  final String? dateDecision;

  factory EtapeSuivi.fromJson(Map<String, dynamic> json) {
    return EtapeSuivi(
      code: _texteNullable(json['code']) ?? '',
      libelle: _texteNullable(json['libelle']) ?? '',
      statut: _texteNullable(json['statut']) ?? 'A_VENIR',
      raison: _texteNullable(json['raison']),
      intituleIntervenants: _texteNullable(json['intitule_intervenants']),
      decideurNom: _texteNullable(json['decideur_nom']),
      pourNom: _texteNullable(json['pour_nom']),
      resultat: _texteNullable(json['resultat']),
      motif: _texteNullable(json['motif']),
      echeanceLe: _texteNullable(json['echeance_le']),
      dateDecision: _texteNullable(json['date_decision']),
    );
  }
}

/// Suivi complet d'une instance workflow (`SuiviInstanceResponse`).
class SuiviInstance {
  const SuiviInstance({
    required this.instanceId,
    required this.objetType,
    required this.objetId,
    this.objetNumero,
    this.modeleCode,
    required this.modeleVersionNumero,
    required this.statut,
    this.etapeCourante,
    this.demarreLe,
    this.termineLe,
    this.resultatMotif,
    required this.etapes,
  });

  final String instanceId;
  final String objetType;
  final String objetId;
  final String? objetNumero;
  final String? modeleCode;
  final int modeleVersionNumero;
  final String statut;
  final EtapeCourante? etapeCourante;
  final String? demarreLe;
  final String? termineLe;
  final String? resultatMotif;
  final List<EtapeSuivi> etapes;

  /// Motif le plus pertinent expliquant un renvoi pour complément ou un refus.
  String? get motifRenvoiOuMotif {
    for (final e in etapes.reversed) {
      if (e.resultat == 'RENVOYEE' && e.motif != null && e.motif!.isNotEmpty) {
        return e.motif;
      }
    }
    for (final e in etapes.reversed) {
      if (e.motif != null && e.motif!.isNotEmpty) {
        return e.motif;
      }
    }
    return resultatMotif;
  }

  factory SuiviInstance.fromJson(Map<String, dynamic> json) {
    final couranteRaw = json['etape_courante'];
    final etapesRaw = json['etapes'];
    final etapes = <EtapeSuivi>[];
    if (etapesRaw is List) {
      for (final e in etapesRaw) {
        if (e is Map) {
          etapes.add(EtapeSuivi.fromJson(_mapStringDynamic(e)));
        }
      }
    }
    return SuiviInstance(
      instanceId: _texteNullable(json['instance_id']) ?? '',
      objetType: _texteNullable(json['objet_type']) ?? '',
      objetId: _texteNullable(json['objet_id']) ?? '',
      objetNumero: _texteNullable(json['objet_numero']),
      modeleCode: _texteNullable(json['modele_code']),
      modeleVersionNumero: _entier(json['modele_version_numero'], 1),
      statut: _texteNullable(json['statut']) ?? 'EN_COURS',
      etapeCourante: couranteRaw is Map ? EtapeCourante.fromJson(_mapStringDynamic(couranteRaw)) : null,
      demarreLe: _texteNullable(json['demarre_le']),
      termineLe: _texteNullable(json['termine_le']),
      resultatMotif: _texteNullable(json['resultat_motif']),
      etapes: etapes,
    );
  }
}

/// Détail complet d'une tâche (`TacheDetailResponse`).
///
/// Attention : la racine du DTO utilise `libelle` (et non `libelle_etape`).
/// De plus, `origine` et `pour_nom` ne figurent pas à la racine de
/// `TacheDetailResponse` côté serveur et peuvent être injectés depuis l'élément
/// de liste via [avecMetaListe].
class TacheDetail {
  const TacheDetail({
    required this.tacheId,
    required this.instanceId,
    required this.type,
    required this.statut,
    required this.libelle,
    required this.objetType,
    required this.objetLibelle,
    this.objetNumero,
    required this.objetId,
    this.demandeurNom,
    this.echeanceLe,
    required this.enRetard,
    required this.actionsPermises,
    this.commentaireObligatoire = false,
    required this.version,
    this.decision,
    this.commentaire,
    this.decideLe,
    this.origine,
    this.pourNom,
    required this.resume,
    this.suivi,
  });

  final String tacheId;
  final String instanceId;
  final String type;
  final String statut;
  final String libelle;
  final String objetType;
  final String objetLibelle;
  final String? objetNumero;
  final String objetId;
  final String? demandeurNom;
  final String? echeanceLe;
  final bool enRetard;
  final List<String> actionsPermises;
  final bool commentaireObligatoire;
  final int version;
  final String? decision;
  final String? commentaire;
  final String? decideLe;
  final String? origine;
  final String? pourNom;
  final Map<String, dynamic> resume;
  final SuiviInstance? suivi;

  bool get estATraiter => statut == 'A_TRAITER';
  bool get estComplementATraiter => estATraiter && type == 'COMPLEMENT';

  TacheDetail avecMetaListe(TacheListeItem? item) {
    if (item == null) return this;
    return TacheDetail(
      tacheId: tacheId,
      instanceId: instanceId,
      type: type,
      statut: statut,
      libelle: libelle,
      objetType: objetType,
      objetLibelle: objetLibelle,
      objetNumero: objetNumero,
      objetId: objetId,
      demandeurNom: demandeurNom,
      echeanceLe: echeanceLe,
      enRetard: enRetard,
      actionsPermises: actionsPermises,
      commentaireObligatoire: commentaireObligatoire,
      version: version,
      decision: decision,
      commentaire: commentaire,
      decideLe: decideLe,
      origine: origine ?? item.origine,
      pourNom: pourNom ?? item.pourNom,
      resume: resume,
      suivi: suivi,
    );
  }

  factory TacheDetail.fromJson(Map<String, dynamic> json) {
    final suiviRaw = json['suivi'];
    return TacheDetail(
      tacheId: _texteNullable(json['tache_id']) ?? '',
      instanceId: _texteNullable(json['instance_id']) ?? '',
      type: _texteNullable(json['type']) ?? 'VALIDATION',
      statut: _texteNullable(json['statut']) ?? 'A_TRAITER',
      libelle: _texteNullable(json['libelle']) ?? _texteNullable(json['libelle_etape']) ?? '',
      objetType: _texteNullable(json['objet_type']) ?? '',
      objetLibelle: _texteNullable(json['objet_libelle']) ?? _texteNullable(json['objet_type']) ?? '',
      objetNumero: _texteNullable(json['objet_numero']),
      objetId: _texteNullable(json['objet_id']) ?? '',
      demandeurNom: _texteNullable(json['demandeur_nom']),
      echeanceLe: _texteNullable(json['echeance_le']),
      enRetard: json['en_retard'] == true,
      actionsPermises: _listeChaines(json['actions_permises']),
      commentaireObligatoire: json['commentaire_obligatoire'] == true,
      version: _entier(json['version']),
      decision: _texteNullable(json['decision']),
      commentaire: _texteNullable(json['commentaire']),
      decideLe: _texteNullable(json['decide_le']),
      origine: _texteNullable(json['origine']),
      pourNom: _texteNullable(json['pour_nom']),
      resume: _mapStringDynamic(json['resume']),
      suivi: suiviRaw is Map ? SuiviInstance.fromJson(_mapStringDynamic(suiviRaw)) : null,
    );
  }
}

/// Résultat d'une décision (`200 OK` ou `202 Accepted` avec `DECISION_EN_COURS`).
class DecisionResult {
  const DecisionResult({
    required this.enCours,
    this.tacheId,
    this.decision,
    this.instanceStatut,
    this.etapeCourante,
  });

  final bool enCours;
  final String? tacheId;
  final String? decision;
  final String? instanceStatut;
  final EtapeCourante? etapeCourante;

  const DecisionResult.enCoursResult()
      : enCours = true,
        tacheId = null,
        decision = null,
        instanceStatut = null,
        etapeCourante = null;

  factory DecisionResult.fromJson(Map<String, dynamic> json) {
    if (_texteNullable(json['statut']) == 'DECISION_EN_COURS') {
      return const DecisionResult.enCoursResult();
    }
    final inst = json['instance'] is Map ? _mapStringDynamic(json['instance']) : const <String, dynamic>{};
    final couranteRaw = inst['etape_courante'];
    return DecisionResult(
      enCours: false,
      tacheId: _texteNullable(json['tache_id']),
      decision: _texteNullable(json['decision']),
      instanceStatut: _texteNullable(inst['statut']),
      etapeCourante: couranteRaw is Map ? EtapeCourante.fromJson(_mapStringDynamic(couranteRaw)) : null,
    );
  }
}

/// Type d'objet ouvert à la saisie (`TypeOuvertResponse`).
class TypeOuvert {
  const TypeOuvert({
    required this.objetType,
    required this.libelle,
    required this.sousTypes,
  });

  final String objetType;
  final String libelle;

  /// Vaut soit `"TOUS"` (`String`), soit une `List<String>`.
  final dynamic sousTypes;

  bool get tousSousTypes => sousTypes is String && sousTypes.toString().toUpperCase() == 'TOUS';

  List<String> get listeSousTypes {
    if (sousTypes is List) {
      return (sousTypes as List).map((e) => e.toString().trim()).where((s) => s.isNotEmpty).toList();
    }
    return const [];
  }

  bool accepteSousType(String? sousType) {
    if (tousSousTypes) return true;
    final liste = listeSousTypes;
    if (sousType == null || sousType.trim().isEmpty) {
      return liste.isNotEmpty;
    }
    return liste.contains(sousType.trim());
  }

  factory TypeOuvert.fromJson(Map<String, dynamic> json) {
    final raw = json['sous_types'];
    final dynamic parsed;
    if (raw is List) {
      parsed = raw.map((e) => e.toString().trim()).where((s) => s.isNotEmpty).toList();
    } else if (raw != null) {
      parsed = raw.toString().trim();
    } else {
      parsed = 'TOUS';
    }
    return TypeOuvert(
      objetType: _texteNullable(json['objet_type']) ?? '',
      libelle: _texteNullable(json['libelle']) ?? _texteNullable(json['objet_type']) ?? '',
      sousTypes: parsed,
    );
  }
}

/// Vérifie si [objetType] (et optionnellement [sousType]) est ouvert selon [typesOuverts].
///
/// Repli D-W03 / Q2 : si [typesOuverts] est `null` (indisponible), tout est considéré ouvert.
bool estTypeOuvert(
  List<TypeOuvert>? typesOuverts,
  String objetType, {
  String? sousType,
}) {
  if (typesOuverts == null) return true;
  for (final t in typesOuverts) {
    if (t.objetType == objetType) {
      return t.accepteSousType(sousType);
    }
  }
  return false;
}

/// Délégation renvoyée par `/api/rh/v1/workflow/delegations/moi`.
class DelegationItem {
  const DelegationItem({
    required this.id,
    required this.delegantId,
    required this.delegantNom,
    required this.delegueId,
    required this.delegueNom,
    required this.du,
    required this.au,
    required this.objetTypes,
    this.motif,
    this.origine,
    required this.active,
    this.creeeLe,
    this.annuleeLe,
  });

  final String id;
  final String delegantId;
  final String delegantNom;
  final String delegueId;
  final String delegueNom;
  final String du;
  final String au;
  final List<String> objetTypes;
  final String? motif;
  final String? origine;
  final bool active;
  final String? creeeLe;
  final String? annuleeLe;

  factory DelegationItem.fromJson(Map<String, dynamic> json) {
    return DelegationItem(
      id: _texteNullable(json['id']) ?? '',
      delegantId: _texteNullable(json['delegant_id']) ?? '',
      delegantNom: _texteNullable(json['delegant_nom']) ?? kValeurAbsente,
      delegueId: _texteNullable(json['delegue_id']) ?? '',
      delegueNom: _texteNullable(json['delegue_nom']) ?? kValeurAbsente,
      du: _texteNullable(json['du']) ?? '',
      au: _texteNullable(json['au']) ?? '',
      objetTypes: _listeChaines(json['objet_types']),
      motif: _texteNullable(json['motif']),
      origine: _texteNullable(json['origine']),
      active: json['active'] == true,
      creeeLe: _texteNullable(json['creee_le']),
      annuleeLe: _texteNullable(json['annulee_le']),
    );
  }
}

enum StatutDelegation { active, planifiee, expiree, annulee }

/// Calcule le statut d'une délégation à la date [aujourdhui] (par défaut aujourd'hui à Tunis).
StatutDelegation statutDelegation(DelegationItem d, [DateTime? aujourdhui]) {
  if (d.annuleeLe != null && d.annuleeLe!.isNotEmpty) {
    return StatutDelegation.annulee;
  }
  if (d.active) {
    return StatutDelegation.active;
  }
  final ref = aujourdhui ?? aujourdhuiTunis();
  final refJour = DateTime.utc(ref.year, ref.month, ref.day);
  final duParsed = analyserDate(d.du);
  if (duParsed != null) {
    final duJour = DateTime.utc(duParsed.year, duParsed.month, duParsed.day);
    if (duJour.isAfter(refJour)) {
      return StatutDelegation.planifiee;
    }
  }
  return StatutDelegation.expiree;
}

/// Valide la plage de dates d'une délégation (`<= 90` jours inclus, aligné sur `DelegationService.java`).
///
/// Retourne un message d'erreur en français ou `null` si valide.
String? validerDelegationDates({
  required DateTime? du,
  required DateTime? au,
  DateTime? aujourdhui,
}) {
  if (du == null || au == null) {
    return 'Sélectionnez une période de délégation.';
  }
  final ref = aujourdhui ?? aujourdhuiTunis();
  final refJour = DateTime.utc(ref.year, ref.month, ref.day);
  final duJour = DateTime.utc(du.year, du.month, du.day);
  final auJour = DateTime.utc(au.year, au.month, au.day);

  if (duJour.isBefore(refJour)) {
    return 'La date de début ne peut pas être antérieure à aujourd’hui.';
  }
  if (auJour.isBefore(duJour)) {
    return 'La date de fin doit être postérieure ou égale à la date de début.';
  }
  if (auJour.difference(duJour).inDays > 90) {
    return 'La durée d’une délégation ne peut pas dépasser 90 jours.';
  }
  return null;
}

/// Collaborateur aplati issu de l'organigramme pour sélection de délégué.
class CollaborateurSelection {
  const CollaborateurSelection({
    required this.identifiant,
    required this.nomComplet,
    this.matricule,
    this.posteLibelle,
  });

  final String identifiant;
  final String nomComplet;
  final String? matricule;
  final String? posteLibelle;
}

/// Aplatit les membres et managers d'[organigramme], dédoublonnés par identifiant,
/// en excluant éventuellement [exclureIdentifiant] (l'utilisateur courant).
List<CollaborateurSelection> aplatirMembresOrganigramme(
  Organigramme organigramme, {
  String? exclureIdentifiant,
}) {
  final vus = <String, CollaborateurSelection>{};

  void enregistrer(OrganigrammeMembre m) {
    final id = m.identifiant.trim();
    if (id.isEmpty) return;
    if (exclureIdentifiant != null && id == exclureIdentifiant.trim()) return;
    vus.putIfAbsent(
      id,
      () => CollaborateurSelection(
        identifiant: id,
        nomComplet: m.displayName.trim().isEmpty ? m.matricule : m.displayName.trim(),
        matricule: m.matricule.trim().isEmpty ? null : m.matricule.trim(),
        posteLibelle: m.posteLibelle?.trim().isEmpty == true ? null : m.posteLibelle?.trim(),
      ),
    );
  }

  void parcourir(OrganigrammeNoeud noeud) {
    final mgr = noeud.manager;
    if (mgr != null) enregistrer(mgr);
    for (final m in noeud.membres) {
      enregistrer(m);
    }
    for (final e in noeud.enfants) {
      parcourir(e);
    }
  }

  for (final r in organigramme.racines) {
    parcourir(r);
  }

  final liste = vus.values.toList()
    ..sort((a, b) => a.nomComplet.toLowerCase().compareTo(b.nomComplet.toLowerCase()));
  return liste;
}
