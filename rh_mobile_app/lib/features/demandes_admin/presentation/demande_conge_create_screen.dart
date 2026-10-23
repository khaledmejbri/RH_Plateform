import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';

import '../data/demande_admin_errors.dart';
import '../data/demande_admin_repository.dart';
import '../data/type_conge.dart';
import 'demandes_admin_list_screen.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';

class DemandeCongeCreateScreen extends ConsumerStatefulWidget {
  const DemandeCongeCreateScreen({super.key});

  @override
  ConsumerState<DemandeCongeCreateScreen> createState() => _DemandeCongeCreateScreenState();
}

class _DemandeCongeCreateScreenState extends ConsumerState<DemandeCongeCreateScreen> {
  DateTime? _debut;
  DateTime? _fin;
  String _typeConge = 'ANNUEL';
  File? _pieceJointe;
  var _loading = false;
  final ImagePicker _picker = ImagePicker();

  bool get _pjRequise => typeCongeExigePieceJointe(_typeConge);

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
    if (d != null) setState(() => _debut = d);
  }

  Future<void> _pickFin() async {
    final d = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2040),
      initialDate: _fin ?? _debut ?? DateTime.now(),
    );
    if (d != null) setState(() => _fin = d);
  }

  void _onTypeChanged(String? value) {
    if (value == null) return;
    setState(() {
      _typeConge = value;
      if (!typeCongeExigePieceJointe(value)) {
        _pieceJointe = null;
      }
    });
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

  Future<void> _submit() async {
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
    if (_pjRequise && _pieceJointe == null) {
      final motif = _typeConge == 'MATERNITE'
          ? 'un congé maternité'
          : 'un congé maladie';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Un justificatif (certificat) est obligatoire pour $motif. Veuillez joindre le document avant d’envoyer.',
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
            typeConge: _typeConge,
            certificatPath: _pieceJointe?.path,
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
    const primaryBlue = AppTheme.primary;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Nouvelle Demande de Congé', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: ListView(
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
              value: _typeConge,
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
              items: [
                for (final t in kTypesConge)
                  DropdownMenuItem(value: t.code, child: Text(t.label)),
              ],
              onChanged: _onTypeChanged,
            ),
          ),
          if (_pjRequise) ...[
            const SizedBox(height: 24),
            Text(
              _typeConge == 'MATERNITE' ? 'Justificatif (obligatoire)' : 'Certificat médical (obligatoire)',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
            ),
            const SizedBox(height: 4),
            Text(
              _typeConge == 'MATERNITE'
                  ? 'Joignez le certificat ou justificatif de maternité.'
                  : 'Joignez le certificat médical pour ce congé maladie.',
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
              onPressed: _loading ? null : _submit,
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
    final fmt = DateFormat.yMMMd('fr_FR');
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
                  date == null ? 'Sélectionner' : fmt.format(date!),
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
