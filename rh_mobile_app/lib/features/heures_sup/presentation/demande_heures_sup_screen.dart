import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/app_theme.dart';
import '../../pointage/data/fenetre_du_jour.dart';
import '../../pointage/data/presence_errors.dart';
import '../data/heures_sup_models.dart';
import '../data/heures_sup_repository.dart';
import '../data/heures_sup_validation.dart';
import '../providers/heures_sup_providers.dart';
import 'heures_sup_widgets.dart';

/// « Demander des heures sup » (RO, chef de département, RH, ADMIN). Le serveur contrôle le périmètre D-H03.
class DemandeHeuresSupScreen extends ConsumerStatefulWidget {
  const DemandeHeuresSupScreen({super.key});

  @override
  ConsumerState<DemandeHeuresSupScreen> createState() => _DemandeHeuresSupScreenState();
}

class _DemandeHeuresSupScreenState extends ConsumerState<DemandeHeuresSupScreen> {
  final _motif = TextEditingController();
  final _choisis = <String>{};
  String? _siteId;
  DateTime? _debut;
  DateTime? _fin;
  int _avant = 0;
  int _apres = 0;
  bool _envoi = false;
  String? _erreur;

  @override
  void dispose() {
    _motif.dispose();
    super.dispose();
  }

  Future<void> _recharger() async {
    ref.invalidate(beneficiairesPossiblesProvider);
    ref.invalidate(sitesHeuresSupProvider);
  }

  Future<void> _choisirDate({required bool debut}) async {
    final aujourdhui = DateTime.now();
    final premier = DateTime(aujourdhui.year, aujourdhui.month, aujourdhui.day);
    final initiale = (debut ? _debut : (_fin ?? _debut)) ?? premier;
    final d = await showDatePicker(
      context: context,
      initialDate: initiale.isBefore(premier) ? premier : initiale,
      firstDate: premier,
      lastDate: premier.add(const Duration(days: 365)),
    );
    if (d == null || !mounted) return;
    setState(() {
      if (debut) {
        _debut = d;
        if (_fin != null && _fin!.isBefore(d)) _fin = null;
      } else {
        _fin = d;
      }
    });
  }

