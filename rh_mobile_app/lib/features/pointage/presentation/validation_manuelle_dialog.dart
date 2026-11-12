import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../data/demande_manuelle_textes.dart';
import '../data/presence_models.dart';

/// Confirmation de validation d'un pointage manuel (D-P24.b) : rappelle les signaux de vigilance et
/// recueille le commentaire (obligatoire avec signal, sinon facultatif ; 10 à 500 caractères après `trim`).
///
/// Renvoie le commentaire nettoyé (chaîne vide si aucun), ou `null` si l'utilisateur annule.
class ValidationManuelleDialog extends StatefulWidget {
  const ValidationManuelleDialog({super.key, required this.element, required this.avecSignal, this.signaux = const []});

  final String element;
  final bool avecSignal;
  final List<SignalVigilance> signaux;

  @override
  State<ValidationManuelleDialog> createState() => _ValidationManuelleDialogState();
}

class _ValidationManuelleDialogState extends State<ValidationManuelleDialog> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final brut = _controller.text;
    final erreur = erreurCommentaireValidation(brut, avecSignal: widget.avecSignal);
    final saisi = brut.trim().isNotEmpty;
    return AlertDialog(
      title: const Text('Valider le pointage manuel'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Êtes-vous sûr ?', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text(widget.element, style: const TextStyle(fontWeight: FontWeight.w600)),
            if (widget.signaux.isNotEmpty) ...[
              const SizedBox(height: 12),
              BandeauSignaux(signaux: widget.signaux),
            ],
            const SizedBox(height: 12),
            TextField(
              key: const Key('commentaire_validation'),
              controller: _controller,
              autofocus: true,
              maxLines: 3,
              maxLength: commentaireValideurMax,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: widget.avecSignal ? 'Commentaire *' : 'Commentaire (facultatif)',
                helperText: 'Au moins $commentaireValideurMin caractères',
                errorText: saisi ? erreur : null,
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Annuler')),
        FilledButton(
          onPressed: erreur != null ? null : () => Navigator.pop(context, brut.trim()),
          child: const Text('Valider'),
        ),
      ],
    );
  }
}

/// Pastille « Vigilance » des listes (D-P24.a).
class PastilleVigilance extends StatelessWidget {
  const PastilleVigilance({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: AppTheme.warningSurface, borderRadius: BorderRadius.circular(999)),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.warning_amber_rounded, size: 14, color: AppTheme.warning),
          SizedBox(width: 4),
          Text('Vigilance', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: AppTheme.warning)),
        ],
      ),
    );
  }
}

/// Signaux de vigilance en clair (libellés rédigés par le serveur).
class BandeauSignaux extends StatelessWidget {
  const BandeauSignaux({super.key, required this.signaux});

  final List<SignalVigilance> signaux;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('bandeau_signaux'),
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppTheme.warningSurface, borderRadius: BorderRadius.circular(12)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Signaux de vigilance', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          for (final s in signaux) Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Text('• ${s.libelle}'),
          ),
        ],
      ),
    );
  }
}
