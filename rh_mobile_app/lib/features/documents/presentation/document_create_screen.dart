import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/document_repository.dart';
import 'documents_list_screen.dart';

class DocumentCreateScreen extends ConsumerStatefulWidget {
  const DocumentCreateScreen({super.key});

  @override
  ConsumerState<DocumentCreateScreen> createState() => _DocumentCreateScreenState();
}

class _DocumentCreateScreenState extends ConsumerState<DocumentCreateScreen> {
  String _type = 'ATTESTATION_TRAVAIL';
  final _comment = TextEditingController();

  // Bulletin de paie fields
  String _bulletinType = 'DERNIERS'; // DERNIERS or SPECIFIQUE
  int _nombreMois = 3;
  String _moisAnnee = DateFormat('yyyy-MM').format(DateTime.now());
  final List<String> _moisSelectionnes = [];

  var _loading = false;
  bool _canSubmit = true;
  String? _restrictionMessage;

  Future<void> _showMonthYearPicker() async {
    final now = DateTime.now();
    int selectedYear = int.parse(_moisAnnee.split('-')[0]);
    int selectedMonth = int.parse(_moisAnnee.split('-')[1]);

    await showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Sélectionner le mois et l\'année', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        content: SizedBox(
          width: double.maxFinite,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Mois', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF64748B))),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                value: selectedMonth,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: Colors.grey.shade50,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                ),
                items: List.generate(12, (index) {
                  final month = index + 1;
                  return DropdownMenuItem(
                    value: month,
                    child: Text(DateFormat('MMMM', 'fr_FR').format(DateTime(2024, month))),
                  );
                }),
                onChanged: (value) {
                  if (value != null) selectedMonth = value;
                },
              ),
              const SizedBox(height: 16),
              const Text('Année', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF64748B))),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                value: selectedYear,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: Colors.grey.shade50,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                ),
                items: List.generate(10, (index) {
                  final year = now.year - index;
                  return DropdownMenuItem(value: year, child: Text('$year'));
                }),
                onChanged: (value) {
                  if (value != null) selectedYear = value;
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuler', style: TextStyle(color: Colors.grey)),
          ),
          FilledButton(
            onPressed: () {
              setState(() {
                _moisAnnee = '$selectedYear-${selectedMonth.toString().padLeft(2, '0')}';
              });
              Navigator.pop(context);
            },
            child: const Text('Confirmer'),
          ),
        ],
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    _checkRestrictions();
  }

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  Future<void> _checkRestrictions() async {
    if (_type == 'ATTESTATION_TRAVAIL') {
      try {
        final repo = ref.read(documentRepositoryProvider);
        final canRequest = await repo.canRequestAttestationTravail();
        setState(() {
          _canSubmit = canRequest.canRequest;
          _restrictionMessage = canRequest.message;
        });
      } catch (e) {
        debugPrint('Error checking restrictions: $e');
      }
    }
  }

  Future<void> _submit() async {
    if (!_canSubmit) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_restrictionMessage ?? 'Vous ne pouvez pas faire cette demande maintenant')),
      );
      return;
    }

    setState(() => _loading = true);
    try {
      final c = _comment.text.trim();

      Map<String, dynamic>? contenu;
      if (_type == 'BULLETIN_PAIE') {
        if (_bulletinType == 'DERNIERS') {
          contenu = {
            'type_demande': 'DERNIERS',
            'nombre_mois': _nombreMois,
          };
        } else {
          contenu = {
            'type_demande': 'SPECIFIQUE',
            'mois_annee': _moisAnnee,
            'nombre_mois': _nombreMois,
          };
        }
      }

      await ref.read(documentRepositoryProvider).create(
            typeDocument: _type,
            commentaire: c.isEmpty ? null : c,
            contenu: contenu,
          );
      ref.invalidate(documentsListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Demande enregistrée')));
        context.pop();
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    const types = [
      'ATTESTATION_TRAVAIL',
      'ATTESTATION_SALAIRE',
      'BULLETIN_PAIE',
      'ATTESTATION_CNSS',
      'DOCUMENT_INTERNE',
      'AUTRE',
    ];
    const primaryBlue = Color(0xFF2563EB);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Demande de document', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            "Quel document souhaitez-vous ?",
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF1E293B)),
          ),
          const SizedBox(height: 20),
          DropdownButtonFormField<String>(
            value: _type,
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              labelText: 'Type de document',
              prefixIcon: const Icon(Icons.description_outlined, color: primaryBlue),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: Colors.grey.shade200),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: Colors.grey.shade200),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: primaryBlue, width: 2),
              ),
            ),
            items: types
                .map((t) => DropdownMenuItem(value: t, child: Text(t.replaceAll('_', ' '))))
                .toList(),
            onChanged: (v) {
              if (v != null) {
                setState(() => _type = v);
                _checkRestrictions();
              }
            },
          ),

          if (_type == 'BULLETIN_PAIE') ...[
            const SizedBox(height: 24),
            const Text(
              "Type de demande",
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
            ),
            const SizedBox(height: 12),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() => _bulletinType = 'DERNIERS'),
                      borderRadius: const BorderRadius.horizontal(left: Radius.circular(16)),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        decoration: BoxDecoration(
                          color: _bulletinType == 'DERNIERS' ? primaryBlue.withOpacity(0.1) : Colors.transparent,
                          borderRadius: const BorderRadius.horizontal(left: Radius.circular(16)),
                        ),
                        child: Center(
                          child: Text(
                            'Derniers',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: _bulletinType == 'DERNIERS' ? primaryBlue : Colors.grey.shade600,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  Container(width: 1, color: Colors.grey.shade200, height: 48),
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() => _bulletinType = 'SPECIFIQUE'),
                      borderRadius: const BorderRadius.horizontal(right: Radius.circular(16)),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        decoration: BoxDecoration(
                          color: _bulletinType == 'SPECIFIQUE' ? primaryBlue.withOpacity(0.1) : Colors.transparent,
                          borderRadius: const BorderRadius.horizontal(right: Radius.circular(16)),
                        ),
                        child: Center(
                          child: Text(
                            'Spécifique',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: _bulletinType == 'SPECIFIQUE' ? primaryBlue : Colors.grey.shade600,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade200),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _bulletinType == 'DERNIERS'
                        ? "Nombre de derniers mois"
                        : "Nombre de mois à demander",
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      IconButton.filledTonal(
                        onPressed: _nombreMois > 1
                            ? () => setState(() => _nombreMois--)
                            : null,
                        icon: const Icon(Icons.remove),
                      ),
                      Expanded(
                        child: Center(
                          child: Text(
                            '$_nombreMois ${_nombreMois == 1 ? 'mois' : 'mois'}',
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                      IconButton.filledTonal(
                        onPressed: _nombreMois < 12
                            ? () => setState(() => _nombreMois++)
                            : null,
                        icon: const Icon(Icons.add),
                      ),
                    ],
                  ),
                  if (_bulletinType == 'DERNIERS')
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        'Les $_nombreMois derniers bulletins de paie seront demandés',
                        style: TextStyle(fontSize: 11, color: Colors.grey.shade600, fontStyle: FontStyle.italic),
                      ),
                    ),
                ],
              ),
            ),

            if (_bulletinType == 'SPECIFIQUE') ...[
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Mois de début",
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
                    ),
                    const SizedBox(height: 8),
                    InkWell(
                      onTap: () => _showMonthYearPicker(),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.grey.shade300),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              DateFormat('MMMM yyyy', 'fr_FR').format(DateFormat('yyyy-MM').parse(_moisAnnee)),
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                            ),
                            const Icon(Icons.calendar_today, size: 20, color: Colors.grey),
                          ],
                        ),
                      ),
                    ),
                    if (_nombreMois > 1)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Text(
                          'Du ${DateFormat('MMMM yyyy', 'fr_FR').format(DateFormat('yyyy-MM').parse(_moisAnnee))} et les ${_nombreMois - 1} mois précédents',
                          style: TextStyle(fontSize: 11, color: Colors.grey.shade600, fontStyle: FontStyle.italic),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ],

          if (!_canSubmit && _type == 'ATTESTATION_TRAVAIL') ...[
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.orange.shade50,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.orange.shade200),
              ),
              child: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: Colors.orange.shade700),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _restrictionMessage ?? 'Vous avez déjà une demande en cours',
                      style: TextStyle(color: Colors.orange.shade900, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 24),
          const Text(
            "Notes ou commentaires",
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _comment,
            maxLines: 4,
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              hintText: "Précisez votre besoin ici...",
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: Colors.grey.shade200),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: Colors.grey.shade200),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: primaryBlue, width: 2),
              ),
            ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: Colors.white,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.05),
                  blurRadius: 10,
                  offset: const Offset(0, -5),
                ),
              ],
            ),
            child: SafeArea(
              child: SizedBox(
                height: 50,
                child: FilledButton(
                  onPressed: (!_loading && _canSubmit) ? _submit : null,
                  style: FilledButton.styleFrom(
                    backgroundColor: _canSubmit ? primaryBlue : Colors.grey,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    elevation: 0,
                  ),
                  child: _loading
                      ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 3)
                      : const Text(
                          'Envoyer la demande',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                        ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
