import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Tonalités de statut partagées (mêmes couleurs que l'admin web).
enum RhTone { success, danger, warning, info, neutral }

extension on RhTone {
  Color get fg => switch (this) {
        RhTone.success => AppTheme.success,
        RhTone.danger => AppTheme.errorDark,
        RhTone.warning => AppTheme.warning,
        RhTone.info => AppTheme.primaryDark,
        RhTone.neutral => AppTheme.textSecondary,
      };

  Color get bg => switch (this) {
        RhTone.success => AppTheme.successSurface,
        RhTone.danger => AppTheme.errorSurface,
        RhTone.warning => AppTheme.warningSurface,
        RhTone.info => AppTheme.primarySurface,
        RhTone.neutral => AppTheme.surfaceMuted,
      };

  IconData get icon => switch (this) {
        RhTone.success => Icons.check_rounded,
        RhTone.danger => Icons.close_rounded,
        RhTone.warning => Icons.schedule_rounded,
        RhTone.info => Icons.info_outline_rounded,
        RhTone.neutral => Icons.remove_rounded,
      };
}

/// Déduit la tonalité d'un code statut back-end (EN_ATTENTE_VALIDATION, REJETEE…).
RhTone rhToneForStatus(String? statut) {
  final s = (statut ?? '').toUpperCase();
  if (s.contains('REJET') || s.contains('REFUS') || s.contains('EXPIR') || s.contains('REVOQ')) {
    return RhTone.danger;
  }
  if (s.contains('VALIDE') || s.contains('APPROUV') || s.contains('DISPONIBLE') ||
      s.contains('RESOLU') || s.contains('CONFIRM') || s.contains('INTEGREE') || s == 'ACTIF') {
    return RhTone.success;
  }
  if (s.contains('ATTENTE') || s.contains('VALIDATION') || s.contains('FILE')) return RhTone.warning;
  if (s.contains('COURS') || s.contains('TRAITEMENT') || s.contains('ANALYSE') ||
      s.contains('NOUVEAU') || s.contains('PLANIF')) {
    return RhTone.info;
  }
  return RhTone.neutral;
}

/// Badge de statut : couleur + icône + texte (jamais la couleur seule).
class RhStatusChip extends StatelessWidget {
  const RhStatusChip({super.key, required this.label, this.tone, this.statut});

  final String label;
  final RhTone? tone;

  /// Code back-end : sert à choisir la tonalité si [tone] est absent.
  final String? statut;

  @override
  Widget build(BuildContext context) {
    final t = tone ?? rhToneForStatus(statut ?? label);
    return Semantics(
      label: 'Statut : $label',
      excludeSemantics: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(color: t.bg, borderRadius: BorderRadius.circular(999)),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(t.icon, size: 14, color: t.fg),
            const SizedBox(width: 4),
            Text(
              label,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(color: t.fg),
            ),
          ],
        ),
      ),
    );
  }
}

/// État vide : icône, titre, explication, action facultative.
class RhEmptyState extends StatelessWidget {
  const RhEmptyState({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.action,
  });

  final IconData icon;
  final String title;
  final String? message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppTheme.spaceXl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: const BoxDecoration(color: AppTheme.primarySurface, shape: BoxShape.circle),
              child: Icon(icon, size: 30, color: AppTheme.primary),
            ),
            const SizedBox(height: AppTheme.spaceMd),
            Text(title, style: text.titleMedium, textAlign: TextAlign.center),
            if (message != null) ...[
              const SizedBox(height: AppTheme.spaceXs),
              Text(message!, style: text.bodyMedium, textAlign: TextAlign.center),
            ],
            if (action != null) ...[
              const SizedBox(height: AppTheme.spaceLg),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}

/// État d'erreur avec bouton « Réessayer ».
class RhErrorState extends StatelessWidget {
  const RhErrorState({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return RhEmptyState(
      icon: Icons.cloud_off_rounded,
      title: 'Chargement impossible',
      message: message,
      action: onRetry == null
          ? null
          : OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: const Text('Réessayer'),
              style: OutlinedButton.styleFrom(minimumSize: const Size(160, AppTheme.minTouch)),
            ),
    );
  }
}

/// Message lisible pour l'utilisateur à partir d'une erreur technique.
String rhErrorMessage(Object? error) {
  if (error is DioException) {
    final code = error.response?.statusCode;
    final data = error.response?.data;
    if (data is Map) {
      final msg = data['erreur'] ?? data['message'];
      if (msg is String && msg.trim().isNotEmpty && code != null && code < 500) return msg;
    }
    if (code == 401) return 'Votre session a expiré. Reconnectez-vous.';
    if (code == 403) return 'Vous n’avez pas les droits pour cette action.';
    if (code == 404) return 'Élément introuvable.';
    if (code != null && code >= 500) return 'Le serveur rencontre un problème. Réessayez dans un instant.';
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
        return 'Le serveur met trop de temps à répondre. Vérifiez votre connexion.';
      case DioExceptionType.connectionError:
        return 'Serveur injoignable. Vérifiez votre connexion ou l’adresse du serveur.';
      default:
        break;
    }
    return 'Une erreur réseau est survenue.';
  }
  final text = '${error ?? ''}'.replaceFirst('Exception: ', '').trim();
  return text.isEmpty ? 'Une erreur est survenue.' : text;
}
