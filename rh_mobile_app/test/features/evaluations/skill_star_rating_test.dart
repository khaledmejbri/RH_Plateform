import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/evaluations/data/evaluation_models.dart';
import 'package:rh_mobile_app/features/evaluations/presentation/skill_star_rating.dart';

Widget _host(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  testWidgets('affiche autant d’étoiles pleines que le niveau', (tester) async {
    await tester.pumpWidget(_host(const SkillStarRating(value: SkillLevel.autonome)));
    expect(find.byIcon(Icons.star_rounded), findsNWidgets(3));
    expect(find.byIcon(Icons.star_outline_rounded), findsNWidgets(2));
    expect(find.text('Autonome'), findsOneWidget);
  });

  testWidgets('toucher la 4e étoile choisit Avancé', (tester) async {
    SkillLevel? choisi;
    await tester.pumpWidget(_host(SkillStarRating(
      value: null,
      onChanged: (l) => choisi = l,
    )));
    expect(find.text('Touchez une étoile'), findsOneWidget);
    await tester.tap(find.byType(InkResponse).at(3));
    expect(choisi, SkillLevel.avance);
  });

  testWidgets('lecture seule : pas de zone cliquable', (tester) async {
    await tester.pumpWidget(_host(const SkillStarRating(value: SkillLevel.expert, size: 18)));
    expect(find.byType(InkResponse), findsNothing);
    expect(find.byIcon(Icons.star_rounded), findsNWidgets(5));
  });
}
