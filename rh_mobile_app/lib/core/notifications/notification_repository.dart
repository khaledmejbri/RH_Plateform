import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../constants/api_constants.dart';
import '../network/api_client.dart';
import 'notification_model.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  return NotificationRepository(ref.watch(dioProvider));
});

/// Notifications conservées par le serveur (ticket 2G) : l'identité est lue dans le jeton côté serveur.
class NotificationRepository {
  NotificationRepository(this._dio);

  final Dio _dio;

  static String get _base => '${ApiConstants.prefixRh}/notifications';

  /// Page de notifications, plus récentes d'abord. [lu] : `true` lues, `false` non lues, `null` toutes.
  Future<PageNotifications> lister({bool? lu, int page = 0, int taille = 20}) async {
    final res = await _dio.get<dynamic>(
      _base,
      queryParameters: {
        if (lu != null) 'lu': lu,
        'page': page,
        'taille': taille,
      },
    );
    final data = res.data;
    if (data is! Map) {
      return PageNotifications(contenu: const [], page: page, taille: taille, total: 0);
    }
    return PageNotifications.fromJson(Map<String, dynamic>.from(data));
  }

  /// Nombre de notifications non lues.
  Future<int> compteur() async {
    final res = await _dio.get<dynamic>('$_base/compteur');
    final data = res.data;
    if (data is Map && data['non_lues'] is num) {
      return (data['non_lues'] as num).toInt();
    }
    return 0;
  }

  /// Marque une notification lue (idempotent). 404 si elle n'appartient pas au compte.
  Future<void> marquerLue(String id) async {
    await _dio.post<dynamic>('$_base/$id/lue');
  }

  /// Marque toutes les notifications non lues comme lues.
  Future<void> toutMarquerLu() async {
    await _dio.post<dynamic>('$_base/lues');
  }
}
