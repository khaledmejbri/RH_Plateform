import 'face_math.dart';

/// Preuve faciale jointe au pointage : un score 0-100 et le résultat du vivant. Jamais d'image ni de vecteur.
class FaceProof {
  const FaceProof({this.score, this.vivacite});

  /// Aucune preuve (caméra ou gabarit indisponible) : le serveur décide (rejet « vérification absente »).
  const FaceProof.aucune()
      : score = null,
        vivacite = null;

  /// Vivacité échouée : transmise sans score (comptée comme échec par le serveur, D-P08).
  const FaceProof.vivaciteEchouee()
      : score = null,
        vivacite = false;

  /// Calcule la preuve : meilleur cosinus contre les embeddings d'enrôlement, converti en score produit.
  factory FaceProof.depuisEmbedding(List<double> embedding, List<List<double>> gabarit) {
    final cos = meilleurCosinus(embedding, gabarit);
    return FaceProof(score: scoreDepuisCosinus(cos), vivacite: true);
  }

  final int? score;
  final bool? vivacite;

  bool get aPreuve => vivacite != null;
}
