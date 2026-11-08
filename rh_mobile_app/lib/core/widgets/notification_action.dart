import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../format/formats.dart';
import '../notifications/notification_provider.dart';

/// Cloche de notifications : compteur exact jusqu'à 99, « 99+ » au-delà, masqué à 0 (D-NOT01).
class NotificationActionBadge extends ConsumerWidget {
  const NotificationActionBadge({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unreadCount = ref.watch(notificationProvider.select((s) => s.nonLues));
    final libelle = libelleBadge(unreadCount);
    return Badge(
      label: libelle == null ? null : Text(libelle),
      isLabelVisible: libelle != null,
      offset: const Offset(-4, 4),
      child: IconButton(
        tooltip: libelle == null ? 'Notifications' : 'Notifications ($libelle non lues)',
        icon: const Icon(Icons.notifications_none_rounded),
        onPressed: () => context.push('/notifications'),
      ),
    );
  }
}
