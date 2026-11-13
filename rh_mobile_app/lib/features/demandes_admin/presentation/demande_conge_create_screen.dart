import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../core/format/formats.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';

import '../../conges/data/solde_models.dart';
import '../../conges/data/solde_repository.dart';
import '../data/demande_admin_errors.dart';
import '../data/demande_admin_repository.dart';
import 'demandes_admin_list_screen.dart';
import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/core/libelles/libelles_service.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

class DemandeCongeCreateScreen extends ConsumerStatefulWidget {
  const DemandeCongeCreateScreen({super.key});

  @override
  ConsumerState<DemandeCongeCreateScreen> createState() => _DemandeCongeCreateScreenState();
}

class _DemandeCongeCreateScreenState extends ConsumerState<DemandeCongeCreateScreen> {
  DateTime? _debut;
  DateTime? _fin;
  /// Code du type choisi parmi les types actifs servis par l'API ; null tant que rien n'est choisi.
  String? _typeConge;
  File? _pieceJointe;
  var _loading = false;

  /// Code système du congé annuel : seul type soumis au solde (D-S03).
  static const String _codeAnnuel = 'ANNUEL';
  String _periodePremier = PeriodeJour.journee;
  String _periodeDernier = PeriodeJour.journee;
  SimulationConge? _simulation;
  Object? _simulationErreur;
  var _simulationEnCours = false;
  var _simulationSeq = 0;

  bool get _estAnnuel => _typeConge == _codeAnnuel;
  bool get _unSeulJour =>
      _debut != null && _fin != null && _debut!.year == _fin!.year && _debut!.month == _fin!.month && _debut!.day == _fin!.day;
  bool get _periodeValide => _debut != null && _fin != null && !_fin!.isBefore(_debut!);

  /// Recale les périodes sur les choix permis par les dates (un jour : trois choix ; plusieurs : deux par bout).
  void _recalerPeriodes() {
    if (_unSeulJour) {
      _periodeDernier = _periodePremier;
    } else {
      if (!PeriodeJour.premierJour(unSeulJour: false).contains(_periodePremier)) _periodePremier = PeriodeJour.journee;
      if (!PeriodeJour.dernierJour.contains(_periodeDernier)) _periodeDernier = PeriodeJour.journee;
    }
  }

  /// Simulation serveur (A3) à chaque changement ; une réponse périmée est ignorée.
  Future<void> _simuler() async {
    final seq = ++_simulationSeq;
    if (!_estAnnuel || !_periodeValide) {
      setState(() {
        _simulation = null;
        _simulationErreur = null;
        _simulationEnCours = false;
      });
      return;
    }
    setState(() {
      _simulationEnCours = true;
      _simulationErreur = null;
      _simulation = null;
    });
    try {
      final r = await ref.read(soldeRepositoryProvider).simuler(
            dateDebut: _debut!,
            dateFin: _fin!,
            periodePremierJour: _periodePremier,
            periodeDernierJour: _unSeulJour ? _periodePremier : _periodeDernier,
          );
      if (!mounted || seq != _simulationSeq) return;
      setState(() {
        _simulation = r;
        _simulationEnCours = false;
      });
    } catch (e) {
      if (!mounted || seq != _simulationSeq) return;
      setState(() {
        _simulationErreur = e;
        _simulationEnCours = false;
      });
    }
  }
  final ImagePicker _picker = ImagePicker();

