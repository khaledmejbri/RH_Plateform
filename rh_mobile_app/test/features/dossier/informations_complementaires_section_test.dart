import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/dossier/data/champs_perso_models.dart';
import 'package:rh_mobile_app/features/dossier/data/dossier_repository.dart';
import 'package:rh_mobile_app/features/dossier/presentation/informations_complementaires_section.dart';

class _FauxRepo extends DossierRepository {
  _FauxRepo({this.champs = const [], this.echecs = 0}) : super(Dio());

  final List<ChampPersoAffiche> champs;
  int echecs;

  @override
  Future<List<ChampPersoAffiche>> mesChampsPerso() async {
    if (echecs > 0) {
      echecs--;
      throw DioException(requestOptions: RequestOptions(path: '/x'), type: DioExceptionType.connectionError);
    }
    return champs;
  }
}

Widget _app(_FauxRepo repo) => ProviderScope(
      overrides: [dossierRepositoryProvider.overrideWithValue(repo)],
      child: const MaterialApp(
        home: Scaffold(body: SingleChildScrollView(child: InformationsComplementairesSection())),
      ),
    );

void main() {
  testWidgets('affiche les champs renvoyés sans débordement à 360 × 640', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(_FauxRepo(champs: const [
      ChampPersoAffiche(
        code: 'A',
        libelle: 'Taille de tenue avec un libellé particulièrement long pour tester',
        valeurAffichee: 'M',
      ),
      ChampPersoAffiche(code: 'B', libelle: 'Pointure', valeurAffichee: '42,5'),
      ChampPersoAffiche(code: 'C', libelle: 'Casier'),
    ])));
    await tester.pumpAndSettle();
    expect(find.text('Informations complémentaires'), findsOneWidget);
    expect(find.text('42,5'), findsOneWidget);
    expect(find.text('M'), findsOneWidget);
    expect(find.text('—'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('liste vide : section absente', (tester) async {
    await tester.pumpWidget(_app(_FauxRepo()));
    await tester.pumpAndSettle();
    expect(find.text('Informations complémentaires'), findsNothing);
  });

  testWidgets('erreur : message en français puis Réessayer', (tester) async {
    final repo = _FauxRepo(
      champs: const [ChampPersoAffiche(code: 'B', libelle: 'Pointure', valeurAffichee: '42')],
      echecs: 1,
    );
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();
    expect(find.text('Réessayer'), findsOneWidget);
    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();
    expect(find.text('Pointure'), findsOneWidget);
    expect(find.text('Réessayer'), findsNothing);
  });
}
