import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_theme.dart';
import '../../auth/providers/collaborateur_notifier.dart';
import '../data/evaluation_models.dart';
import '../data/evaluation_repository.dart';
import 'evaluations_list_screen.dart';
import 'package:rh_mobile_app/core/widgets/rh_ui.dart';

final evaluationDetailProvider =
    FutureProvider.autoDispose.family<EvaluationItem, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirEvaluation(id);
});

final generalQuestionsProvider = FutureProvider.autoDispose
    .family<List<EvaluationQuestion>, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirQuestionsGenerales(id);
});

final technicalQuestionsProvider = FutureProvider.autoDispose
    .family<List<TechnicalQuestion>, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirQuestionsTechniques(id);
});

final evaluationAnalyticsProvider =
    FutureProvider.autoDispose.family<EvaluationAnalytics, String>((ref, id) {
  return ref.watch(evaluationRepositoryProvider).obtenirAnalytics(id);
});

class EvaluationDetailScreen extends ConsumerStatefulWidget {
  const EvaluationDetailScreen({
    super.key,
    required this.id,
    this.forceManagerMode = false,
  });

  final String id;
  final bool forceManagerMode;

  @override
  ConsumerState<EvaluationDetailScreen> createState() =>
      _EvaluationDetailScreenState();
}

