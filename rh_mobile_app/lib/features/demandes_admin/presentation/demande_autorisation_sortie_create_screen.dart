import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/demande_admin_repository.dart';
import 'demandes_admin_list_screen.dart';

class DemandeAutorisationSortieCreateScreen extends ConsumerStatefulWidget {
  const DemandeAutorisationSortieCreateScreen({super.key});

  @override
  ConsumerState<DemandeAutorisationSortieCreateScreen> createState() =>
      _DemandeAutorisationSortieCreateScreenState();
}

class _DemandeAutorisationSortieCreateScreenState
    extends ConsumerState<DemandeAutorisationSortieCreateScreen> {
  static const Color _blue = Color(0xFF2563EB);

  DateTime? _dateJour;
  TimeOfDay? _heureDebut;
  TimeOfDay? _heureFin;
  final _motifController = TextEditingController();
  var _loading = false;

  @override
  void dispose() {
    _motifController.dispose();
    super.dispose();
  }

  int _dureeMinutes() {
    if (_heureDebut == null || _heureFin == null) return -1;
    final debut = _heureDebut!.hour * 60 + _heureDebut!.minute;
    final fin = _heureFin!.hour * 60 + _heureFin!.minute;
    return fin - debut;
  }

  String _formatDuree(int minutes) {
    if (minutes <= 0) return '';
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '${m}min';
    if (m == 0) return '${h}h';
    return '${h}h${m.toString().padLeft(2, '0')}';
  }

  Future<void> _pickDate() async {
    final d = await showDatePicker(
      context: context,
      firstDate: DateTime.now(),
      lastDate: DateTime(2040),
      initialDate: _dateJour ?? DateTime.now(),
    );
    if (d != null) setState(() => _dateJour = d);
  }

  Future<void> _pickHeure(bool isDebut) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: isDebut
          ? (_heureDebut ?? const TimeOfDay(hour: 8, minute: 0))
          : (_heureFin ?? const TimeOfDay(hour: 10, minute: 0)),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
        child: child!,
      ),
    );
    if (picked != null) {
      setState(() {
        if (isDebut) _heureDebut = picked;
        else _heureFin = picked;
      });
    }
  }

  String _formatTime(TimeOfDay? t) {
    if (t == null) return 'Sélectionner';
    return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
  }

  String _toTimeString(TimeOfDay t) =>
      '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

  Future<void> _submit() async {
    if (_dateJour == null) { _snack('Veuillez choisir la date'); return; }
    if (_heureDebut == null || _heureFin == null) { _snack('Veuillez choisir les heures'); return; }
    final duree = _dureeMinutes();
    if (duree <= 0) { _snack("L'heure de fin doit être après l'heure de début"); return; }
    if (duree > 4 * 60) { _snack('La durée ne peut pas dépasser 4 heures (moitié de journée)'); return; }
    if (_motifController.text.trim().isEmpty) { _snack('Veuillez indiquer un motif'); return; }

    final fmt = DateFormat('yyyy-MM-dd');
    setState(() => _loading = true);
    try {
      await ref.read(demandeAdminRepositoryProvider).createAutorisationSortie(
            dateJour: fmt.format(_dateJour!),
            heureDebut: _toTimeString(_heureDebut!),
            heureFin: _toTimeString(_heureFin!),
            motif: _motifController.text.trim(),
          );
      ref.invalidate(demandesAdminListProvider);
      if (mounted) {
        _snack('Demande envoyée ✓');
        context.pop();
      }
    } catch (e) {
      if (mounted) _snack('$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _snack(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  @override
  Widget build(BuildContext context) {
    final fmtDate = DateFormat.yMMMd('fr_FR');
    final duree = _dureeMinutes();
    final dureeValide = duree > 0 && duree <= 4 * 60;
    final dureeDepasse = duree > 4 * 60;

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: const Text('Autorisation de sortie',
            style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          // Info banner
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: _blue.withOpacity(0.06),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: _blue.withOpacity(0.2)),
            ),
            child: Row(
              children: [
                Icon(Icons.info_outline_rounded, color: _blue, size: 20),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    'Autorisation de sortie courte uniquement — maximum 4h (moins de la moitié de la journée)',
                    style: TextStyle(fontSize: 13, color: Color(0xFF1D4ED8)),
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 28),
          _label('Date de sortie'),
          const SizedBox(height: 8),
          _Tile(
            icon: Icons.calendar_today_outlined,
            color: _blue,
            label: 'Jour',
            value: _dateJour == null ? 'Sélectionner' : fmtDate.format(_dateJour!),
            selected: _dateJour != null,
            onTap: _pickDate,
          ),

          const SizedBox(height: 24),
          _label('Horaires'),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(child: _Tile(
              icon: Icons.login_rounded, color: _blue,
              label: 'Départ', value: _formatTime(_heureDebut),
              selected: _heureDebut != null, onTap: () => _pickHeure(true),
            )),
            const SizedBox(width: 12),
            Expanded(child: _Tile(
              icon: Icons.logout_rounded, color: _blue,
              label: 'Retour', value: _formatTime(_heureFin),
              selected: _heureFin != null, onTap: () => _pickHeure(false),
            )),
          ]),

          if (_heureDebut != null && _heureFin != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: dureeDepasse
                    ? Colors.red.withOpacity(0.08)
                    : dureeValide ? Colors.green.withOpacity(0.08) : Colors.orange.withOpacity(0.08),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: dureeDepasse
                      ? Colors.red.withOpacity(0.3)
                      : dureeValide ? Colors.green.withOpacity(0.3) : Colors.orange.withOpacity(0.3),
                ),
              ),
              child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                Icon(
                  dureeDepasse ? Icons.warning_amber_rounded : Icons.timer_outlined,
                  size: 16,
                  color: dureeDepasse ? Colors.red : dureeValide ? Colors.green : Colors.orange,
                ),
                const SizedBox(width: 8),
                Text(
                  duree <= 0
                      ? 'Heure de retour invalide'
                      : dureeDepasse
                          ? 'Durée dépassée : ${_formatDuree(duree)} (max 4h)'
                          : 'Durée : ${_formatDuree(duree)} / 4h max',
                  style: TextStyle(
                    fontSize: 13, fontWeight: FontWeight.w600,
                    color: dureeDepasse ? Colors.red : dureeValide ? Colors.green.shade700 : Colors.orange,
                  ),
                ),
              ]),
            ),
          ],

          const SizedBox(height: 24),
          _label('Motif'),
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.grey.shade200),
            ),
            child: TextField(
              controller: _motifController,
              maxLines: 3,
              decoration: InputDecoration(
                hintText: 'Raison de la sortie...',
                hintStyle: TextStyle(color: Colors.grey.shade400, fontSize: 14),
                prefixIcon: Padding(
                  padding: const EdgeInsets.only(bottom: 44),
                  child: Icon(Icons.notes_rounded, color: _blue),
                ),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(16),
                  borderSide: BorderSide.none,
                ),
                filled: true, fillColor: Colors.white,
                contentPadding: const EdgeInsets.all(16),
              ),
            ),
          ),

          const SizedBox(height: 40),
          SizedBox(
            height: 56,
            child: FilledButton(
              onPressed: _loading ? null : _submit,
              style: FilledButton.styleFrom(
                backgroundColor: _blue,
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
      style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)));
}

class _Tile extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String label;
  final String value;
  final bool selected;
  final VoidCallback onTap;

  const _Tile({
    required this.icon, required this.color, required this.label,
    required this.value, required this.selected, required this.onTap,
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
          border: Border.all(
            color: selected ? color.withOpacity(0.3) : Colors.grey.shade200, width: 1.5,
          ),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: (selected ? color : Colors.grey).withOpacity(0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: selected ? color : Colors.grey, size: 18),
            ),
            const Spacer(),
            Icon(Icons.chevron_right_rounded, color: Colors.grey.shade300, size: 18),
          ]),
          const SizedBox(height: 10),
          Text(label, style: TextStyle(color: Colors.grey.shade500, fontSize: 11, fontWeight: FontWeight.w500)),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(
              fontSize: 15, fontWeight: FontWeight.bold,
              color: selected ? const Color(0xFF1E293B) : Colors.grey.shade400,
            ),
          ),
        ]),
      ),
    );
  }
}
