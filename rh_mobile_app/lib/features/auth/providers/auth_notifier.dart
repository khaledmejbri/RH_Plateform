import 'package:flutter_riverpod/flutter_riverpod.dart';

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
  AuthNotifier(this._repo) : super(const AuthState(status: AuthStatus.unknown)) {
    _bootstrap();
  }

  final AuthRepository _repo;

  Future<void> _bootstrap() async {
    // Always start unauthenticated to force login and fresh user info
    await _repo.signOut();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  Future<void> signIn(String email, String password) async {
    state = state.copyWith(status: AuthStatus.unauthenticated, errorMessage: null);
    try {
      await _repo.signIn(email: email, password: password);
      state = const AuthState(status: AuthStatus.authenticated);
    } on AuthException catch (e) {
      state = AuthState(status: AuthStatus.unauthenticated, errorMessage: e.message);
      rethrow;
    }
  }

  Future<void> demo() async {
    await _repo.signInDemo();
    state = const AuthState(status: AuthStatus.authenticated);
  }

  Future<void> signOut() async {
    await _repo.signOut();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.watch(authRepositoryProvider));
});