class _EvaluationDetailScreenState
    extends ConsumerState<EvaluationDetailScreen> {
  final Map<String, TextEditingController> _textControllers = {};
  final Map<String, int> _scores = {};
  final Map<String, SkillLevel> _skillScores = {};
  final Map<String, int> _managerNotes = {};
  bool _saving = false;
  String? _inlineError;

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  bool _isManagerMode(EvaluationItem item) {
    if (widget.forceManagerMode) return true;
    final me = ref.read(collaborateurNotifierProvider).valueOrNull?.identifiant;
    if (me == null || me.isEmpty) return false;
    if (item.collaborateurId.isNotEmpty && item.collaborateurId == me) {
      return false;
    }
    return item.superieurId.isNotEmpty && item.superieurId == me;
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(evaluationDetailProvider(widget.id));

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Évaluation'),
      ),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorState(message: rhErrorMessage(error)),
        data: (item) {
          final managerMode = _isManagerMode(item);
          if (managerMode) {
            return _buildManagerBody(item);
          }
          return _buildSelfBody(item);
        },
      ),
    );
  }

  Widget _buildSelfBody(EvaluationItem item) {
    final isTechnical = item.etapeActuelle == 'EVALUATION_TECHNIQUE';
    final alreadyValidated = item.validationCollaborateurLe != null ||
        item.statut == 'VALIDEE_COLLABORATEUR' ||
        item.statut == 'VALIDEE';
    final analytics = ref.watch(evaluationAnalyticsProvider(widget.id));

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          _HeaderCard(item: item),
          const SizedBox(height: 12),
          analytics.maybeWhen(
            data: (value) =>
                _AnalyticsCard(analytics: value, scoreSur20: item.scoreSur20),
            orElse: () => const SizedBox.shrink(),
          ),
          const SizedBox(height: 12),
          _Stepper(
            isTechnical: isTechnical,
            generalDone: isTechnical || alreadyValidated,
          ),
          const SizedBox(height: 8),
          Text(
            isTechnical
                ? 'Étape 2 sur 2 — Compétences'
                : 'Étape 1 sur 2 — Évaluation générale',
            style: const TextStyle(
              fontWeight: FontWeight.w600,
              color: AppTheme.textSecondary,
            ),
          ),
          const SizedBox(height: 4),
          const Text(
            'Votre manager notera après votre validation.',
            style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
          ),
          if (_inlineError != null) ...[
            const SizedBox(height: 12),
            _InlineAlert(message: _inlineError!),
          ],
          const SizedBox(height: 12),
          if (isTechnical)
            _TechnicalSection(
              evaluationId: widget.id,
              skillScores: _skillScores,
              readOnly: alreadyValidated,
              onChanged: (id, level) =>
                  setState(() => _skillScores[id] = level),
            )
          else
            _GeneralSection(
              evaluationId: widget.id,
              textControllers: _textControllers,
              scores: _scores,
              readOnly: alreadyValidated,
              onScoreChanged: (id, v) => setState(() => _scores[id] = v),
              onTextChanged: () => setState(() {}),
            ),
          const SizedBox(height: 20),
          if (!alreadyValidated) ...[
            if (!isTechnical) ...[
              OutlinedButton(
                onPressed: _saving ? null : () => _submitCurrentStep(false),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
                child:
                    Text(_saving ? 'Enregistrement…' : 'Enregistrer brouillon'),
              ),
              const SizedBox(height: 10),
              FilledButton(
                onPressed: _saving ? null : _moveToTechnical,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
                child: Text(
                  _saving ? 'Enregistrement…' : 'Enregistrer et continuer',
                ),
              ),
            ] else ...[
              Builder(
                builder: (context) {
                  final techAsync =
                      ref.watch(technicalQuestionsProvider(widget.id));
                  final hasTech = techAsync.maybeWhen(
                    data: (q) => q.isNotEmpty,
                    orElse: () => true,
                  );
                  return Column(
                    children: [
                      if (hasTech)
                        FilledButton(
                          onPressed:
                              _saving ? null : () => _submitCurrentStep(true),
                          style: FilledButton.styleFrom(
                            minimumSize: const Size.fromHeight(52),
                          ),
                          child: Text(
                            _saving
                                ? 'Enregistrement…'
                                : 'Enregistrer mes compétences',
                          ),
                        ),
                      if (hasTech) const SizedBox(height: 10),
                      FilledButton(
                        onPressed: _saving ? null : _validateEvaluation,
                        style: FilledButton.styleFrom(
                          minimumSize: const Size.fromHeight(52),
                        ),
                        child: Text(
                          _saving
                              ? 'Validation…'
                              : 'Valider mon auto-évaluation',
                        ),
                      ),
                    ],
                  );
                },
              ),
            ],
          ],
        ],
      ),
    );
  }

  Widget _buildManagerBody(EvaluationItem item) {
    final collabDone = item.validationCollaborateurLe != null ||
        item.statut == 'VALIDEE_COLLABORATEUR' ||
        item.statut == 'VALIDEE';
    final managerDone = item.validationSuperieurLe != null ||
        item.statut == 'VALIDEE_SUPERIEUR' ||
        item.statut == 'VALIDEE';

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: AppTheme.primarySurface,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Text(
              'Vous notez en tant que manager',
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: AppTheme.primary,
              ),
            ),
          ),
          const SizedBox(height: 12),
          _HeaderCard(item: item),
          const SizedBox(height: 8),
          const Text(
            'Vous ne pouvez noter une question qu’après la réponse du collaborateur.',
            style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
          ),
          if (_inlineError != null) ...[
            const SizedBox(height: 12),
            _InlineAlert(message: _inlineError!),
          ],
          if (!collabDone) ...[
            const SizedBox(height: 16),
            const _InlineAlert(
              message:
                  'Le collaborateur n’a pas encore validé son auto-évaluation. '
                  'Les notes manager restent indisponibles.',
            ),
          ],
          const SizedBox(height: 12),
          _ManagerGeneralSection(
            evaluationId: widget.id,
            managerNotes: _managerNotes,
            enabled: collabDone && !managerDone,
            onNoteChanged: (id, note) =>
                setState(() => _managerNotes[id] = note),
          ),
          const SizedBox(height: 12),
          _ManagerTechnicalSection(
            evaluationId: widget.id,
            skillScores: _skillScores,
            enabled: collabDone &&
                !managerDone &&
                item.etapeActuelle == 'EVALUATION_TECHNIQUE',
            onChanged: (id, level) => setState(() => _skillScores[id] = level),
          ),
          const SizedBox(height: 20),
          if (collabDone && !managerDone)
            FilledButton(
              onPressed: _saving ? null : _submitManagerNotes,
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: Text(
                _saving ? 'Enregistrement…' : 'Enregistrer les notes',
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _submitCurrentStep(bool isTechnical) async {
    setState(() {
      _saving = true;
      _inlineError = null;
    });
    try {
      final repository = ref.read(evaluationRepositoryProvider);
      if (isTechnical) {
        final questions =
            await repository.obtenirQuestionsTechniques(widget.id);
        for (final q in questions) {
          final level = _skillScores[q.id];
          if (level == null) continue;
          await repository.evaluerCompetenceTechnique(
            evaluationId: widget.id,
            questionId: q.id,
            niveau: level,
          );
        }
      } else {
        final questions = await repository.obtenirQuestionsGenerales(widget.id);
        for (final question in questions) {
          final controller = _textControllers[question.id];
          final note = _scores[question.id];
          final reponse = controller?.text.trim() ?? '';
          if (reponse.isEmpty && note == null && question.obligatoire) {
            throw Exception(
              'Répondez à toutes les questions obligatoires avant d’enregistrer.',
            );
          }
          if (reponse.isEmpty && note == null) continue;
          await repository.repondreQuestionGenerale(
            evaluationId: widget.id,
            questionId: question.id,
            reponse: reponse.isEmpty ? '${note ?? ''}' : reponse,
            note: note,
          );
        }
      }
      await _refresh();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Réponses enregistrées.')),
        );
      }
    } catch (e) {
      setState(() => _inlineError = '$e'.replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _moveToTechnical() async {
    await _submitCurrentStep(false);
    if (_inlineError != null) return;
    setState(() => _saving = true);
    try {
      await ref
          .read(evaluationRepositoryProvider)
          .passerEtapeTechnique(widget.id);
      await _refresh();
      final tech = await ref
          .read(evaluationRepositoryProvider)
          .obtenirQuestionsTechniques(widget.id);
      if (tech.isEmpty && mounted) {
        setState(() {
          _inlineError =
              'Aucune question technique pour votre profil. '
              'Vous pouvez valider votre auto-évaluation.';
        });
      }
    } catch (e) {
      setState(() => _inlineError = '$e'.replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _validateEvaluation() async {
    setState(() {
      _saving = true;
      _inlineError = null;
    });
    try {
      final tech = await ref
          .read(evaluationRepositoryProvider)
          .obtenirQuestionsTechniques(widget.id);
      if (tech.isNotEmpty) {
        await _submitCurrentStep(true);
        if (_inlineError != null) return;
      }
      await ref
          .read(evaluationRepositoryProvider)
          .validerParCollaborateur(widget.id);
      await _refresh();
      ref.invalidate(evaluationsListProvider);
      ref.invalidate(evaluationsBadgeCountProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Auto-évaluation validée.')),
        );
      }
    } catch (e) {
      setState(() => _inlineError = '$e'.replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _submitManagerNotes() async {
    setState(() {
      _saving = true;
      _inlineError = null;
    });
    try {
      final repo = ref.read(evaluationRepositoryProvider);
      final generales = await repo.obtenirQuestionsGenerales(widget.id);
      for (final q in generales) {
        final note = _managerNotes[q.id];
        if (note == null) continue;
        final hasCollab = (q.reponseExistante?.trim().isNotEmpty ?? false) ||
            q.noteExistante != null;
        if (!hasCollab) continue;
        await repo.repondreQuestionManager(
          evaluationId: widget.id,
          questionId: q.id,
          reponse: 'Note manager: $note',
          note: note,
        );
      }
      final tech = await repo.obtenirQuestionsTechniques(widget.id);
      for (final q in tech) {
        final level = _skillScores[q.id];
        if (level == null) continue;
        await repo.evaluerCompetenceTechniqueManager(
          evaluationId: widget.id,
          questionId: q.id,
          niveau: level,
        );
      }
      await _refresh();
      ref.invalidate(managerEvaluationsProvider);
      ref.invalidate(evaluationsBadgeCountProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Notes enregistrées.')),
        );
      }
    } catch (e) {
      setState(() => _inlineError = '$e'.replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _refresh() async {
    ref.invalidate(evaluationDetailProvider(widget.id));
    ref.invalidate(generalQuestionsProvider(widget.id));
    ref.invalidate(technicalQuestionsProvider(widget.id));
    ref.invalidate(evaluationAnalyticsProvider(widget.id));
    await ref.read(evaluationDetailProvider(widget.id).future);
  }
}

class _GeneralSection extends ConsumerWidget {
  const _GeneralSection({
    required this.evaluationId,
    required this.textControllers,
    required this.scores,
    required this.readOnly,
    required this.onScoreChanged,
    required this.onTextChanged,
  });

  final String evaluationId;
  final Map<String, TextEditingController> textControllers;
  final Map<String, int> scores;
  final bool readOnly;
  final void Function(String id, int value) onScoreChanged;
  final VoidCallback onTextChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final questionsAsync = ref.watch(generalQuestionsProvider(evaluationId));
    return questionsAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => _ErrorState(message: rhErrorMessage(error)),
      data: (questions) {
        if (questions.isEmpty) {
          return const _InlineAlert(
            message: 'Aucune question générale pour cette évaluation.',
          );
        }
        final remaining = questions.where((q) => q.obligatoire).where((q) {
          final text = textControllers[q.id]?.text.trim() ??
              q.reponseExistante?.trim() ??
              '';
          final note = scores[q.id] ?? q.noteExistante;
          return text.isEmpty && note == null;
        }).length;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (remaining > 0 && !readOnly)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  remaining == 1
                      ? '1 question obligatoire restante'
                      : '$remaining questions obligatoires restantes',
                  style: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.warning,
                  ),
                ),
              ),
            ...questions.map((question) {
              final controller = textControllers.putIfAbsent(
                question.id,
                () => TextEditingController(
                  text: question.reponseExistante ?? '',
                ),
              );
              if (question.noteExistante != null) {
                scores.putIfAbsent(question.id, () => question.noteExistante!);
              }
              return _QuestionCard(
                title: question.libelle,
                section: question.sectionLibelle,
                required: question.obligatoire,
                child: _QuestionInput(
                  question: question,
                  controller: controller,
                  score: scores[question.id] ?? question.noteExistante,
                  readOnly: readOnly,
                  onScoreChanged: (v) => onScoreChanged(question.id, v),
                  onTextChanged: onTextChanged,
                ),
              );
            }),
          ],
        );
      },
    );
  }
}

class _TechnicalSection extends ConsumerWidget {
  const _TechnicalSection({
    required this.evaluationId,
    required this.skillScores,
    required this.readOnly,
    required this.onChanged,
  });

  final String evaluationId;
  final Map<String, SkillLevel> skillScores;
  final bool readOnly;
  final void Function(String id, SkillLevel level) onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final questionsAsync = ref.watch(technicalQuestionsProvider(evaluationId));
    return questionsAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, _) => _ErrorState(message: rhErrorMessage(error)),
      data: (questions) {
        if (questions.isEmpty) {
          return const _InlineAlert(
            message:
                'Aucune question technique pour votre profil. '
                'Validez votre auto-évaluation.',
          );
        }
        return Column(
          children: questions.map((question) {
            if (question.niveauAutoEvaluation != null) {
              skillScores.putIfAbsent(
                question.id,
                () => SkillLevelExtension.fromString(
                  question.niveauAutoEvaluation!,
                ),
              );
            }
            final selected = skillScores[question.id];
            return _QuestionCard(
              title: question.competence,
              section: 'Compétences',
              required: true,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (question.description.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(
                        question.description,
                        style: const TextStyle(color: AppTheme.textSecondary),
                      ),
                    ),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: SkillLevel.values.map((level) {
                      final active = selected == level;
                      return ChoiceChip(
                        label: Text('${level.score} ${level.label}'),
                        selected: active,
                        onSelected: readOnly
                            ? null
                            : (_) => onChanged(question.id, level),
                      );
                    }).toList(),
                  ),
                ],
              ),
            );
          }).toList(),
        );
      },
    );
  }
}