  Future<void> _envoyer() async {
    final erreur = validerFormulaireHeuresSup(
      nbBeneficiaires: _choisis.length,
      siteId: _siteId,
      dateDebut: _debut,
      dateFin: _fin,
      avantMinutes: _avant,
      apresMinutes: _apres,
      motif: _motif.text,
    );
    if (erreur != null) {
      setState(() => _erreur = erreur);
      return;
    }
    setState(() {
      _erreur = null;
      _envoi = true;
    });
    try {
      final demande = await ref.read(heuresSupRepositoryProvider).creerDemande(
            construireDemande(
              collaborateurIds: _choisis,
              siteId: _siteId!,
              dateDebut: _debut!,
              dateFin: _fin,
              avantMinutes: _avant,
              apresMinutes: _apres,
              motif: _motif.text,
            ),
          );
      if (!mounted) return;
      ref.invalidate(mesDemandesHeuresSupProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text((demande.numeroAffiche.isEmpty ? 'Demande envoyée aux RH pour validation.' : 'Demande ${demande.numeroAffiche} envoyée aux RH pour validation.'))),
      );
      if (context.canPop()) {
        context.pop();
      } else {
        context.go('/heures-sup/demandes');
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _envoi = false;
        _erreur = estHorsLigne(e)
            ? 'Vous semblez hors ligne. Vérifiez votre connexion puis réessayez.'
            : messageErreurPresence(e, fallback: 'Envoi de la demande impossible.');
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final benef = ref.watch(beneficiairesPossiblesProvider);
    final sites = ref.watch(sitesHeuresSupProvider);

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(backgroundColor: AppTheme.surface, title: const Text('Demander des heures sup')),
      body: benef.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => EtatErreur(error: e, onRetry: _recharger),
        data: (liste) {
          if (liste.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text(
                  'Aucun collaborateur de votre périmètre pour lequel demander des heures sup.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppTheme.textSecondary),
                ),
              ),
            );
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              _section('Collaborateurs (${_choisis.length}/$maxBeneficiaires)'),
              Card(
                margin: EdgeInsets.zero,
                child: Column(
                  children: [
                    for (final b in liste)
                      CheckboxListTile(
                        dense: true,
                        value: _choisis.contains(b.collaborateurId),
                        title: Text(b.libelle),
                        onChanged: _envoi
                            ? null
                            : (v) => setState(() {
                                  if (v == true) {
                                    _choisis.add(b.collaborateurId);
                                  } else {
                                    _choisis.remove(b.collaborateurId);
                                  }
                                }),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              _section('Site'),
              sites.when(
                loading: () => const LinearProgressIndicator(),
                error: (e, _) => EtatErreur(error: e, onRetry: _recharger),
                data: (tous) {
                  final avecHoraire = tous.where((s) => s.avecHoraire).toList();
                  if (avecHoraire.isEmpty) {
                    return const Text(
                      'Aucun site avec horaire : les heures sup ne sont pas possibles.',
                      style: TextStyle(color: AppTheme.textSecondary),
                    );
                  }
                  return DropdownButtonFormField<String>(
                    value: avecHoraire.any((s) => s.siteId == _siteId) ? _siteId : null,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Site *'),
                    items: [
                      for (final s in avecHoraire)
                        DropdownMenuItem(value: s.siteId, child: Text(s.libelle, overflow: TextOverflow.ellipsis)),
                    ],
                    onChanged: _envoi ? null : (v) => setState(() => _siteId = v),
                  );
                },
              ),
              const SizedBox(height: 16),
              _section('Période (31 jours maximum)'),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _envoi ? null : () => _choisirDate(debut: true),
                      icon: const Icon(Icons.event_rounded),
                      label: Text(_debut == null ? 'Début *' : formaterDateIso(dateApi(_debut!))),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _envoi || _debut == null ? null : () => _choisirDate(debut: false),
                      icon: const Icon(Icons.event_available_rounded),
                      label: Text(_fin == null ? 'Fin (option)' : formaterDateIso(dateApi(_fin!))),
                    ),
                  ),
                ],
              ),
              if (_fin != null)
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton(
                    onPressed: _envoi ? null : () => setState(() => _fin = null),
                    child: const Text('Un seul jour'),
                  ),
                ),
              const SizedBox(height: 16),
              _section('Ouverture de la pointeuse'),
              Row(
                children: [
                  Expanded(
                    child: _listeDuree('Avant l’horaire', _avant, (v) => setState(() => _avant = v ?? 0)),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _listeDuree('Après l’horaire', _apres, (v) => setState(() => _apres = v ?? 0)),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _motif,
                maxLines: 3,
                maxLength: 500,
                enabled: !_envoi,
                decoration: const InputDecoration(labelText: 'Motif *'),
              ),
              if (_erreur != null) ...[
                const SizedBox(height: 8),
                Text(_erreur!, style: const TextStyle(color: AppTheme.error)),
              ],
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _envoi ? null : _envoyer,
                icon: _envoi
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.send_rounded),
                label: Text(_envoi ? 'Envoi…' : 'Envoyer la demande'),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _section(String texte) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(texte, style: const TextStyle(fontWeight: FontWeight.w700)),
      );

  Widget _listeDuree(String label, int valeur, ValueChanged<int?> onChanged) {
    return DropdownButtonFormField<int>(
      value: valeur,
      isExpanded: true,
      decoration: InputDecoration(labelText: label),
      items: [
        const DropdownMenuItem(value: 0, child: Text('Aucune')),
        for (final m in dureesOuvertureMinutes) DropdownMenuItem(value: m, child: Text(libelleDuree(m))),
      ],
      onChanged: _envoi ? null : onChanged,
    );
  }
}
