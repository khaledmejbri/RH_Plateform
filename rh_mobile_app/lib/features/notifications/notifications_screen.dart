import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:go_router/go_router.dart';

import '../../../core/notifications/notification_model.dart';
import '../../../core/notifications/notification_provider.dart';

import '../auth/providers/collaborateur_notifier.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(notificationProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          // Debug connection status
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: state.connected ? Colors.green : Colors.red,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 4),
                Text(
                  state.connected ? 'Connecté' : 'Déconnecté',
                  style: TextStyle(
                    fontSize: 12,
                    color: state.connected ? Colors.green : Colors.red,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
          if (state.unreadCount > 0)
            TextButton.icon(
              icon: const Icon(Icons.done_all_rounded, size: 18),
              label: const Text('Tout lire'),
              onPressed: () =>
                  ref.read(notificationProvider.notifier).markAllRead(),
            ),
        ],
      ),
      body: state.notifications.isEmpty
          ? _EmptyState(connected: state.connected)
          : ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              itemCount: state.notifications.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, i) {
                final notif = state.notifications[i];
                return _NotificationCard(
                  notif: notif,
                  onTap: () {
                    ref.read(notificationProvider.notifier).markRead(notif.id);
                    
                    final content = '${notif.subject} ${notif.content}'.toLowerCase();
                    
                    if (content.contains('document')) {
                      context.push('/documents');
                    } else if (content.contains('actualité') || content.contains('news') || content.contains('feed')) {
                      // Navigate to dashboard and feed could be clicked
                      context.go('/home');
                    } else if (content.contains('congé') || content.contains('autorisation') || content.contains('demande')) {
                      context.push('/demandes-admin');
                    } else if (content.contains('plainte') || content.contains('réclamation')) {
                      context.push('/plaintes');
                    }
                  },
                );
              },
            ),
    );
  }
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({required this.notif, required this.onTap});

  final AppNotification notif;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final timeStr =
        DateFormat('dd/MM • HH:mm', 'fr').format(notif.receivedAt);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Container(
          decoration: BoxDecoration(
            color: notif.isRead
                ? scheme.surface
                : scheme.primaryContainer.withAlpha(60),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: notif.isRead
                  ? scheme.outlineVariant.withAlpha(80)
                  : scheme.primary.withAlpha(100),
            ),
          ),
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: notif.isRead
                      ? scheme.surfaceContainerHighest
                      : scheme.primary.withAlpha(30),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.notifications_rounded,
                  size: 20,
                  color: notif.isRead ? scheme.outline : scheme.primary,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            notif.subject,
                            style: Theme.of(context)
                                .textTheme
                                .labelLarge
                                ?.copyWith(
                                  fontWeight: notif.isRead
                                      ? FontWeight.w500
                                      : FontWeight.w700,
                                  color: scheme.onSurface,
                                ),
                          ),
                        ),
                        if (!notif.isRead)
                          Container(
                            width: 8,
                            height: 8,
                            decoration: BoxDecoration(
                              color: scheme.primary,
                              shape: BoxShape.circle,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      notif.content,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      timeStr,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: scheme.outline,
                          ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends ConsumerWidget {
  const _EmptyState({required this.connected});

  final bool connected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest,
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.notifications_off_outlined,
              size: 48,
              color: scheme.outline,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            'Aucune notification',
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          Text(
            connected
                ? 'Vous êtes connecté en temps réel'
                : 'En attente de connexion...',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: connected ? Colors.green : Colors.orange,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 6),
              Text(
                connected ? 'Connecté' : 'Déconnecté',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: connected ? Colors.green : Colors.orange,
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          ElevatedButton.icon(
            onPressed: () async {
              final userState = ref.read(collaborateurNotifierProvider);
              
              // If we don't have the user info yet (or it failed with 403), try fetching it again
              if (userState.valueOrNull == null) {
                debugPrint('[UI] User info missing or error, attempting to fetch profile...');
                await ref.read(collaborateurNotifierProvider.notifier).fetchMoi();
              }
              
              final user = ref.read(collaborateurNotifierProvider).valueOrNull;
              final userId = user?.identifiant;

              if (userId != null) {
                debugPrint('[AUTH] ═════════════════════════════════════════');
                debugPrint('[AUTH] REFRESH TRIGGERED - USER INFO:');
                debugPrint('[AUTH] ID: ${user?.identifiant}');
                debugPrint('[AUTH] NOM: ${user?.name}');
                debugPrint('[AUTH] PRENOM: ${user?.prenom}');
                debugPrint('[AUTH] EMAIL: ${user?.email}');
                debugPrint('[AUTH] ═════════════════════════════════════════');

                debugPrint('[UI] Manual connection trigger for user: $userId');
                ref.read(notificationProvider.notifier).connect(userId);
              } else {
                debugPrint('[UI] Cannot connect: User ID is null');
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Erreur: Impossible de récupérer votre identifiant.')),
                  );
                }
              }
            },
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('Réessayer la connexion'),
          ),
        ],
      ),
    );
  }
}
