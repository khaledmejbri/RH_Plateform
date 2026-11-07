import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/widgets/confirmation_action.dart';

void main() {
  /// Affiche un bouton qui ouvre la confirmation ; compte les actions exécutées après « Confirmer ».
  Future<({List<bool> reponses, List<int> actions})> monter(WidgetTester tester) async {
    final reponses = <bool>[];
    final actions = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () async {
                  final ok = await confirmerAction(
                    context,
                    action: 'Annuler cette demande ?',
                    element: 'Congé annuel — DEM-202611-0003',
                  );
                  reponses.add(ok);
                  if (ok) actions.add(1);
                },
                child: const Text('Ouvrir'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Ouvrir'));
    await tester.pumpAndSettle();
    return (reponses: reponses, actions: actions);
  }

  testWidgets('affiche « Êtes-vous sûr ? » et le nom de l’élément', (tester) async {
    await monter(tester);
    expect(find.text('Êtes-vous sûr ?'), findsOneWidget);
    expect(find.text('Congé annuel — DEM-202611-0003'), findsOneWidget);
  });

  testWidgets('Annuler renvoie false et n’exécute aucune action', (tester) async {
    final r = await monter(tester);
    await tester.tap(find.text('Non, revenir'));
    await tester.pumpAndSettle();
    expect(r.reponses, [false]);
    expect(r.actions, isEmpty);
    expect(find.text('Êtes-vous sûr ?'), findsNothing);
  });

  testWidgets('Confirmer renvoie true et exécute l’action', (tester) async {
    final r = await monter(tester);
    await tester.tap(find.text('Confirmer'));
    await tester.pumpAndSettle();
    expect(r.reponses, [true]);
    expect(r.actions, [1]);
  });
}
