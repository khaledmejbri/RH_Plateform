import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../constants/api_constants.dart';
import '../network/api_client.dart';
import '../storage/secure_token_storage.dart';
import 'notification_model.dart';
import 'ws_url.dart';

class NotificationState {
  final List<AppNotification> notifications;
  final bool connected;

  const NotificationState({
    this.notifications = const [],
    this.connected = false,
  });

  int get unreadCount => notifications.where((n) => !n.isRead).length;

  NotificationState copyWith({
    List<AppNotification>? notifications,
    bool? connected,
  }) {
    return NotificationState(
      notifications: notifications ?? this.notifications,
      connected: connected ?? this.connected,
    );
  }
}

class NotificationNotifier extends StateNotifier<NotificationState> {
  NotificationNotifier(this._storage) : super(const NotificationState());

  final SecureTokenStorage _storage;
  StompClient? _client;
  String? _currentUserId;
  bool _connecting = false;
  Timer? _timerReconnexion;
  int _tentativesReconnexion = 0;
  bool _arretVoulu = false;
  final Set<String> _seenNotificationKeys = <String>{};

  Future<void> connect(String userId) async {
    if (_connecting) {
      debugPrint('[STOMP] Connection already in progress, skipping duplicate connect()');
      return;
    }
    _connecting = true;
    try {
      final token = await _storage.readAccessToken();

      // Properly construct WebSocket URL
      final baseUrl = ApiConstants.baseUrl;
      debugPrint('[STOMP] ═════════════════════════════════════════');
      debugPrint('[STOMP] DEBUG: Raw ApiConstants.baseUrl = "$baseUrl"');

      final wsUrl = deduireUrlWebSocket(baseUrl);

      debugPrint('[STOMP] Constructed WebSocket URL: $wsUrl');
      debugPrint('[STOMP] Using raw WebSocket (Flutter native support)');

      // 1. Try to extract user ID from token if possible, otherwise use provided userId
      String finalUserId = userId;
      if (token != null) {
        try {
          final parts = token.split('.');
          if (parts.length == 3) {
            final payload = json.decode(
              utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
            );
            final tokenUserId = payload['identifiant_utilisateur'] ?? payload['sub'];
            if (tokenUserId != null) {
              debugPrint('[STOMP] Found ID in token: $tokenUserId');
              finalUserId = tokenUserId.toString();
            }
          }
        } catch (e) {
          debugPrint('[STOMP] Could not parse token for ID: $e');
        }
      }

      debugPrint('[STOMP] Final User ID to use: $finalUserId');
      debugPrint('[STOMP] Token present: ${token != null}');
      debugPrint('[STOMP] Token length: ${token?.length ?? 0}');
      debugPrint('[STOMP] ═════════════════════════════════════════');

      if (state.connected && _currentUserId == finalUserId && _client != null) {
        debugPrint('[STOMP] Already connected for user $finalUserId, skipping duplicate connect()');
        return;
      }

      if (_client != null) {
        debugPrint('[STOMP] Existing client found, deactivating before reconnect');
        _arretVoulu = true;
        _timerReconnexion?.cancel();
        _client!.deactivate();
        // Give it a moment to disconnect
        await Future.delayed(const Duration(milliseconds: 500));
        state = state.copyWith(connected: false);
      }

      _currentUserId = finalUserId;
      _arretVoulu = false;

      // STOMP-level headers — these populate the CONNECT frame that
      // StompAuthInterceptor reads on the backend.
      final stompHeaders = <String, String>{};
      if (token != null && token.isNotEmpty) {
        stompHeaders['Authorization'] = 'Bearer $token';
        debugPrint('[STOMP] ✅ Authorization header added to STOMP CONNECT');
      } else {
        debugPrint('[STOMP] ⚠️ WARNING: No token available! Authorization header will NOT be sent');
      }

      // WebSocket HTTP-upgrade headers (separate from STOMP headers)
      final wsHeaders = <String, String>{};
      if (token != null && token.isNotEmpty) {
        wsHeaders['Authorization'] = 'Bearer $token';
      }

      debugPrint('[STOMP] STOMP headers: $stompHeaders');

      _client = StompClient(
        config: StompConfig(
          url: wsUrl,
          stompConnectHeaders: stompHeaders,
          webSocketConnectHeaders: wsHeaders,
          connectionTimeout: const Duration(seconds: 15),
          // Reconnexion gérée ici (1 s → 60 s) : l'intervalle fixe du client est désactivé.
          reconnectDelay: Duration.zero,
          useSockJS: false, // Flutter has native WebSocket, no need for SockJS
          onConnect: (frame) {
            debugPrint('[STOMP] ✅ CONNECTED SUCCESSFULLY!');
            _tentativesReconnexion = 0;
            _timerReconnexion?.cancel();
            debugPrint('[STOMP] Frame: ${frame.command}');
            // Use microtask to avoid updating state during build phase
            Future.microtask(() {
              state = state.copyWith(connected: true);
            });

            // Subscribe to user-specific queue.
            // Spring's convertAndSendToUser(userId, "/queue/notifications", …)
            // already routes to /user/{userId}/queue/notifications internally.
            // The client must subscribe to /user/queue/notifications — NOT
            // /user/$finalUserId/queue/notifications — to avoid a double prefix.
            const userQueueDest = '/user/queue/notifications';
            debugPrint('[STOMP] 📨 Subscribing to: $userQueueDest');
            _client?.subscribe(
              destination: userQueueDest,
              callback: _handleFrame,
            );
            debugPrint('[STOMP] User queue subscription active');
          },
          onDisconnect: (frame) {
            debugPrint('[STOMP] ❌ DISCONNECTED');
            debugPrint('[STOMP] Disconnect frame: ${frame?.command}');
            // Use microtask to avoid updating state during build phase
            Future.microtask(() {
              state = state.copyWith(connected: false);
            });
            _planifierReconnexion();
          },
          onWebSocketError: (dynamic error) {
            debugPrint('[STOMP] ⚠️  WebSocket Error: $error');
            debugPrint('[STOMP] Error type: ${error.runtimeType}');
            if (error is Exception) {
              debugPrint('[STOMP] Error details: $error');
            }
            // Use microtask to avoid updating state during build phase
            Future.microtask(() {
              state = state.copyWith(connected: false);
            });
            _planifierReconnexion();
          },
          onStompError: (StompFrame frame) {
            debugPrint('[STOMP] ⚠️  STOMP Protocol Error');
            debugPrint('[STOMP] Command: ${frame.command}');
            debugPrint('[STOMP] Headers: ${frame.headers}');
            debugPrint('[STOMP] Body: ${frame.body}');
            // Use microtask to avoid updating state during build phase
            Future.microtask(() {
              state = state.copyWith(connected: false);
            });
            _planifierReconnexion();
          },
          beforeConnect: () async {
            debugPrint('[STOMP] 🔄 Before connect - preparing connection...');
            debugPrint('[STOMP] Target URL: $wsUrl');
          },
          onDebugMessage: (String message) {
            // Only log important debug messages to avoid spam
            if (message.contains('Opening') || 
                message.contains('Connected') || 
                message.contains('Sending') ||
                message.contains('Received')) {
              debugPrint('[STOMP] 🔍 $message');
            }
          },
        ),
      );

      debugPrint('[STOMP] 🚀 Activating STOMP client...');
      _client!.activate();

      // Wait for connection with timeout
      int waitCount = 0;
      while (!state.connected && waitCount < 10) {
        await Future.delayed(const Duration(seconds: 1));
        waitCount++;
        debugPrint('[STOMP] ⏳ Waiting for connection... ($waitCount/10)');
      }

      if (state.connected) {
        debugPrint('[STOMP] ✅ Connection established successfully!');
      } else {
        debugPrint('[STOMP] ⚠️  Connection timeout after 10 seconds');
        debugPrint('[STOMP] Please check:');
        debugPrint('[STOMP]   1. Backend is running (gateway)');
        debugPrint('[STOMP]   2. WebSocket endpoint /ws is available');
        debugPrint('[STOMP]   3. CORS is configured for WebSocket');
        debugPrint('[STOMP]   4. User ID is correct: $finalUserId');
      }
    } catch (e, stackTrace) {
      debugPrint('[STOMP] ❌ Fatal Connection Error: $e');
      debugPrint('[STOMP] Stack trace: $stackTrace');
      state = state.copyWith(connected: false);
    } finally {
      _connecting = false;
    }
  }

