import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/login_screen.dart';
import '../widgets/garde_role.dart';
import '../widgets/rh_ui.dart';
import '../../features/auth/providers/auth_notifier.dart';
import '../../features/auth/providers/collaborateur_notifier.dart';
import '../../features/demandes_admin/presentation/demande_admin_detail_screen.dart';
import '../../features/demandes_admin/presentation/demandes_admin_list_screen.dart';
import '../../features/demandes_admin/presentation/autorisations_list_screen.dart';
import '../../features/demandes_admin/presentation/demande_conge_create_screen.dart';
import '../../features/demandes_admin/presentation/demande_autorisation_sortie_create_screen.dart';
import '../../features/demandes_admin/presentation/demande_ordre_mission_create_screen.dart';
import '../../features/demandes_admin/presentation/ro_validation_screen.dart';
import '../../features/conges/presentation/mon_solde_screen.dart';
import '../../features/documents/presentation/document_create_screen.dart';
import '../../features/documents/presentation/document_detail_screen.dart';
import '../../features/documents/presentation/documents_list_screen.dart';
import '../../features/facial/data/facial_models.dart';
import '../../features/facial/presentation/facial_consentement_screen.dart';
import '../../features/facial/presentation/facial_enrolement_screen.dart';
import '../../features/facial/presentation/facial_mon_visage_screen.dart';
import '../../features/formations/presentation/formation_create_screen.dart';
import '../../features/formations/presentation/formations_list_screen.dart';
import '../../features/heures_sup/presentation/demande_heures_sup_screen.dart';
import '../../features/heures_sup/presentation/mes_demandes_heures_sup_screen.dart';
import '../../features/heures_sup/presentation/mes_heures_sup_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/notifications/notifications_screen.dart';
import '../../features/evaluations/presentation/evaluations_list_screen.dart';
import '../../features/evaluations/presentation/evaluation_detail_screen.dart';
import '../../features/plaintes/presentation/plainte_create_screen.dart';
import '../../features/plaintes/presentation/plainte_detail_screen.dart';
import '../../features/plaintes/presentation/plaintes_list_screen.dart';
import '../../features/pointage/presentation/pointage_manuel_screen.dart';
import '../../features/pointage/presentation/pointage_screen.dart';
import '../../features/pointage/presentation/presence_sites_admin_screen.dart';
import '../../features/pointage/presentation/presence_test_terrain_screen.dart';
import '../../features/splash/presentation/splash_screen.dart';
import '../../features/organigramme/presentation/organigramme_screen.dart';
import '../../features/a_valider/presentation/a_valider_screen.dart';
import '../../features/dossier/presentation/fiche_annuaire_screen.dart';
import '../../features/dossier/presentation/fiche_equipe_screen.dart';
import '../../features/dossier/presentation/mon_dossier_screen.dart';
import '../../features/dossier/presentation/mon_equipe_screen.dart';
import '../../features/formations/presentation/formation_detail_screen.dart';
import '../../features/pointage/presentation/mes_pointages_screen.dart';
import '../../features/pointage/presentation/pointage_manuel_detail_screen.dart';

bool _peutConfigurerPresence(CollaborateurInfo info) => info.peutConfigurerPresence;
bool _peutDemanderHeuresSup(CollaborateurInfo info) => info.peutDemanderHeuresSup;

final _routerRefresh = ValueNotifier<int>(0);

