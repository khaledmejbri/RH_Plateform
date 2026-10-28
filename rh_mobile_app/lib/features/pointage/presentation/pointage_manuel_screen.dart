import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/theme/app_theme.dart';
import '../data/presence_errors.dart';
import '../data/presence_models.dart';
import '../data/presence_repository.dart';

final _mesDemandesProvider = FutureProvider.autoDispose<List<DemandeManuelle>>((ref) {
  return ref.watch(presenceRepositoryProvider).mesDemandesManuelles();
});

final _aValiderProvider = FutureProvider.autoDispose<List<DemandeManuelle>>((ref) {
  return ref.watch(presenceRepositoryProvider).demandesManuellesAValider();
});

final _heure = DateFormat('dd/MM/yyyy HH:mm', 'fr_FR');

/// Pointages manuels (D-P04) : mes demandes, et la file du responsable à valider.
class PointageManuelScreen extends ConsumerWidget {
  const PointageManuelScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final aValider = ref.watch(_aValiderProvider).valueOrNull?.length ?? 0;
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        backgroundColor: AppTheme.background,
        appBar: AppBar(
          backgroundColor: AppTheme.surface,
          title: const Text('Pointages manuels'),
          bottom: TabBar(
            tabs: [
              const Tab(text: 'Mes demandes'),
              Tab(text: aValider > 0 ? 'À valider ($aValider)' : 'À valider'),
            ],
          ),
        ),
        body: const TabBarView(
          children: [_MesDemandes(), _AValider()],
        ),
      ),
    );
  }
}

class _MesDemandes extends ConsumerWidget {
  const _MesDemandes();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _Liste(
      async: ref.watch(_mesDemandesProvider),
      onRefresh: () async => ref.invalidate(_mesDemandesProvider),
      vide: 'Aucune demande. Après un pointage refusé, vous pouvez demander un pointage manuel.',
      itemBuilder: (d) => _DemandeCard(demande: d, vueResponsable: false),
    );
  }
}

class _AValider extends ConsumerWidget {
  const _AValider();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _Liste(
      async: ref.watch(_aValiderProvider),
      onRefresh: () async => ref.invalidate(_aValiderProvider),
      vide: 'Aucun pointage manuel à valider.',
      itemBuilder: (d) => _DemandeCard(
        demande: d,
        vueResponsable: true,
        onDecision: () {
          ref.invalidate(_aValiderProvider);
        },
      ),
    );
  }
}

class _Liste extends StatelessWidget {
  const _Liste({
    required this.async,
    required this.onRefresh,
    required this.vide,
    required this.itemBuilder,
  });

  final AsyncValue<List<DemandeManuelle>> async;
  final Future<void> Function() onRefresh;
  final String vide;
  final Widget Function(DemandeManuelle) itemBuilder;

  @override
  Widget build(BuildContext context) {
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(messageErreurPresence(e, fallback: 'Chargement impossible.'), textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton(onPressed: onRefresh, child: const Text('Réessayer')),
            ],
          ),
        ),
      ),
      data: (items) => RefreshIndicator(
        onRefresh: onRefresh,
        child: items.isEmpty
            ? ListView(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(vide, textAlign: TextAlign.center, style: const TextStyle(color: AppTheme.textSecondary)),
                  ),
                ],
              )
            : ListView.separated(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (_, i) => itemBuilder(items[i]),
              ),
      ),
    );
  }
}

class _DemandeCard extends ConsumerStatefulWidget {
  const _DemandeCard({required this.demande, required this.vueResponsable, this.onDecision});

  final DemandeManuelle demande;
  final bool vueResponsable;
  final VoidCallback? onDecision;

  @override
  ConsumerState<_DemandeCard> createState() => _DemandeCardState();
}

class _DemandeCardState extends ConsumerState<_DemandeCard> {
  bool _busy = false;

  Future<void> _valider() async {
    await _decider(() => ref.read(presenceRepositoryProvider).validerDemandeManuelle(widget.demande.id),
        'Pointage manuel validé.');
  }

  Future<void> _refuser() async {
    final motif = await showDialog<String>(context: context, builder: (_) => const _MotifRefusDialog());
    if (motif == null) return;
    await _decider(() => ref.read(presenceRepositoryProvider).refuserDemandeManuelle(widget.demande.id, motif),
        'Pointage manuel refusé.');
  }

  Future<void> _decider(Future<DemandeManuelle> Function() action, String succes) async {
    setState(() => _busy = true);
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(succes)));
      widget.onDecision?.call();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(messageErreurPresence(e)), backgroundColor: AppTheme.error),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = widget.demande;
    final (fond, texte) = switch (d.statut) {
      'VALIDEE' => (AppTheme.successSurface, AppTheme.success),
      'REFUSEE' => (AppTheme.errorSurface, AppTheme.errorDark),
      _ => (AppTheme.warningSurface, AppTheme.warning),
    };
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
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
                  widget.vueResponsable
                      ? (d.collaborateurNom ?? 'Collaborateur')
                      : '${d.typeLibelle} du ${d.heureDeclaree != null ? _heure.format(d.heureDeclaree!.toLocal()) : '—'}',
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(8)),
                child: Text(d.statutLibelle, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: texte)),
              ),
            ],
          ),
          if (widget.vueResponsable) ...[
            const SizedBox(height: 4),
            Text(
              '${d.typeLibelle} du ${d.heureDeclaree != null ? _heure.format(d.heureDeclaree!.toLocal()) : '—'}',
              style: const TextStyle(color: AppTheme.textSecondary),
            ),
          ],
          const SizedBox(height: 8),
          Text('« ${d.justification} »'),
          if (d.motifRejetInitial != null) ...[
            const SizedBox(height: 4),
            Text('Refus automatique : ${d.motifRejetInitial}',
                style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
          ],
          const SizedBox(height: 6),
          if (!widget.vueResponsable)
            Text(
              d.enAttente
                  ? 'En attente de validation par ${d.valideurLibelle}.'
                  : (d.validee ? 'Validée par ${d.decidePar ?? d.valideurLibelle}.' : 'Refusée par ${d.decidePar ?? d.valideurLibelle} : ${d.motifRefus ?? ''}'),
              style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
            ),
          if (widget.vueResponsable && d.enAttente) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _busy ? null : _refuser,
                    child: const Text('Refuser'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton(
                    onPressed: _busy ? null : _valider,
                    child: const Text('Valider'),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _MotifRefusDialog extends StatefulWidget {
  const _MotifRefusDialog();

  @override
  State<_MotifRefusDialog> createState() => _MotifRefusDialogState();
}

class _MotifRefusDialogState extends State<_MotifRefusDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final motif = _controller.text.trim();
    return AlertDialog(
      title: const Text('Refuser le pointage manuel'),
      content: TextField(
        controller: _controller,
        autofocus: true,
        maxLines: 3,
        maxLength: 500,
        onChanged: (_) => setState(() {}),
        decoration: const InputDecoration(labelText: 'Motif du refus *'),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
        FilledButton(
          onPressed: motif.isEmpty ? null : () => Navigator.pop(context, motif),
          child: const Text('Refuser'),
        ),
      ],
    );
  }
}
