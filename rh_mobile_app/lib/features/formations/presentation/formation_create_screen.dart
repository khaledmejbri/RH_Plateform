import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../auth/providers/collaborateur_notifier.dart';
import '../data/formation_models.dart';
import '../data/formation_repository.dart';
import 'formations_list_screen.dart';

final formationUnitesCiblesProvider =
    FutureProvider.autoDispose<List<FormationUniteCible>>((ref) async {
      return ref.watch(formationRepositoryProvider).unitesCibles();
    });

final formationCollaborateursCiblesProvider =
    FutureProvider.autoDispose<List<FormationCollaborateurCible>>((ref) async {
      return ref.watch(formationRepositoryProvider).collaborateursCibles();
    });

class FormationCreateScreen extends ConsumerStatefulWidget {
  const FormationCreateScreen({super.key});

  @override
  ConsumerState<FormationCreateScreen> createState() =>
      _FormationCreateScreenState();
}

class _FormationCreateScreenState extends ConsumerState<FormationCreateScreen> {
  final _formKey = GlobalKey<FormState>();
  final _typeController = TextEditingController();
  final _organismeController = TextEditingController();
  final _dureeController = TextEditingController();
  final _coutController = TextEditingController();
  final _objectifsController = TextEditingController();
  final _justificationController = TextEditingController();

  String? _uniteId;
  final Set<String> _collaborateurIds = {};
  bool _loading = false;

  @override
  void dispose() {
    _typeController.dispose();
    _organismeController.dispose();
    _dureeController.dispose();
    _coutController.dispose();
    _objectifsController.dispose();
    _justificationController.dispose();
    super.dispose();
  }

  Future<void> _submit(bool isChef, bool isRo) async {
    if (!_formKey.currentState!.validate()) return;
    if (isChef && _uniteId == null) {
      _snack('Choisissez une unite cible');
      return;
    }
    if (isRo && _collaborateurIds.isEmpty) {
      _snack('Choisissez au moins un collaborateur');
      return;
    }

    setState(() => _loading = true);
    try {
      await ref
          .read(formationRepositoryProvider)
          .creer(
            typeFormation: _typeController.text.trim(),
            organisme: _organismeController.text.trim(),
            dureeHeures: int.parse(_dureeController.text.trim()),
            coutEstime: num.parse(
              _coutController.text.trim().replaceAll(',', '.'),
            ),
            objectifsPedagogiques: _objectifsController.text.trim(),
            justification: _justificationController.text.trim(),
            uniteCibleIdentifiant: isChef ? _uniteId : null,
            collaborateursCiblesIdentifiants:
                isRo ? _collaborateurIds.toList() : null,
          );
      ref.invalidate(formationsListProvider);
      if (mounted) {
        _snack('Demande formation envoyee');
        context.pop();
      }
    } catch (e) {
      if (mounted) _snack(e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(collaborateurNotifierProvider).valueOrNull;
    final isChef = user?.isChefDept == true;
    final isRo = user?.isRo == true;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Demande de formation'),
      ),
      bottomNavigationBar:
          !isChef && !isRo
              ? null
              : SafeArea(
                top: false,
                child: Container(
                  padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
                  decoration: const BoxDecoration(
                    color: AppTheme.surface,
                    border: Border(
                      top: BorderSide(color: AppTheme.border, width: 0.5),
                    ),
                  ),
                  child: FilledButton.icon(
                    onPressed: _loading ? null : () => _submit(isChef, isRo),
                    icon:
                        _loading
                            ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                            : const Icon(Icons.send_rounded),
                    label: const Text('Envoyer au RH'),
                  ),
                ),
              ),
      body:
          !isChef && !isRo
              ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AppTheme.surface,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: AppTheme.border),
                    ),
                    child: const Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.lock_outline_rounded,
                          color: AppTheme.textSecondary,
                          size: 34,
                        ),
                        SizedBox(height: 12),
                        Text(
                          'Cette demande est reservee aux chefs de departement et responsables operationnels.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ),
              )
              : Form(
                key: _formKey,
                child: ListView(
                  padding: const EdgeInsets.fromLTRB(16, 16, 16, 120),
                  children: [
                    _FormSection(
                      title: 'Informations',
                      icon: Icons.school_outlined,
                      children: [
                        _TextField(
                          controller: _typeController,
                          label: 'Type de formation',
                          icon: Icons.school_outlined,
                        ),
                        const SizedBox(height: 12),
                        _TextField(
                          controller: _organismeController,
                          label: 'Organisme',
                          icon: Icons.business_outlined,
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    _FormSection(
                      title: 'Budget et duree',
                      icon: Icons.payments_outlined,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: _TextField(
                                controller: _dureeController,
                                label: 'Duree (h)',
                                icon: Icons.schedule_outlined,
                                keyboardType: TextInputType.number,
                                inputFormatters: [
                                  FilteringTextInputFormatter.digitsOnly,
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _TextField(
                                controller: _coutController,
                                label: 'Cout estime',
                                icon: Icons.payments_outlined,
                                keyboardType:
                                    const TextInputType.numberWithOptions(
                                      decimal: true,
                                    ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    _FormSection(
                      title: 'Besoin',
                      icon: Icons.flag_outlined,
                      children: [
                        _TextField(
                          controller: _objectifsController,
                          label: 'Objectifs pedagogiques',
                          icon: Icons.flag_outlined,
                          maxLines: 3,
                        ),
                        const SizedBox(height: 12),
                        _TextField(
                          controller: _justificationController,
                          label: 'Justification',
                          icon: Icons.notes_outlined,
                          maxLines: 3,
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    _FormSection(
                      title: 'Cible',
                      icon:
                          isChef
                              ? Icons.account_tree_outlined
                              : Icons.people_outline,
                      children: [
                        if (isChef)
                          _UnitePicker(
                            value: _uniteId,
                            onChanged: (v) => setState(() => _uniteId = v),
                          ),
                        if (isRo)
                          _CollaborateursPicker(
                            selected: _collaborateurIds,
                            onChanged: (id, selected) {
                              setState(() {
                                selected
                                    ? _collaborateurIds.add(id)
                                    : _collaborateurIds.remove(id);
                              });
                            },
                          ),
                      ],
                    ),
                  ],
                ),
              ),
    );
  }
}

class _FormSection extends StatelessWidget {
  const _FormSection({
    required this.title,
    required this.icon,
    required this.children,
  });

  final String title;
  final IconData icon;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppTheme.border, width: 0.5),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF0F172A).withValues(alpha: 0.03),
            blurRadius: 16,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: AppTheme.primary, size: 18),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                  color: AppTheme.textPrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }
}

class _TextField extends StatelessWidget {
  const _TextField({
    required this.controller,
    required this.label,
    required this.icon,
    this.maxLines = 1,
    this.keyboardType,
    this.inputFormatters,
  });

  final TextEditingController controller;
  final String label;
  final IconData icon;
  final int maxLines;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      maxLines: maxLines,
      keyboardType: keyboardType,
      inputFormatters: inputFormatters,
      validator:
          (v) => v == null || v.trim().isEmpty ? 'Champ obligatoire' : null,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        filled: true,
        fillColor: const Color(0xFFF8FAFC),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppTheme.border),
        ),
      ),
    );
  }
}

