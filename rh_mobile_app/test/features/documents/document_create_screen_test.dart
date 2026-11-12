import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/core/libelles/libelles_service.dart';
import 'package:rh_mobile_app/features/documents/presentation/document_create_screen.dart';

Widget _ecran(Override typesOverride) => ProviderScope(
      overrides: [typesOverride],
      child: const MaterialApp(home: DocumentCreateScreen()),
    );

void main() {
  testWidgets('liste vide : « Aucun type de document disponible » et envoi inactif', (tester) async {
    await tester.pumpWidget(_ecran(typesDocumentActifsProvider.overrideWith((ref) async => <ParametreValeur>[])));
    await tester.pumpAndSettle();

    expect(find.text('Aucun type de document disponible'), findsOneWidget);
    final bouton = tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Envoyer la demande'));
    expect(bouton.onPressed, isNull);
  });

  testWidgets('échec de lecture : erreur et Réessayer, aucun type proposé', (tester) async {
    var appels = 0;
    await tester.pumpWidget(_ecran(typesDocumentActifsProvider.overrideWith((ref) async {
      appels++;
      if (appels == 1) {
        throw DioException(
          requestOptions: RequestOptions(path: '/x'),
          type: DioExceptionType.connectionError,
        );
      }
      return const [ParametreValeur(code: 'ATTESTATION_STAGE', libelle: 'Attestation de stage', ordre: 1)];
    })));
    await tester.pumpAndSettle();

    expect(find.byType(DropdownButtonFormField<String>), findsNothing);
    expect(find.text('Réessayer'), findsOneWidget);

    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();
    expect(find.byType(DropdownButtonFormField<String>), findsOneWidget);
    expect(appels, 2);
  });

  testWidgets('types servis par l’API, aucune liste figée', (tester) async {
    await tester.pumpWidget(_ecran(typesDocumentActifsProvider.overrideWith((ref) async => const [
          ParametreValeur(code: 'ATTESTATION_STAGE', libelle: 'Attestation de stage', ordre: 1),
          ParametreValeur(code: 'ATTESTATION_TRAVAIL', libelle: 'Attestation de travail', ordre: 2),
        ])));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    expect(find.text('Attestation de stage'), findsOneWidget);
    expect(find.text('Bulletin de paie'), findsNothing);
  });
}
