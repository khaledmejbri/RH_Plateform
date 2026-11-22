import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/theme/app_theme.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_models.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_repository.dart';
import 'package:rh_mobile_app/features/workflow/presentation/tache_detail_screen.dart';

class _AppelDecision {
  _AppelDecision({
    required this.tacheId,
    required this.action,
    required this.commentaire,
    required this.version,
    required this.cleIdempotence,
  });

  final String tacheId;
  final String action;
  final String? commentaire;
  final int version;
  final String cleIdempotence;
}

class _FauxWorkflowRepo extends WorkflowRepository {
  _FauxWorkflowRepo({
    required this.detail,
    this.onDecider,
    this.onComplement,
  }) : super(Dio());

  final TacheDetail detail;
  final Future<DecisionResult> Function(_AppelDecision appel)? onDecider;
  final Future<void> Function(String objetType, String objetId, String commentaire)?
      onComplement;

  final List<_AppelDecision> appelsDecision = [];
  int appelsComplement = 0;

  @override
  Future<TacheDetail> detailTache(String tacheId) async => detail;

  @override
  Future<CompteurTaches> compteurMesTaches() async =>
      const CompteurTaches(aTraiter: 1, enRetard: 0);

  @override
  Future<DecisionResult> deciderTache({
    required String tacheId,
    required String action,
    String? commentaire,
    required int version,
    required String cleIdempotence,
  }) async {
    final appel = _AppelDecision(
      tacheId: tacheId,
      action: action,
      commentaire: commentaire,
      version: version,
      cleIdempotence: cleIdempotence,
    );
    appelsDecision.add(appel);
    if (onDecider != null) return onDecider!(appel);
    return DecisionResult(enCours: false, tacheId: tacheId, decision: action);
  }

  @override
  Future<void> envoyerComplement({
    required String objetType,
    required String objetId,
    required String tacheId,
    required String commentaire,
    required int version,
    required String cleIdempotence,
  }) async {
    appelsComplement++;
    if (onComplement != null) {
      await onComplement!(objetType, objetId, commentaire);
    }
  }
}

TacheDetail _detail({
  String type = 'VALIDATION',
  String statut = 'A_TRAITER',
  List<String> actions = const ['APPROUVER', 'REFUSER', 'RENVOYER'],
  String? motifRenvoi,
}) {
  return TacheDetail(
    tacheId: 't1',
    instanceId: 'i1',
    type: type,
    statut: statut,
    libelle: type == 'COMPLEMENT' ? 'Complément demandé' : 'Validation manager',
    objetType: 'CONGE',
    objetLibelle: 'Congé annuel',
    objetNumero: 'DEM-202611-0001',
    objetId: '00000000-0000-4000-8000-000000000001',
    demandeurNom: 'Sarra Khemiri',
    echeanceLe: '2026-11-28T10:00:00Z',
    enRetard: true,
    actionsPermises: actions,
    commentaireObligatoire: false,
    version: 4,
    resume: const {'affichage_periode': 'du 01/12/2026 au 03/12/2026'},
    suivi: SuiviInstance(
      instanceId: 'i1',
      objetType: 'CONGE',
      objetId: '00000000-0000-4000-8000-000000000001',
      modeleVersionNumero: 1,
      statut: 'EN_COURS',
      etapes: [
        EtapeSuivi(
          code: 'E1',
          libelle: 'Validation manager',
          statut: motifRenvoi != null ? 'TERMINEE' : 'ACTIVE',
          intituleIntervenants: 'Manager du service',
          resultat: motifRenvoi != null ? 'RENVOYEE' : null,
          motif: motifRenvoi,
        ),
        const EtapeSuivi(
          code: 'E2',
          libelle: 'Validation RH',
          statut: 'A_VENIR',
          intituleIntervenants: 'Ressources humaines',
        ),
      ],
    ),
  );
}

Widget _app(_FauxWorkflowRepo repo) {
  return ProviderScope(
    overrides: [
      workflowRepositoryProvider.overrideWithValue(repo),
    ],
    child: MaterialApp(
      theme: AppTheme.light(),
      home: const TacheDetailScreen(tacheId: 't1'),
    ),
  );
}

