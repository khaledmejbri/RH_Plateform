import 'dart:math' as math;

/// Version du modèle d'embedding envoyée au serveur (`modele_version`, 32 caractères au plus).
/// Tout changement de modèle = nouvelle version et nouveau calibrage du point `t`.
abstract final class FaceModelInfo {
  static const version = 'mfn-qaihub-1';
  static const assetPath = 'assets/models/mobilefacenet.tflite';
  static const tailleEntree = 112;
  static const tailleEmbedding = 128;

  /// Normalisation des pixels : `(p - moyenne) / ecart`. À valider avec le modèle réellement déposé
  /// (voir `assets/models/LISEZMOI.md`).
  static const entreeMoyenne = 127.5;
  static const entreeEcart = 128.0;

  /// Point de fonctionnement `t` (cosinus) de la conversion en score produit ; départ 0,60,
  /// à calibrer sur vrais téléphones et lié à [version].
  static const pointFonctionnement = 0.60;
}

/// Normalise un vecteur (norme L2 = 1). Vecteur nul : renvoyé tel quel.
List<double> normaliserL2(List<double> v) {
  var somme = 0.0;
  for (final x in v) {
    somme += x * x;
  }
  final norme = math.sqrt(somme);
  if (norme == 0) return List<double>.of(v);
  return [for (final x in v) x / norme];
}

/// Cosinus entre deux vecteurs de même taille (0 si l'un est nul).
double cosinus(List<double> a, List<double> b) {
  if (a.length != b.length) {
    throw ArgumentError('Vecteurs de tailles différentes (${a.length} / ${b.length}).');
  }
  var prod = 0.0, na = 0.0, nb = 0.0;
  for (var i = 0; i < a.length; i++) {
    prod += a[i] * b[i];
    na += a[i] * a[i];
    nb += b[i] * b[i];
  }
  if (na == 0 || nb == 0) return 0;
  return prod / (math.sqrt(na) * math.sqrt(nb));
}

/// Meilleur cosinus entre l'embedding du pointage et chacun des embeddings d'enrôlement (ADR § 1).
double meilleurCosinus(List<double> embedding, List<List<double>> gabarit) {
  if (gabarit.isEmpty) return 0;
  var best = -1.0;
  for (final g in gabarit) {
    final c = cosinus(embedding, g);
    if (c > best) best = c;
  }
  return best;
}

/// Conversion cosinus -> score produit 0-100 (formule de l'ADR, linéaire par morceaux autour de `t`) :
/// `cos <= t ? 92 * max(cos, 0) / t : 92 + 8 * (cos - t) / (1 - t)`, arrondi et borné.
/// Le seuil de décision n'est jamais connu de l'appareil : le serveur décide.
int scoreDepuisCosinus(double cos, {double t = FaceModelInfo.pointFonctionnement}) {
  assert(t > 0 && t < 1);
  final brut = cos <= t ? 92 * math.max(cos, 0) / t : 92 + 8 * (cos - t) / (1 - t);
  final arrondi = brut.round();
  if (arrondi < 0) return 0;
  if (arrondi > 100) return 100;
  return arrondi;
}
