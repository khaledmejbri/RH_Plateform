import '../../../core/libelles/libelles.dart';
import '../../../core/libelles/libelles_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/document_repository.dart';
import 'documents_list_screen.dart';
import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

class DocumentCreateScreen extends ConsumerStatefulWidget {
  const DocumentCreateScreen({super.key});

  @override
  ConsumerState<DocumentCreateScreen> createState() => _DocumentCreateScreenState();
}

class _DocumentCreateScreenState extends ConsumerState<DocumentCreateScreen> {
  /// Code du type choisi parmi les types actifs servis par l'API ; null tant que rien n'est choisi.
  String? _type;
  final _comment = TextEditingController();

  // Bulletin de paie fields
  String _bulletinType = 'DERNIERS'; // DERNIERS or SPECIFIQUE
  int _nombreMois = 3;
  String _moisAnnee = DateFormat('yyyy-MM').format(DateTime.now());
  final List<String> _moisSelectionnes = [];

  var _loading = false;
  bool _canSubmit = false;
  bool _verifEnCours = false;
  bool _verifEnErreur = false;
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
              const Text('Mois', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.textTertiary)),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                value: selectedMonth,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: AppTheme.background,
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
              const Text('Année', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.textTertiary)),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                value: selectedYear,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: AppTheme.background,
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
            child: const Text('Annuler', style: TextStyle(color: AppTheme.textTertiary)),
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
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  Future<void> _checkRestrictions() async {
    final type = _type;
    if (type == null) return;
    setState(() {
      _verifEnCours = true;
      _verifEnErreur = false;
      _canSubmit = false;
      _restrictionMessage = null;
    });
    try {
      final r = await ref.read(documentRepositoryProvider).verifierRestriction(type);
      if (!mounted || type != _type) return;
      setState(() {
        _canSubmit = r.canRequest;
        _restrictionMessage = r.canRequest ? null : (r.message ?? 'Vous avez déjà une demande en cours pour ce document.');
        _verifEnCours = false;
      });
    } catch (_) {
      if (!mounted || type != _type) return;
      setState(() {
        _canSubmit = false;
        _verifEnErreur = true;
        _verifEnCours = false;
      });
    }
  }

  Future<void> _submit() async {
    final type = _type;
    if (type == null || !_canSubmit) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(_verifEnErreur
              ? 'Vérification impossible, réessayez.'
              : (_restrictionMessage ?? 'Vous ne pouvez pas faire cette demande maintenant')),
        ),
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
            typeDocument: type,
            commentaire: c.isEmpty ? null : c,
            contenu: contenu,
          );
      ref.invalidate(documentsListProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Demande enregistrée')));
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(rhErrorMessage(e))));
        // 409 DEMANDE_DOCUMENT_DEJA_EN_COURS : le message serveur est affiché tel quel, on resynchronise le pré-contrôle.
        if (e is DioException && e.response?.statusCode == 409) _checkRestrictions();
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final typesAsync = ref.watch(typesDocumentActifsProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Demande de document', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      body: typesAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => RhErrorState.depuis(
          e,
          onRetry: () => ref.invalidate(typesDocumentActifsProvider),
        ),
        data: (types) => types.isEmpty
            ? Column(
                children: [
                  const Expanded(
                    child: RhEmptyState(
                      icon: Icons.folder_off_outlined,
                      title: 'Aucun type de document disponible',
                      message: 'Contactez la RH : aucun document n’est ouvert à la demande.',
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.all(20),
                    child: SizedBox(
                      width: double.infinity,
                      height: 50,
                      child: FilledButton(
                        onPressed: null,
                        child: Text('Envoyer la demande'),
                      ),
                    ),
                  ),
                ],
              )
            : _formulaire(types),
      ),
    );
  }

  /// Formulaire défilant + barre d'envoi fixe en bas : le bouton reste visible quelle que soit
  /// la longueur du formulaire (bulletin de paie, clavier ouvert).
  Widget _formulaire(List<ParametreValeur> types) {
    return Column(
      children: [
        Expanded(child: _champs(types)),
        _barreEnvoi(),
      ],
    );
  }

  /// Raison affichée sous le bouton tant que l'envoi est impossible et que le formulaire ne
  /// l'explique pas déjà (l'erreur de vérification et la restriction ont leur encadré).
  String? get _raisonEnvoiImpossible {
    if (_type == null) return 'Choisissez d’abord un type de document.';
    if (_verifEnCours) return 'Vérification en cours…';
    return null;
  }

  Widget _barreEnvoi() {
    final actif = !_loading && _type != null && _canSubmit;
    final raison = _loading ? null : _raisonEnvoiImpossible;
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: AppTheme.textPrimary.withValues(alpha: 0.08),
            blurRadius: 10,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (raison != null) ...[
              Text(
                raison,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: AppTheme.textTertiary),
              ),
              const SizedBox(height: 8),
            ],
            SizedBox(
              height: 50,
              child: FilledButton(
                onPressed: actif ? _submit : null,
                style: FilledButton.styleFrom(
                  backgroundColor: AppTheme.primary,
                  foregroundColor: Colors.white,
                  disabledBackgroundColor: AppTheme.border,
                  disabledForegroundColor: AppTheme.textTertiary,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  elevation: 0,
                ),
                child: _loading
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 3),
                      )
                    : const Text(
                        'Envoyer la demande',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _champs(List<ParametreValeur> types) {
    const primaryBlue = AppTheme.primary;
    return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            "Quel document souhaitez-vous ?",
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: AppTheme.textPrimary),
          ),
          const SizedBox(height: 20),
          DropdownButtonFormField<String>(
            value: types.any((t) => t.code == _type) ? _type : null,
            isExpanded: true,
            hint: const Text('Choisir un document', overflow: TextOverflow.ellipsis),
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              labelText: 'Type de document',
              prefixIcon: const Icon(Icons.description_outlined, color: primaryBlue),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: AppTheme.border),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: AppTheme.border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: primaryBlue, width: 2),
              ),
            ),
            items: [
              for (final t in types) DropdownMenuItem(value: t.code, child: Text(t.libelle, overflow: TextOverflow.ellipsis)),
            ],
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
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
            ),
            const SizedBox(height: 12),
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppTheme.border),
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
                          color: _bulletinType == 'DERNIERS' ? primaryBlue.withValues(alpha: 0.1) : Colors.transparent,
                          borderRadius: const BorderRadius.horizontal(left: Radius.circular(16)),
                        ),
                        child: Center(
                          child: Text(
                            'Derniers',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: _bulletinType == 'DERNIERS' ? primaryBlue : AppTheme.textSecondary,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  Container(width: 1, color: AppTheme.border, height: 48),
                  Expanded(
                    child: InkWell(
                      onTap: () => setState(() => _bulletinType = 'SPECIFIQUE'),
                      borderRadius: const BorderRadius.horizontal(right: Radius.circular(16)),
                      child: Container(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        decoration: BoxDecoration(
                          color: _bulletinType == 'SPECIFIQUE' ? primaryBlue.withValues(alpha: 0.1) : Colors.transparent,
                          borderRadius: const BorderRadius.horizontal(right: Radius.circular(16)),
                        ),
                        child: Center(
                          child: Text(
                            'Spécifique',
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: _bulletinType == 'SPECIFIQUE' ? primaryBlue : AppTheme.textSecondary,
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
                border: Border.all(color: AppTheme.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _bulletinType == 'DERNIERS'
                        ? "Nombre de derniers mois"
                        : "Nombre de mois à demander",
                    style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      IconButton.filledTonal(
                        tooltip: 'Réduire le nombre de mois',
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
                        tooltip: 'Augmenter le nombre de mois',
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
                        style: TextStyle(fontSize: 11, color: AppTheme.textSecondary, fontStyle: FontStyle.italic),
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
                  border: Border.all(color: AppTheme.border),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Mois de début",
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
                    ),
                    const SizedBox(height: 8),
                    InkWell(
                      onTap: () => _showMonthYearPicker(),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
                        decoration: BoxDecoration(
                          border: Border.all(color: AppTheme.border),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              DateFormat('MMMM yyyy', 'fr_FR').format(DateFormat('yyyy-MM').parse(_moisAnnee)),
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                            ),
                            const Icon(Icons.calendar_today, size: 20, color: AppTheme.textTertiary),
                          ],
                        ),
                      ),
                    ),
                    if (_nombreMois > 1)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Text(
                          'Du ${DateFormat('MMMM yyyy', 'fr_FR').format(DateFormat('yyyy-MM').parse(_moisAnnee))} et les ${_nombreMois - 1} mois précédents',
                          style: TextStyle(fontSize: 11, color: AppTheme.textSecondary, fontStyle: FontStyle.italic),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ],

          if (_type == null) ...[
            const SizedBox(height: 8),
          ] else if (_verifEnCours) ...[
            const SizedBox(height: 24),
            const Center(child: CircularProgressIndicator()),
          ] else if (_verifEnErreur) ...[
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.errorSurface,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline_rounded, color: AppTheme.errorDark),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Vérification impossible, réessayez.',
                      style: const TextStyle(color: AppTheme.errorDark, fontSize: 13),
                    ),
                  ),
                  TextButton(onPressed: _checkRestrictions, child: const Text('Réessayer')),
                ],
              ),
            ),
          ] else if (!_canSubmit) ...[
            const SizedBox(height: 24),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppTheme.warningSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.orange.shade200),
              ),
              child: Row(
                children: [
                  const Icon(Icons.warning_amber_rounded, color: AppTheme.warning),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _restrictionMessage ?? 'Vous avez déjà une demande en cours',
                      style: const TextStyle(color: AppTheme.warning, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 24),
          const Text(
            "Notes ou commentaires",
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textTertiary),
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
                borderSide: BorderSide(color: AppTheme.border),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide(color: AppTheme.border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(color: primaryBlue, width: 2),
              ),
            ),
          ),
          const SizedBox(height: 8),
        ],
    );
  }
}
