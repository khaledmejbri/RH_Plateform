import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import 'evaluation_models.dart';

final evaluationRepositoryProvider = Provider<EvaluationRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return EvaluationRepository(dio: dio);
});

/// Compteur pastille accueil : évals non archivées avec action (collab + manager).
final evaluationsBadgeCountProvider = FutureProvider.autoDispose<int>((ref) async {
  final repo = ref.watch(evaluationRepositoryProvider);
  try {
    final mine = await repo.mesEvaluations();
    final pending = await repo.managerPending();
    final collabActions =
        mine.where((e) => !e.isArchived && e.actionCollaborateurRequise).length;
    final managerActions =
        pending.where((e) => !e.isArchived && e.actionManagerRequise).length;
    return collabActions + managerActions;
  } catch (_) {
    return 0;
  }
});

/// § 12.2 (vague) : la rubrique Évaluations n'est visible que tant qu'une évaluation non archivée existe,
/// pour le collaborateur (auto-évaluation) ou pour le manager (évaluations à noter) — SCRUM-20.
final evaluationsVisiblesProvider = FutureProvider.autoDispose<bool>((ref) async {
  final repo = ref.watch(evaluationRepositoryProvider);
  final mine = await repo.mesEvaluations();
  if (mine.any((e) => !e.isArchived)) return true;
  final pending = await repo.managerPending();
  return pending.any((e) => !e.isArchived);
});

class EvaluationRepository {
  final Dio dio;

  EvaluationRepository({required this.dio});

  /// Liste des évaluations du collaborateur.
  /// Matching client (`niveau_seniorite` / `role_metier`) volontairement non envoyé (E4-R11).
  Future<List<EvaluationItem>> mesEvaluations({bool ensure = true}) async {
    try {
      final response = await dio.get(
        '/api/rh/v1/mobile/evaluations/moi',
        queryParameters: {'ensure': ensure},
      );
      final data = response.data as List? ?? const [];
      return data
          .map((e) => EvaluationItem.fromJson(e as Map<String, dynamic>))
          .where((e) => !e.isArchived)
          .toList();
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de charger vos évaluations');
    } catch (e) {
      throw Exception('Impossible de charger vos évaluations');
    }
  }

  Future<List<EvaluationItem>> managerPending() async {
    try {
      final response = await dio.get('/api/rh/v1/mobile/evaluations/manager/pending');
      final data = response.data as List? ?? const [];
      return data
          .map((e) => EvaluationItem.fromJson(e as Map<String, dynamic>))
          .where((e) => !e.isArchived)
          .toList();
    } on DioException catch (e) {
      if (e.response?.statusCode == 403 || e.response?.statusCode == 404) {
        return const [];
      }
      throw _formatDioError(e, 'Impossible de charger les évaluations à noter');
    } catch (_) {
      return const [];
    }
  }

  Future<void> passerEtapeTechnique(String evaluationId) async {
    try {
      await dio.post('/api/rh/v1/mobile/evaluations/$evaluationId/passer-technique');
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de passer à l’étape compétences');
    }
  }

  Future<EvaluationItem> obtenirEvaluation(String id) async {
    try {
      final response = await dio.get('/api/rh/v1/mobile/evaluations/$id');
      return EvaluationItem.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de charger l’évaluation');
    }
  }

