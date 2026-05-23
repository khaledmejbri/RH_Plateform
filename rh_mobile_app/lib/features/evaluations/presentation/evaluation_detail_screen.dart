import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../data/evaluation_models.dart';
import '../data/evaluation_repository.dart';

final evaluationDetailProvider = FutureProvider.autoDispose.family<EvaluationItem, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirEvaluation(id);
});

final generalQuestionsProvider = FutureProvider.autoDispose.family<List<EvaluationQuestion>, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirQuestionsGenerales(id);
});

final technicalQuestionsProvider = FutureProvider.autoDispose.family<List<TechnicalQuestion>, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirQuestionsTechniques(id);
});

final evaluationAnalyticsProvider = FutureProvider.autoDispose.family<EvaluationAnalytics, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirAnalytics(id);
});

class EvaluationDetailScreen extends ConsumerStatefulWidget {
  const EvaluationDetailScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<EvaluationDetailScreen> createState() => _EvaluationDetailScreenState();
}

class _EvaluationDetailScreenState extends ConsumerState<EvaluationDetailScreen> {
  final Map<String, TextEditingController> _textControllers = {};
  final Map<String, int> _scores = {};
  final Map<String, SkillLevel> _skillScores = {};
  bool _saving = false;

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(evaluationDetailProvider(widget.id));
    final analytics = ref.watch(evaluationAnalyticsProvider(widget.id));

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Evaluation'),
      ),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorState(message: '$error'),
        data: (item) {
          final isTechnical = item.etapeActuelle == 'EVALUATION_TECHNIQUE';
          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(evaluationDetailProvider(widget.id));
              ref.invalidate(generalQuestionsProvider(widget.id));
              ref.invalidate(technicalQuestionsProvider(widget.id));
              ref.invalidate(evaluationAnalyticsProvider(widget.id));
            },
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _HeaderCard(item: item),
                const SizedBox(height: 12),
                analytics.maybeWhen(
                  data: (value) => _AnalyticsCard(analytics: value),
                  orElse: () => const SizedBox.shrink(),
                ),
                const SizedBox(height: 12),
                _ProgressCard(isTechnical: isTechnical),
                const SizedBox(height: 12),
                if (isTechnical) _TechnicalSection(evaluationId: widget.id) else _GeneralSection(evaluationId: widget.id),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _saving ? null : () => _submitCurrentStep(isTechnical),
                  child: Text(_saving ? 'Enregistrement...' : isTechnical ? 'Envoyer mes competences' : 'Enregistrer mes reponses'),
                ),
                if (!isTechnical) ...[
                  const SizedBox(height: 10),
                  OutlinedButton(
                    onPressed: _saving ? null : _moveToTechnical,
                    child: const Text('Passer aux competences'),
                  ),
                ],
                const SizedBox(height: 10),
                OutlinedButton(
                  onPressed: _saving ? null : _validateEvaluation,
                  child: const Text('Valider mon evaluation'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _GeneralSection({required String evaluationId}) {
    final questionsAsync = ref.watch(generalQuestionsProvider(evaluationId));
    return questionsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _ErrorState(message: '$error'),
      data: (questions) => Column(
        children: questions.map((question) {
          final controller = _textControllers.putIfAbsent(
            question.id,
            () => TextEditingController(text: question.reponseExistante ?? ''),
          );
          if (question.noteExistante != null) {
            _scores.putIfAbsent(question.id, () => question.noteExistante!);
          }
          return _QuestionCard(
            title: question.libelle,
            section: question.sectionLibelle,
            required: question.obligatoire,
            child: _buildQuestionInput(question, controller),
          );
        }).toList(),
      ),
    );
  }

  Widget _TechnicalSection({required String evaluationId}) {
    final questionsAsync = ref.watch(technicalQuestionsProvider(evaluationId));
    return questionsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _ErrorState(message: '$error'),
      data: (questions) => Column(
        children: questions.map((question) {
          if (question.niveauAutoEvaluation != null) {
            _skillScores.putIfAbsent(question.id, () => SkillLevelExtension.fromString(question.niveauAutoEvaluation!));
          }
          final selected = _skillScores[question.id];
          return _QuestionCard(
            title: question.competence,
            section: 'Competences',
            required: true,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (question.description.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(question.description, style: const TextStyle(color: AppTheme.textSecondary)),
                  ),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: SkillLevel.values.map((level) {
                    final active = selected == level;
                    return ChoiceChip(
                      label: Text('${level.score} ${level.label}'),
                      selected: active,
                      onSelected: (_) => setState(() => _skillScores[question.id] = level),
                    );
                  }).toList(),
                ),
              ],
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildQuestionInput(EvaluationQuestion question, TextEditingController controller) {
    switch (question.typeQuestion) {
      case 'RATING':
      case 'SCALE':
      case 'NUMBER':
        final min = (question.valeurMinimale ?? 1).toInt();
        final max = (question.valeurMaximale ?? 5).toInt();
        final value = _scores[question.id] ?? question.noteExistante ?? min;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Slider(
              value: value.toDouble(),
              min: min.toDouble(),
              max: max.toDouble(),
              divisions: max - min,
              label: '$value',
              onChanged: (next) => setState(() => _scores[question.id] = next.round()),
            ),
            Text('$value / $max', style: const TextStyle(fontWeight: FontWeight.w700)),
          ],
        );
      case 'MULTIPLE_CHOICE':
        return Column(
          children: question.optionsReponses.map((option) {
            return RadioListTile<String>(
              value: option,
              groupValue: controller.text,
              onChanged: (value) => setState(() => controller.text = value ?? ''),
              title: Text(option),
            );
          }).toList(),
        );
      default:
        return TextField(
          controller: controller,
          maxLines: question.typeQuestion == 'TEXT' ? 1 : 4,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Votre reponse',
          ),
        );
    }
  }

  Future<void> _submitCurrentStep(bool isTechnical) async {
    setState(() => _saving = true);
    try {
      final repository = ref.read(evaluationRepositoryProvider);
      if (isTechnical) {
        for (final entry in _skillScores.entries) {
          await repository.evaluerCompetenceTechnique(
            evaluationId: widget.id,
            questionId: entry.key,
            niveau: entry.value,
          );
        }
      } else {
        for (final entry in _textControllers.entries) {
          await repository.repondreQuestionGenerale(
            evaluationId: widget.id,
            questionId: entry.key,
            reponse: entry.value.text,
            note: _scores[entry.key],
          );
        }
      }
      _refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Brouillon enregistre')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _moveToTechnical() async {
    await _submitCurrentStep(false);
    setState(() => _saving = true);
    try {
      await ref.read(evaluationRepositoryProvider).dio.post('/api/rh/v1/evaluations/${widget.id}/passer-technique');
      _refresh();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _validateEvaluation() async {
    setState(() => _saving = true);
    try {
      await ref.read(evaluationRepositoryProvider).validerParCollaborateur(widget.id);
      _refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Evaluation validee')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _refresh() {
    ref.invalidate(evaluationDetailProvider(widget.id));
    ref.invalidate(generalQuestionsProvider(widget.id));
    ref.invalidate(technicalQuestionsProvider(widget.id));
    ref.invalidate(evaluationAnalyticsProvider(widget.id));
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.item});

  final EvaluationItem item;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(item.campaignNom, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Text('Manager: ${item.superieurNom}', style: const TextStyle(color: AppTheme.textSecondary)),
            const SizedBox(height: 8),
            LinearProgressIndicator(value: item.etapeActuelle == 'EVALUATION_TECHNIQUE' ? 0.65 : 0.25),
          ],
        ),
      ),
    );
  }
}

