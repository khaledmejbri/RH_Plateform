import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_models.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_repository.dart';
import 'package:rh_mobile_app/features/workflow/presentation/mes_taches_screen.dart';

class _FauxWorkflowRepo extends WorkflowRepository {
  _FauxWorkflowRepo(this._onMesTaches) : super(Dio());

  final Future<PageResponse<TacheListeItem>> Function(String statut, int page, int taille)
      _onMesTaches;

  @override
  Future<CompteurTaches> compteurMesTaches() async =>
      const CompteurTaches(aTraiter: 2, enRetard: 1);

  @override
  Future<PageResponse<TacheListeItem>> mesTaches({
    String statut = 'A_TRAITER',
    int page = 0,
    int taille = 20,
  }) =>
      _onMesTaches(statut, page, taille);
}

TacheListeItem _tache({
  required String id,
  required String etape,
  String type = 'VALIDATION',
  bool enRetard = false,
  String? pourNom,
}) {
  return TacheListeItem(
    tacheId: id,
    instanceId: 'inst-$id',
    type: type,
    libelleEtape: etape,
    objetType: 'CONGE',
    objetLibelle: 'Congé annuel',
    objetNumero: 'DEM-202611-0001',
    objetId: 'obj-$id',
    demandeurNom: 'Sarra Khemiri',
    echeanceLe: '2026-11-28T10:00:00Z',
    enRetard: enRetard,
    origine: pourNom != null ? 'DELEGATION' : 'NOMINATIF',
    pourNom: pourNom,
    actionsPermises: const ['APPROUVER', 'REFUSER'],
    version: 1,
    resume: const {'affichage_periode': 'du 01/12/2026 au 03/12/2026'},
  );
}

Widget _app(_FauxWorkflowRepo repo) {
  return ProviderScope(
    overrides: [
      workflowRepositoryProvider.overrideWithValue(repo),
    ],
    child: MaterialApp(
      theme: AppTheme.light(),
      home: const MesTachesScreen(),
    ),
  );
}

void main() {
  testWidgets('affiche l’état vide lorsque aucune tâche n’est à traiter', (tester) async {
    final repo = _FauxWorkflowRepo(
      (statut, page, taille) async =>
          const PageResponse(elements: [], page: 0, taille: 20, total: 0),
    );

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Aucune tâche à traiter'), findsOneWidget);
  });

  testWidgets('affiche l’état d’erreur et permet de réessayer', (tester) async {
    var essais = 0;
    final repo = _FauxWorkflowRepo((statut, page, taille) async {
      if (statut == 'A_TRAITER') {
        essais++;
        if (essais == 1) {
          throw DioException(
            requestOptions: RequestOptions(path: '/taches/moi'),
            type: DioExceptionType.connectionError,
          );
        }
        return PageResponse(
          elements: [_tache(id: 't1', etape: 'Validation manager')],
          page: 0,
          taille: 20,
          total: 1,
        );
      }
      return const PageResponse(elements: [], page: 0, taille: 20, total: 0);
    });

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Réessayer'), findsOneWidget);
    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();

    expect(find.text('Validation manager'), findsOneWidget);
    expect(essais, 2);
  });

  testWidgets('affiche les pastilles En retard, Pour le compte de et pagination Charger plus',
      (tester) async {
    final appelsPages = <int>[];
    final repo = _FauxWorkflowRepo((statut, page, taille) async {
      if (statut != 'A_TRAITER') {
        return const PageResponse(elements: [], page: 0, taille: 20, total: 0);
      }
      appelsPages.add(page);
      if (page == 0) {
        return PageResponse(
          elements: [
            _tache(
              id: 't1',
              etape: 'Validation N+1',
              enRetard: true,
              pourNom: 'Ali Mansouri',
            ),
            _tache(
              id: 't2',
              etape: 'Précisions demandées',
              type: 'COMPLEMENT',
            ),
          ],
          page: 0,
          taille: 20,
          total: 25,
        );
      }
      return PageResponse(
        elements: [
          _tache(id: 't3', etape: 'Validation RH'),
        ],
        page: 1,
        taille: 20,
        total: 25,
      );
    });

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Validation N+1'), findsOneWidget);
    expect(find.text('En retard'), findsOneWidget);
    expect(find.text('Pour le compte de Ali Mansouri'), findsOneWidget);
    expect(find.text('Complément demandé'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.text('Charger plus (2 / 25)'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Charger plus (2 / 25)'));
    await tester.pumpAndSettle();

    expect(appelsPages, [0, 1]);
    expect(find.text('Validation RH'), findsOneWidget);
  });
}
