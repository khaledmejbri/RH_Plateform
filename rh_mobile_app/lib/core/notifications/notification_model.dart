/// Notification conservée par le serveur (`NotificationDto`, ticket 2G, D-NOT01).
///
/// Les champs d'objet (`objet_type`, `objet_id`, `objet_numero`) permettent d'ouvrir le détail
/// de l'élément concerné (ticket 2F) ; ils sont absents des anciennes notifications.
class AppNotification {
  final String id;
  final String subject;
  final String content;

  /// Date de réception par le serveur (`recu_le`, sinon `emis_le`, sinon l'heure de l'appareil).
  final DateTime receivedAt;
  final bool isRead;

  /// Événement métier (ex. `DEMANDE_VALIDEE`), texte libre côté serveur.
  final String? evenement;

  /// `DEMANDE`, `DOCUMENT`, `PLAINTE`, `FORMATION`, `EVALUATION`, `ALERTE_ECART`, `POINTAGE_MANUEL`, `HEURES_SUP`.
  final String? objetType;
  final String? objetId;

  /// Numéro lisible de l'objet (`DEM-202611-0003`).
  final String? objetNumero;

  /// Ancien format (jusqu'au lot 1) : type métier porté par le message temps réel.
  final String? type;
  final Map<String, dynamic>? metadata;

  const AppNotification({
    required this.id,
    required this.subject,
    required this.content,
    required this.receivedAt,
    this.isRead = false,
    this.evenement,
    this.objetType,
    this.objetId,
    this.objetNumero,
    this.type,
    this.metadata,
  });

  AppNotification copyWith({bool? isRead}) {
    return AppNotification(
      id: id,
      subject: subject,
      content: content,
      receivedAt: receivedAt,
      isRead: isRead ?? this.isRead,
      evenement: evenement,
      objetType: objetType,
      objetId: objetId,
      objetNumero: objetNumero,
      type: type,
      metadata: metadata,
    );
  }

  /// Lit un `NotificationDto` (liste, compteur ou message STOMP). Tolère l'ancien format
  /// (`subject`, `content`, `type`) et ne lit jamais `recipient`.
  factory AppNotification.fromMap(Map<String, dynamic> json, {DateTime? maintenant}) {
    String? texte(Object? v) {
      final t = v?.toString().trim();
      return (t == null || t.isEmpty) ? null : t;
    }

    DateTime? date(Object? v) {
      final t = texte(v);
      if (t == null) return null;
      final d = DateTime.tryParse(t);
      if (d == null) return null;
      return d.isUtc ? d.toLocal() : d;
    }

    final horloge = maintenant ?? DateTime.now();
    final metadata = json['metadata'];
    return AppNotification(
      id: texte(json['id']) ?? horloge.microsecondsSinceEpoch.toString(),
      subject: texte(json['sujet']) ?? texte(json['subject']) ?? 'Notification',
      content: (json['contenu'] ?? json['content'] ?? '').toString(),
      receivedAt: date(json['recu_le']) ?? date(json['emis_le']) ?? horloge,
      isRead: json['lu'] == true,
      evenement: texte(json['evenement']),
      objetType: texte(json['objet_type'])?.toUpperCase(),
      objetId: texte(json['objet_id']),
      objetNumero: texte(json['objet_numero']),
      type: texte(json['type']),
      metadata: metadata is Map ? Map<String, dynamic>.from(metadata) : null,
    );
  }

  bool get isFormationInvitation => type == 'FORMATION_INVITATION';
  String? get formationId => metadata?['formation_id'] as String?;

  /// Préfixe technique `TYPE|cle=valeur|...|` (ex. `EVALUATION_CAMPAGNE_OUVERTE|evaluation=…|`).
  static final RegExp _prefixeTechnique =
      RegExp(r'^[A-Z][A-Z0-9_]+(?:\|[A-Za-z_]+=[^|]*)*\|');

  /// Contenu affiché : masque le préfixe technique `TYPE|cle=valeur|` s’il est présent.
  String get displayContent => masquerPrefixeTechnique(content, subject);

  /// Retire le préfixe technique ; renvoie [subject] si le texte est vide ensuite.
  static String masquerPrefixeTechnique(String content, [String subject = '']) {
    final raw = content.trim();
    final match = _prefixeTechnique.firstMatch(raw);
    if (match == null) return content;
    final texte = raw.substring(match.end).trim();
    return texte.isEmpty ? subject : texte;
  }
}

/// Page de notifications renvoyée par `GET /api/rh/v1/notifications`.
class PageNotifications {
  final List<AppNotification> contenu;
  final int page;
  final int taille;
  final int total;

  const PageNotifications({
    required this.contenu,
    required this.page,
    required this.taille,
    required this.total,
  });

  /// Vrai s'il reste des notifications après cette page.
  bool get aSuite => (page + 1) * taille < total;

  factory PageNotifications.fromJson(Map<String, dynamic> json) {
    final brut = json['contenu'];
    final liste = brut is List
        ? brut
            .whereType<Map>()
            .map((e) => AppNotification.fromMap(Map<String, dynamic>.from(e)))
            .toList()
        : <AppNotification>[];
    int entier(Object? v, int defaut) => v is num ? v.toInt() : defaut;
    return PageNotifications(
      contenu: liste,
      page: entier(json['page'], 0),
      taille: entier(json['taille'], liste.length),
      total: entier(json['total'], liste.length),
    );
  }
}
