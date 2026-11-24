import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rh_mobile_app/core/format/formats.dart';
import 'package:rh_mobile_app/features/dossier/presentation/pointages_equipe_screen.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_repository.dart';

class _AppelPointages {
  const _AppelPointages({this.du, this.au, this.q, required this.page});

  final String? du;
  final String? au;
  final String? q;
  final int page;
}

class _FauxPresenceRepo extends PresenceRepository {
  _FauxPresenceRepo(this._handler) : super(Dio());

  final Future<PointagesEquipePage> Function(_AppelPointages appel) _handler;
  final List<_AppelPointages> appels = [];

  @override
  Future<PointagesEquipePage> pointagesEquipe({
    String? du,
    String? au,
    String? q,
    String? collaborateurId,
    String? siteId,
    int page = 0,
    int size = 20,
  }) {
    final appel = _AppelPointages(du: du, au: au, q: q, page: page);
    appels.add(appel);
    return _handler(appel);
  }
}

PointageEquipeItem _item({
  required String id,
  required String collaborateurId,
  required String nom,
  String? matricule,
  String type = 'ENTREE',
  String? typeLibelle,
  String statut = 'VALIDE',
  String? statutLibelle,
  String? motifRejet,
  String siteLibelle = 'Siège Social',
  String serverTs = '2026-11-24T08:15:12Z',
}) {
  return PointageEquipeItem(
    id: id,
    collaborateurId: collaborateurId,
    collaborateurNom: nom,
    collaborateurMatricule: matricule,
    type: type,
    typeLibelle: typeLibelle,
    statut: statut,
    statutLibelle: statutLibelle,
    serverTs: DateTime.parse(serverTs),
    siteLibelle: siteLibelle,
    motifRejet: motifRejet,
  );
}

Widget _app(_FauxPresenceRepo repo, {List<String>? navigations}) {
  final router = GoRouter(
    initialLocation: '/equipe/pointages',
    routes: [
      GoRoute(
        path: '/equipe/pointages',
        builder: (_, __) => const PointagesEquipeScreen(),
      ),
      GoRoute(
        path: '/equipe/:id',
        builder: (_, s) {
          final id = s.pathParameters['id']!;
          navigations?.add('/equipe/$id');
          return Scaffold(body: Text('Fiche $id'));
        },
      ),
    ],
  );

  return ProviderScope(
    overrides: [
      presenceRepositoryProvider.overrideWithValue(repo),
    ],
    child: MaterialApp.router(
      theme: ThemeData(useMaterial3: true),
      routerConfig: router,
    ),
  );
}

