class AppNotification {
  final String id;
  final String subject;
  final String content;
  final DateTime receivedAt;
  final bool isRead;

  const AppNotification({
    required this.id,
    required this.subject,
    required this.content,
    required this.receivedAt,
    this.isRead = false,
  });

  AppNotification copyWith({bool? isRead}) {
    return AppNotification(
      id: id,
      subject: subject,
      content: content,
      receivedAt: receivedAt,
      isRead: isRead ?? this.isRead,
    );
  }

  factory AppNotification.fromMap(Map<String, dynamic> json) {
    return AppNotification(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      subject: json['subject'] as String? ?? 'Notification',
      content: json['content'] as String? ?? '',
      receivedAt: DateTime.now(),
    );
  }
}
