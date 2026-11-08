import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/format/formats.dart';
import '../../core/notifications/notification_model.dart';
import '../../core/notifications/notification_navigation.dart';
import '../../core/notifications/notification_provider.dart';
import '../../core/theme/app_theme.dart';
import '../../core/widgets/rh_ui.dart';
import '../auth/providers/collaborateur_notifier.dart';
import 'liste_notifications.dart';

/// « Mes notifications » : liste conservée par le serveur, 20 par page, lu / non lu (ticket 2G).
class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  final ScrollController _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_surDefilement);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  void _surDefilement() {
    if (!_scroll.hasClients) return;
    if (_scroll.position.pixels >= _scroll.position.maxScrollExtent - 240) {
      ref.read(listeNotificationsProvider.notifier).chargerSuite();
    }
  }

  void _message(String texte) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(texte)));
  }

  Future<void> _toutMarquerLu() async {
    try {
      await ref.read(notificationProvider.notifier).markAllRead();
      if (!mounted) return;
      ref.read(listeNotificationsProvider.notifier).toutesLuesLocalement();
    } catch (e) {
      _message(rhErrorMessage(e));
    }
  }

  Future<void> _ouvrir(AppNotification notif) async {
    if (!notif.isRead) {
      try {
        await ref.read(notificationProvider.notifier).markRead(notif.id);
        if (!mounted) return;
        ref.read(listeNotificationsProvider.notifier).marquerLueLocalement(notif.id);
      } catch (e) {
        _message(rhErrorMessage(e));
        return;
      }
    }
    final route = routePourNotification(notif);
    if (route == null) {
      // Notification sans objet : marquée lue, aucun écran à ouvrir.
      return;
    }
    if (!mounted) return;
    context.push(route);
  }

  Future<void> _reconnecter() async {
    var info = ref.read(collaborateurNotifierProvider).valueOrNull;
    if (info == null) {
      await ref.read(collaborateurNotifierProvider.notifier).fetchMoi();
      info = ref.read(collaborateurNotifierProvider).valueOrNull;
    }
    if (info == null) {
      _message('Impossible de récupérer votre profil. Réessayez dans un instant.');
      return;
    }
    await ref.read(notificationProvider.notifier).connect(info.identifiant);
  }

  @override
  Widget build(BuildContext context) {
    final liste = ref.watch(listeNotificationsProvider);
    final etat = ref.watch(notificationProvider);

    // Notification temps réel : ajoutée en tête de la liste affichée.
    ref.listen<List<AppNotification>>(
      notificationProvider.select((s) => s.notifications),
      (prev, next) {
        if (next.isEmpty) return;
        ref.read(listeNotificationsProvider.notifier).inserer(next.first);
      },
    );

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Mes notifications'),
        actions: [
          if (etat.nonLues > 0)
            TextButton.icon(
              icon: const Icon(Icons.done_all_rounded, size: 18),
              label: const Text('Tout marquer comme lu'),
              onPressed: _toutMarquerLu,
            ),
        ],
      ),
      body: Column(
        children: [
          if (!etat.connected)
            Container(
              width: double.infinity,
              color: AppTheme.warningSurface,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      'Mise à jour en temps réel indisponible.',
                      style: TextStyle(fontSize: 12),
                    ),
                  ),
                  TextButton(onPressed: _reconnecter, child: const Text('Reconnecter')),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Row(
              children: [
                ChoiceChip(
                  label: const Text('Toutes'),
                  selected: !liste.nonLuesSeulement,
                  onSelected: (_) => ref
                      .read(listeNotificationsProvider.notifier)
                      .choisirFiltre(nonLuesSeulement: false),
                ),
                const SizedBox(width: 8),
                ChoiceChip(
                  label: const Text('Non lues'),
                  selected: liste.nonLuesSeulement,
                  onSelected: (_) => ref
                      .read(listeNotificationsProvider.notifier)
                      .choisirFiltre(nonLuesSeulement: true),
                ),
              ],
            ),
          ),
          Expanded(child: _corps(liste)),
        ],
      ),
    );
  }

  Widget _corps(ListeNotificationsState liste) {
    final notifier = ref.read(listeNotificationsProvider.notifier);
    if (liste.chargement) {
      return const Center(child: CircularProgressIndicator());
    }
    if (liste.erreur != null) {
      return RhErrorState.depuis(liste.erreur, onRetry: notifier.charger);
    }
    if (liste.items.isEmpty) {
      return RefreshIndicator(
        onRefresh: notifier.charger,
        child: ListView(
          children: const [
            SizedBox(height: 80),
            RhEmptyState(
              icon: Icons.notifications_off_outlined,
              title: 'Aucune notification',
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: notifier.charger,
      child: ListView.separated(
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        itemCount: liste.items.length + 1,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, i) {
          if (i == liste.items.length) return _pied(liste);
          final notif = liste.items[i];
          return _NotificationCard(notif: notif, onTap: () => _ouvrir(notif));
        },
      ),
    );
  }

  Widget _pied(ListeNotificationsState liste) {
    if (liste.chargementSuite) {
      return const Padding(
        padding: EdgeInsets.all(12),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (liste.erreurSuite != null) {
      return Column(
        children: [
          Text(rhErrorMessage(liste.erreurSuite), textAlign: TextAlign.center),
          TextButton(
            onPressed: ref.read(listeNotificationsProvider.notifier).chargerSuite,
            child: const Text('Réessayer'),
          ),
        ],
      );
    }
    if (liste.aSuite) {
      return TextButton(
        onPressed: ref.read(listeNotificationsProvider.notifier).chargerSuite,
        child: const Text('Afficher plus'),
      );
    }
    return const SizedBox.shrink();
  }
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({required this.notif, required this.onTap});

  final AppNotification notif;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final theme = Theme.of(context);
    return Semantics(
      label: notif.isRead ? 'Notification lue' : 'Notification non lue',
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Container(
            decoration: BoxDecoration(
              color: notif.isRead ? scheme.surface : scheme.primaryContainer.withAlpha(60),
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
                              style: theme.textTheme.labelLarge?.copyWith(
                                fontWeight: notif.isRead ? FontWeight.w500 : FontWeight.w700,
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
                        notif.displayContent,
                        style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        [
                          if ((notif.objetNumero ?? '').isNotEmpty) notif.objetNumero!,
                          formatDateHeure(notif.receivedAt),
                        ].join(' · '),
                        style: theme.textTheme.labelSmall?.copyWith(color: scheme.outline),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
