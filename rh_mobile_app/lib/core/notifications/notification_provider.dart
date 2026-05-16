import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../constants/api_constants.dart';
import '../network/api_client.dart';
import '../storage/secure_token_storage.dart';
import 'notification_model.dart';

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

  Future<void> connect(String userId) async {
    try {
      final token = await _storage.readAccessToken();

      // Properly construct WebSocket URL
      final baseUrl = ApiConstants.baseUrl;
      debugPrint('[STOMP] ═════════════════════════════════════════');
      debugPrint('[STOMP] DEBUG: Raw ApiConstants.baseUrl = "$baseUrl"');

      // Extract host and port from base URL
      // Use raw WebSocket endpoint (Flutter has native WebSocket support)
      String wsUrl;
      if (baseUrl.contains('10.0.2.2')) {
        // Android emulator
        wsUrl = 'ws://10.0.2.2:8080/ws';
      } else if (baseUrl.contains('127.0.0.1')) {
        // iOS simulator
        wsUrl = 'ws://127.0.0.1:8080/ws';
      } else if (baseUrl.contains('localhost')) {
        // Web or other
        wsUrl = 'ws://localhost:8080/ws';
      } else {
        // Generic conversion
        final base = baseUrl.replaceFirst('http', 'ws');
        wsUrl = base.endsWith('/ws') ? base : '$base/ws';
      }

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

      _currentUserId = finalUserId;

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
          useSockJS: false, // Flutter has native WebSocket, no need for SockJS
          onConnect: (frame) {
            debugPrint('[STOMP] ✅ CONNECTED SUCCESSFULLY!');
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

            // Also subscribe to topic
            final userTopicDest = '/topic/$finalUserId';
            debugPrint('[STOMP] 📨 Subscribing to: $userTopicDest');
            _client?.subscribe(
              destination: userTopicDest,
              callback: _handleFrame,
            );

            // Subscribe to broadcast
            debugPrint('[STOMP] 📨 Subscribing to: /topic/RH');
            _client?.subscribe(
              destination: '/topic/RH',
              callback: _handleFrame,
            );
            
            debugPrint('[STOMP] ✅ All subscriptions active');
          },
          onDisconnect: (frame) {
            debugPrint('[STOMP] ❌ DISCONNECTED');
            debugPrint('[STOMP] Disconnect frame: ${frame?.command}');
            // Use microtask to avoid updating state during build phase
            Future.microtask(() {
              state = state.copyWith(connected: false);
            });
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
        debugPrint('[STOMP]   1. Backend is running on port 8080');
        debugPrint('[STOMP]   2. WebSocket endpoint /ws is available');
        debugPrint('[STOMP]   3. CORS is configured for WebSocket');
        debugPrint('[STOMP]   4. User ID is correct: $finalUserId');
      }
    } catch (e, stackTrace) {
      debugPrint('[STOMP] ❌ Fatal Connection Error: $e');
      debugPrint('[STOMP] Stack trace: $stackTrace');
      state = state.copyWith(connected: false);
    }
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
    debugPrint('[STOMP] Disconnecting...');
    _client?.deactivate();
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
    _client?.deactivate();
    super.dispose();
  }
}

final notificationProvider =
    StateNotifierProvider<NotificationNotifier, NotificationState>((ref) {
  return NotificationNotifier(ref.watch(secureTokenStorageProvider));
});