class _ManagerGeneralSection extends ConsumerWidget {
  const _ManagerGeneralSection({
    required this.evaluationId,
    required this.managerNotes,
    required this.enabled,
    required this.onNoteChanged,
  });

  final String evaluationId;
  final Map<String, int> managerNotes;
  final bool enabled;
  final void Function(String id, int note) onNoteChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(generalQuestionsProvider(evaluationId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorState(message: rhErrorMessage(e)),
      data: (questions) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Questions générales',
            style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15),
          ),
          const SizedBox(height: 8),
          ...questions.map((q) {
            final collabAnswer = q.reponseExistante;
            final hasAnswer =
                (collabAnswer != null && collabAnswer.trim().isNotEmpty) ||
                    q.noteExistante != null;
            if (q.noteManagerExistante != null) {
              managerNotes.putIfAbsent(q.id, () => q.noteManagerExistante!);
            }
            final note = managerNotes[q.id] ?? q.noteManagerExistante;
            return _QuestionCard(
              title: q.libelle,
              section: q.sectionLibelle,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    hasAnswer
                        ? 'Réponse collab. : ${collabAnswer ?? q.noteExistante}'
                        : 'Le collaborateur n’a pas encore répondu à cette question.',
                    style: TextStyle(
                      color: hasAnswer
                          ? AppTheme.textPrimary
                          : AppTheme.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Votre note :',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 8,
                    children: List.generate(5, (i) {
                      final value = i + 1;
                      final active = note == value;
                      return ChoiceChip(
                        label: Text('$value'),
                        selected: active,
                        onSelected: (!enabled || !hasAnswer)
                            ? null
                            : (_) => onNoteChanged(q.id, value),
                      );
                    }),
                  ),
                ],
              ),
            );
          }),
        ],
      ),
    );
  }
}

