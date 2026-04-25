import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';

import '../data/demande_admin_repository.dart';
import 'demandes_admin_list_screen.dart';

class DemandeCongeCreateScreen extends ConsumerStatefulWidget {
  const DemandeCongeCreateScreen({super.key});

  @override
  ConsumerState<DemandeCongeCreateScreen> createState() => _DemandeCongeCreateScreenState();
}

class _DemandeCongeCreateScreenState extends ConsumerState<DemandeCongeCreateScreen> {
  DateTime? _debut;
  DateTime? _fin;
  String _typeConge = 'ANNUEL';
  File? _certificatMedical;
  var _loading = false;
  final ImagePicker _picker = ImagePicker();

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

  Future<void> _pickCertificat() async {
    final source = await showDialog<ImageSource>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Sélectionner un certificat'),
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
        setState(() => _certificatMedical = File(image.path));
      }
    }
  }

  Future<void> _submit() async {
    if (_debut == null || _fin == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Choisissez les dates')));
      return;
    }
    if (_fin!.isBefore(_debut!)) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Date fin invalide')));
      return;
    }
    if (_typeConge == 'MALADIE' && _certificatMedical == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Certificat médical requis pour un congé maladie')),
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
            certificatPath: _certificatMedical?.path,
          );
      ref.invalidate(demandesAdminListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Demande envoyée')));
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
    final fmt = DateFormat.yMMMd('fr_FR');
    const primaryBlue = Color(0xFF2563EB);

    return Scaffold(
      backgroundColor: const Color(0xFFF0F4FF),
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
            "Détails du congé",
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: Color(0xFF1E293B)),
          ),
          const SizedBox(height: 20),
          
          _DateTile(
            label: "Date de début",
            date: _debut,
            onTap: _pickDebut,
            icon: Icons.calendar_today_outlined,
            color: primaryBlue,
          ),
          
          const SizedBox(height: 16),
          
          _DateTile(
            label: "Date de fin",
            date: _fin,
            onTap: _pickFin,
            icon: Icons.calendar_month_outlined,
            color: primaryBlue,
          ),
          
          const SizedBox(height: 24),
          
          const Text(
            "Type de congé",
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
          ),
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.grey.shade200),
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
              items: const [
                DropdownMenuItem(value: 'ANNUEL', child: Text('Congé Annuel')),
                DropdownMenuItem(value: 'MALADIE', child: Text('Congé Maladie')),
              ],
              onChanged: (value) {
                if (value != null) {
                  setState(() => _typeConge = value);
                }
              },
            ),
          ),
          
          if (_typeConge == 'MALADIE') ...[
            const SizedBox(height: 24),
            const Text(
              "Certificat médical",
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF64748B)),
            ),
            const SizedBox(height: 8),
            InkWell(
              onTap: _pickCertificat,
              borderRadius: BorderRadius.circular(16),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: _certificatMedical != null ? Colors.green : Colors.grey.shade200,
                    width: 2,
                  ),
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: (_certificatMedical != null ? Colors.green : primaryBlue).withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        _certificatMedical != null ? Icons.check_circle : Icons.upload_file,
                        color: _certificatMedical != null ? Colors.green : primaryBlue,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _certificatMedical != null ? 'Certificat ajouté' : 'Ajouter un certificat',
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: _certificatMedical != null ? Colors.green : const Color(0xFF1E293B),
                            ),
                          ),
                          if (_certificatMedical != null)
                            Text(
                              _certificatMedical!.path.split('/').last,
                              style: const TextStyle(fontSize: 12, color: Colors.grey),
                            )
                          else
                            const Text(
                              'PDF ou image',
                              style: TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded, color: Colors.grey.shade300),
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
          border: Border.all(color: date != null ? color.withOpacity(0.2) : Colors.grey.shade200, width: 1.5),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.02),
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
                color: (date != null ? color : Colors.grey).withOpacity(0.1),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Icon(icon, color: date != null ? color : Colors.grey, size: 24),
            ),
            const SizedBox(width: 16),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(color: Colors.grey.shade500, fontSize: 13, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 4),
                Text(
                  date == null ? 'Sélectionner' : fmt.format(date!),
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: date == null ? Colors.grey.shade400 : const Color(0xFF1E293B),
                  ),
                ),
              ],
            ),
            const Spacer(),
            Icon(Icons.chevron_right_rounded, color: Colors.grey.shade300),
          ],
        ),
      ),
    );
  }
}
