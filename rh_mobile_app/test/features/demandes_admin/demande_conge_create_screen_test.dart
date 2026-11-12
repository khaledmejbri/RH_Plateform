import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/core/libelles/libelles_service.dart';
import 'package:rh_mobile_app/features/demandes_admin/presentation/demande_conge_create_screen.dart';

const _types = [
  ParametreValeur(code: 'ANNUEL', libelle: 'Congé annuel', ordre: 1),
  ParametreValeur(code: 'MALADIE', libelle: 'Congé maladie', ordre: 2, pieceJointeObligatoire: true),
  ParametreValeur(code: 'MARIAGE', libelle: 'Congé de mariage', ordre: 3),
];

Widget _ecran(Override typesCongeOverride) => ProviderScope(
      overrides: [typesCongeOverride],
      child: const MaterialApp(home: DemandeCongeCreateScreen()),
    );

FilledButton _boutonEnvoi(WidgetTester tester) =>
    tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Soumettre la demande'));

Future<void> _choisir(WidgetTester tester, String libelle) async {
  await tester.ensureVisible(find.byType(DropdownButtonFormField<String>));
  await tester.tap(find.byType(DropdownButtonFormField<String>));
  await tester.pumpAndSettle();
  await tester.tap(find.text(libelle).last);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('propose uniquement les types servis par l’API (aucune liste figée)', (tester) async {
    await tester.pumpWidget(_ecran(typesCongeActifsProvider.overrideWith((ref) async => _types)));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byType(DropdownButtonFormField<String>));
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    expect(find.text('Congé de mariage'), findsOneWidget);
    expect(find.text('Congé maternité'), findsNothing);
    expect(find.text('Congé sans solde'), findsNothing);
  });

  testWidgets('envoi inactif tant qu’aucun type n’est choisi', (tester) async {
    await tester.pumpWidget(_ecran(typesCongeActifsProvider.overrideWith((ref) async => _types)));
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Soumettre la demande'));
    expect(_boutonEnvoi(tester).onPressed, isNull);
  });

  testWidgets('pièce jointe obligatoire : envoi inactif sans pièce, actif pour un type sans pièce', (tester) async {
    await tester.pumpWidget(_ecran(typesCongeActifsProvider.overrideWith((ref) async => _types)));
    await tester.pumpAndSettle();

    await _choisir(tester, 'Congé maladie');
    expect(find.text('Justificatif (obligatoire)'), findsOneWidget);
    await tester.ensureVisible(find.text('Soumettre la demande'));
    expect(_boutonEnvoi(tester).onPressed, isNull);

    await _choisir(tester, 'Congé de mariage');
    expect(find.text('Justificatif (obligatoire)'), findsNothing);
    await tester.ensureVisible(find.text('Soumettre la demande'));
    expect(_boutonEnvoi(tester).onPressed, isNotNull);
  });

  testWidgets('échec de lecture : message d’erreur et Réessayer, aucune valeur proposée', (tester) async {
    var appels = 0;
    await tester.pumpWidget(_ecran(typesCongeActifsProvider.overrideWith((ref) async {
      appels++;
      if (appels == 1) {
        throw DioException(
          requestOptions: RequestOptions(path: '/x'),
          type: DioExceptionType.connectionError,
        );
      }
      return _types;
    })));
    await tester.pumpAndSettle();

    expect(find.text('Impossible de joindre le serveur. Vérifiez votre connexion.'), findsOneWidget);
    expect(find.byType(DropdownButtonFormField<String>), findsNothing);
    expect(find.text('Soumettre la demande'), findsNothing);

    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();
    expect(find.byType(DropdownButtonFormField<String>), findsOneWidget);
    expect(appels, 2);
  });

  testWidgets('aucun type de congé actif : message, pas de formulaire', (tester) async {
    await tester.pumpWidget(_ecran(typesCongeActifsProvider.overrideWith((ref) async => <ParametreValeur>[])));
    await tester.pumpAndSettle();

    expect(find.text('Aucun type de congé disponible'), findsOneWidget);
    expect(find.byType(DropdownButtonFormField<String>), findsNothing);
  });
}
