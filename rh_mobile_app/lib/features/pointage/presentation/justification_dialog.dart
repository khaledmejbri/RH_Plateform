import 'package:flutter/material.dart';

/// Saisie de la justification d'une demande de pointage manuel (10 caractères minimum, D-P04).
class JustificationPointageDialog extends StatefulWidget {
  const JustificationPointageDialog({super.key});

  @override
  State<JustificationPointageDialog> createState() => _JustificationPointageDialogState();
}

class _JustificationPointageDialogState extends State<JustificationPointageDialog> {
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
