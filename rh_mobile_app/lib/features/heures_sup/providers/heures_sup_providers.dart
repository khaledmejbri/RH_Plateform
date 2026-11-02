import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/heures_sup_models.dart';
import '../data/heures_sup_repository.dart';

final sitesHeuresSupProvider = FutureProvider.autoDispose<List<SitePointageChoix>>((ref) {
  return ref.watch(heuresSupRepositoryProvider).sites();
});

final beneficiairesPossiblesProvider = FutureProvider.autoDispose<List<Beneficiaire>>((ref) {
  return ref.watch(heuresSupRepositoryProvider).beneficiairesPossibles();
});

final mesDemandesHeuresSupProvider = FutureProvider.autoDispose<List<DemandeHeuresSup>>((ref) {
  return ref.watch(heuresSupRepositoryProvider).mesDemandes();
});

/// Heures comptées des bénéficiaires de mes demandes validées (période par défaut côté serveur).
final heuresComptesMesDemandesProvider = FutureProvider.autoDispose<List<HeuresComptees>>((ref) {
  return ref.watch(heuresSupRepositoryProvider).heuresComptees();
});

final mesHeuresSupProvider = FutureProvider.autoDispose<MesHeuresSup>((ref) {
  return ref.watch(heuresSupRepositoryProvider).mesHeuresSup();
});