  /// Pièce jointe exigée selon `piece_jointe_obligatoire` du type choisi (le serveur reste le garde).
  bool _pjRequiseDe(List<ParametreValeur> types) {
    final code = _typeConge;
    if (code == null) return false;
    for (final t in types) {
      if (t.code == code) return t.pieceJointeObligatoire;
    }
    return false;
  }

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _pickDebut() async {
    final d = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2040),
      initialDate: _debut ?? DateTime.now(),
    );
    if (d != null) {
      setState(() {
        _debut = d;
        _recalerPeriodes();
      });
      _simuler();
    }
  }

  Future<void> _pickFin() async {
    final d = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2040),
      initialDate: _fin ?? _debut ?? DateTime.now(),
    );
    if (d != null) {
      setState(() {
        _fin = d;
        _recalerPeriodes();
      });
      _simuler();
    }
  }

  void _onTypeChanged(String? value, List<ParametreValeur> types) {
    if (value == null) return;
    setState(() {
      _typeConge = value;
      final exige = types.any((t) => t.code == value && t.pieceJointeObligatoire);
      if (!exige) _pieceJointe = null;
    });
    _simuler();
  }

  Future<void> _pickPieceJointe() async {
    final source = await showDialog<ImageSource>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Joindre un justificatif'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: const Text('Prendre une photo'),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: const Text('Choisir depuis la galerie'),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );

    if (source != null) {
      final XFile? image = await _picker.pickImage(source: source);
      if (image != null) {
        setState(() => _pieceJointe = File(image.path));
      }
    }
  }

  Future<void> _submit(List<ParametreValeur> types) async {
    final type = _typeConge;
    if (type == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Veuillez choisir le type de congé.')),
      );
      return;
    }
    if (_debut == null || _fin == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Veuillez choisir la date de début et la date de fin.')),
      );
      return;
    }
    if (_fin!.isBefore(_debut!)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('La date de fin ne peut pas être antérieure à la date de début.')),
      );
      return;
    }
    if (_pjRequiseDe(types) && _pieceJointe == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Un justificatif est obligatoire pour « ${Libelles.typeConge(type)} ». Veuillez joindre le document avant d’envoyer.',
          ),
        ),
      );
      return;
    }
    final fmt = DateFormat('yyyy-MM-dd');
    setState(() => _loading = true);
    try {
      await ref.read(demandeAdminRepositoryProvider).createConge(
            dateDebut: fmt.format(_debut!),
            dateFin: fmt.format(_fin!),
            typeConge: type,
            certificatPath: _pieceJointe?.path,
            periodePremierJour: _estAnnuel ? _periodePremier : null,
            periodeDernierJour: _estAnnuel ? (_unSeulJour ? _periodePremier : _periodeDernier) : null,
          );
      ref.invalidate(demandesAdminListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Demande de congé envoyée.')),
        );
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              messageErreurDemandeAdmin(
                e,
                fallback: 'Impossible d’envoyer la demande de congé.',
              ),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final typesAsync = ref.watch(typesCongeActifsProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Nouvelle Demande de Congé', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: typesAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(
          e,
          onRetry: () => ref.invalidate(typesCongeActifsProvider),
        ),
        data: (types) => types.isEmpty
            ? const RhEmptyState(
                icon: Icons.event_busy_outlined,
                title: 'Aucun type de congé disponible',
                message: 'Contactez la RH : aucun type de congé n’est ouvert à la demande.',
              )
            : _formulaire(types),
      ),
    );
  }

  Widget _menuPeriode({
    required String label,
    required String valeur,
    required List<String> choix,
    required ValueChanged<String> onChanged,
  }) {
    return DropdownButtonFormField<String>(
      key: ValueKey('periode-$label'),
      value: choix.contains(valeur) ? valeur : null,
      decoration: InputDecoration(labelText: label, filled: true, fillColor: Colors.white),
      items: [for (final c in choix) DropdownMenuItem(value: c, child: Text(PeriodeJour.libelle(c)))],
      onChanged: (v) {
        if (v != null) onChanged(v);
      },
    );
  }

  Widget _blocPeriodes() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_unSeulJour)
          _menuPeriode(
            label: 'Période',
            valeur: _periodePremier,
            choix: PeriodeJour.premierJour(unSeulJour: true),
            onChanged: (v) {
              setState(() {
                _periodePremier = v;
                _periodeDernier = v;
              });
              _simuler();
            },
          )
        else ...[
          _menuPeriode(
            label: 'Premier jour',
            valeur: _periodePremier,
            choix: PeriodeJour.premierJour(unSeulJour: false),
            onChanged: (v) {
              setState(() => _periodePremier = v);
              _simuler();
            },
          ),
          const SizedBox(height: 12),
          _menuPeriode(
            label: 'Dernier jour',
            valeur: _periodeDernier,
            choix: PeriodeJour.dernierJour,
            onChanged: (v) {
              setState(() => _periodeDernier = v);
              _simuler();
            },
          ),
        ],
        const SizedBox(height: 12),
        _ResumeSimulation(
          simulation: _simulation,
          erreur: _simulationErreur,
          enCours: _simulationEnCours,
          onRetry: _simuler,
        ),
      ],
    );
  }

  Widget _formulaire(List<ParametreValeur> types) {
    const primaryBlue = AppTheme.primary;
    final pjRequise = _pjRequiseDe(types);
    // Congé annuel : l'envoi attend une simulation sans blocage (le serveur reste le garde).
    final simulationOk = !_estAnnuel || (_simulation != null && !_simulation!.bloquee);
    final peutEnvoyer =
        !_loading && types.any((t) => t.code == _typeConge) && (!pjRequise || _pieceJointe != null) && simulationOk;
    return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Détails du congé',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppTheme.textPrimary),
          ),
          const SizedBox(height: 20),
          _DateTile(
            label: 'Date de début',
            date: _debut,
            onTap: _pickDebut,
            icon: Icons.calendar_today_outlined,
            color: primaryBlue,
          ),
          const SizedBox(height: 16),
          _DateTile(
            label: 'Date de fin',
            date: _fin,
            onTap: _pickFin,
            icon: Icons.calendar_month_outlined,
            color: primaryBlue,
          ),
          const SizedBox(height: 24),
          const Text(
            'Type de congé',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
          ),
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppTheme.border),
            ),
            child: DropdownButtonFormField<String>(
              value: types.any((t) => t.code == _typeConge) ? _typeConge : null,
              decoration: InputDecoration(
                filled: true,
                fillColor: Colors.white,
                prefixIcon: const Icon(Icons.category_outlined, color: primaryBlue),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(16),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
              ),
              hint: const Text('Choisir un type de congé'),
              items: [
                for (final t in types) DropdownMenuItem(value: t.code, child: Text(t.libelle)),
              ],
              onChanged: (v) => _onTypeChanged(v, types),
            ),
          ),
          if (_estAnnuel && _periodeValide) ...[
            const SizedBox(height: 24),
            _blocPeriodes(),
          ],
          if (pjRequise) ...[
            const SizedBox(height: 24),
            Text(
              'Justificatif (obligatoire)',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
            ),
            const SizedBox(height: 4),
            Text(
              'Joignez le justificatif demandé pour « ${Libelles.typeConge(_typeConge)} ».',
              style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
            ),
            const SizedBox(height: 8),
            InkWell(
              onTap: _pickPieceJointe,
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _pieceJointe != null ? AppTheme.success : AppTheme.border,
                    width: 2,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: (_pieceJointe != null ? AppTheme.success : primaryBlue).withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        _pieceJointe != null ? Icons.check_circle : Icons.upload_file,
                        color: _pieceJointe != null ? AppTheme.success : primaryBlue,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _pieceJointe != null ? 'Document joint' : 'Ajouter un justificatif',
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: _pieceJointe != null ? AppTheme.success : AppTheme.textPrimary,
                            ),
                          ),
                          if (_pieceJointe != null)
                            Text(
                              _pieceJointe!.path.split(RegExp(r'[/\\]')).last,
                              style: const TextStyle(fontSize: 12, color: AppTheme.textTertiary),
                            )
                          else
                            const Text(
                              'Photo ou image du certificat',
                              style: TextStyle(fontSize: 12, color: AppTheme.textTertiary),
                            ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded, color: AppTheme.border),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 40),
          SizedBox(
            height: 56,
            child: FilledButton(
              onPressed: peutEnvoyer ? () => _submit(types) : null,
              style: FilledButton.styleFrom(
                backgroundColor: primaryBlue,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                elevation: 0,
              ),
              child: _loading
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 3)
                  : const Text(
                      'Soumettre la demande',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
            ),
          ),
        ],
    );
  }
}

