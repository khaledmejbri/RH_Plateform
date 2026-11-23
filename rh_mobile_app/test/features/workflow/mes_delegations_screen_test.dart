import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/format/formats.dart';
import 'package:rh_mobile_app/core/storage/secure_token_storage.dart';
import 'package:rh_mobile_app/features/auth/providers/collaborateur_notifier.dart';
import 'package:rh_mobile_app/features/organigramme/data/organigramme_models.dart';
import 'package:rh_mobile_app/features/organigramme/data/organigramme_repository.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_models.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_repository.dart';
import 'package:rh_mobile_app/features/workflow/presentation/mes_delegations_screen.dart';

class _FauxCollabNotifier extends CollaborateurNotifier {
  _FauxCollabNotifier() : super(Dio(), SecureTokenStorage()) {
    state = AsyncValue.data(
      CollaborateurInfo(
        identifiant: 'moi-id',
        name: 'Belhadj',
        prenom: 'Omar',
      ),
    );
  }
}

class _FauxOrgRepo extends OrganigrammeRepository {
  _FauxOrgRepo() : super(Dio());

  @override
  Future<Organigramme> fetch() async => const Organigramme(
        racines: [
          OrganigrammeNoeud(
            identifiant: 'n1',
            code: 'DIR',
            libelle: 'Direction',
            typeNoeud: 'DIRECTION',
            actif: true,
            manager: OrganigrammeMembre(
              identifiant: 'moi-id',
              matricule: 'M001',
              prenom: 'Omar',
              nom: 'Belhadj',
            ),
            membres: [
              OrganigrammeMembre(
                identifiant: 'c-nadia',
                matricule: 'M002',
                prenom: 'Nadia',
                nom: 'Ayari',
                posteLibelle: 'Responsable RH',
              ),
            ],
            enfants: [],
          ),
        ],
      );
}

class _FauxWorkflowRepo extends WorkflowRepository {
  _FauxWorkflowRepo({
    this.donnees = const [],
    this.recues = const [],
    this.onCreer,
  }) : super(Dio());

  List<DelegationItem> donnees;
  List<DelegationItem> recues;
  final Future<DelegationItem> Function(String delegueId, DateTime du, DateTime au, String? motif)?
      onCreer;

  final List<String> annulations = [];
  int creations = 0;

  @override
  Future<List<DelegationItem>> delegationsMoi({
    String role = 'DELEGANT',
    bool? actives,
  }) async {
    return role == 'DELEGUE' ? recues : donnees;
  }

  @override
  Future<DelegationItem> creerDelegation({
    required String delegueId,
    required DateTime du,
    required DateTime au,
    List<String>? objetTypes,
    String? motif,
  }) async {
    creations++;
    if (onCreer != null) {
      return onCreer!(delegueId, du, au, motif);
    }
    return DelegationItem(
      id: 'del-nouv',
      delegantId: 'moi-id',
      delegantNom: 'Omar Belhadj',
      delegueId: delegueId,
      delegueNom: 'Nadia Ayari',
      du: formatDateApi(du),
      au: formatDateApi(au),
      objetTypes: objetTypes ?? const [],
      motif: motif,
      origine: 'MANUELLE',
      active: true,
    );
  }

  @override
  Future<void> annulerDelegation(String delegationId) async {
    annulations.add(delegationId);
  }
}