class _AnalyticsCard extends StatelessWidget {
  const _AnalyticsCard({required this.analytics});

  final EvaluationAnalytics analytics;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            _Metric(label: 'Self', value: '${analytics.selfAverage}/5'),
            _Metric(label: 'Final', value: '${analytics.finalScore}/5'),
            _Metric(label: 'Gap', value: '${analytics.discrepancyPercentage}%'),
          ],
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(value, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
          Text(label, style: const TextStyle(color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}

class _ProgressCard extends StatelessWidget {
  const _ProgressCard({required this.isTechnical});

  final bool isTechnical;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(child: _StepPill(label: 'General', active: true)),
        const SizedBox(width: 8),
        Expanded(child: _StepPill(label: 'Competences', active: isTechnical)),
      ],
    );
  }
}

class _StepPill extends StatelessWidget {
  const _StepPill({required this.label, required this.active});

  final String label;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        color: active ? AppTheme.primarySurface : Colors.white,
        borderRadius: BorderRadius.circular(10),
      ),
      alignment: Alignment.center,
      child: Text(label, style: TextStyle(fontWeight: FontWeight.w700, color: active ? AppTheme.primary : AppTheme.textSecondary)),
    );
  }
}

class _QuestionCard extends StatelessWidget {
  const _QuestionCard({
    required this.title,
    required this.child,
    this.section,
    this.required = false,
  });

  final String title;
  final String? section;
  final bool required;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (section != null)
              Text(section!, style: const TextStyle(color: AppTheme.primary, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('$title${required ? ' *' : ''}', style: const TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(message, textAlign: TextAlign.center, style: const TextStyle(color: AppTheme.textSecondary)),
      ),
    );
  }
}
