import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import 'plainte_models.dart';

final plainteRepositoryProvider = Provider<PlainteRepository>((ref) {
  return PlainteRepository(ref.watch(dioProvider));
});

class PlainteRepository {
  PlainteRepository(this._dio);

  final Dio _dio;

  Future<List<PlainteItem>> mesPlaintes() async {
    final res = await _dio.get<List<dynamic>>(ApiConstants.plaintes);
    final list = res.data ?? [];
    return list.map((e) => PlainteItem.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<PlainteItem> getOne(String id) async {
    final res = await _dio.get<Map<String, dynamic>>('${ApiConstants.plaintes}/$id');
    return PlainteItem.fromJson(res.data!);
  }

  Future<void> create({
    required String typePlainte,
    required String titre,
    required String description,
  }) async {
    await _dio.post<Map<String, dynamic>>(
      ApiConstants.plaintes,
      data: {
        'type_plainte': typePlainte,
        'titre': titre,
        'description': description,
      },
    );
  }
}