/// Résumé d'une simulation : jours décomptés, solde après, ou message bloquant / erreur avec « Réessayer ».
class _ResumeSimulation extends StatelessWidget {
  const _ResumeSimulation({
    required this.simulation,
    required this.erreur,
    required this.enCours,
    required this.onRetry,
  });

  final SimulationConge? simulation;
  final Object? erreur;
  final bool enCours;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (enCours) {
      return const Row(children: [
        SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
        SizedBox(width: 10),
        Text('Calcul des jours en cours…'),
      ]);
    }
    if (erreur != null) {
      return Row(children: [
        Expanded(child: Text(rhErrorMessage(erreur), style: const TextStyle(color: AppTheme.error))),
        TextButton(onPressed: onRetry, child: const Text('Réessayer')),
      ]);
    }
    final s = simulation;
    if (s == null) return const SizedBox.shrink();
    if (s.bloquee) {
      return Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: AppTheme.errorSurface, borderRadius: BorderRadius.circular(12)),
        child: Row(children: [
          const Icon(Icons.block_rounded, color: AppTheme.error, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(s.blocage!.libelle, style: const TextStyle(color: AppTheme.error, fontWeight: FontWeight.w600)),
          ),
        ]),
      );
    }
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: AppTheme.primarySurface, borderRadius: BorderRadius.circular(12)),
      child: Text(
        '${s.joursOuvres}, solde après : ${formatJours(s.disponibleApres)}',
        key: const Key('resume-simulation'),
        style: const TextStyle(fontWeight: FontWeight.w700, color: AppTheme.primaryDark),
      ),
    );
  }
}

class _DateTile extends StatelessWidget {
  final String label;
  final DateTime? date;
  final VoidCallback onTap;
  final IconData icon;
  final Color color;

  const _DateTile({
    required this.label,
    required this.date,
    required this.onTap,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: date != null ? color.withValues(alpha: 0.2) : AppTheme.border, width: 1.5),
          boxShadow: [
            BoxShadow(
              color: AppTheme.textPrimary.withValues(alpha: 0.02),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: (date != null ? color : AppTheme.textTertiary).withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: date != null ? color : AppTheme.textTertiary, size: 24),
            ),
            const SizedBox(width: 16),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(color: AppTheme.textTertiary, fontSize: 13, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 4),
                Text(
                  date == null ? 'Sélectionner' : formatDate(date!),
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: date == null ? AppTheme.textTertiary : AppTheme.textPrimary,
                  ),
                ),
              ],
            ),
            const Spacer(),
            Icon(Icons.chevron_right_rounded, color: AppTheme.border),
          ],
        ),
      ),
    );
  }
}
