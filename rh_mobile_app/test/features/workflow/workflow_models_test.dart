import 'package:flutter_test/flutter_test.dart';

import 'package:rh_mobile_app/core/format/formats.dart';
import 'package:rh_mobile_app/features/organigramme/data/organigramme_models.dart';
import 'package:rh_mobile_app/features/workflow/data/workflow_models.dart';

void main() {
  group('PageResponse', () {
    test('parse elements, page, taille, total et calcule aPlus', () {
      final page = PageResponse.fromJson({
        'elements': [
          {
            'tache_id': 't1',
            'instance_id': 'i1',
            'type': 'VALIDATION',
            'libelle_etape': 'Validation manager',
            'objet_type': 'CONGE',
            'objet_libelle': 'Congé annuel',
            'objet_numero': 'DEM-202611-0001',
            'objet_id': 'o1',
            'demandeur_nom': 'Sarra Khemiri',
            'en_retard': true,
            'origine': 'DELEGATION',
            'pour_nom': 'Omar Belhadj',
            'actions_permises': ['APPROUVER', 'REFUSER'],
            'version': 3,
            'resume': {'affichage_periode': 'du 01/12/2026 au 03/12/2026'},
          },
        ],
        'page': 0,
        'taille': 20,
        'total': 25,
      }, TacheListeItem.fromJson);

      expect(page.elements.length, 1);
      expect(page.page, 0);
      expect(page.taille, 20);
      expect(page.total, 25);
      expect(page.aPlus, isTrue);

      final item = page.elements.single;
      expect(item.tacheId, 't1');
      expect(item.libelleEtape, 'Validation manager');
      expect(item.enRetard, isTrue);
      expect(item.pourNom, 'Omar Belhadj');
      expect(item.actionsPermises, ['APPROUVER', 'REFUSER']);
      expect(item.version, 3);
    });
  });

  group('TacheDetail et meta liste', () {
    test('lit libelle (sans libelle_etape), fusionne origine/pour_nom, n’expose pas candidats pool', () {
      final detail = TacheDetail.fromJson({
        'tache_id': 't2',
        'instance_id': 'i2',
        'type': 'COMPLEMENT',
        'statut': 'A_TRAITER',
        'libelle': 'Complément demandé sur votre congé',
        'objet_type': 'CONGE',
        'objet_libelle': 'Congé annuel',
        'objet_id': 'o2',
        'en_retard': false,
        'actions_permises': ['FAIT'],
        'commentaire_obligatoire': true,
        'version': 2,
        'resume': {'affichage_periode': 'du 02/12/2026 au 04/12/2026'},
        'suivi': {
          'instance_id': 'i2',
          'objet_type': 'CONGE',
          'objet_id': 'o2',
          'modele_version_numero': 1,
          'statut': 'EN_COURS',
          'etapes': [
            {
              'code': 'E1',
              'libelle': 'Validation RH',
              'statut': 'TERMINEE',
              'resultat': 'RENVOYEE',
              'motif': 'Merci de joindre le justificatif médical.',
              'candidats': ['Secrète Pool 1', 'Secrète Pool 2'],
              'intitule_intervenants': 'Ressources humaines',
            },
          ],
        },
      });

      expect(detail.libelle, 'Complément demandé sur votre congé');
      expect(detail.estATraiter, isTrue);
      expect(detail.estComplementATraiter, isTrue);
      expect(detail.commentaireObligatoire, isTrue);
      expect(detail.suivi?.motifRenvoiOuMotif, 'Merci de joindre le justificatif médical.');

      const listeItem = TacheListeItem(
        tacheId: 't2',
        instanceId: 'i2',
        type: 'COMPLEMENT',
        libelleEtape: 'Complément demandé sur votre congé',
        objetType: 'CONGE',
        objetLibelle: 'Congé annuel',
        objetId: 'o2',
        enRetard: false,
        origine: 'DELEGATION',
        pourNom: 'Ali Mansouri',
        actionsPermises: ['FAIT'],
        version: 2,
        resume: {},
      );
      final fusionne = detail.avecMetaListe(listeItem);
      expect(fusionne.origine, 'DELEGATION');
      expect(fusionne.pourNom, 'Ali Mansouri');
    });
  });

  group('TypeOuvert', () {
    test('gère sous_types String TOUS et List<String>, avec repli null = tous ouverts', () {
      final conge = TypeOuvert.fromJson({
        'objet_type': 'CONGE',
        'libelle': 'Congé',
        'sous_types': ['ANNUEL', 'MALADIE'],
      });
      final doc = TypeOuvert.fromJson({
        'objet_type': 'DOCUMENT',
        'libelle': 'Document',
        'sous_types': 'TOUS',
      });

      final liste = [conge, doc];
      expect(estTypeOuvert(liste, 'CONGE', sousType: 'ANNUEL'), isTrue);
      expect(estTypeOuvert(liste, 'CONGE', sousType: 'MATERNITE'), isFalse);
      expect(estTypeOuvert(liste, 'DOCUMENT', sousType: 'ATTESTATION_TRAVAIL'), isTrue);
      expect(estTypeOuvert(liste, 'FORMATION'), isFalse);
      expect(estTypeOuvert(null, 'FORMATION'), isTrue);
    });
  });

  group('Délégations et organigramme', () {
    test('statutDelegation distingue Active, Planifiée, Expirée, Annulée', () {
      final ref = DateTime(2026, 11, 22);

      const annulee = DelegationItem(
        id: '1',
        delegantId: 'u1',
        delegantNom: 'A',
        delegueId: 'u2',
        delegueNom: 'B',
        du: '2026-11-25',
        au: '2026-11-30',
        objetTypes: [],
        active: false,
        annuleeLe: '2026-11-20T10:00:00Z',
      );
      expect(statutDelegation(annulee, ref), StatutDelegation.annulee);

      const active = DelegationItem(
        id: '2',
        delegantId: 'u1',
        delegantNom: 'A',
        delegueId: 'u2',
        delegueNom: 'B',
        du: '2026-11-20',
        au: '2026-11-28',
        objetTypes: [],
        active: true,
      );
      expect(statutDelegation(active, ref), StatutDelegation.active);

      const planifiee = DelegationItem(
        id: '3',
        delegantId: 'u1',
        delegantNom: 'A',
        delegueId: 'u2',
        delegueNom: 'B',
        du: '2026-11-25',
        au: '2026-11-30',
        objetTypes: [],
        active: false,
      );
      expect(statutDelegation(planifiee, ref), StatutDelegation.planifiee);

      const expiree = DelegationItem(
        id: '4',
        delegantId: 'u1',
        delegantNom: 'A',
        delegueId: 'u2',
        delegueNom: 'B',
        du: '2026-11-01',
        au: '2026-11-10',
        objetTypes: [],
        active: false,
      );
      expect(statutDelegation(expiree, ref), StatutDelegation.expiree);
    });

    test('validerDelegationDates accepte du + 90 j et refuse du + 91 j ou avant aujourd’hui', () {
      final auj = DateTime(2026, 11, 22);
      expect(
        validerDelegationDates(
          du: auj,
          au: auj.add(const Duration(days: 90)),
          aujourdhui: auj,
        ),
        isNull,
      );
      expect(
        validerDelegationDates(
          du: auj,
          au: auj.add(const Duration(days: 91)),
          aujourdhui: auj,
        ),
        isNotNull,
      );
      expect(
        validerDelegationDates(
          du: auj.subtract(const Duration(days: 1)),
          au: auj.add(const Duration(days: 2)),
          aujourdhui: auj,
        ),
        isNotNull,
      );
    });

    test('aplatirMembresOrganigramme dédoublonne et exclut le collaborateur courant', () {
      const m1 = OrganigrammeMembre(identifiant: 'c1', matricule: 'M01', prenom: 'Moi', nom: 'Meme');
      const m2 = OrganigrammeMembre(identifiant: 'c2', matricule: 'M02', prenom: 'Nadia', nom: 'Ayari');
      const m3 = OrganigrammeMembre(identifiant: 'c3', matricule: 'M03', prenom: 'Karim', nom: 'Zidi');
      const org = Organigramme(
        racines: [
          OrganigrammeNoeud(
            identifiant: 'n1',
            code: 'D1',
            libelle: 'Direction',
            typeNoeud: 'DIRECTION',
            actif: true,
            manager: m1,
            membres: [m1, m2],
            enfants: [
              OrganigrammeNoeud(
                identifiant: 'n2',
                code: 'S1',
                libelle: 'Service',
                typeNoeud: 'SERVICE',
                actif: true,
                manager: m2,
                membres: [m3],
              ),
            ],
          ),
        ],
      );

      final liste = aplatirMembresOrganigramme(org, exclureIdentifiant: 'c1');
      expect(liste.map((e) => e.identifiant).toList(), ['c3', 'c2']);
    });
  });

  group('Formats Africa/Tunis', () {
    test('formatDateHeureTunisIso convertit un instant UTC en UTC+1', () {
      expect(formatDateHeureTunisIso('2026-11-25T10:30:00Z'), '25/11/2026 11:30');
      expect(formatDateHeureTunisIso('2026-11-25T10:30:00+02:00'), '25/11/2026 09:30');
    });
  });
}
