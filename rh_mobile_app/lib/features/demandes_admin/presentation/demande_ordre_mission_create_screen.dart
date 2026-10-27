import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/demande_admin_errors.dart';
import '../data/demande_admin_repository.dart';
import 'demandes_admin_list_screen.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';

/// § 6.4 — Ordre de mission : lieu, date de début, date de fin, motif obligatoires ; fin ≥ début (C-M01-02).
/// SCRUM-17 : le dépôt `createOrdreMission` existait mais aucun écran ne l'appelait.
class DemandeOrdreMissionCreateScreen extends ConsumerStatefulWidget {
  const DemandeOrdreMissionCreateScreen({super.key});

  @override
  ConsumerState<DemandeOrdreMissionCreateScreen> createState() =>
      _DemandeOrdreMissionCreateScreenState();
}

class _DemandeOrdreMissionCreateScreenState
    extends ConsumerState<DemandeOrdreMissionCreateScreen> {
  static const Color _violet = Color(0xFF7C3AED);

  DateTime? _dateDebut;
  DateTime? _dateFin;
  final _lieuController = TextEditingController();
  final _motifController = TextEditingController();
  final _objectifsController = TextEditingController();
  var _loading = false;

  @override
  void dispose() {
    _lieuController.dispose();
    _motifController.dispose();
    _objectifsController.dispose();
    super.dispose();
  }

  Future<void> _pickDate(bool isDebut) async {
    final initial = isDebut
        ? (_dateDebut ?? DateTime.now())
        : (_dateFin ?? _dateDebut ?? DateTime.now());
    final d = await showDatePicker(
      context: context,
      firstDate: isDebut ? DateTime.now() : (_dateDebut ?? DateTime.now()),
      lastDate: DateTime(2040),
      initialDate: initial,
    );
    if (d == null) return;
    setState(() {
      if (isDebut) {
        _dateDebut = d;
        // Garde la cohérence fin ≥ début si l'utilisateur recule la date de début.
        if (_dateFin != null && _dateFin!.isBefore(d)) _dateFin = d;
      } else {
        _dateFin = d;
      }
    });
  }

  Future<void> _submit() async {
    if (_lieuController.text.trim().isEmpty) { _snack('Veuillez indiquer le lieu de la mission'); return; }
    if (_dateDebut == null || _dateFin == null) { _snack('Veuillez choisir les dates de début et de fin'); return; }
    if (_dateFin!.isBefore(_dateDebut!)) { _snack('La date de fin doit être postérieure ou égale à la date de début'); return; }
    if (_motifController.text.trim().isEmpty) { _snack('Veuillez indiquer le motif de la mission'); return; }

    final fmt = DateFormat('yyyy-MM-dd');
    setState(() => _loading = true);
    try {
      await ref.read(demandeAdminRepositoryProvider).createOrdreMission(
            lieu: _lieuController.text.trim(),
            dateDebut: fmt.format(_dateDebut!),
            dateFin: fmt.format(_dateFin!),
            motif: _motifController.text.trim(),
            objectifs: _objectifsController.text.trim(),
          );
      ref.invalidate(demandesAdminListProvider);
      if (mounted) {
        _snack('Demande envoyée ✓');
        context.pop();
      }
    } catch (e) {
      if (mounted) _snack(messageErreurDemandeAdmin(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _snack(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  @override
  Widget build(BuildContext context) {
    final fmtDate = DateFormat.yMMMd('fr_FR');
    final nbJours = (_dateDebut != null && _dateFin != null && !_dateFin!.isBefore(_dateDebut!))
        ? _dateFin!.difference(_dateDebut!).inDays + 1
        : null;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Ordre de mission', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          _label('Lieu de la mission'),
          const SizedBox(height: 8),
          _champTexte(_lieuController, 'Ville, site, organisme…', Icons.place_outlined, maxLines: 1),

          const SizedBox(height: 24),
          _label('Période'),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: _DateTile(
              label: 'Début',
              value: _dateDebut == null ? 'Sélectionner' : fmtDate.format(_dateDebut!),
              selected: _dateDebut != null,
              color: _violet,
              onTap: () => _pickDate(true),
            )),
            const SizedBox(width: 12),
            Expanded(child: _DateTile(
              label: 'Fin',
              value: _dateFin == null ? 'Sélectionner' : fmtDate.format(_dateFin!),
              selected: _dateFin != null,
              color: _violet,
              onTap: () => _pickDate(false),
            )),
          ]),
          if (nbJours != null) ...[
            const SizedBox(height: 8),
            Text(
              'Durée : $nbJours jour${nbJours > 1 ? 's' : ''}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.textSecondary),
            ),
          ],

          const SizedBox(height: 24),
          _label('Motif'),
          const SizedBox(height: 8),
          _champTexte(_motifController, 'Objet de la mission…', Icons.notes_rounded),

          const SizedBox(height: 24),
          _label('Objectifs (optionnel)'),
          const SizedBox(height: 8),
          _champTexte(_objectifsController, 'Résultats attendus…', Icons.flag_outlined),

          const SizedBox(height: 40),
          SizedBox(
            height: 56,
            child: FilledButton(
              onPressed: _loading ? null : _submit,
              style: FilledButton.styleFrom(
                backgroundColor: _violet,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                elevation: 0,
              ),
              child: _loading
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 3)
                  : const Text('Soumettre la demande',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }

  Widget _label(String text) => Text(text,
      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary));

  Widget _champTexte(TextEditingController c, String hint, IconData icon, {int maxLines = 3}) {
    return TextField(
      controller: c,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: hint,
        prefixIcon: Icon(icon, color: _violet),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16)),
        filled: true,
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.all(16),
      ),
    );
  }
}

class _DateTile extends StatelessWidget {
  final String label;
  final String value;
  final bool selected;
  final Color color;
  final VoidCallback onTap;

  const _DateTile({
    required this.label, required this.value, required this.selected,
    required this.color, required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: selected ? color.withValues(alpha: 0.3) : AppTheme.border, width: 1.5),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.calendar_today_outlined, color: selected ? color : AppTheme.textTertiary, size: 18),
          const SizedBox(height: 10),
          Text(label, style: const TextStyle(color: AppTheme.textTertiary, fontSize: 11, fontWeight: FontWeight.w500)),
          const SizedBox(height: 2),
          Text(value, style: TextStyle(
            fontSize: 15, fontWeight: FontWeight.bold,
            color: selected ? AppTheme.textPrimary : AppTheme.textTertiary,
          )),
        ]),
      ),
    );
  }
}