DioException _dioErreur(int statusCode, String message, {String? code}) {
  final req = RequestOptions(path: '/api/rh/v1/workflow/taches/t1/decision');
  return DioException(
    requestOptions: req,
    response: Response<dynamic>(
      requestOptions: req,
      statusCode: statusCode,
      data: {
        if (code != null) 'code': code,
        'message': message,
      },
    ),
    type: DioExceptionType.badResponse,
  );
}

void main() {
  testWidgets(
      'barre fixe visible sans défilement ni débordement sur Size(360, 560) avec 3 actions',
      (tester) async {
    tester.view.physicalSize = const Size(360, 560);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repo = _FauxWorkflowRepo(detail: _detail());
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Approuver'), findsOneWidget);
    expect(find.text('Refuser'), findsOneWidget);
    expect(find.text('Renvoyer'), findsOneWidget);

    for (final label in ['Approuver', 'Refuser', 'Renvoyer']) {
      final rect = tester.getRect(find.text(label));
      expect(rect.bottom, lessThanOrEqualTo(560));
      expect(rect.right, lessThanOrEqualTo(360));
    }
  });

  testWidgets('masque la barre d’actions si statut != A_TRAITER même avec actions_permises',
      (tester) async {
    final repo = _FauxWorkflowRepo(
      detail: _detail(
        statut: 'TRAITEE',
        actions: const ['APPROUVER', 'REFUSER'],
      ),
    );
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Approuver'), findsNothing);
    expect(find.text('Refuser'), findsNothing);
  });

  testWidgets(
      'motif < 5 car. désactivé pour REFUSER, affiche 422 exact sans fermer et réutilise cle_idempotence',
      (tester) async {
    var tentative = 0;
    final repo = _FauxWorkflowRepo(
      detail: _detail(),
      onDecider: (appel) async {
        tentative++;
        if (tentative == 1) {
          throw _dioErreur(422, 'Le motif doit être plus précis (règle serveur).');
        }
        return DecisionResult(enCours: false, tacheId: appel.tacheId, decision: appel.action);
      },
    );

    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Refuser'));
    await tester.pumpAndSettle();

    expect(find.text('0 / 500 caractères'), findsOneWidget);

    // Bouton confirmer (FilledButton) désactivé sous 5 caractères
    final boutonFeuille = find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(FilledButton),
    );
    FilledButton confirmerBtn() => tester.widget<FilledButton>(boutonFeuille);
    expect(confirmerBtn().onPressed, isNull);

    await tester.enterText(find.byType(TextField), 'Non');
    await tester.pump();
    expect(find.text('3 / 500 caractères'), findsOneWidget);
    expect(confirmerBtn().onPressed, isNull);

    await tester.enterText(find.byType(TextField), 'Motif complet valide');
    await tester.pump();
    expect(confirmerBtn().onPressed, isNotNull);

    // 1er essai -> 422 : reste ouvert avec message serveur exact
    await tester.tap(boutonFeuille);
    await tester.pumpAndSettle();

    expect(find.text('Le motif doit être plus précis (règle serveur).'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);

    // 2e essai -> 200 : réutilise la même cle_idempotence
    await tester.tap(boutonFeuille);
    await tester.pumpAndSettle();

    expect(repo.appelsDecision.length, 2);
    expect(
      repo.appelsDecision[0].cleIdempotence,
      repo.appelsDecision[1].cleIdempotence,
    );
    expect(find.text('Décision enregistrée.'), findsOneWidget);
  });

  testWidgets('gère 202 Décision en cours d’enregistrement', (tester) async {
    final repo202 = _FauxWorkflowRepo(
      detail: _detail(actions: const ['APPROUVER']),
      onDecider: (_) async => const DecisionResult.enCoursResult(),
    );
    await tester.pumpWidget(_app(repo202));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Approuver'));
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(FilledButton),
    ));
    await tester.pumpAndSettle();
    expect(find.text('Décision en cours d’enregistrement'), findsOneWidget);
  });

  testWidgets('gère 409 Déjà traitée par quelqu’un d’autre', (tester) async {
    final repo409 = _FauxWorkflowRepo(
      detail: _detail(actions: const ['APPROUVER']),
      onDecider: (_) async =>
          throw _dioErreur(409, 'Conflit', code: 'TACHE_DEJA_TRAITEE'),
    );
    await tester.pumpWidget(_app(repo409));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Approuver'));
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(FilledButton),
    ));
    await tester.pumpAndSettle();
    expect(find.text('Déjà traitée par quelqu’un d’autre.'), findsOneWidget);
  });

  testWidgets('mode COMPLEMENT affiche la consigne, Voir la demande et J’ai complété',
      (tester) async {
    final repo = _FauxWorkflowRepo(
      detail: _detail(
        type: 'COMPLEMENT',
        actions: const ['FAIT'],
        motifRenvoi: 'Merci de préciser les dates exactes.',
      ),
    );
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Merci de préciser les dates exactes.'), findsOneWidget);
    expect(find.text('Voir la demande'), findsOneWidget);
    expect(find.text('J’ai complété'), findsOneWidget);

    await tester.tap(find.text('J’ai complété'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'Dates confirmées avec mon manager.');
    await tester.pump();
    await tester.tap(find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(FilledButton),
    ));
    await tester.pumpAndSettle();

    expect(repo.appelsComplement, 1);
    expect(find.text('Complément envoyé.'), findsOneWidget);
  });

  testWidgets('frise de suivi n’expose jamais les noms d’un pool', (tester) async {
    final detailDepuisJson = TacheDetail.fromJson({
      'tache_id': 't1',
      'instance_id': 'i1',
      'type': 'VALIDATION',
      'statut': 'A_TRAITER',
      'libelle': 'Validation RH',
      'objet_type': 'CONGE',
      'objet_libelle': 'Congé annuel',
      'objet_id': '00000000-0000-4000-8000-000000000001',
      'en_retard': false,
      'actions_permises': ['APPROUVER'],
      'version': 1,
      'resume': {},
      'suivi': {
        'instance_id': 'i1',
        'objet_type': 'CONGE',
        'objet_id': '00000000-0000-4000-8000-000000000001',
        'modele_version_numero': 1,
        'statut': 'EN_COURS',
        'etapes': [
          {
            'code': 'RH',
            'libelle': 'Validation RH',
            'statut': 'ACTIVE',
            'intitule_intervenants': 'Équipe Ressources humaines',
            'candidats': ['Candidat Secret 1', 'Candidat Secret 2'],
          },
        ],
      },
    });
    final repo = _FauxWorkflowRepo(detail: detailDepuisJson);
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.text('Équipe Ressources humaines'), findsOneWidget);
    expect(find.textContaining('Candidat Secret'), findsNothing);
  });

  testWidgets('tâche TITULAIRE n’affiche pas Valeur non reconnue et frise ANNULEE masque Étape en cours',
      (tester) async {
    final detailAnnulee = TacheDetail.fromJson({
      'tache_id': 't1',
      'instance_id': 'i1',
      'type': 'VALIDATION',
      'statut': 'TRAITEE',
      'libelle': 'Validation manager',
      'objet_type': 'ORDRE_MISSION',
      'objet_libelle': 'Ordre de mission',
      'objet_id': '00000000-0000-4000-8000-000000000001',
      'en_retard': false,
      'origine': 'TITULAIRE',
      'actions_permises': const [],
      'version': 2,
      'resume': const {},
      'suivi': {
        'instance_id': 'i1',
        'objet_type': 'ORDRE_MISSION',
        'objet_id': '00000000-0000-4000-8000-000000000001',
        'modele_version_numero': 1,
        'statut': 'ANNULEE',
        'etape_courante': {
          'code': 'MGR',
          'libelle': 'Validation manager',
        },
        'etapes': const [
          {
            'code': 'MGR',
            'libelle': 'Validation manager',
            'statut': 'ANNULEE',
          },
        ],
      },
    });
    final repo = _FauxWorkflowRepo(detail: detailAnnulee);
    await tester.pumpWidget(_app(repo));
    await tester.pumpAndSettle();

    expect(find.textContaining('Valeur non reconnue'), findsNothing);
    expect(find.textContaining('Étape en cours'), findsNothing);
    expect(find.text('Annulée'), findsAtLeastNWidgets(1));
  });
}