class _UnitePicker extends ConsumerWidget {
  const _UnitePicker({required this.value, required this.onChanged});

  final String? value;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(formationUnitesCiblesProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error:
          (e, _) =>
              const _PickerError(message: 'Impossible de charger les unites.'),
      data:
          (items) => DropdownButtonFormField<String>(
            initialValue: value,
            decoration: InputDecoration(
              labelText: 'Unite cible',
              prefixIcon: const Icon(Icons.account_tree_outlined),
              filled: true,
              fillColor: const Color(0xFFF8FAFC),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
            validator: (v) => v == null ? 'Choisissez une unite' : null,
            items:
                items
                    .map(
                      (u) => DropdownMenuItem(
                        value: u.id,
                        child: Text(
                          '${u.code} - ${u.libelle}',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
            onChanged: onChanged,
          ),
    );
  }
}

class _CollaborateursPicker extends ConsumerWidget {
  const _CollaborateursPicker({
    required this.selected,
    required this.onChanged,
  });

  final Set<String> selected;
  final void Function(String id, bool selected) onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(formationCollaborateursCiblesProvider);
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error:
          (e, _) => const _PickerError(
            message: 'Impossible de charger les collaborateurs.',
          ),
      data:
          (items) => Container(
            decoration: BoxDecoration(
              color: const Color(0xFFF8FAFC),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppTheme.border),
            ),
            child: Column(
              children: [
                ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 2,
                  ),
                  leading: const Icon(Icons.people_outline),
                  title: const Text('Collaborateurs cibles'),
                  trailing:
                      selected.isEmpty
                          ? null
                          : Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 5,
                            ),
                            decoration: BoxDecoration(
                              color: AppTheme.primarySurface,
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Text(
                              '${selected.length}',
                              style: const TextStyle(
                                color: AppTheme.primary,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                ),
                const Divider(height: 1),
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxHeight: MediaQuery.of(context).size.height * 0.4,
                  ),
                  child: ListView.builder(
                    shrinkWrap: true,
                    physics: const BouncingScrollPhysics(),
                    itemCount: items.length,
                    itemBuilder: (context, index) {
                      final c = items[index];
                      return CheckboxListTile(
                        dense: true,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 8),
                        value: selected.contains(c.id),
                        onChanged: (v) => onChanged(c.id, v == true),
                        title: Text(c.nomComplet),
                        subtitle: Text(c.matricule),
                        controlAffinity: ListTileControlAffinity.leading,
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
    );
  }
}

class _PickerError extends StatelessWidget {
  const _PickerError({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF1F2),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFECACA)),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.error_outline_rounded,
            color: AppTheme.error,
            size: 18,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(
                color: AppTheme.error,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
