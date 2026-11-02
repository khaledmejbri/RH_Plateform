import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../../pointage/data/presence_errors.dart';
import '../data/heures_sup_models.dart';

/// Gère chargement / erreur / hors ligne / vide autour d'un `FutureProvider`.
/// [builder] n'est appelé que pour des données non vides (selon [estVide]).
class HeuresSupAsyncView<T> extends StatelessWidget {
  const HeuresSupAsyncView({
    super.key,
    required this.value,
    required this.onRefresh,
    required this.estVide,
    required this.messageVide,
    required this.builder,
  });

  final AsyncValue<T> value;
  final Future<void> Function() onRefresh;
  final bool Function(T data) estVide;
  final String messageVide;
  final Widget Function(BuildContext context, T data) builder;

  @override
  Widget build(BuildContext context) {
    return value.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => EtatErreur(error: e, onRetry: onRefresh),
      data: (data) {
        if (estVide(data)) {
          return RefreshIndicator(
            onRefresh: onRefresh,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                Padding(
                  padding: const EdgeInsets.all(32),
                  child: Text(
                    messageVide,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppTheme.textSecondary),
                  ),
                ),
              ],
            ),
          );
        }
        return RefreshIndicator(onRefresh: onRefresh, child: builder(context, data));
      },
    );
  }
}

/// Erreur réseau (« hors ligne ») ou serveur, avec bouton « Réessayer ».
class EtatErreur extends StatelessWidget {
  const EtatErreur({super.key, required this.error, required this.onRetry});

  final Object error;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    final horsLigne = estHorsLigne(error);
    final message = horsLigne
        ? 'Vous semblez hors ligne. Vérifiez votre connexion puis réessayez.'
        : messageErreurPresence(error, fallback: 'Chargement impossible.');
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              horsLigne ? Icons.cloud_off_rounded : Icons.error_outline_rounded,
              size: 40,
              color: AppTheme.textTertiary,
            ),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: const Text('Réessayer')),
          ],
        ),
      ),
    );
  }
}

/// Pastille de statut d'une demande.
class StatutPastille extends StatelessWidget {
  const StatutPastille({super.key, required this.statut});

  final String statut;

  @override
  Widget build(BuildContext context) {
    final (fond, texte) = switch (statut) {
      StatutHeuresSup.validee => (AppTheme.successSurface, AppTheme.success),
      StatutHeuresSup.enAttente => (AppTheme.warningSurface, AppTheme.warning),
      StatutHeuresSup.refusee => (AppTheme.errorSurface, AppTheme.errorDark),
      _ => (AppTheme.surfaceMuted, AppTheme.textSecondary),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(20)),
      child: Text(
        StatutHeuresSup.libelle(statut),
        style: TextStyle(color: texte, fontWeight: FontWeight.w600, fontSize: 12),
      ),
    );
  }
}

/// Ligne d'heures comptées : nom, jour, entrée/sortie, durée et état.
class LigneHeuresComptees extends StatelessWidget {
  const LigneHeuresComptees({super.key, required this.ligne, this.afficherNom = true});

  final HeuresComptees ligne;
  final bool afficherNom;

  @override
  Widget build(BuildContext context) {
    final avecNom = afficherNom && ligne.nom.isNotEmpty;
    final jour = formaterDateIso(ligne.journee);
    final details = <String>[
      if (avecNom && jour.isNotEmpty) jour,
      if (ligne.siteLibelle != null && ligne.siteLibelle!.isNotEmpty) ligne.siteLibelle!,
      if (ligne.sansPointage)
        'Aucun pointage'
      else
        '${_heure(ligne.entree)} → ${_heure(ligne.sortie)}',
      if (ligne.plagesLibelle.isNotEmpty) ligne.plagesLibelle,
      if (ligne.etat != 'COMPLET') ligne.etatLibelle,
    ];
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(
        avecNom ? ligne.nom : (jour.isEmpty ? 'Journée' : jour),
        style: const TextStyle(fontWeight: FontWeight.w600),
      ),
      subtitle: details.isEmpty ? null : Text(details.join(' · '), style: const TextStyle(fontSize: 12)),
      trailing: Text(ligne.heuresComptees, style: const TextStyle(fontWeight: FontWeight.w700)),
    );
  }

  /// Extrait HH:mm d'un instant ISO ; `--:--` si absent.
  static String _heure(String? iso) {
    if (iso == null || iso.isEmpty) return '--:--';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return iso;
    final l = dt.toLocal();
    return '${l.hour.toString().padLeft(2, '0')}:${l.minute.toString().padLeft(2, '0')}';
  }
}