final appRouterProvider = Provider<GoRouter>((ref) {
  ref.listen<AuthState>(authNotifierProvider, (_, __) => _routerRefresh.value++);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: _routerRefresh,
    redirect: (context, state) {
      final auth = ref.read(authNotifierProvider);
      final loc = state.matchedLocation;

      if (auth.status == AuthStatus.unknown) {
        return null; // Don't redirect while status is unknown (Splash handles it)
      }

      // Force login on fresh restart
      if (loc == '/splash') return '/login';

      if (auth.status == AuthStatus.unauthenticated) {
        if (loc != '/login') return '/login';
        return null;
      }

      if (auth.isAuthenticated) {
        if (loc == '/splash' || loc == '/login') return '/home';
        return null;
      }

      return null;
    },
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: const Text('Élément introuvable')),
      body: RhEmptyState(
        icon: Icons.search_off_rounded,
        title: 'Élément introuvable',
        message: 'Cette page n’existe pas ou n’est plus disponible.',
        action: FilledButton(
          onPressed: () => context.go('/home'),
          child: const Text('Retour à l’accueil'),
        ),
      ),
    ),
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/home', builder: (_, __) => const HomeScreen()),
      GoRoute(path: '/pointage', builder: (_, __) => const PointageScreen()),
      GoRoute(path: '/pointage/manuel', builder: (_, __) => const PointageManuelScreen()),
      GoRoute(path: '/pointage/mes-pointages', builder: (_, __) => const MesPointagesScreen()),
      GoRoute(
        path: '/pointage/manuels/:id',
        builder: (c, s) => PointageManuelDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(path: '/dossier', builder: (_, __) => const MonDossierScreen()),
      GoRoute(path: '/equipe', builder: (_, __) => const MonEquipeScreen()),
      GoRoute(
        path: '/equipe/:id',
        builder: (c, s) => FicheEquipeScreen(collaborateurId: s.pathParameters['id']!),
      ),
      GoRoute(
        path: '/annuaire/:id',
        builder: (c, s) => FicheAnnuaireScreen(collaborateurId: s.pathParameters['id']!),
      ),
      GoRoute(path: '/a-valider', builder: (_, __) => const AValiderScreen()),
      GoRoute(path: '/heures-sup/moi', builder: (_, __) => const MesHeuresSupScreen()),
      GoRoute(
        path: '/heures-sup/demandes',
        builder: (_, __) => const GardeRole(
          autorise: _peutDemanderHeuresSup,
          enfant: MesDemandesHeuresSupScreen(),
        ),
      ),
      GoRoute(
        path: '/heures-sup/nouveau',
        builder: (_, __) => const GardeRole(
          autorise: _peutDemanderHeuresSup,
          enfant: DemandeHeuresSupScreen(),
        ),
      ),
      GoRoute(
        path: '/presence/sites',
        builder: (_, __) => const GardeRole(
          autorise: _peutConfigurerPresence,
          enfant: PresenceSitesAdminScreen(),
        ),
      ),
      GoRoute(
        path: '/presence/test-terrain',
        builder: (_, __) => const GardeRole(
          autorise: _peutConfigurerPresence,
          enfant: PresenceTestTerrainScreen(),
        ),
      ),
      GoRoute(
        path: '/facial/consentement',
        builder: (_, s) => FacialConsentementScreen(
          mode: s.extra is FacialRoute ? s.extra as FacialRoute : null,
        ),
      ),
      GoRoute(
        path: '/facial/enrolement',
        builder: (_, s) => FacialEnrolementScreen(
          versionConsentement: s.extra is String ? s.extra as String : '',
        ),
      ),
      GoRoute(path: '/facial/mon-visage', builder: (_, __) => const FacialMonVisageScreen()),
      // D-T02.a : l'écran Actualités est retiré, l'ancienne route renvoie à l'accueil.
      GoRoute(path: '/feed', redirect: (_, __) => '/home'),
      GoRoute(path: '/formations', builder: (_, __) => const FormationsListScreen()),
      GoRoute(path: '/formations/nouveau', builder: (_, __) => const FormationCreateScreen()),
      GoRoute(
        path: '/formations/:id',
        builder: (c, s) => FormationDetailScreen(formationId: s.pathParameters['id']!),
      ),
      GoRoute(path: '/plaintes', builder: (_, __) => const PlaintesListScreen()),
      GoRoute(path: '/plaintes/nouveau', builder: (_, __) => const PlainteCreateScreen()),
      GoRoute(
        path: '/plaintes/:id',
        builder: (c, s) => PlainteDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(path: '/conges/solde', builder: (_, __) => const MonSoldeScreen()),
      GoRoute(path: '/demandes-admin', builder: (_, __) => const DemandesAdminListScreen()),
      GoRoute(path: '/demandes-admin/autorisations', builder: (_, __) => const AutorisationsListScreen()),
      GoRoute(path: '/demandes-admin/ro/validation', builder: (_, __) => const RoValidationScreen()),
      GoRoute(path: '/demandes-admin/conge/nouveau', builder: (_, __) => const DemandeCongeCreateScreen()),
      GoRoute(path: '/demandes-admin/autorisation/nouveau', builder: (_, __) => const DemandeAutorisationSortieCreateScreen()),
      GoRoute(path: '/demandes-admin/mission/nouveau', builder: (_, __) => const DemandeOrdreMissionCreateScreen()),
      GoRoute(path: '/demandes-admin/ro/validation', builder: (_, __) => const RoValidationScreen()),
      GoRoute(
        path: '/demandes-admin/:id',
        builder: (c, s) => DemandeAdminDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(path: '/documents', builder: (_, __) => const DocumentsListScreen()),
      GoRoute(path: '/documents/nouveau', builder: (_, __) => const DocumentCreateScreen()),
      GoRoute(
        path: '/documents/:id',
        builder: (c, s) => DocumentDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(path: '/evaluations', builder: (_, __) => const EvaluationsListScreen()),
      GoRoute(
        path: '/evaluations/:id',
        builder: (c, s) => EvaluationDetailScreen(
          id: s.pathParameters['id']!,
          forceManagerMode: s.uri.queryParameters['mode'] == 'manager',
        ),
      ),
      GoRoute(path: '/notifications', builder: (_, __) => const NotificationsScreen()),
      GoRoute(path: '/organigramme', builder: (_, __) => const OrganigrammeScreen()),
    ],
  );
});
