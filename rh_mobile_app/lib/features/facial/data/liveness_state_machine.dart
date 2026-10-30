import 'dart:math' as math;

/// Défis de vivacité (D-P09) tirés au hasard sur l'appareil.
enum LivenessChallenge {
  cligner('CLIGNER', 'Clignez des yeux'),
  teteGauche('TETE_GAUCHE', 'Tournez la tête vers la gauche'),
  teteDroite('TETE_DROITE', 'Tournez la tête vers la droite');

  const LivenessChallenge(this.code, this.consigne);
  final String code;
  final String consigne;
}

/// Une image analysée par le détecteur de visages (indépendante de la caméra et de ML Kit).
class FaceObservation {
  const FaceObservation({
    required this.faceCount,
    this.trackingId,
    this.leftEyeOpen,
    this.rightEyeOpen,
    this.headYaw,
  });

  /// Image sans aucun visage.
  const FaceObservation.aucunVisage()
      : faceCount = 0,
        trackingId = null,
        leftEyeOpen = null,
        rightEyeOpen = null,
        headYaw = null;

  final int faceCount;
  final int? trackingId;

  /// Probabilités d'yeux ouverts (0-1), nulles si non classifiées.
  final double? leftEyeOpen;
  final double? rightEyeOpen;

  /// `headEulerAngleY` en degrés.
  final double? headYaw;
}

enum LivenessPhase { defis, attenteFace, reussi, echec }

enum LivenessFailure {
  delai('Temps écoulé. Réessayez.'),
  plusieursVisages('Un seul visage doit être visible. Réessayez.'),
  perteDeSuivi('Le visage a été perdu de vue. Réessayez en restant face à la caméra.'),
  changementDeVisage('Le visage a changé pendant le contrôle. Réessayez.');

  const LivenessFailure(this.message);
  final String message;
}

/// Machine à états de la vivacité, en Dart pur (ADR-M09-FACE § 1, D-P09, D-P09.a).
///
/// - [nbDefis] défis **distincts** tirés parmi les trois ;
/// - chrono de [chrono] lancé à l'affichage du premier défi ([demarrer]) ; `null` = sans limite (enrôlement) ;
/// - un seul visage du début à la fin, même `trackingId` jusqu'à la prise de comparaison ;
/// - après les défis, la prise de comparaison exige un visage de face (|angle Y| < [angleFaceMax]) :
///   la phase passe à [LivenessPhase.reussi] sur cette image, qui est celle à comparer.
///
/// Sens de rotation : [inverserSensRotation] permet de corriger la convention de signe de l'angle selon la
/// plateforme (à valider sur téléphone).
class LivenessStateMachine {
  /// Perte de visage tolérée (image ratée, clignement de la détection) avant de conclure à une perte de suivi.
  static const perteVisageToleree = Duration(milliseconds: 300);

  LivenessStateMachine({
    List<LivenessChallenge>? defis,
    int nbDefis = 2,
    math.Random? random,
    DateTime Function()? horloge,
    this.chrono = const Duration(seconds: 10),
    this.seuilYeuxOuverts = 0.6,
    this.seuilYeuxFermes = 0.3,
    this.angleDefi = 20,
    this.angleFaceMax = 10,
    this.inverserSensRotation = false,
  })  : _horloge = horloge ?? DateTime.now,
        defis = defis ?? tirerDefis(nbDefis, random ?? math.Random.secure()) {
    assert(this.defis.toSet().length == this.defis.length, 'Défis distincts requis');
  }

  /// Tire [nb] défis distincts parmi les trois.
  static List<LivenessChallenge> tirerDefis(int nb, math.Random random) {
    assert(nb >= 1 && nb <= LivenessChallenge.values.length);
    final tous = List<LivenessChallenge>.of(LivenessChallenge.values)..shuffle(random);
    return tous.take(nb).toList(growable: false);
  }

  final List<LivenessChallenge> defis;
  final Duration? chrono;
  final double seuilYeuxOuverts;
  final double seuilYeuxFermes;
  final double angleDefi;
  final double angleFaceMax;
  final bool inverserSensRotation;
  final DateTime Function() _horloge;

  LivenessPhase _phase = LivenessPhase.defis;
  LivenessFailure? _echec;
  int _index = 0;
  DateTime? _debut;
  int? _trackingId;
  DateTime? _perteDepuis;
  bool _clignerYeuxOuvertsVus = false;
  bool _clignerYeuxFermesVus = false;

