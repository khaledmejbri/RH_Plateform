// Modèles « Mon dossier », annuaire et fiches d'équipe (ticket 2E, API A7 à A10, JSON snake_case).

String? _texte(Object? brut) {
  final t = brut?.toString().trim();
  return (t == null || t.isEmpty) ? null : t;
}

List<T> _liste<T>(Object? brut, T Function(Map<String, dynamic>) lire) {
  if (brut is! List) return <T>[];
  return brut.whereType<Map>().map((e) => lire(Map<String, dynamic>.from(e))).toList();
}

/// Personne telle qu'un écran l'affiche (nom, matricule, poste, unité).
class PersonneResume {
  final String id;
  final String nomComplet;
  final String? matricule;
  final String? poste;
  final String? intituleRole;
  final String? uniteLibelle;
  final bool actif;

  const PersonneResume({
    required this.id,
    required this.nomComplet,
    this.matricule,
    this.poste,
    this.intituleRole,
    this.uniteLibelle,
    this.actif = true,
  });

  factory PersonneResume.fromJson(Map<String, dynamic> json) {
    return PersonneResume(
      id: _texte(json['collaborateur_id']) ?? '',
      nomComplet: _texte(json['nom_complet']) ?? 'Nom indisponible',
      matricule: _texte(json['matricule']),
      poste: _texte(json['poste_libelle']),
      intituleRole: _texte(json['intitule_role']),
      uniteLibelle: _texte(json['unite_libelle']),
      actif: json['actif'] != false,
    );
  }

  /// Intitulé du rôle d'un responsable, sinon le poste.
  String? get fonction => intituleRole ?? poste;

  /// « Développeuse backend · Département Software » (éléments absents omis).
  String get sousTitre => [fonction, uniteLibelle].whereType<String>().join(' · ');
}

/// Nœud d'organigramme en clair (libellé et type, jamais d'identifiant à l'écran).
class UniteResume {
  final String id;
  final String libelle;
  final String? typeLibelle;

  const UniteResume({required this.id, required this.libelle, this.typeLibelle});

  factory UniteResume.fromJson(Map<String, dynamic> json) {
    return UniteResume(
      id: _texte(json['unite_id']) ?? '',
      libelle: _texte(json['libelle']) ?? 'Unité sans nom',
      typeLibelle: _texte(json['type_libelle']),
    );
  }
}

/// Hiérarchie de l'appelant (A7).
class Hierarchie {
  final PersonneResume? collaborateur;
  final List<UniteResume> chemin;
  final PersonneResume? responsableDirect;
  final PersonneResume? valideur;

  /// Vrai quand aucun supérieur ne valide : les demandes vont à la RH.
  final bool valideurEstRh;
  final List<UniteResume> noeudsManages;
  final List<PersonneResume> collaborateursEncadres;

  const Hierarchie({
    this.collaborateur,
    this.chemin = const [],
    this.responsableDirect,
    this.valideur,
    this.valideurEstRh = false,
    this.noeudsManages = const [],
    this.collaborateursEncadres = const [],
  });

  factory Hierarchie.fromJson(Map<String, dynamic> json) {
    PersonneResume? personne(Object? brut) =>
        brut is Map ? PersonneResume.fromJson(Map<String, dynamic>.from(brut)) : null;
    return Hierarchie(
      collaborateur: personne(json['collaborateur']),
      chemin: _liste(json['chemin'], UniteResume.fromJson),
      responsableDirect: personne(json['responsable_direct']),
      valideur: personne(json['valideur']),
      valideurEstRh: json['valideur_est_rh'] == true,
      noeudsManages: _liste(json['noeuds_manages'], UniteResume.fromJson),
      collaborateursEncadres: _liste(json['collaborateurs_encadres'], PersonneResume.fromJson),
    );
  }

  /// « Aucun supérieur — demandes à la RH » ou le nom du valideur.
  String get valideurAffiche {
    final v = valideur;
    if (v != null) return v.nomComplet;
    return 'Aucun supérieur — demandes à la RH';
  }

  /// Chemin de la DG à l'unité : « Direction générale › Direction Technique › … ».
  String get cheminAffiche => chemin.map((u) => u.libelle).join(' › ');
}

/// Fiche annuaire d'un collègue (A8) : huit informations, ni matricule ni statut.
class FicheAnnuaire {
  final String id;
  final String nomComplet;
  final String? poste;
  final String? intituleRole;
  final String? uniteLibelle;
  final String? responsableId;
  final String? responsableNom;
  final String? courriel;

  const FicheAnnuaire({
    required this.id,
    required this.nomComplet,
    this.poste,
    this.intituleRole,
    this.uniteLibelle,
    this.responsableId,
    this.responsableNom,
    this.courriel,
  });

  factory FicheAnnuaire.fromJson(Map<String, dynamic> json) {
    final r = json['responsable'];
    return FicheAnnuaire(
      id: _texte(json['collaborateur_id']) ?? '',
      nomComplet: _texte(json['nom_complet']) ?? 'Nom indisponible',
      poste: _texte(json['poste_libelle']),
      intituleRole: _texte(json['intitule_role']),
      uniteLibelle: _texte(json['unite_libelle']),
      responsableId: r is Map ? _texte(r['collaborateur_id']) : null,
      responsableNom: r is Map ? _texte(r['nom_complet']) : null,
      courriel: _texte(json['courriel_professionnel']),
    );
  }

  String? get fonction => intituleRole ?? poste;
}

/// Demande M01 d'un membre de l'équipe visible de son valideur.
class DemandeEquipe {
  final String id;
  final String? numero;
  final String typeLibelle;
  final String statutLibelle;
  final String? periodeDebut;
  final String? periodeFin;

  const DemandeEquipe({
    required this.id,
    this.numero,
    required this.typeLibelle,
    required this.statutLibelle,
    this.periodeDebut,
    this.periodeFin,
  });

  factory DemandeEquipe.fromJson(Map<String, dynamic> json) {
    return DemandeEquipe(
      id: _texte(json['identifiant']) ?? '',
      numero: _texte(json['numero']),
      typeLibelle: _texte(json['type_libelle']) ?? 'Demande',
      statutLibelle: _texte(json['statut_libelle']) ?? '',
      periodeDebut: _texte(json['periode_debut']),
      periodeFin: _texte(json['periode_fin']),
    );
  }
}

/// Fiche d'équipe (A10) : annuaire et demandes dont l'appelant est ou a été le valideur.
class FicheEquipe {
  final FicheAnnuaire annuaire;
  final List<DemandeEquipe> demandes;

  const FicheEquipe({required this.annuaire, this.demandes = const []});

  factory FicheEquipe.fromJson(Map<String, dynamic> json) {
    final a = json['annuaire'];
    return FicheEquipe(
      annuaire: FicheAnnuaire.fromJson(a is Map ? Map<String, dynamic>.from(a) : <String, dynamic>{}),
      demandes: _liste(json['demandes'], DemandeEquipe.fromJson),
    );
  }
}

/// Écran à ouvrir quand on touche une personne : fiche d'équipe si l'appelant en est le valideur
/// (présence et retards), fiche annuaire sinon (D-N02).
String routePourPersonne(String personneId, Set<String> idsEquipe) =>
    idsEquipe.contains(personneId) ? '/equipe/$personneId' : '/annuaire/$personneId';