class _ManagerTechnicalSection extends ConsumerWidget {
  const _ManagerTechnicalSection({
    required this.evaluationId,
    required this.skillScores,
    required this.enabled,
    required this.onChanged,
  });

  final String evaluationId;
  final Map<String, SkillLevel> skillScores;
  final bool enabled;
  final void Function(String id, SkillLevel level) onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!enabled) return const SizedBox.shrink();
    final async = ref.watch(technicalQuestionsProvider(evaluationId));
    return async.when(
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
      data: (questions) {
        if (questions.isEmpty) return const SizedBox.shrink();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Compétences',
              style: TextStyle(fontWeight: FontWeight.w800, fontSize: 15),
            ),
            const SizedBox(height: 8),
            ...questions.map((q) {
              final selected = skillScores[q.id];
              return _QuestionCard(
                title: q.competence,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      q.niveauAutoEvaluation != null
                          ? 'Réponse collab. : ${SkillLevelExtension.fromString(q.niveauAutoEvaluation!).label}'
                          : 'Le collaborateur n’a pas encore répondu à cette question.',
                      style: const TextStyle(color: AppTheme.textSecondary),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: SkillLevel.values.map((level) {
                        return ChoiceChip(
                          label: Text('${level.score} ${level.label}'),
                          selected: selected == level,
                          onSelected: (q.niveauAutoEvaluation == null)
                              ? null
                              : (_) => onChanged(q.id, level),
                        );
                      }).toList(),
                    ),
                  ],
                ),
              );
            }),
          ],
        );
      },
    );
  }
}

