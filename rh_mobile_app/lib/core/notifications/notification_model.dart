class AppNotification {
  final String id;
  final String subject;
  final String content;
  final DateTime receivedAt;
  final bool isRead;
  final String? type; // 'FORMATION_INVITATION', 'DEMANDE_ADMIN', etc.
  final Map<String, dynamic>? metadata; // Extra data like formation_id, demande_id, etc.

  const AppNotification({
    required this.id,
    required this.subject,
    required this.content,
    required this.receivedAt,
    this.isRead = false,
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
      type: type,
      metadata: metadata,
    );
  }

  factory AppNotification.fromMap(Map<String, dynamic> json) {
    return AppNotification(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      subject: json['subject'] as String? ?? 'Notification',
      content: json['content'] as String? ?? '',
      receivedAt: DateTime.now(),
      type: json['type'] as String?,
      metadata: json['metadata'] as Map<String, dynamic>?,
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
