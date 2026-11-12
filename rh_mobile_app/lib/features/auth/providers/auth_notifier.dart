import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/libelles/libelles_service.dart';
import '../data/auth_repository.dart';

enum AuthStatus { unknown, unauthenticated, authenticated }

class AuthState {
  const AuthState({required this.status, this.errorMessage});

  final AuthStatus status;
  final String? errorMessage;

  bool get isAuthenticated => status == AuthStatus.authenticated;

  AuthState copyWith({AuthStatus? status, String? errorMessage}) {
    return AuthState(
      status: status ?? this.status,
      errorMessage: errorMessage,
    );
  }
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._repo, {LibellesService? libelles})
      : _libelles = libelles,
        super(const AuthState(status: AuthStatus.unknown)) {
    _bootstrap();
  }

  final AuthRepository _repo;

  /// Libellés paramétrés (lot 3) : chargés à la connexion, vidés à la déconnexion.
  final LibellesService? _libelles;

  /// Charge les libellés avant d'ouvrir l'accueil pour que les listes n'affichent pas
  /// « Valeur non reconnue ». Un échec ou un délai dépassé n'empêche pas la connexion : la
  /// relecture périodique et l'ouverture des formulaires reprennent la main.
  Future<void> _chargerLibelles() async {
    final l = _libelles;
    if (l == null) return;
    try {
      await l.demarrer().timeout(const Duration(seconds: 8));
    } catch (_) {}
  }

  Future<void> _bootstrap() async {
    // Always start unauthenticated to force login and fresh user info
    await _repo.signOut();
    _libelles?.arreter();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  Future<void> signIn(String email, String password) async {
    state = state.copyWith(status: AuthStatus.unauthenticated, errorMessage: null);
    try {
      await _repo.signIn(email: email, password: password);
      await _chargerLibelles();
      state = const AuthState(status: AuthStatus.authenticated);
    } on AuthException catch (e) {
      state = AuthState(status: AuthStatus.unauthenticated, errorMessage: e.message);
      rethrow;
    }
  }

  Future<void> demo() async {
    await _repo.signInDemo();
    await _chargerLibelles();
    state = const AuthState(status: AuthStatus.authenticated);
  }

  Future<void> signOut() async {
    await _repo.signOut();
    _libelles?.arreter();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.watch(authRepositoryProvider), libelles: ref.watch(libellesServiceProvider));
});
