import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';
import '../data/presence_errors.dart';
import '../data/presence_gps.dart';
import '../data/presence_models.dart';
import '../data/presence_repository.dart';

/// Pointage Must : scan QR → GPS → ENTREE/SORTIE → API (pas de face).
class PointageScreen extends ConsumerStatefulWidget {
  const PointageScreen({super.key});

  @override
  ConsumerState<PointageScreen> createState() => _PointageScreenState();
}

enum _PointageStep { scan, type, submitting, result }

class _PointageScreenState extends ConsumerState<PointageScreen> {
  _PointageStep _step = _PointageStep.scan;
  String? _qrToken;
  TypePointage? _type;
  TypePointage? _suggestion;
  PointageResult? _result;
  bool _demandeEnvoyee = false;
  String? _error;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _loadSuggestion();
  }

  Future<void> _loadSuggestion() async {
    try {
      final list =
          await ref.read(presenceRepositoryProvider).mesPointages(size: 5);
      PointageHistoriqueItem? lastValide;
      for (final e in list) {
        if (PointageStatut.estValide(e.statut)) {
          lastValide = e;
          break;
        }
      }
      if (!mounted || lastValide == null) return;
      final last = TypePointage.tryParse(lastValide.type);
      setState(() {
        _suggestion = last == TypePointage.entree
            ? TypePointage.sortie
            : TypePointage.entree;
      });
    } catch (_) {
      // Suggestion UX only — ignore réseau / service absent.
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Pointage'),
        actions: [
          IconButton(
            tooltip: 'Pointages manuels',
            onPressed: () => context.push('/pointage/manuel'),
            icon: const Icon(Icons.edit_calendar_rounded),
          ),
          const NotificationActionBadge(),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              _StepIndicator(step: _stepIndex),
              const SizedBox(height: 32),
              Expanded(child: _buildContent()),
            ],
          ),
        ),
      ),
    );
  }

  int get _stepIndex {
    switch (_step) {
      case _PointageStep.scan:
        return 1;
      case _PointageStep.type:
      case _PointageStep.submitting:
        return 2;
      case _PointageStep.result:
        return 3;
    }
  }

  Widget _buildContent() {
    switch (_step) {
      case _PointageStep.scan:
        return _StepCard(
          icon: Icons.qr_code_scanner_rounded,
          iconBg: AppTheme.primarySurface,
          iconColor: AppTheme.primary,
          title: 'Scanner le QR Code',
          subtitle:
              'Scannez le code QR du site de pointage, puis confirmez entrée ou sortie.',
          action: FilledButton.icon(
            onPressed: _busy ? null : () => _startQrScan(context),
            icon: const Icon(Icons.camera_alt_rounded),
            label: const Text('Lancer le scanner'),
          ),
        );
      case _PointageStep.type:
        return _buildTypeChoice();
      case _PointageStep.submitting:
        return const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Enregistrement du pointage…'),
            ],
          ),
        );
      case _PointageStep.result:
        return _buildResult();
    }
  }

  Widget _buildTypeChoice() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Type de pointage',
          style: Theme.of(context)
              .textTheme
              .titleMedium
              ?.copyWith(fontWeight: FontWeight.w700),
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          'Votre position GPS sera envoyée au serveur (contrôle ≤ 50 m).',
          style: Theme.of(context).textTheme.bodySmall,
          textAlign: TextAlign.center,
        ),
        if (_suggestion != null) ...[
          const SizedBox(height: 12),
          Text(
            _suggestion == TypePointage.sortie
                ? 'Suggestion : SORTIE (dernier pointage = ENTREE)'
                : 'Suggestion : ENTREE (dernier pointage = SORTIE)',
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: AppTheme.primary,
                ),
            textAlign: TextAlign.center,
          ),
        ],
        const SizedBox(height: 28),
        Row(
          children: [
            Expanded(
              child: _TypeButton(
                label: 'Entrée',
                icon: Icons.login_rounded,
                selected: _type == TypePointage.entree,
                suggested: _suggestion == TypePointage.entree,
                onTap: () => setState(() => _type = TypePointage.entree),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _TypeButton(
                label: 'Sortie',
                icon: Icons.logout_rounded,
                selected: _type == TypePointage.sortie,
                suggested: _suggestion == TypePointage.sortie,
                onTap: () => setState(() => _type = TypePointage.sortie),
              ),
            ),
          ],
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(
            _error!,
            style: Theme.of(context)
                .textTheme
                .bodySmall
                ?.copyWith(color: AppTheme.error),
            textAlign: TextAlign.center,
          ),
        ],
        const Spacer(),
        FilledButton.icon(
          onPressed: _type == null || _busy ? null : _submitPointage,
          icon: const Icon(Icons.my_location_rounded),
          label: const Text('Pointer avec ma position'),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: _busy
              ? null
              : () => setState(() {
                    _step = _PointageStep.scan;
                    _qrToken = null;
                    _type = null;
                    _error = null;
                  }),
          child: const Text('Rescanner'),
        ),
      ],
    );
  }

  Widget _buildResult() {
    final result = _result;
    final ok = result?.isValide == true;
    final ts = result?.serverTs;
    final tsLabel = ts == null
        ? null
        : DateFormat('dd/MM/yyyy HH:mm:ss').format(ts.toLocal());

    final typeLabel = _type == TypePointage.entree
        ? 'Type : Entrée'
        : (_type == TypePointage.sortie ? 'Type : Sortie' : null);

    final title = ok ? 'Pointage validé' : 'Pointage refusé';
    final subtitle = ok
        ? [
            if (typeLabel != null) typeLabel,
            if (tsLabel != null) 'Heure serveur : $tsLabel',
            if (result?.siteLibelle != null) 'Site : ${result!.siteLibelle}',
            if (result?.distanceMetres != null)
              'Distance : ${result!.distanceMetres!.toStringAsFixed(1)} m',
          ].whereType<String>().join('\n')
        : [
            messageMotifPointage(
              result?.statut,
              motifServeur: result?.motifRejet,
            ),
            if (result?.distanceMetres != null)
              'Distance mesurée : ${result!.distanceMetres!.toStringAsFixed(1)} m',
          ].join('\n');

    return _StepCard(
      icon: ok ? Icons.check_circle_rounded : Icons.cancel_rounded,
      iconBg: ok ? AppTheme.successSurface : AppTheme.errorSurface,
      iconColor: ok ? AppTheme.success : AppTheme.error,
      title: title,
      subtitle: subtitle.isEmpty ? (ok ? 'Enregistré.' : 'Refusé.') : subtitle,
      action: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // D-P04 : un rejet de bonne foi est rattrapé par le responsable
          if (!ok && result?.id != null) ...[
            FilledButton.icon(
              onPressed: _demandeEnvoyee || _busy ? null : _demanderPointageManuel,
              icon: const Icon(Icons.edit_calendar_rounded),
              label: Text(
                _demandeEnvoyee ? 'Demande envoyée' : 'Demander un pointage manuel',
              ),
            ),
            const SizedBox(height: 8),
          ],
          OutlinedButton(
            onPressed: _reset,
            child: const Text('Nouveau pointage'),
          ),
        ],
      ),
    );
  }

  Future<void> _demanderPointageManuel() async {
    final rejetId = _result?.id;
    if (rejetId == null) return;
    final justification = await showDialog<String>(
      context: context,
      builder: (_) => const _JustificationDialog(),
    );
    if (justification == null || !mounted) return;
    setState(() => _busy = true);
    try {
      final demande = await ref.read(presenceRepositoryProvider).demanderPointageManuel(
            pointageRejeteId: rejetId,
            justification: justification,
          );
      if (!mounted) return;
      setState(() => _demandeEnvoyee = true);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Demande envoyée à ${demande.valideurLibelle} pour validation.')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(messageErreurPresence(e, fallback: 'Envoi de la demande impossible.')),
          backgroundColor: AppTheme.error,
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _reset() {
    setState(() {
      _step = _PointageStep.scan;
      _qrToken = null;
      _type = null;
      _result = null;
      _error = null;
      _busy = false;
      _demandeEnvoyee = false;
    });
    _loadSuggestion();
  }

  Future<void> _startQrScan(BuildContext context) async {
    setState(() => _error = null);
    final result = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.black,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetContext) {
        var closed = false;
        return SizedBox(
          height: MediaQuery.of(sheetContext).size.height * 0.7,
          child: Stack(
            children: [
              MobileScanner(
                onDetect: (capture) {
                  if (closed) return;
                  final barcodes = capture.barcodes;
                  final raw = barcodes.isNotEmpty
                      ? barcodes.first.rawValue
                      : null;
                  if (raw == null || raw.trim().isEmpty) return;
                  closed = true;
                  Navigator.pop(sheetContext, raw.trim());
                },
              ),
              Positioned(
                top: 12,
                right: 12,
                child: IconButton(
                  style: IconButton.styleFrom(backgroundColor: Colors.black54),
                  onPressed: () => Navigator.pop(sheetContext),
                  icon: const Icon(Icons.close, color: Colors.white),
                ),
              ),
            ],
          ),
        );
      },
    );

    if (!mounted || result == null) return;
    setState(() {
      _qrToken = result;
      _step = _PointageStep.type;
      _type = _suggestion;
    });
  }

  Future<void> _submitPointage() async {
    final token = _qrToken;
    final type = _type;
    if (token == null || type == null) return;

    setState(() {
      _busy = true;
      _error = null;
      _step = _PointageStep.submitting;
    });

    final gps = await PresenceGps.obtain();
    if (!mounted) return;
    if (gps.position == null) {
      setState(() {
        _busy = false;
        _step = _PointageStep.type;
        _error = gps.error ?? 'GPS requis pour pointer.';
      });
      return;
    }

    final pos = gps.position!;
    final request = PointageRequest(
      qrToken: token,
      type: type,
      latitude: pos.latitude,
      longitude: pos.longitude,
      accuracyMeters: pos.accuracyMeters,
      clientTimestamp: DateTime.now().toUtc(),
      idempotencyKey: _newIdempotencyKey(),
      positionSimulee: pos.isMock,
    );

    try {
      final result =
          await ref.read(presenceRepositoryProvider).creerPointage(request);
      if (!mounted) return;
      setState(() {
        _busy = false;
        _result = result;
        _step = _PointageStep.result;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _step = _PointageStep.type;
        _error = messageErreurPresence(e);
      });
    }
  }

  String _newIdempotencyKey() {
    final r = Random.secure();
    final bytes = List<int>.generate(16, (_) => r.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    String hex(int b) => b.toRadixString(16).padLeft(2, '0');
    final h = bytes.map(hex).join();
    return '${h.substring(0, 8)}-${h.substring(8, 12)}-'
        '${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}';
  }
}

