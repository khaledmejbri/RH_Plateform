import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../notifications/notification_provider.dart';

class NotificationActionBadge extends ConsumerWidget {
  const NotificationActionBadge({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unreadCount = ref.watch(notificationProvider).unreadCount;
    return Badge.count(
      count: unreadCount,
      isLabelVisible: unreadCount > 0,
      offset: const Offset(-4, 4),
      child: IconButton(
        icon: const Icon(Icons.notifications_none_rounded),
        onPressed: () => context.push('/notifications'),
      ),
    );
  }
}
