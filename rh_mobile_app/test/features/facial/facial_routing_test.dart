import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rh_mobile_app/features/facial/data/facial_errors.dart';
import 'package:rh_mobile_app/features/facial/data/facial_models.dart';
import 'package:rh_mobile_app/features/facial/data/facial_repository.dart';
import 'package:rh_mobile_app/features/facial/presentation/compte_a_rebours.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_errors.dart';
import 'package:rh_mobile_app/features/pointage/data/presence_models.dart';

EtatFacialMoi _etat({
  bool actif = true,
  bool controle = true,
  String statut = FacialStatut.nonEnrole,
  bool reconsent = false,
  bool ecran = false,
  bool? appareil,
  bool incompatible = false,
  bool effacer = false,
}) =>
    EtatFacialMoi(
      verificationActive: actif,
      controleExige: controle,
      statut: statut,
      reconsentementRequis: reconsent,
      ecranConsentementRequis: ecran,
      appareilCorrespond: appareil,
      appareilIncompatible: incompatible,
      gabaritAEffacer: effacer,
    );

FacialDecision _d(EtatFacialMoi? e, {bool gabarit = false, bool natif = true}) =>
    decider(e, gabaritLocal: gabarit, plateformeNative: natif);

void main() {
  group('routage consentement / reconsentement', () {
    test('réglage INACTIVE : aucun écran', () {
      expect(_d(_etat(actif: false, ecran: true)).route, FacialRoute.aucune);
    });
    test('état inconnu (GET /moi en échec) : aucun écran, accueil non bloqué', () {
      expect(_d(null).route, FacialRoute.aucune);
    });
    test('actif et NON_ENROLE : écran de consentement', () {
      expect(_d(_etat(ecran: true)).route, FacialRoute.consentement);
    });
    test('REFUSE : pas d\'écran (l\'écran ne revient pas)', () {
      expect(_d(_etat(statut: FacialStatut.refuse)).route, FacialRoute.aucune);
    });
    test('ENROLE avec nouvelle version : reconsentement', () {
      final d = _d(_etat(statut: FacialStatut.enrole, reconsent: true, ecran: true, appareil: true), gabarit: true);
      expect(d.route, FacialRoute.reconsentement);
    });
    test('web : aucun écran facial', () {
      expect(_d(_etat(ecran: true), natif: false).route, FacialRoute.aucune);
    });
    test('appareil incompatible : aucun écran', () {
      expect(_d(_etat(ecran: true, incompatible: true)).route, FacialRoute.aucune);
      expect(
        decider(_etat(ecran: true), gabaritLocal: false, plateformeNative: true, incompatibleLocal: true).route,
        FacialRoute.aucune,
      );
    });
    test('gabarit_a_effacer : effacement demandé seulement s\'il y a un gabarit, même INACTIVE', () {
      expect(_d(_etat(actif: false, effacer: true), gabarit: true).effacerGabarit, isTrue);
      expect(_d(_etat(effacer: true), gabarit: false).effacerGabarit, isFalse);
    });
    test('ré-enrôlement proposé : autre appareil ou gabarit perdu', () {
      expect(_d(_etat(statut: FacialStatut.enrole, appareil: false)).reenrolementProposable, isTrue);
      expect(_d(_etat(statut: FacialStatut.enrole, appareil: true), gabarit: false).reenrolementProposable, isTrue);
      expect(_d(_etat(statut: FacialStatut.enrole, appareil: true), gabarit: true).reenrolementProposable, isFalse);
      expect(_d(_etat(statut: FacialStatut.aReenroler, appareil: false)).reenrolementProposable, isFalse);
    });
  });

  group('étape visage au pointage', () {
    bool requis(EtatFacialMoi? e, {bool gabarit = true, bool natif = true}) =>
        controleFacialRequis(e, gabaritLocal: gabarit, plateformeNative: natif);

    test('ENROLE sur son appareil avec gabarit, contrôle exigé : oui', () {
      expect(requis(_etat(statut: FacialStatut.enrole, appareil: true)), isTrue);
      expect(requis(_etat(statut: FacialStatut.aReenroler, appareil: true)), isTrue);
    });
    test('réglage INACTIVE ou date non atteinte : non', () {
      expect(requis(_etat(actif: false, statut: FacialStatut.enrole, appareil: true)), isFalse);
      expect(requis(_etat(controle: false, statut: FacialStatut.enrole, appareil: true)), isFalse);
    });
    test('autre appareil, gabarit absent, web, REFUSE, état inconnu : non', () {
      expect(requis(_etat(statut: FacialStatut.enrole, appareil: false)), isFalse);
      expect(requis(_etat(statut: FacialStatut.enrole, appareil: true), gabarit: false), isFalse);
      expect(requis(_etat(statut: FacialStatut.enrole, appareil: true), natif: false), isFalse);
      expect(requis(_etat(statut: FacialStatut.refuse)), isFalse);
      expect(requis(null), isFalse);
    });
  });

  test('plateforme envoyée', () {
    expect(plateformeApi(web: true, ios: false), 'WEB');
    expect(plateformeApi(web: false, ios: true), 'IOS');
    expect(plateformeApi(web: false, ios: false), 'ANDROID');
  });

  test('EtatFacialMoi.fromJson (contrat)', () {
    final e = EtatFacialMoi.fromJson({
      'verification_active': true,
      'controle_exige': true,
      'statut': 'ENROLE',
      'version_consentement_courante': 'v2',
      'reconsentement_requis': true,
      'appareil_correspond': true,
      'echecs_consecutifs': 2,
      'bloque_jusqu_a': '2026-10-30T08:15:00Z',
    });
    expect(e.estEnrole, isTrue);
    expect(e.reconsentementRequis, isTrue);
    expect(e.echecsConsecutifs, 2);
    expect(e.estBloque(DateTime.utc(2026, 10, 30, 8, 10)), isTrue);
    expect(e.estBloque(DateTime.utc(2026, 10, 30, 8, 16)), isFalse);
  });

  group('pointage : contrat', () {
    PointageRequest req({int? score, bool? vivant}) => PointageRequest(
          qrToken: 'qr',
          type: TypePointage.entree,
          latitude: 1,
          longitude: 2,
          deviceId: 'dev-1',
          idempotencyKey: 'k',
          plateforme: 'ANDROID',
          scoreFacial: score,
          vivaciteReussie: vivant,
          modeleFacialVersion: score != null ? 'mfn-qaihub-1' : null,
        );

    test('device_id et plateforme toujours envoyés, champs faciaux absents sans étape visage', () {
      final j = req().toJson();
      expect(j['device_id'], 'dev-1');
      expect(j['plateforme'], 'ANDROID');
      expect(j.containsKey('score_facial'), isFalse);
      expect(j.containsKey('vivacite_reussie'), isFalse);
    });
    test('score et vivant envoyés ; jamais d\'image ni de vecteur', () {
      final j = req(score: 95, vivant: true).toJson();
      expect(j['score_facial'], 95);
      expect(j['vivacite_reussie'], true);
      expect(j['modele_facial_version'], 'mfn-qaihub-1');
      expect(j.keys.where((k) => k.contains('image') || k.contains('embedding') || k.contains('vecteur')), isEmpty);
    });
    test('vivant échoué : false sans score', () {
      final j = req(vivant: false).toJson();
      expect(j['vivacite_reussie'], false);
      expect(j.containsKey('score_facial'), isFalse);
    });

    test('réponse : champs faciaux', () {
      final r = PointageResult.fromJson({
        'id': 'p1',
        'statut': 'REJETE_VISAGE_NON_RECONNU',
        'verification_faciale': null,
        'score_facial': 70,
        'vivacite_reussie': true,
        'echecs_consecutifs': 3,
        'bloque_jusqu_a': '2026-10-30T08:15:00Z',
      });
      expect(r.scoreFacial, 70);
      expect(r.echecsConsecutifs, 3);
      expect(r.bloqueJusquA, isNotNull);
      expect(PointageResult.fromJson({'statut': 'VALIDE', 'verification_faciale': 'NON_DISPONIBLE'}).sansVerificationFaciale,
          isTrue);
    });

    test('nouveaux rejets : messages en français', () {
      expect(messageMotifPointage(PointageStatut.rejeteVisageNonReconnu), contains('non reconnu'));
      expect(messageMotifPointage(PointageStatut.rejeteVivacite), contains('vivant'));
      expect(messageMotifPointage(PointageStatut.rejeteVerificationFacialeAbsente), contains('enrôlement'));
      expect(PointageStatut.estRejet(PointageStatut.rejeteVivacite), isTrue);
    });

    DioException conflit(Object? data, int statut) => DioException(
          requestOptions: RequestOptions(path: '/pointages'),
          response: Response(requestOptions: RequestOptions(path: '/pointages'), statusCode: statut, data: data),
        );

    test('409 POINTAGE_BLOQUE_VISAGE : fin du blocage lue', () {
      final fin = blocageVisageJusqua(conflit({
        'code': 'POINTAGE_BLOQUE_VISAGE',
        'message': 'bloqué',
        'bloque_jusqu_a': '2026-10-30T08:15:00Z',
        'http_status': 409,
      }, 409));
      expect(fin, DateTime.utc(2026, 10, 30, 8, 15));
    });
    test('autres erreurs : pas un blocage', () {
      expect(blocageVisageJusqua(conflit({'code': 'AUTRE'}, 409)), isNull);
      expect(blocageVisageJusqua(conflit({'code': 'POINTAGE_BLOQUE_VISAGE'}, 400)), isNull);
      expect(blocageVisageJusqua(Exception('x')), isNull);
    });
  });

  group('erreurs nommées', () {
    test('code connu : message français ; inconnu : message du serveur', () {
      expect(messageErreurFaciale(const FacialApiException(code: 'REENROLEMENT_A_DEBLOQUER', message: 'x')),
          contains('déblocage'));
      expect(messageErreurFaciale(const FacialApiException(code: 'INCONNU', message: 'Message serveur')),
          'Message serveur');
    });
    test('réseau coupé : erreur à rejouer', () {
      final e = FacialApiException.depuis(DioException(
        requestOptions: RequestOptions(path: '/x'),
        type: DioExceptionType.connectionError,
      ));
      expect(e.reseau, isTrue);
    });
    test('409 : refus définitif avec code', () {
      final e = FacialApiException.depuis(DioException(
        requestOptions: RequestOptions(path: '/x'),
        response: Response(
          requestOptions: RequestOptions(path: '/x'),
          statusCode: 409,
          data: {'code': 'DEJA_ENROLE', 'message': 'déjà'},
        ),
      ));
      expect(e.reseau, isFalse);
      expect(e.code, 'DEJA_ENROLE');
    });
  });

  test('compte à rebours mm:ss', () {
    expect(formaterCompteARebours(const Duration(minutes: 15)), '15:00');
    expect(formaterCompteARebours(const Duration(seconds: 61)), '01:01');
    expect(formaterCompteARebours(const Duration(milliseconds: 500)), '00:01');
    expect(formaterCompteARebours(const Duration(seconds: -3)), '00:00');
  });
}
