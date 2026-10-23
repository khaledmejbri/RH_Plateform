import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/presence_errors.dart';
import '../data/presence_gps.dart';
import '../data/presence_models.dart';
import '../data/presence_repository.dart';

final _presenceSitesProvider =
    FutureProvider.autoDispose<List<PresenceSite>>((ref) {
  return ref.watch(presenceRepositoryProvider).listerSites();
});

/// RH | ADMIN — liste des sites + pose GPS appareil (P5).
class PresenceSitesAdminScreen extends ConsumerStatefulWidget {
  const PresenceSitesAdminScreen({super.key});

  @override
  ConsumerState<PresenceSitesAdminScreen> createState() =>
      _PresenceSitesAdminScreenState();
}

class _PresenceSitesAdminScreenState
    extends ConsumerState<PresenceSitesAdminScreen> {
  String? _savingSiteId;

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(_presenceSitesProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Sites de pointage'),
        actions: [
          IconButton(
            tooltip: 'Actualiser',
            onPressed: () => ref.invalidate(_presenceSitesProvider),
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  messageErreurPresence(
                    e,
                    fallback: 'Impossible de charger les sites.',
                  ),
                  style: Theme.of(context).textTheme.titleMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => ref.invalidate(_presenceSitesProvider),
                  child: const Text('Réessayer'),
                ),
              ],
            ),
          ),
        ),
        data: (sites) {
          if (sites.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text(
                  'Aucun site de pointage. Créez-en un depuis la Plateforme RH.',
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
            itemCount: sites.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (context, i) => _SiteTile(
              site: sites[i],
              saving: _savingSiteId == sites[i].id,
              onSaveGps: () => _saveGps(sites[i]),
            ),
          );
        },
      ),
    );
  }

  Future<void> _saveGps(PresenceSite site) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Enregistrer emplacement GPS'),
        content: Text(
          'Utiliser la position actuelle de cet appareil comme emplacement du site « ${site.libelle} » ?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Confirmer'),
          ),
        ],
      ),
    );
    if (confirm != true || !mounted) return;

    setState(() => _savingSiteId = site.id);
    final gps = await PresenceGps.obtain();
    if (!mounted) return;
    if (gps.position == null) {
      setState(() => _savingSiteId = null);
      _snack(gps.error ?? 'GPS requis.', error: true);
      return;
    }

    try {
      await ref.read(presenceRepositoryProvider).enregistrerEmplacement(
            siteId: site.id,
            latitude: gps.position!.latitude,
            longitude: gps.position!.longitude,
          );
      if (!mounted) return;
      ref.invalidate(_presenceSitesProvider);
      _snack('Emplacement GPS enregistré pour « ${site.libelle} ».');
    } catch (e) {
      if (!mounted) return;
      _snack(messageErreurPresence(e), error: true);
    } finally {
      if (mounted) setState(() => _savingSiteId = null);
    }
  }

  void _snack(String message, {bool error = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: error ? AppTheme.error : null,
      ),
    );
  }
}

class _SiteTile extends StatelessWidget {
  const _SiteTile({
    required this.site,
    required this.saving,
    required this.onSaveGps,
  });

  final PresenceSite site;
  final bool saving;
  final VoidCallback onSaveGps;

  @override
  Widget build(BuildContext context) {
    final gpsLabel = site.hasEmplacement
        ? '${site.latitude!.toStringAsFixed(5)}, ${site.longitude!.toStringAsFixed(5)}'
        : 'Emplacement non posé';

    return Material(
      color: AppTheme.surface,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppTheme.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    site.libelle,
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                ),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: site.actif
                        ? AppTheme.successSurface
                        : AppTheme.surfaceMuted,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    site.actif ? 'Actif' : 'Inactif',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                      color: site.actif
                          ? AppTheme.success
                          : AppTheme.textSecondary,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              'Code : ${site.code}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 4),
            Text(
              gpsLabel,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: site.hasEmplacement
                        ? AppTheme.textSecondary
                        : AppTheme.warning,
                  ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: saving || !site.actif ? null : onSaveGps,
                icon: saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.my_location_rounded),
                label: Text(
                  saving
                      ? 'Enregistrement…'
                      : 'Enregistrer emplacement GPS ici',
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
