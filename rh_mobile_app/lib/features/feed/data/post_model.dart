import 'package:intl/intl.dart';

class Post {
  final String id;
  final String authorName;
  final String authorRole;
  final String content;
  final String? imageUrl;
  final DateTime createdAt;
  final int likes;
  final int commentCount;
  final bool isLiked;

  Post({
    required this.id,
    required this.authorName,
    required this.authorRole,
    required this.content,
    this.imageUrl,
    required this.createdAt,
    this.likes = 0,
    this.commentCount = 0,
    this.isLiked = false,
  });

  String get timeAgo {
    final diff = DateTime.now().difference(createdAt);
    if (diff.inDays > 0) return '${diff.inDays}j';
    if (diff.inHours > 0) return '${diff.inHours}h';
    if (diff.inMinutes > 0) return '${diff.inMinutes}m';
    return "À l'instant";
  }
}
