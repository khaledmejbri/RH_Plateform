import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/notifications/notification_provider.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/providers/auth_notifier.dart';
import 'features/auth/providers/collaborateur_notifier.dart';

class RhMobileApp extends ConsumerStatefulWidget {
  const RhMobileApp({super.key});

  @override
  ConsumerState<RhMobileApp> createState() => _RhMobileAppState();
}

class _RhMobileAppState extends ConsumerState<RhMobileApp> {
  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);

    // Listen to auth changes for side-effects (WebSocket, etc.)
    ref.listen<AuthState>(authNotifierProvider, (prev, next) async {
      debugPrint('[APP] Auth state changed: ${prev?.status} -> ${next.status}');
      if (next.isAuthenticated) {
        debugPrint('[APP] ═════════════════════════════════════════');
        debugPrint('[APP] User is authenticated, starting notification connection...');

        // 1. Fetch current collaborator info
        await ref.read(collaborateurNotifierProvider.notifier).fetchMoi();
        final info = ref.read(collaborateurNotifierProvider).value;

        if (info != null) {
          debugPrint('[AUTH] LOGIN SUCCESS - USER DATA FOUND');
          debugPrint('[APP] 🚀 Initiating WebSocket connection for user: ${info.identifiant}');
          ref.read(notificationProvider.notifier).connect(info.identifiant);
        } else {
          debugPrint('[APP] ⚠️ Collaborator info NULL, falling back to RH');
          ref.read(notificationProvider.notifier).connect('RH');
        }
      } else {
        if (prev?.isAuthenticated == true || prev == null) {
          debugPrint('[APP] User is not authenticated, disconnecting WebSocket...');
          ref.read(notificationProvider.notifier).disconnect();
        }
      }
    });

    return MaterialApp.router(
      title: 'RH Connect',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      routerConfig: router,
    );
  }
}