class _QuestionInput extends StatelessWidget {
  const _QuestionInput({
    required this.question,
    required this.controller,
    required this.score,
    required this.readOnly,
    required this.onScoreChanged,
    required this.onTextChanged,
  });

  final EvaluationQuestion question;
  final TextEditingController controller;
  final int? score;
  final bool readOnly;
  final ValueChanged<int> onScoreChanged;
  final VoidCallback onTextChanged;

  @override
  Widget build(BuildContext context) {
    switch (question.typeQuestion) {
      case 'RATING':
      case 'SCALE':
      case 'NUMBER':
        final min = (question.valeurMinimale ?? 1).toInt();
        final max = (question.valeurMaximale ?? 5).toInt();
        final value = (score ?? min).clamp(min, max);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (question.labelsEchelle.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  question.labelsEchelle.join(' · '),
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppTheme.textSecondary,
                  ),
                ),
              ),
            Slider(
              value: value.toDouble(),
              min: min.toDouble(),
              max: max.toDouble(),
              divisions: (max - min).clamp(1, 100),
              label: '$value',
              onChanged:
                  readOnly ? null : (next) => onScoreChanged(next.round()),
            ),
            Text(
              '$value / $max',
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ],
        );
      case 'MULTIPLE_CHOICE':
        return Column(
          children: question.optionsReponses.map((option) {
            return RadioListTile<String>(
              value: option,
              groupValue: controller.text,
              onChanged: readOnly
                  ? null
                  : (value) {
                      controller.text = value ?? '';
                      onTextChanged();
                    },
              title: Text(option),
              contentPadding: EdgeInsets.zero,
            );
          }).toList(),
        );
      case 'CHECKBOX':
        final selected = controller.text
            .split(',')
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toSet();
        return Column(
          children: question.optionsReponses.map((option) {
            final checked = selected.contains(option);
            return CheckboxListTile(
              value: checked,
              onChanged: readOnly
                  ? null
                  : (v) {
                      final next = {...selected};
                      if (v == true) {
                        next.add(option);
                      } else {
                        next.remove(option);
                      }
                      controller.text = next.join(', ');
                      onTextChanged();
                    },
              title: Text(option),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            );
          }).toList(),
        );
      case 'DATE':
        return TextField(
          controller: controller,
          readOnly: true,
          enabled: !readOnly,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Choisir une date',
            suffixIcon: Icon(Icons.calendar_today_outlined),
          ),
          onTap: readOnly
              ? null
              : () async {
                  final now = DateTime.now();
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: now,
                    firstDate: DateTime(now.year - 5),
                    lastDate: DateTime(now.year + 5),
                  );
                  if (picked != null) {
                    controller.text =
                        '${picked.year.toString().padLeft(4, '0')}-'
                        '${picked.month.toString().padLeft(2, '0')}-'
                        '${picked.day.toString().padLeft(2, '0')}';
                    onTextChanged();
                  }
                },
        );
      default:
        return TextField(
          controller: controller,
          maxLines: question.typeQuestion == 'TEXT' ? 1 : 4,
          enabled: !readOnly,
          onChanged: (_) => onTextChanged(),
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: 'Votre réponse',
          ),
        );
    }
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
            Text(
              item.campaignNom,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            Text(
              'Manager : ${item.superieurNom}',
              style: const TextStyle(color: AppTheme.textSecondary),
            ),
            Text(
              'Statut : ${EvaluationLabels.statut(item.statut)}',
              style: const TextStyle(color: AppTheme.textSecondary),
            ),
            if (item.profilSnapshotLabel != null) ...[
              const SizedBox(height: 6),
              Text(
                'Profil : ${item.profilSnapshotLabel}',
                style: const TextStyle(
                  fontSize: 13,
                  color: AppTheme.textSecondary,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _AnalyticsCard extends StatelessWidget {
  const _AnalyticsCard({required this.analytics, this.scoreSur20});

  final EvaluationAnalytics analytics;
  final int? scoreSur20;

  @override
  Widget build(BuildContext context) {
    final appreciation = EvaluationLabels.appreciation(scoreSur20);
    return Card(
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Score', style: TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 12),
            Row(
              children: [
                _Metric(
                  label: 'Auto-éval.',
                  value: '${analytics.selfAverage}/5',
                ),
                _Metric(label: 'Final', value: '${analytics.finalScore}/5'),
                _Metric(
                  label: 'Sur 20',
                  value: scoreSur20 != null
                      ? '$scoreSur20/20'
                      : '${(analytics.finalScore * 4).round()}/20',
                ),
              ],
            ),
            if (appreciation.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                appreciation,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  color: AppTheme.primary,
                ),
              ),
            ],
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
          Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18),
          ),
          Text(label, style: const TextStyle(color: AppTheme.textSecondary)),
        ],
      ),
    );
  }
}

