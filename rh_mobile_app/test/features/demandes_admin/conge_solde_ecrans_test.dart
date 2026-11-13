import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/core/libelles/libelles.dart';
import 'package:rh_mobile_app/core/libelles/libelles_service.dart';
import 'package:rh_mobile_app/features/conges/data/solde_models.dart';
import 'package:rh_mobile_app/features/conges/data/solde_repository.dart';
import 'package:rh_mobile_app/features/demandes_admin/data/demande_admin_models.dart';
import 'package:rh_mobile_app/features/demandes_admin/presentation/demande_admin_detail_screen.dart';
import 'package:rh_mobile_app/features/demandes_admin/presentation/demande_conge_create_screen.dart';

const _types = [
  ParametreValeur(code: 'ANNUEL', libelle: 'Congé annuel', ordre: 1),
  ParametreValeur(code: 'MARIAGE', libelle: 'Congé de mariage', ordre: 2),
];

class _FauxRepo extends SoldeRepository {
  _FauxRepo(this.simulation) : super(Dio());

  final SimulationConge simulation;
  int appels = 0;
  String? dernierPremier;

  @override
  Future<SimulationConge> simuler({
    required DateTime dateDebut,
    required DateTime dateFin,
    required String periodePremierJour,
    required String periodeDernierJour,
  }) async {
    appels++;
    dernierPremier = periodePremierJour;
    return simulation;
  }
}

Future<void> _choisirType(WidgetTester tester, String libelle) async {
  await tester.ensureVisible(find.byType(DropdownButtonFormField<String>).first);
  await tester.tap(find.byType(DropdownButtonFormField<String>).first);
  await tester.pumpAndSettle();
  await tester.tap(find.text(libelle).last);
  await tester.pumpAndSettle();
}

