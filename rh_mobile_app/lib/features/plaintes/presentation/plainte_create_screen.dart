import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../data/plainte_repository.dart';
import 'plaintes_list_screen.dart';

class PlainteCreateScreen extends ConsumerStatefulWidget {
  const PlainteCreateScreen({super.key});

  @override
  ConsumerState<PlainteCreateScreen> createState() => _PlainteCreateScreenState();
}

class _PlainteCreateScreenState extends ConsumerState<PlainteCreateScreen> {
  final _form = GlobalKey<FormState>();
  final _titre = TextEditingController();
  final _desc = TextEditingController();
  String _type = 'INTERNE';
  var _loading = false;

  @override
  void dispose() {
    _titre.dispose();
    _desc.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      await ref.read(plainteRepositoryProvider).create(
            typePlainte: _type,
            titre: _titre.text.trim(),
            description: _desc.text.trim(),
          );
      ref.invalidate(plaintesListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Plainte envoyée')));
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Nouvelle plainte')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              DropdownButtonFormField<String>(
                initialValue: _type,
                decoration: const InputDecoration(labelText: 'Type'),
                items: const [
                  DropdownMenuItem(value: 'INTERNE', child: Text('Interne')),
                  DropdownMenuItem(value: 'EXTERNE', child: Text('Externe')),
                ],
                onChanged: (v) => setState(() => _type = v ?? 'INTERNE'),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _titre,
                decoration: const InputDecoration(labelText: 'Titre'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Requis' : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _desc,
                maxLines: 5,
                decoration: const InputDecoration(labelText: 'Description', alignLabelWithHint: true),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Requis' : null,
              ),
              const SizedBox(height: 28),
              FilledButton(
                onPressed: _loading ? null : _submit,
                child: _loading
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('Envoyer'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