void main() {
  testWidgets(
      'affiche les pointages (Entrée/Sortie, Validé/Refusé, motif, horodatage Tunis) et ouvre /equipe/:id',
      (tester) async {
    final navigations = <String>[];
    final repo = _FauxPresenceRepo((_) async {
      return PointagesEquipePage(
        elements: [
          _item(
            id: 'p1',
            collaborateurId: 'col-sarra',
            nom: 'Sarra Khemiri',
            matricule: 'COL-WEB',
            type: 'ENTREE',
            typeLibelle: 'Entrée',
            statut: 'VALIDE',
            statutLibelle: 'Validé',
            serverTs: '2026-11-24T08:15:12Z',
          ),
          _item(
            id: 'p2',
            collaborateurId: 'col-ahmed',
            nom: 'Ahmed Nasri',
            matricule: 'COL-WEB2',
            type: 'SORTIE',
            typeLibelle: 'Sortie',
            statut: 'REJETE_HORS_ZONE',
            statutLibelle: 'Refusé',
            motifRejet: 'Hors zone (85 m)',
            siteLibelle: 'Annexe',
            serverTs: '2026-11-24T16:30:00Z',
          ),
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      );
    });

    await tester.pumpWidget(_app(repo, navigations: navigations));
    await tester.pumpAndSettle();

    expect(find.text("Pointages de l'équipe"), findsOneWidget);
    expect(find.text('Sarra Khemiri'), findsOneWidget);
    expect(find.text('COL-WEB'), findsOneWidget);
    expect(find.text('Entrée'), findsOneWidget);
    expect(find.text('Validé'), findsOneWidget);
    expect(find.text('24/11/2026 09:15'), findsOneWidget);

    expect(find.text('Ahmed Nasri'), findsOneWidget);
    expect(find.text('Sortie'), findsOneWidget);
    expect(find.text('Refusé'), findsOneWidget);
    expect(find.text('Hors zone (85 m)'), findsOneWidget);
    expect(find.text('Annexe'), findsOneWidget);
    expect(find.text('24/11/2026 17:30'), findsOneWidget);

    await tester.tap(find.text('Sarra Khemiri'));
    await tester.pumpAndSettle();

    expect(navigations, ['/equipe/col-sarra']);
    expect(find.text('Fiche col-sarra'), findsOneWidget);
  });

  testWidgets('raccourcis de période mettent à jour du / au et rechargent',
      (tester) async {
    final repo = _FauxPresenceRepo((_) async {
      return const PointagesEquipePage(
        elements: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      );
    });

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    final auj = aujourdhuiTunis();
    final septJoursDu = formatDateApi(auj.subtract(const Duration(days: 6)));
    final aujStr = formatDateApi(auj);

    expect(repo.appels.length, 1);
    expect(repo.appels.last.du, septJoursDu);
    expect(repo.appels.last.au, aujStr);
    expect(find.text('Aucun pointage sur cette période'), findsOneWidget);

    await tester.tap(find.byKey(const Key('periode-aujourdhui')));
    await tester.pumpAndSettle();

    expect(repo.appels.length, 2);
    expect(repo.appels.last.du, aujStr);
    expect(repo.appels.last.au, aujStr);

    await tester.tap(find.byKey(const Key('periode-mois')));
    await tester.pumpAndSettle();

    expect(repo.appels.length, 3);
    expect(repo.appels.last.au, aujStr);
  });

  testWidgets(
      'recherche debounced 300ms et état vide anti-IDOR « Aucun pointage trouvé dans votre équipe »',
      (tester) async {
    final repo = _FauxPresenceRepo((appel) async {
      if (appel.q == 'Mariem') {
        return const PointagesEquipePage(
          elements: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        );
      }
      return PointagesEquipePage(
        elements: [
          _item(
            id: 'p1',
            collaborateurId: 'col-sarra',
            nom: 'Sarra Khemiri',
            matricule: 'COL-WEB',
          ),
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      );
    });

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();
    expect(find.text('Sarra Khemiri'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('equipe-pointages-recherche')),
      'Mariem',
    );
    await tester.pump(const Duration(milliseconds: 150));
    expect(repo.appels.length, 1);

    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();

    expect(repo.appels.length, 2);
    expect(repo.appels.last.q, 'Mariem');
    expect(
      find.text('Aucun pointage trouvé dans votre équipe'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('equipe-pointages-effacer')));
    await tester.pumpAndSettle();

    expect(repo.appels.length, 3);
    expect(repo.appels.last.q, isNull);
    expect(find.text('Sarra Khemiri'), findsOneWidget);
  });

  testWidgets('pagination charge la page suivante avec Charger plus',
      (tester) async {
    final repo = _FauxPresenceRepo((appel) async {
      if (appel.page == 0) {
        return PointagesEquipePage(
          elements: [
            _item(
              id: 'p1',
              collaborateurId: 'col-1',
              nom: 'Sarra Khemiri',
            ),
          ],
          page: 0,
          size: 1,
          totalElements: 2,
          totalPages: 2,
        );
      }
      return PointagesEquipePage(
        elements: [
          _item(
            id: 'p2',
            collaborateurId: 'col-2',
            nom: 'Ahmed Nasri',
          ),
        ],
        page: 1,
        size: 1,
        totalElements: 2,
        totalPages: 2,
      );
    });

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Sarra Khemiri'), findsOneWidget);
    expect(find.text('Charger plus'), findsOneWidget);

    await tester.tap(find.text('Charger plus'));
    await tester.pumpAndSettle();

    expect(repo.appels.map((a) => a.page).toList(), [0, 1]);
    expect(find.text('Ahmed Nasri'), findsOneWidget);
  });
}