Future<void> _choisirDates(WidgetTester tester) async {
  for (var i = 0; i < 2; i++) {
    await tester.tap(find.text('Sélectionner').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
  }
}

Future<_FauxRepo> _ouvrirFormulaire(WidgetTester tester, SimulationConge simulation) async {
  tester.view.physicalSize = const Size(800, 3000);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
  final repo = _FauxRepo(simulation);
  await tester.pumpWidget(ProviderScope(
    overrides: [
      typesCongeActifsProvider.overrideWith((ref) async => _types),
      soldeRepositoryProvider.overrideWithValue(repo),
    ],
    child: const MaterialApp(home: DemandeCongeCreateScreen()),
  ));
  await tester.pumpAndSettle();
  return repo;
}

FilledButton _envoi(WidgetTester tester) =>
    tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Soumettre la demande'));

Widget _detail(Map<String, dynamic> j) => ProviderScope(
      overrides: [
        demandeAdminDetailProvider('d1').overrideWith((ref) async => j),
        demandeAdminSuiviProvider('d1').overrideWith(
          (ref) async => DemandeAdminSuivi(id: 'd1', typeDemande: 'CONGE', statut: 'x', etapeSuperieurRequise: true, etapes: []),
        ),
        demandeAdminHistoriqueProvider('d1').overrideWith((ref) async => <WorkflowHistoryItem>[]),
      ],
      child: const MaterialApp(home: DemandeAdminDetailScreen(id: 'd1')),
    );

Map<String, dynamic> _demande(String statut, [Map<String, dynamic> extra = const {}]) => {
      'identifiant': 'd1',
      'type_demande': 'CONGE',
      'statut': statut,
      'numero': 'DEM-202612-0001',
      'periode_debut': '2026-12-14',
      'periode_fin': '2026-12-16',
      'contenu': {'type_conge': 'ANNUEL'},
      ...extra,
    };

/// Écran haut : la ListView ne construit pas ce qui sort de la zone visible.
void _testHaut(String nom, Future<void> Function(WidgetTester) corps) {
  testWidgets(nom, (tester) async {
    tester.view.physicalSize = const Size(800, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await corps(tester);
  });
}

void main() {
  group('formulaire de congé annuel : demi-journées et simulation', () {
    testWidgets('un seul jour : trois choix, simulation affichée et envoi actif', (tester) async {
      final repo = await _ouvrirFormulaire(
        tester,
        const SimulationConge(joursDecomptes: 1, disponible: 12.5, disponibleApres: 11.5),
      );
      await _choisirType(tester, 'Congé annuel');
      await _choisirDates(tester);

      expect(find.text('Période'), findsWidgets);
      expect(repo.appels, greaterThan(0));
      expect(find.text('1 jour ouvré, solde après : 11,5 jours'), findsOneWidget);
      await tester.ensureVisible(find.text('Soumettre la demande'));
      expect(_envoi(tester).onPressed, isNotNull);
    });

    testWidgets('blocage de la simulation : message du serveur et envoi inactif', (tester) async {
      await _ouvrirFormulaire(
        tester,
        const SimulationConge(
          joursDecomptes: 3.5,
          disponible: 3,
          disponibleApres: -0.5,
          blocage: BlocageSimulation(
            code: 'SOLDE_INSUFFISANT',
            libelle: 'Solde insuffisant : 3,5 jours demandés, 3 jours disponibles',
          ),
        ),
      );
      await _choisirType(tester, 'Congé annuel');
      await _choisirDates(tester);

      expect(find.textContaining('Solde insuffisant'), findsOneWidget);
      await tester.ensureVisible(find.text('Soumettre la demande'));
      expect(_envoi(tester).onPressed, isNull);
    });

    testWidgets('autre type : ni périodes ni simulation', (tester) async {
      final repo = await _ouvrirFormulaire(
        tester,
        const SimulationConge(joursDecomptes: 1, disponible: 1, disponibleApres: 0),
      );
      await _choisirType(tester, 'Congé de mariage');
      await _choisirDates(tester);

      expect(repo.appels, 0);
      expect(find.text('Période'), findsNothing);
      await tester.ensureVisible(find.text('Soumettre la demande'));
      expect(_envoi(tester).onPressed, isNotNull);
    });
  });

  group('détail d’une demande', () {
    _testHaut('congé approuvé : aucun bouton Annuler, mention de contacter les RH', (tester) async {
      await tester.pumpWidget(_detail(_demande('APPROUVEE', {'jours_decomptes': 2.0})));
      await tester.pumpAndSettle();

      expect(find.text('Annuler ma demande'), findsNothing);
      expect(find.text('Pour annuler un congé approuvé, contactez les RH.'), findsOneWidget);
      expect(find.text('Jours décomptés : 2 jours'), findsOneWidget);
    });

    _testHaut('valideur : pas de bouton « Annuler ma demande »', (tester) async {
      await tester.pumpWidget(_detail(_demande('EN_VALIDATION_SUPERIEUR', {'peut_annuler': false})));
      await tester.pumpAndSettle();
      expect(find.text('Annuler ma demande'), findsNothing);
    });

    _testHaut('champ peut_annuler absent : bouton masqué', (tester) async {
      await tester.pumpWidget(_detail(_demande('EN_VALIDATION_SUPERIEUR')));
      await tester.pumpAndSettle();
      expect(find.text('Annuler ma demande'), findsNothing);
    });

    _testHaut('demandeur, demande en cours de validation : « Annuler ma demande » proposé', (tester) async {
      await tester.pumpWidget(_detail(_demande('EN_VALIDATION_SUPERIEUR', {'peut_annuler': true})));
      await tester.pumpAndSettle();

      expect(find.text('Annuler ma demande'), findsOneWidget);
      expect(find.textContaining('contactez les RH'), findsNothing);
    });

    _testHaut('annulée après approbation : motif, auteur et recrédit visibles', (tester) async {
      await tester.pumpWidget(_detail(_demande('ANNULEE', {
        'annulee_apres_approbation': true,
        'motif_annulation': 'Erreur de saisie',
        'annule_par_nom': 'Amel Trabelsi',
        'jours_recredites': 2.0,
      })));
      await tester.pumpAndSettle();

      expect(find.text('Annulée après approbation'), findsWidgets);
      expect(find.textContaining('Motif : Erreur de saisie'), findsOneWidget);
      expect(find.textContaining('par Amel Trabelsi'), findsOneWidget);
      expect(find.textContaining('2 jours recrédités'), findsOneWidget);
      expect(find.text('Annuler ma demande'), findsNothing);
    });

    _testHaut('vue du valideur : solde acquis dont part réservée par d’autres demandes', (tester) async {
      await tester.pumpWidget(_detail(_demande('EN_VALIDATION_SUPERIEUR', {
        'solde_disponible_demandeur': 10.0,
        'solde_reserve_autres_demandes': 3.0,
        'solde_apres_demande': 8.0,
      })));
      await tester.pumpAndSettle();

      expect(find.text('Solde acquis : 10 jours, dont 3 jours réservés par d’autres demandes'), findsOneWidget);
      expect(find.text('Solde après cette demande : 8 jours'), findsOneWidget);
    });

    _testHaut('vue du demandeur : aucun champ du décideur', (tester) async {
      await tester.pumpWidget(_detail(_demande('EN_VALIDATION_SUPERIEUR')));
      await tester.pumpAndSettle();
      expect(find.textContaining('Solde acquis'), findsNothing);
    });
  });
}
