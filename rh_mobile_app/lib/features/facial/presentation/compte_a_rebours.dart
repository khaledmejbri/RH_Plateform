import 'dart:async';

import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';

/// Format `mm:ss` d'une durée restante (jamais négatif).
String formaterCompteARebours(Duration d) {
  final total = d.isNegative ? 0 : d.inSeconds + (d.inMilliseconds % 1000 > 0 ? 1 : 0);
  final m = (total ~/ 60).toString().padLeft(2, '0');
  final s = (total % 60).toString().padLeft(2, '0');
  return '$m:$s';
}

/// Compte à rebours jusqu'à [fin] (blocage de 15 min après 3 échecs faciaux, D-P10).
class CompteARebours extends StatefulWidget {
  const CompteARebours({super.key, required this.fin, this.onTermine, this.style});

  final DateTime fin;
  final VoidCallback? onTermine;
  final TextStyle? style;

  @override
  State<CompteARebours> createState() => _CompteAReboursState();
}

class _CompteAReboursState extends State<CompteARebours> {
  Timer? _timer;
  late Duration _reste;
  bool _termine = false;

  @override
  void initState() {
    super.initState();
    _reste = widget.fin.difference(DateTime.now());
    _timer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
    if (_reste <= Duration.zero) _finir();
  }

  void _tick() {
    final reste = widget.fin.difference(DateTime.now());
    if (!mounted) return;
    setState(() => _reste = reste);
    if (reste <= Duration.zero) _finir();
  }

  void _finir() {
    if (_termine) return;
    _termine = true;
    _timer?.cancel();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.onTermine?.call();
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Text(
      formaterCompteARebours(_reste),
      style: widget.style ??
          Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800,
                color: AppTheme.error,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
    );
  }
}
