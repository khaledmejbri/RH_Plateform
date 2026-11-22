import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/widgets/rh_ui.dart';
import '../../demandes_admin/data/demande_admin_models.dart';
import '../../demandes_admin/data/demande_admin_repository.dart';
import '../../pointage/data/presence_models.dart';
import '../../pointage/data/presence_repository.dart';
import '../../workflow/data/workflow_models.dart';
import '../../workflow/data/workflow_repository.dart';
import '../data/a_valider_logic.dart';

/// Contenu du hub « À valider ».
class AValider {
  const AValider({
    required this.demandes,
    required this.pointagesManuels,
    this.taches = const [],
    this.tachesTotal = 0,
  });

  final List<DemandeAdminItem> demandes;
  final List<DemandeManuelle> pointagesManuels;
  final List<TacheListeItem> taches;
  final int tachesTotal;

  int get _nombreTachesComptees =>
      tachesTotal > taches.length ? tachesTotal : taches.length;

  int get total => compterAValider(
        demandes: demandes.length,
        pointagesManuels: pointagesManuels.length,
        taches: _nombreTachesComptees,
      );
}

Future<PageResponse<TacheListeItem>> _tachesOuVideSiIndisponible(
  WorkflowRepository repo,
) async {
  try {
    return await repo.mesTaches(statut: 'A_TRAITER', page: 0, taille: 50);
  } catch (e) {
    if (rhEstAccesRefuse(e)) {
      return const PageResponse(elements: [], page: 0, taille: 50, total: 0);
    }
    rethrow;
  }
}

final aValiderProvider = FutureProvider.autoDispose<AValider>((ref) async {
  final demandesRepo = ref.watch(demandeAdminRepositoryProvider);
  final presenceRepo = ref.watch(presenceRepositoryProvider);
  final workflowRepo = ref.watch(workflowRepositoryProvider);
  final resultats = await Future.wait([
    listeOuVideSiRefuse<DemandeAdminItem>(() => demandesRepo.demandesEnAttenteRo()),
    listeOuVideSiRefuse<DemandeManuelle>(() => presenceRepo.demandesManuellesAValider()),
    _tachesOuVideSiIndisponible(workflowRepo),
  ]);
  final pageTaches = resultats[2] as PageResponse<TacheListeItem>;
  return AValider(
    demandes: resultats[0] as List<DemandeAdminItem>,
    pointagesManuels: resultats[1] as List<DemandeManuelle>,
    taches: pageTaches.elements,
    tachesTotal: pageTaches.total,
  );
});
