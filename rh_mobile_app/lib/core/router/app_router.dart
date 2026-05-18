import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/providers/auth_notifier.dart';
import '../../features/demandes_admin/presentation/demande_admin_detail_screen.dart';
import '../../features/demandes_admin/presentation/demandes_admin_list_screen.dart';
import '../../features/demandes_admin/presentation/autorisations_list_screen.dart';
import '../../features/demandes_admin/presentation/demande_conge_create_screen.dart';
import '../../features/demandes_admin/presentation/demande_autorisation_sortie_create_screen.dart';
import '../../features/demandes_admin/presentation/ro_validation_screen.dart';
import '../../features/documents/presentation/document_create_screen.dart';
import '../../features/documents/presentation/document_detail_screen.dart';
import '../../features/documents/presentation/documents_list_screen.dart';
import '../../features/feed/presentation/feed_screen.dart';
import '../../features/formations/presentation/formation_create_screen.dart';
import '../../features/formations/presentation/formations_list_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/notifications/notifications_screen.dart';
import '../../features/evaluations/presentation/evaluations_list_screen.dart';
import '../../features/plaintes/presentation/plainte_create_screen.dart';
import '../../features/plaintes/presentation/plainte_detail_screen.dart';
import '../../features/plaintes/presentation/plaintes_list_screen.dart';
import '../../features/pointage/presentation/pointage_screen.dart';
import '../../features/splash/presentation/splash_screen.dart';

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
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/home', builder: (_, __) => const HomeScreen()),
      GoRoute(path: '/pointage', builder: (_, __) => const PointageScreen()),
      GoRoute(path: '/feed', builder: (_, __) => const FeedScreen()),
      GoRoute(path: '/formations', builder: (_, __) => const FormationsListScreen()),
      GoRoute(path: '/formations/nouveau', builder: (_, __) => const FormationCreateScreen()),
      GoRoute(path: '/plaintes', builder: (_, __) => const PlaintesListScreen()),
      GoRoute(path: '/plaintes/nouveau', builder: (_, __) => const PlainteCreateScreen()),
      GoRoute(
        path: '/plaintes/:id',
        builder: (c, s) => PlainteDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(path: '/demandes-admin', builder: (_, __) => const DemandesAdminListScreen()),
      GoRoute(path: '/demandes-admin/autorisations', builder: (_, __) => const AutorisationsListScreen()),
      GoRoute(path: '/demandes-admin/ro/validation', builder: (_, __) => const RoValidationScreen()),
      GoRoute(path: '/demandes-admin/conge/nouveau', builder: (_, __) => const DemandeCongeCreateScreen()),
      GoRoute(path: '/demandes-admin/autorisation/nouveau', builder: (_, __) => const DemandeAutorisationSortieCreateScreen()),
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
      GoRoute(path: '/notifications', builder: (_, __) => const NotificationsScreen()),
    ],
  );
});