class _Stepper extends StatelessWidget {
  const _Stepper({required this.isTechnical, required this.generalDone});

  final bool isTechnical;
  final bool generalDone;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _StepPill(
            label: '1 Générale',
            active: !isTechnical,
            done: generalDone,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _StepPill(
            label: '2 Compétences',
            active: isTechnical,
            done: false,
          ),
        ),
      ],
    );
  }
}

class _StepPill extends StatelessWidget {
  const _StepPill({
    required this.label,
    required this.active,
    required this.done,
  });

  final String label;
  final bool active;
  final bool done;

  @override
  Widget build(BuildContext context) {
    final bg = active
        ? AppTheme.primarySurface
        : done
            ? AppTheme.successSurface
            : Colors.white;
    final fg = active
        ? AppTheme.primary
        : done
            ? AppTheme.success
            : AppTheme.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppTheme.border),
      ),
      alignment: Alignment.center,
      child: Text(
        label,
        style: TextStyle(fontWeight: FontWeight.w700, color: fg),
      ),
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
              Text(
                section!,
                style: const TextStyle(
                  color: AppTheme.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
            const SizedBox(height: 4),
            Text(
              '$title${required ? ' *' : ''}',
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}

class _InlineAlert extends StatelessWidget {
  const _InlineAlert({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppTheme.accentSurface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFFFED7AA)),
      ),
      child: Text(
        message,
        style: const TextStyle(color: AppTheme.accent, height: 1.35),
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final displayMessage = _formatErrorMessage(message);
    final is403 = displayMessage.toLowerCase().contains('accès refusé');
    final is404 = displayMessage.toLowerCase().contains('introuvable');

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              is403
                  ? Icons.lock_outline_rounded
                  : is404
                      ? Icons.search_off_rounded
                      : Icons.error_outline,
              size: 64,
              color: AppTheme.error,
            ),
            const SizedBox(height: 16),
            Text(
              displayMessage,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 16,
              ),
            ),
            const SizedBox(height: 24),
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Retour'),
            ),
          ],
        ),
      ),
    );
  }

  String _formatErrorMessage(String error) {
    if (error.contains('message')) {
      try {
        final match = RegExp(r'"message":\s*"([^"]+)"').firstMatch(error);
        if (match != null) return match.group(1)!;
      } catch (_) {}
    }
    if (error.contains('403') || error.toLowerCase().contains('accès refusé')) {
      return 'Accès refusé. Cette évaluation ne vous est pas destinée.';
    }
    if (error.contains('404') || error.toLowerCase().contains('introuvable')) {
      return 'Évaluation introuvable.';
    }
    return error.replaceFirst('Exception: ', '');
  }
}
