import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../demandes_admin/data/demande_admin_models.dart';
import '../../demandes_admin/data/demande_admin_repository.dart';
import '../../pointage/data/presence_models.dart';
import '../../pointage/data/presence_repository.dart';
import '../data/a_valider_logic.dart';

/// Contenu du hub « À valider ».
class AValider {
  const AValider({required this.demandes, required this.pointagesManuels});

  final List<DemandeAdminItem> demandes;
  final List<DemandeManuelle> pointagesManuels;

  int get total => compterAValider(demandes: demandes.length, pointagesManuels: pointagesManuels.length);
}

final aValiderProvider = FutureProvider.autoDispose<AValider>((ref) async {
  final demandesRepo = ref.watch(demandeAdminRepositoryProvider);
  final presenceRepo = ref.watch(presenceRepositoryProvider);
  final resultats = await Future.wait([
    listeOuVideSiRefuse<DemandeAdminItem>(() => demandesRepo.demandesEnAttenteRo()),
    listeOuVideSiRefuse<DemandeManuelle>(() => presenceRepo.demandesManuellesAValider()),
  ]);
  return AValider(
    demandes: resultats[0] as List<DemandeAdminItem>,
    pointagesManuels: resultats[1] as List<DemandeManuelle>,
  );
});
