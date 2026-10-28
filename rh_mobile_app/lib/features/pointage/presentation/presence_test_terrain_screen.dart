import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/gps_moyenne.dart';
import '../data/presence_errors.dart';
import '../data/presence_gps.dart';
import '../data/presence_models.dart';
import '../data/presence_repository.dart';

final _sitesTerrainProvider =
    FutureProvider.autoDispose<List<PresenceSite>>((ref) {
  return ref.watch(presenceRepositoryProvider).listerSites();
});

/// RH | ADMIN — test terrain (D-P02) : distance, précision et verdict en direct,
/// pour valider le rayon d'un site avant de l'imposer aux collaborateurs.
class PresenceTestTerrainScreen extends ConsumerStatefulWidget {
  const PresenceTestTerrainScreen({super.key});

  @override
  ConsumerState<PresenceTestTerrainScreen> createState() =>
      _PresenceTestTerrainScreenState();
}

class _PresenceTestTerrainScreenState
    extends ConsumerState<PresenceTestTerrainScreen> {
  PresenceSite? _site;
  GpsFix? _mesure;
  String? _erreur;
  bool _enCours = false;
  Timer? _timer;

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _basculer() {
    if (_enCours) {
      _timer?.cancel();
      setState(() => _enCours = false);
      return;
    }
    setState(() {
      _enCours = true;
      _erreur = null;
    });
    _mesurer();
    _timer = Timer.periodic(const Duration(seconds: 3), (_) => _mesurer());
  }

  Future<void> _mesurer() async {
    final r = await PresenceGps.obtain(fresh: true);
    if (!mounted || !_enCours) return;
    setState(() {
      _mesure = r.position ?? _mesure;
      _erreur = r.position == null ? r.error : null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(_sitesTerrainProvider);
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(title: const Text('Test terrain')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              messageErreurPresence(e, fallback: 'Impossible de charger les sites.'),
              textAlign: TextAlign.center,
            ),
          ),
        ),
        data: (sites) {
          final posables = sites.where((s) => s.hasEmplacement).toList();
          if (posables.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text(
                  'Aucun site avec emplacement posé. Posez d’abord l’emplacement d’un site.',
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }
          final site = _site ?? posables.first;
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
            children: [
              const Text(
                'Déplacez-vous sur le site : l’écran indique en direct si un pointage '
                'serait accepté à cet endroit, avec les mêmes règles que le serveur.',
                style: TextStyle(color: AppTheme.textSecondary),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                // `value` reste compatible avec Flutter 3.29 (Codemagic) ; `initialValue` n'existe qu'à partir de 3.33
                // ignore: deprecated_member_use
                value: site.id,
                decoration: const InputDecoration(labelText: 'Site'),
                items: posables
                    .map((s) => DropdownMenuItem(value: s.id, child: Text(s.libelle)))
                    .toList(),
                onChanged: (id) => setState(
                  () => _site = posables.firstWhere((s) => s.id == id),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '${site.typeLieuLibelle} · rayon autorisé ${site.rayonMetres} m'
                '${site.precisionAncrageMetres != null ? ' · pose ±${site.precisionAncrageMetres!.toStringAsFixed(1)} m' : ''}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 20),
              _Resultat(site: site, mesure: _mesure, erreur: _erreur),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: _basculer,
                icon: Icon(_enCours ? Icons.stop_rounded : Icons.play_arrow_rounded),
                label: Text(_enCours ? 'Arrêter la mesure' : 'Mesurer en continu'),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _Resultat extends StatelessWidget {
  const _Resultat({required this.site, required this.mesure, required this.erreur});

  final PresenceSite site;
  final GpsFix? mesure;
  final String? erreur;

  @override
  Widget build(BuildContext context) {
    if (erreur != null && mesure == null) {
      return _Carte(couleur: AppTheme.errorSurface, texte: erreur!, icone: Icons.gps_off_rounded);
    }
    if (mesure == null) {
      return const _Carte(
        couleur: AppTheme.surfaceMuted,
        texte: 'Lancez la mesure pour voir la distance et la précision.',
        icone: Icons.my_location_rounded,
      );
    }
    final m = mesure!;
    final distance = GpsCalcul.distanceMetres(
      site.latitude!, site.longitude!, m.latitude, m.longitude,
    );
    final verdict = verdictTerrain(
      distanceMetres: distance,
      precisionMetres: m.accuracyMeters,
      rayonMetres: site.rayonMetres,
      positionSimulee: m.isMock,
    );
    final (libelle, fond, texte, icone) = switch (verdict) {
      VerdictTerrain.dansLaZone => ('Pointage accepté ici', AppTheme.successSurface, AppTheme.success, Icons.check_circle_rounded),
      VerdictTerrain.horsZone => ('Hors zone', AppTheme.errorSurface, AppTheme.errorDark, Icons.wrong_location_rounded),
      VerdictTerrain.precisionInsuffisante => ('Signal GPS trop faible', AppTheme.warningSurface, AppTheme.warning, Icons.signal_cellular_alt_1_bar_rounded),
      VerdictTerrain.positionSimulee => ('Position simulée détectée', AppTheme.errorSurface, AppTheme.errorDark, Icons.gpp_bad_rounded),
    };
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: fond, borderRadius: BorderRadius.circular(14)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icone, color: texte),
              const SizedBox(width: 8),
              Text(libelle, style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16, color: texte)),
            ],
          ),
          const SizedBox(height: 12),
          _Ligne('Distance au site', '${distance.toStringAsFixed(1)} m'),
          _Ligne(
            'Précision GPS',
            m.accuracyMeters != null ? '±${m.accuracyMeters!.toStringAsFixed(1)} m' : 'inconnue',
          ),
          _Ligne('Rayon autorisé', '${site.rayonMetres} m'),
          if (erreur != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('Dernière mesure en échec : $erreur', style: const TextStyle(fontSize: 12)),
            ),
        ],
      ),
    );
  }
}

class _Ligne extends StatelessWidget {
  const _Ligne(this.libelle, this.valeur);

  final String libelle;
  final String valeur;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          Expanded(child: Text(libelle, style: const TextStyle(color: AppTheme.textSecondary))),
          Text(valeur, style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _Carte extends StatelessWidget {
  const _Carte({required this.couleur, required this.texte, required this.icone});

  final Color couleur;
  final String texte;
  final IconData icone;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: couleur, borderRadius: BorderRadius.circular(14)),
      child: Row(
        children: [
          Icon(icone, color: AppTheme.textSecondary),
          const SizedBox(width: 10),
          Expanded(child: Text(texte)),
        ],
      ),
    );
  }
}