class _TypeButton extends StatelessWidget {
  const _TypeButton({
    required this.label,
    required this.icon,
    required this.selected,
    required this.suggested,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final bool suggested;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppTheme.primarySurface : AppTheme.surface,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 20),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected
                  ? AppTheme.primary
                  : (suggested ? AppTheme.primaryLight : AppTheme.border),
              width: selected ? 2 : 1,
            ),
          ),
          child: Column(
            children: [
              Icon(
                icon,
                size: 32,
                color: selected ? AppTheme.primary : AppTheme.textSecondary,
              ),
              const SizedBox(height: 8),
              Text(
                label,
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  color: selected ? AppTheme.primary : AppTheme.textPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StepIndicator extends StatelessWidget {
  final int step;
  const _StepIndicator({required this.step});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _StepDot(label: 'QR', active: step >= 1, done: step > 1),
        Expanded(
          child: Container(
            height: 1,
            color: step > 1 ? AppTheme.primary : AppTheme.border,
          ),
        ),
        _StepDot(label: 'Type + GPS', active: step >= 2, done: step > 2),
        Expanded(
          child: Container(
            height: 1,
            color: step > 2 ? AppTheme.primary : AppTheme.border,
          ),
        ),
        _StepDot(label: 'Résultat', active: step >= 3, done: false),
      ],
    );
  }
}

