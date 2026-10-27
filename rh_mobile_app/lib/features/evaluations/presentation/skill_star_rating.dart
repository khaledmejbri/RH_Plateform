import 'package:flutter/material.dart';

import '../../../core/theme/app_theme.dart';
import '../data/evaluation_models.dart';

/// Notation d'une compétence technique en étoiles 1 à 5 (doc § 12 : « étoiles 1–5 »).
///
/// Une étoile = Débutant … cinq étoiles = Expert ; le libellé du niveau choisi
/// est affiché à côté pour ne pas reposer sur la seule couleur.
class SkillStarRating extends StatelessWidget {
  const SkillStarRating({
    super.key,
    required this.value,
    this.onChanged,
    this.size = 32,
  });

  final SkillLevel? value;

  /// null = lecture seule.
  final ValueChanged<SkillLevel>? onChanged;
  final double size;

  /// Ambre foncé : contraste ≥ 3:1 sur fond blanc (élément graphique).
  static const Color starColor = Color(0xFFD97706);

  @override
  Widget build(BuildContext context) {
    final note = value?.score ?? 0;
    final readOnly = onChanged == null;
    return Semantics(
      label: value == null
          ? 'Compétence non notée'
          : 'Note : $note étoile${note > 1 ? 's' : ''} sur 5, ${value!.label}',
      readOnly: readOnly,
      child: Row(
        children: [
          ...SkillLevel.values.map((level) {
            final filled = level.score <= note;
            final icon = Icon(
              filled ? Icons.star_rounded : Icons.star_outline_rounded,
              size: size,
              color: filled ? starColor : AppTheme.inputBorder,
            );
            if (readOnly) {
              return Padding(
                padding: const EdgeInsets.only(right: 2),
                child: ExcludeSemantics(child: icon),
              );
            }
            return Semantics(
              button: true,
              selected: level == value,
              label: '${level.score} sur 5, ${level.label}',
              excludeSemantics: true,
              child: InkResponse(
                onTap: () => onChanged!(level),
                radius: size * 0.75,
                child: SizedBox(
                  width: 44,
                  height: 44,
                  child: Center(child: icon),
                ),
              ),
            );
          }),
          const SizedBox(width: 8),
          Flexible(
            child: ExcludeSemantics(
              child: Text(
                value?.label ?? (readOnly ? 'Non noté' : 'Touchez une étoile'),
                style: TextStyle(
                  fontSize: readOnly ? 13 : 14,
                  fontWeight: value == null ? FontWeight.w400 : FontWeight.w600,
                  color: value == null ? AppTheme.textTertiary : AppTheme.textPrimary,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
