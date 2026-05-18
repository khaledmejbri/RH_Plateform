import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import 'evaluation_models.dart';

final evaluationRepositoryProvider = Provider<EvaluationRepository>((ref) {
  final dio = ref.watch(dioProvider);
  return EvaluationRepository(dio: dio);
});

class EvaluationRepository {
  final Dio dio;

  EvaluationRepository({required this.dio});

  Future<List<EvaluationItem>> mesEvaluations() async {
    try {
      final response = await dio.get('/api/rh/v1/evaluations/moi');
      final data = response.data as List;
      return data.map((e) => EvaluationItem.fromJson(e)).toList();
    } catch (e) {
      throw Exception('Erreur lors du chargement des évaluations: $e');
    }
  }

  Future<EvaluationItem> obtenirEvaluation(String id) async {
    try {
      final response = await dio.get('/api/rh/v1/evaluations/$id');
      return EvaluationItem.fromJson(response.data);
    } catch (e) {
      throw Exception('Erreur lors du chargement de l\'évaluation: $e');
    }
  }

  Future<List<EvaluationQuestion>> obtenirQuestionsGenerales(String evaluationId) async {
    try {
      final response = await dio.get('/api/rh/v1/evaluations/$evaluationId/questions/generales');
      final data = response.data as List;
      return data.map((e) => EvaluationQuestion.fromJson(e)).toList();
    } catch (e) {
      throw Exception('Erreur lors du chargement des questions: $e');
    }
  }

  Future<void> repondreQuestionGenerale({
    required String evaluationId,
    required String questionId,
    required String reponse,
    int? note,
  }) async {
    try {
      await dio.post('/api/rh/v1/evaluations/$evaluationId/reponses/generales', data: {
        'questionId': questionId,
        'reponse': reponse,
        'note': note,
      });
    } catch (e) {
      throw Exception('Erreur lors de l\'enregistrement de la réponse: $e');
    }
  }

  Future<List<TechnicalQuestion>> obtenirQuestionsTechniques(String evaluationId) async {
    try {
      final response = await dio.get('/api/rh/v1/evaluations/$evaluationId/questions/techniques');
      final data = response.data as List;
      return data.map((e) => TechnicalQuestion.fromJson(e)).toList();
    } catch (e) {
      throw Exception('Erreur lors du chargement des questions techniques: $e');
    }
  }

  Future<void> evaluerCompetenceTechnique({
    required String evaluationId,
    required String questionId,
    required SkillLevel niveau,
    String? commentaire,
  }) async {
    try {
      await dio.post('/api/rh/v1/evaluations/$evaluationId/reponses/techniques', data: {
        'questionId': questionId,
        'niveau': niveau.name.toUpperCase(),
        'commentaire': commentaire,
      });
    } catch (e) {
      throw Exception('Erreur lors de l\'évaluation technique: $e');
    }
  }

  Future<void> validerParCollaborateur(String evaluationId) async {
    try {
      await dio.post('/api/rh/v1/evaluations/$evaluationId/validate/collaborator');
    } catch (e) {
      throw Exception('Erreur lors de la validation: $e');
    }
  }

  Future<List<EvaluationAnswer>> obtenirReponses(String evaluationId) async {
    try {
      final response = await dio.get('/api/rh/v1/evaluations/$evaluationId/reponses');
      final data = response.data as List;
      return data.map((e) => EvaluationAnswer.fromJson(e)).toList();
    } catch (e) {
      throw Exception('Erreur lors du chargement des réponses: $e');
    }
  }
}