class _StepDot extends StatelessWidget {
  final String label;
  final bool active;
  final bool done;
  const _StepDot({
    required this.label,
    required this.active,
    required this.done,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: active ? AppTheme.primary : AppTheme.border,
          ),
          child: Icon(
            done ? Icons.check_rounded : Icons.circle,
            size: done ? 18 : 10,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            color: active ? AppTheme.primary : AppTheme.textSecondary,
            fontWeight: active ? FontWeight.w600 : FontWeight.w400,
          ),
        ),
      ],
    );
  }
}

class _StepCard extends StatelessWidget {
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final String title;
  final String subtitle;
  final Widget action;

  const _StepCard({
    required this.icon,
    required this.iconBg,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    required this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 96,
            height: 96,
            decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
            child: Icon(icon, size: 48, color: iconColor),
          ),
          const SizedBox(height: 24),
          Text(
            title,
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(fontWeight: FontWeight.w700),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 36),
          SizedBox(width: double.infinity, child: action),
        ],
      ),
    );
  }
}

/// Justification obligatoire d'une demande de pointage manuel (D-P04).
class _JustificationDialog extends StatefulWidget {
  const _JustificationDialog();

  @override
  State<_JustificationDialog> createState() => _JustificationDialogState();
}

class _JustificationDialogState extends State<_JustificationDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final texte = _controller.text.trim();
    return AlertDialog(
      title: const Text('Demander un pointage manuel'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Votre responsable validera ce pointage à l’heure du refus. '
            'Expliquez pourquoi le pointage automatique a échoué.',
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _controller,
            autofocus: true,
            maxLines: 3,
            maxLength: 500,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: 'Justification *',
              hintText: 'Ex. : signal GPS trop faible dans le bâtiment',
              helperText: '10 caractères minimum',
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Annuler'),
        ),
        FilledButton(
          onPressed: texte.length < 10 ? null : () => Navigator.pop(context, texte),
          child: const Text('Envoyer'),
        ),
      ],
    );
  }
}