  /// Relance le client avec une attente croissante (1 s, 2 s, 4 s… 60 s) ; une seule relance en attente.
  void _planifierReconnexion() {
    if (_arretVoulu || _client == null || (_timerReconnexion?.isActive ?? false)) return;
    final delai = delaiReconnexion(_tentativesReconnexion++);
    debugPrint('[STOMP] Nouvelle tentative dans ${delai.inSeconds} s');
    _timerReconnexion = Timer(delai, () {
      final client = _client;
      if (_arretVoulu || client == null || state.connected) return;
      client.deactivate();
      client.activate();
    });
  }

  void _handleFrame(StompFrame frame) {
    if (frame.body == null) {
      debugPrint('[STOMP] ℹ️  Received frame with null body');
      return;
    }
    try {
      debugPrint('[STOMP] 📥 Raw frame received');
      debugPrint('[STOMP] Command: ${frame.command}');
      debugPrint('[STOMP] Body: ${frame.body}');

      final notificationKey = frame.body!;
      if (_seenNotificationKeys.contains(notificationKey)) {
        debugPrint('[STOMP] Duplicate notification frame ignored');
        return;
      }
      _seenNotificationKeys.add(notificationKey);
      if (_seenNotificationKeys.length > 200) {
        _seenNotificationKeys.remove(_seenNotificationKeys.first);
      }

      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      debugPrint('[STOMP] ✅ JSON decoded: $json');

      final notif = AppNotification.fromMap(json);
      debugPrint('[STOMP] ✅ Notification received: "${notif.subject}"');
      debugPrint('[STOMP] Content: ${notif.content}');

      // Use microtask to avoid updating state during build phase
      Future.microtask(() {
        state = state.copyWith(
          notifications: [notif, ...state.notifications],
        );
        debugPrint('[STOMP] ✅ Notification added to state. Total: ${state.notifications.length}');
      });
    } catch (e, stackTrace) {
      debugPrint('[STOMP] ❌ Parse error: $e');
      debugPrint('[STOMP] Stack: $stackTrace');
      debugPrint('[STOMP] Body that failed: ${frame.body}');
    }
  }

  void disconnect() {
    _arretVoulu = true;
    _timerReconnexion?.cancel();
    _tentativesReconnexion = 0;
    debugPrint('[STOMP] Disconnecting...');
    _client?.deactivate();
    _client = null;
    _currentUserId = null;
    _connecting = false;
    state = state.copyWith(connected: false);
  }

  void markAllRead() {
    final updated = state.notifications
        .map((n) => n.copyWith(isRead: true))
        .toList();
    state = state.copyWith(notifications: updated);
  }

  void markRead(String id) {
    final updated = state.notifications
        .map((n) => n.id == id ? n.copyWith(isRead: true) : n)
        .toList();
    state = state.copyWith(notifications: updated);
  }

  @override
  void dispose() {
    _arretVoulu = true;
    _timerReconnexion?.cancel();
    _client?.deactivate();
    super.dispose();
  }
}

final notificationProvider =
    StateNotifierProvider<NotificationNotifier, NotificationState>((ref) {
  return NotificationNotifier(ref.watch(secureTokenStorageProvider));
});
