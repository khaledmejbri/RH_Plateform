import 'package:flutter/material.dart';

import '../../../core/format/formats.dart';
import '../../../core/theme/app_theme.dart';

/// Carte blanche avec un titre de section.
class SectionDossier extends StatelessWidget {
  const SectionDossier({super.key, required this.titre, required this.children});

  final String titre;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.border, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titre, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
          const SizedBox(height: 10),
          ...children,
        ],
      ),
    );
  }
}

/// Ligne « libellé : valeur ».
class LigneInfo extends StatelessWidget {
  const LigneInfo({super.key, required this.libelle, required this.valeur});

  final String libelle;
  final String? valeur;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(libelle, style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary)),
          ),
          Expanded(
            child: SelectableText(
              (valeur == null || valeur!.isEmpty) ? kValeurAbsente : valeur!,
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

/// Personne cliquable : initiale, nom, fonction et unité.
class LignePersonne extends StatelessWidget {
  const LignePersonne({super.key, required this.nom, this.sousTitre, this.onTap});

  final String nom;
  final String? sousTitre;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      leading: CircleAvatar(
        radius: 18,
        backgroundColor: AppTheme.primarySurface,
        child: Text(
          initialeDe(nom),
          style: const TextStyle(color: AppTheme.primary, fontWeight: FontWeight.w700),
        ),
      ),
      title: Text(nom, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: (sousTitre == null || sousTitre!.isEmpty) ? null : Text(sousTitre!),
      trailing: onTap == null ? null : const Icon(Icons.chevron_right_rounded, size: 20),
      onTap: onTap,
    );
  }
}
