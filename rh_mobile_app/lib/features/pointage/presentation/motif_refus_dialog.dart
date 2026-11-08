import 'package:flutter/material.dart';

/// Saisie du motif obligatoire d'un refus de pointage manuel, après rappel « Êtes-vous sûr ? ».
class MotifRefusDialog extends StatefulWidget {
  const MotifRefusDialog({super.key, required this.element});

  /// Nom de l'élément refusé, rappelé dans le dialogue.
  final String element;

  @override
  State<MotifRefusDialog> createState() => _MotifRefusDialogState();
}

class _MotifRefusDialogState extends State<MotifRefusDialog> {
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
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Êtes-vous sûr ?', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(widget.element, style: const TextStyle(fontWeight: FontWeight.w600)),
          const SizedBox(height: 12),
          TextField(
            controller: _controller,
            autofocus: true,
            maxLines: 3,
            maxLength: 500,
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(labelText: 'Motif du refus *'),
          ),
        ],
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