  LivenessPhase get phase => _phase;
  LivenessFailure? get echec => _echec;
  int get indexDefi => _index;
  bool get termine => _phase == LivenessPhase.reussi || _phase == LivenessPhase.echec;

  /// Défi à afficher, `null` quand tous sont réussis.
  LivenessChallenge? get defiCourant => _index < defis.length ? defis[_index] : null;

  /// Lance le chrono : à appeler quand le premier défi est affiché.
  void demarrer() {
    _debut ??= _horloge();
  }

  /// Temps restant, `null` sans chrono ou avant [demarrer].
  Duration? get tempsRestant {
    final c = chrono;
    final d = _debut;
    if (c == null || d == null) return null;
    final reste = c - _horloge().difference(d);
    return reste.isNegative ? Duration.zero : reste;
  }

  /// Vérifie le chrono sans image (minuteur d'écran).
  LivenessPhase tick() {
    _verifierDelai();
    return _phase;
  }

  /// Traite une image ; renvoie la phase résultante.
  LivenessPhase mettreAJour(FaceObservation obs) {
    if (termine) return _phase;
    demarrer();
    if (_verifierDelai()) return _phase;

    if (obs.faceCount > 1) return _echouer(LivenessFailure.plusieursVisages);
    if (obs.faceCount == 0) {
      // Un visage déjà suivi qui disparaît : tolérance de [perteVisageToleree], puis perte de suivi.
      // Avant tout visage, on attend.
      if (_trackingId != null) {
        _perteDepuis ??= _horloge();
        if (_perteTropLongue()) return _echouer(LivenessFailure.perteDeSuivi);
      }
      return _phase;
    }
    _perteDepuis = null;
    final id = obs.trackingId;
    if (_trackingId == null) {
      _trackingId = id;
    } else if (id != _trackingId) {
      return _echouer(id == null ? LivenessFailure.perteDeSuivi : LivenessFailure.changementDeVisage);
    }

    if (_phase == LivenessPhase.defis) {
      final defi = defiCourant;
      if (defi != null && _defiReussi(defi, obs)) {
        _index++;
        _clignerYeuxOuvertsVus = false;
        _clignerYeuxFermesVus = false;
        if (_index >= defis.length) _phase = LivenessPhase.attenteFace;
      }
    }
    if (_phase == LivenessPhase.attenteFace) {
      final yaw = obs.headYaw;
      if (yaw != null && yaw.abs() < angleFaceMax) {
        _phase = LivenessPhase.reussi;
      }
    }
    return _phase;
  }

  bool _perteTropLongue() {
    final d = _perteDepuis;
    return d != null && _horloge().difference(d) > perteVisageToleree;
  }

  bool _verifierDelai() {
    if (!termine && _perteTropLongue()) {
      _echouer(LivenessFailure.perteDeSuivi);
      return true;
    }
    final c = chrono;
    final d = _debut;
    if (c == null || d == null || termine) return false;
    if (_horloge().difference(d) > c) {
      _echouer(LivenessFailure.delai);
      return true;
    }
    return false;
  }

  LivenessPhase _echouer(LivenessFailure f) {
    _phase = LivenessPhase.echec;
    _echec = f;
    return _phase;
  }

  bool _defiReussi(LivenessChallenge defi, FaceObservation obs) {
    switch (defi) {
      case LivenessChallenge.cligner:
        final g = obs.leftEyeOpen;
        final d = obs.rightEyeOpen;
        if (g == null || d == null) return false;
        if (g > seuilYeuxOuverts && d > seuilYeuxOuverts) {
          if (_clignerYeuxFermesVus) return true; // ouvert -> fermé -> ouvert
          _clignerYeuxOuvertsVus = true;
        } else if (g < seuilYeuxFermes && d < seuilYeuxFermes && _clignerYeuxOuvertsVus) {
          _clignerYeuxFermesVus = true;
        }
        return false;
      case LivenessChallenge.teteGauche:
        final yaw = obs.headYaw;
        if (yaw == null) return false;
        return (inverserSensRotation ? -yaw : yaw) >= angleDefi;
      case LivenessChallenge.teteDroite:
        final yaw = obs.headYaw;
        if (yaw == null) return false;
        return (inverserSensRotation ? -yaw : yaw) <= -angleDefi;
    }
  }
}
