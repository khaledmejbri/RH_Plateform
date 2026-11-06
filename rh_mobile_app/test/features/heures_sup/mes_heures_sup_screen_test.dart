import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/heures_sup/data/heures_sup_models.dart';
import 'package:rh_mobile_app/features/heures_sup/presentation/mes_heures_sup_screen.dart';
import 'package:rh_mobile_app/features/heures_sup/providers/heures_sup_providers.dart';

Widget app(Override o) =>
    ProviderScope(overrides: [o], child: const MaterialApp(home: MesHeuresSupScreen()));

void main() {
  testWidgets('erreur serveur : message d\'erreur + Réessayer, jamais « aucune heure »', (tester) async {
    await tester.pumpWidget(app(mesHeuresSupProvider.overrideWith((ref) async {
      throw DioException(
        requestOptions: RequestOptions(path: '/heures-sup/moi'),
        response: Response(requestOptions: RequestOptions(path: '/heures-sup/moi'), statusCode: 403),
        type: DioExceptionType.badResponse,
      );
    })));
    await tester.pumpAndSettle();
    expect(find.text('Réessayer'), findsOneWidget);
    expect(find.textContaining('Aucune heure'), findsNothing);
  });

  testWidgets('réseau coupé : message hors ligne + Réessayer', (tester) async {
    await tester.pumpWidget(app(mesHeuresSupProvider.overrideWith((ref) async {
      throw DioException(
        requestOptions: RequestOptions(path: '/heures-sup/moi'),
        type: DioExceptionType.connectionError,
      );
    })));
    await tester.pumpAndSettle();
    expect(find.textContaining('hors ligne'), findsOneWidget);
    expect(find.text('Réessayer'), findsOneWidget);
    expect(find.textContaining('Aucune heure'), findsNothing);
  });

  testWidgets('liste vide réelle : état vide', (tester) async {
    await tester.pumpWidget(app(mesHeuresSupProvider.overrideWith((ref) async => const MesHeuresSup())));
    await tester.pumpAndSettle();
    expect(find.textContaining('Aucune heure sup'), findsOneWidget);
    expect(find.text('Réessayer'), findsNothing);
  });
}
