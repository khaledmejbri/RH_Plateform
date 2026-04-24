import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../data/plainte_models.dart';

final plainteDetailProvider = FutureProvider.family.autoDispose<PlainteItem, String>((ref, id) async {
  final dio = ref.watch(dioProvider);
  final res = await dio.get<Map<String, dynamic>>('${ApiConstants.plaintes}/$id');
  return PlainteItem.fromJson(res.data!);
});

class PlainteDetailScreen extends ConsumerWidget {
  const PlainteDetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(plainteDetailProvider(id));
    return Scaffold(
      appBar: AppBar(title: const Text('Détail plainte')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('$e')),
        data: (p) => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(p.titre, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Chip(label: Text(p.statut)),
            const SizedBox(height: 16),
            Text(p.description ?? '', style: Theme.of(context).textTheme.bodyLarge),
          ],
        ),
      ),
    );
  }
}