  Future<List<EvaluationQuestion>> obtenirQuestionsGenerales(String evaluationId) async {
    try {
      final response = await dio.get(
        '/api/rh/v1/mobile/evaluations/$evaluationId/questions/generales',
      );
      final data = response.data as List? ?? const [];
      return data
          .map((e) => EvaluationQuestion.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de charger les questions');
    }
  }

  Future<void> repondreQuestionGenerale({
    required String evaluationId,
    required String questionId,
    required String reponse,
    int? note,
  }) async {
    try {
      await dio.post(
        '/api/rh/v1/mobile/evaluations/$evaluationId/reponses/generales',
        data: {
          'questionId': questionId,
          'reponse': reponse,
          'note': note,
        },
      );
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible d’enregistrer la réponse');
    }
  }

  Future<List<TechnicalQuestion>> obtenirQuestionsTechniques(String evaluationId) async {
    try {
      final response = await dio.get(
        '/api/rh/v1/mobile/evaluations/$evaluationId/questions/techniques',
      );
      final data = response.data as List? ?? const [];
      return data
          .map((e) => TechnicalQuestion.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      final msg = _extractMessage(e) ?? '';
      if (e.response?.statusCode == 400 ||
          e.response?.statusCode == 409 ||
          e.response?.statusCode == 422 ||
          msg.toLowerCase().contains('template') ||
          msg.toLowerCase().contains('étape technique') ||
          msg.toLowerCase().contains('etape technique')) {
        return const [];
      }
      throw _formatDioError(e, 'Impossible de charger les questions techniques');
    }
  }

  Future<void> evaluerCompetenceTechnique({
    required String evaluationId,
    required String questionId,
    required SkillLevel niveau,
    String? commentaire,
  }) async {
    try {
      await dio.post(
        '/api/rh/v1/mobile/evaluations/$evaluationId/reponses/techniques',
        data: {
          'questionId': questionId,
          'niveau': niveau.apiValue,
          'commentaire': commentaire,
        },
      );
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible d’enregistrer la compétence');
    }
  }

  Future<void> repondreQuestionManager({
    required String evaluationId,
    required String questionId,
    required String reponse,
    int? note,
    String? commentaire,
  }) async {
    try {
      await dio.post(
        '/api/rh/v1/mobile/evaluations/$evaluationId/manager/reponses/generales',
        data: {
          'questionId': questionId,
          'reponse': reponse,
          'note': note,
          'commentaire': commentaire,
        },
      );
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible d’enregistrer la note manager');
    }
  }

  Future<void> evaluerCompetenceTechniqueManager({
    required String evaluationId,
    required String questionId,
    required SkillLevel niveau,
    String? commentaire,
  }) async {
    try {
      await dio.post(
        '/api/rh/v1/mobile/evaluations/$evaluationId/manager/reponses/techniques',
        data: {
          'questionId': questionId,
          'niveau': niveau.apiValue,
          'commentaire': commentaire,
        },
      );
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible d’enregistrer la note technique');
    }
  }

  Future<void> validerParCollaborateur(String evaluationId) async {
    try {
      await dio.post(
        '/api/rh/v1/mobile/evaluations/$evaluationId/validate/collaborator',
      );
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de valider l’auto-évaluation');
    }
  }

  Future<List<EvaluationAnswer>> obtenirReponses(String evaluationId) async {
    try {
      final response =
          await dio.get('/api/rh/v1/mobile/evaluations/$evaluationId/reponses');
      final data = response.data as List? ?? const [];
      return data
          .map((e) => EvaluationAnswer.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de charger les réponses');
    }
  }

  Future<EvaluationAnalytics> obtenirAnalytics(String evaluationId) async {
    try {
      final response =
          await dio.get('/api/rh/v1/mobile/evaluations/$evaluationId/analytics');
      return EvaluationAnalytics.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _formatDioError(e, 'Impossible de charger le score', silent: true);
    }
  }

  String? _extractMessage(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      return data['message'] as String? ?? data['erreur'] as String?;
    }
    return null;
  }

  Exception _formatDioError(DioException e, String context, {bool silent = false}) {
    if (silent) {
      return Exception(context);
    }

    final userMessage = _extractMessage(e) ?? context;

    switch (e.response?.statusCode) {
      case 400:
      case 409:
      case 422:
        return Exception(userMessage);
      case 401:
        return Exception('Session expirée. Veuillez vous reconnecter.');
      case 403:
        return Exception(
          'Accès refusé. Cette évaluation ne vous est pas destinée.',
        );
      case 404:
        return Exception('Évaluation introuvable.');
      case 500:
        return Exception('Erreur serveur. Veuillez réessayer plus tard.');
      default:
        if (e.type == DioExceptionType.connectionTimeout ||
            e.type == DioExceptionType.receiveTimeout ||
            e.type == DioExceptionType.connectionError) {
          return Exception('Connexion indisponible. Vérifiez le réseau.');
        }
        return Exception(userMessage);
    }
  }
}
