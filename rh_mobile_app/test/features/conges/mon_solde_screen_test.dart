import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/auth/presentation/profile_screen.dart';
import 'package:rh_mobile_app/features/conges/data/solde_models.dart';
import 'package:rh_mobile_app/features/conges/data/solde_repository.dart';
import 'package:rh_mobile_app/features/conges/presentation/mon_solde_screen.dart';

class _FauxRepo extends SoldeRepository {
  _FauxRepo({this.solde, this.pages = const [], this.erreur}) : super(Dio());

  final SoldeConge? solde;
  final List<PageMouvements> pages;
  final Object? erreur;
  final List<String?> typesDemandes = [];

  @override
  Future<SoldeConge> monSolde() async {
    if (erreur != null) throw erreur!;
    return solde!;
  }

  @override
  Future<PageMouvements> mesMouvements({int page = 0, int taille = 20, String? type}) async {
    if (erreur != null) throw erreur!;
    typesDemandes.add(type);
    return pages.isEmpty ? const PageMouvements(contenu: [], page: 0, totalPages: 0, totalElements: 0) : pages[page];
  }
}

DioException _reseau() =>
    DioException(requestOptions: RequestOptions(path: '/x'), type: DioExceptionType.connectionError);

MouvementSolde _m(String type, String libelle, double nombre, {String? motif, String? auteur, String? mois, String? num}) =>
    MouvementSolde(
      type: type,
      typeLibelle: libelle,
      nombre: nombre,
      soldeApres: 10,
      motif: motif,
      auteurNom: auteur,
      moisAcquisitionLibelle: mois,
      demandeNumero: num,
      demandeId: num == null ? null : 'id-$num',
    );

Widget _app(_FauxRepo repo, Widget home) => ProviderScope(
      overrides: [soldeRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp(home: home),
    );

void main() {
  testWidgets('affiche disponible, acquis, réservé et les mouvements formatés', (tester) async {
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    final repo = _FauxRepo(
      solde: const SoldeConge(acquis: 12.5, reserve: 2, disponible: 10.5),
      pages: [
        PageMouvements(
          contenu: [
            _m('ACQUISITION', 'Acquisition', 1.5, mois: 'décembre 2026'),
            _m('CONGE_PRIS', 'Congé pris', -5, num: 'DEM-202612-0001'),
            _m('AJUSTEMENT', 'Ajustement', 2, motif: 'Report 2025', auteur: 'Amel Trabelsi'),
          ],
          page: 0,
          totalPages: 1,
          totalElements: 3,
        ),
      ],
    );
    await tester.pumpWidget(_app(repo, const MonSoldeScreen()));
    await tester.pumpAndSettle();

    expect(find.text('10,5 jours'), findsOneWidget);
    expect(find.text('12,5 jours'), findsOneWidget);
    expect(find.text('2 jours'), findsWidgets);
    expect(find.text('Acquisition décembre 2026'), findsOneWidget);
    expect(find.text('+1,5 jour'), findsOneWidget);
    expect(find.text('Congé pris DEM-202612-0001'), findsOneWidget);
    expect(find.text('−5 jours'), findsOneWidget);
    expect(find.text('Report 2025 — Amel Trabelsi'), findsOneWidget);
  });

  testWidgets('aucun mouvement : état vide', (tester) async {
    final repo = _FauxRepo(solde: const SoldeConge(acquis: 0, reserve: 0, disponible: 0));
    await tester.pumpWidget(_app(repo, const MonSoldeScreen()));
    await tester.pumpAndSettle();
    expect(find.text('Aucun mouvement'), findsOneWidget);
  });

  testWidgets('hors ligne : message et Réessayer, jamais « 0 jour »', (tester) async {
    final repo = _FauxRepo(erreur: _reseau());
    await tester.pumpWidget(_app(repo, const MonSoldeScreen()));
    await tester.pumpAndSettle();
    expect(find.text('Réessayer'), findsWidgets);
    expect(find.text('0 jour'), findsNothing);
  });

  testWidgets('le filtre par type relance la lecture avec le type choisi', (tester) async {
    final repo = _FauxRepo(solde: const SoldeConge(acquis: 1, reserve: 0, disponible: 1));
    await tester.pumpWidget(_app(repo, const MonSoldeScreen()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ajustements'));
    await tester.pumpAndSettle();
    expect(repo.typesDemandes.last, 'AJUSTEMENT');
  });

  testWidgets('carte du profil : disponible réel, indicateur puis valeur', (tester) async {
    final repo = _FauxRepo(solde: const SoldeConge(acquis: 12.5, reserve: 0, disponible: 12.5));
    await tester.pumpWidget(_app(repo, const Scaffold(body: CarteJoursConge())));
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('0 jour'), findsNothing);
    await tester.pumpAndSettle();
    expect(find.text('12,5 jours'), findsOneWidget);
  });

  testWidgets('carte du profil : erreur réseau = Réessayer, pas de « 0 »', (tester) async {
    final repo = _FauxRepo(erreur: _reseau());
    await tester.pumpWidget(_app(repo, const Scaffold(body: CarteJoursConge())));
    await tester.pumpAndSettle();
    expect(find.text('Réessayer'), findsOneWidget);
    expect(find.textContaining('0 jour'), findsNothing);
  });
}