Widget _app({
  required _FauxWorkflowRepo workflowRepo,
}) {
  return ProviderScope(
    overrides: [
      workflowRepositoryProvider.overrideWithValue(workflowRepo),
      organigrammeRepositoryProvider.overrideWithValue(_FauxOrgRepo()),
      collaborateurNotifierProvider.overrideWith((_) => _FauxCollabNotifier()),
    ],
    child: MaterialApp(
      theme: ThemeData(useMaterial3: true),
      locale: const Locale('fr', 'FR'),
      supportedLocales: const [Locale('fr', 'FR'), Locale('fr')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: const MesDelegationsScreen(),
    ),
  );
}

void main() {
  testWidgets('affiche les onglets Données / Reçues et annule avec confirmation',
      (tester) async {
    final repo = _FauxWorkflowRepo(
      donnees: const [
        DelegationItem(
          id: 'del-1',
          delegantId: 'moi-id',
          delegantNom: 'Omar Belhadj',
          delegueId: 'c-nadia',
          delegueNom: 'Nadia Ayari',
          du: '2026-11-20',
          au: '2026-11-29',
          objetTypes: ['CONGE'],
          motif: 'Déplacement',
          origine: 'MANUELLE',
          active: true,
        ),
      ],
      recues: const [
        DelegationItem(
          id: 'del-2',
          delegantId: 'c-ali',
          delegantNom: 'Ali Mansouri',
          delegueId: 'moi-id',
          delegueNom: 'Omar Belhadj',
          du: '2026-11-21',
          au: '2026-11-25',
          objetTypes: [],
          origine: 'AUTO_CONGE',
          active: true,
        ),
      ],
    );

    await tester.pumpWidget(_app(workflowRepo: repo));
    await tester.pumpAndSettle();

    expect(find.text('Délégué à : Nadia Ayari'), findsOneWidget);
    expect(find.text('Active'), findsOneWidget);

    await tester.tap(find.text('Annuler'));
    await tester.pumpAndSettle();
    expect(find.text('Êtes-vous sûr ?'), findsOneWidget);

    await tester.tap(find.text('Annuler la délégation'));
    await tester.pumpAndSettle();
    expect(repo.annulations, ['del-1']);
    expect(find.text('Délégation annulée.'), findsOneWidget);

    await tester.tap(find.text('Reçues'));
    await tester.pumpAndSettle();
    expect(find.text('Délégant : Ali Mansouri'), findsOneWidget);
    expect(find.text('Origine : Automatique (congé)'), findsOneWidget);
    expect(find.textContaining('Valeur non reconnue'), findsNothing);
  });

  testWidgets('création : exclut soi-même, recherche organigramme, période 90 j et 409',
      (tester) async {
    final req = RequestOptions(path: '/delegations/moi');
    var essai = 0;
    final repo = _FauxWorkflowRepo(
      onCreer: (delegueId, du, au, motif) async {
        essai++;
        if (essai == 1) {
          throw DioException(
            requestOptions: req,
            response: Response<dynamic>(
              requestOptions: req,
              statusCode: 409,
              data: {
                'code': 'DELEGATION_CHEVAUCHEMENT',
                'message': 'Une délégation chevauche déjà cette période.',
              },
            ),
            type: DioExceptionType.badResponse,
          );
        }
        return DelegationItem(
          id: 'del-ok',
          delegantId: 'moi-id',
          delegantNom: 'Omar Belhadj',
          delegueId: delegueId,
          delegueNom: 'Nadia Ayari',
          du: formatDateApi(du),
          au: formatDateApi(au),
          objetTypes: const [],
          motif: motif,
          origine: 'MANUELLE',
          active: true,
        );
      },
    );

    await tester.pumpWidget(_app(workflowRepo: repo));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('delegation-nouvelle-fab')));
    await tester.pumpAndSettle();

    // Soi-même (« Omar Belhadj ») ne doit pas apparaître dans la liste des délégués
    expect(find.text('Omar Belhadj'), findsNothing);
    expect(find.text('Nadia Ayari'), findsOneWidget);
    // PLAINTE hors catalogue serveur ne doit pas être proposé
    expect(find.text('Plainte / réclamation'), findsNothing);

    await tester.enterText(find.byKey(const Key('delegation-recherche')), 'Nadia');
    await tester.pump();
    await tester.tap(find.text('Nadia Ayari'));
    await tester.pumpAndSettle();

    // Sélection de la période via showDateRangePicker
    await tester.tap(find.byKey(const Key('delegation-bouton-periode')));
    await tester.pumpAndSettle();

    final jour = '${aujourdhuiTunis().day}';
    await tester.tap(find.text(jour).first);
    await tester.pump();
    await tester.tap(find.text(jour).first);
    await tester.pump();
    await tester.tap(
      find.byWidgetPredicate(
        (w) => w is Text && (w.data ?? '').toUpperCase() == 'VALIDER',
      ),
    );
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byKey(const Key('delegation-motif')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('delegation-motif')), 'Congés');
    await tester.pump();

    // 1er essai -> 409 chevauchement affiché sans fermer la feuille
    await tester.ensureVisible(find.byKey(const Key('delegation-soumettre')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('delegation-soumettre')));
    await tester.pumpAndSettle();

    expect(find.text('Une délégation chevauche déjà cette période.'), findsOneWidget);

    // 2e essai -> 201 OK
    await tester.ensureVisible(find.byKey(const Key('delegation-soumettre')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('delegation-soumettre')));
    await tester.pumpAndSettle();

    expect(repo.creations, 2);
    expect(find.text('Délégation créée.'), findsOneWidget);
  });
}
