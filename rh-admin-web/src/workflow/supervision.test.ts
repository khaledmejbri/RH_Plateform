import { describe, expect, it } from 'vitest';
import {
  libelleEvenementHistorique,
  libelleOrigineCandidat,
  libelleOrigineDelegation,
  libelleStatutInstance,
  STATUT_INSTANCE,
} from './libelles';
import {
  diffJours,
  formatDelaiHeures,
  formatEvenementHistorique,
  formatHistoriqueDetails,
  formatObjetTypesDelegation,
  formatOrigineCandidat,
  libelleStatutDelegation,
  libelleVariableDossier,
  periodeParDefaut30Jours,
  routeNotificationWorkflow,
  statutDelegation,
  validerFormulaireDelegation,
  validerReaffectation,
} from './supervision';

describe('supervision — statutDelegation', () => {
  const ref = '2026-11-22';

  it('distingue Annulée, Active, Planifiée et Expirée', () => {
    expect(
      statutDelegation({ annulee_le: '2026-11-21T10:00:00Z', active: true, du: '2026-11-20' }, ref),
    ).toBe('ANNULEE');
    expect(
      statutDelegation({ annulee_le: null, active: true, du: '2026-11-20' }, ref),
    ).toBe('ACTIVE');
    expect(
      statutDelegation({ annulee_le: null, active: false, du: '2026-11-23' }, ref),
    ).toBe('PLANIFIEE');
    expect(
      statutDelegation({ annulee_le: null, active: false, du: '2026-11-22' }, ref),
    ).toBe('EXPIREE');
    expect(
      statutDelegation({ annulee_le: null, active: false, du: '2026-11-01' }, ref),
    ).toBe('EXPIREE');
  });

  it('retourne les libellés français attendus', () => {
    expect(libelleStatutDelegation('ACTIVE')).toBe('Active');
    expect(libelleStatutDelegation('PLANIFIEE')).toBe('Planifiée');
    expect(libelleStatutDelegation('EXPIREE')).toBe('Expirée');
    expect(libelleStatutDelegation('ANNULEE')).toBe('Annulée');
  });
});

describe('supervision — validerFormulaireDelegation', () => {
  const aujourdhui = '2026-11-22';
  const base = {
    delegant_id: 'col-1',
    delegue_id: 'col-2',
    du: '2026-11-22',
    au: '2026-11-25',
    motif: '',
  };

  it('accepte une délégation valide jusqu’à du + 90 jours inclus', () => {
    expect(diffJours('2026-11-22', '2027-02-20')).toBe(90);
    const res = validerFormulaireDelegation({ ...base, au: '2027-02-20' }, aujourdhui);
    expect(res.valide).toBe(true);
    expect(res.erreurs).toEqual({});
  });

  it('refuse du + 91 jours (aligné sur DelegationService.java:459)', () => {
    expect(diffJours('2026-11-22', '2027-02-21')).toBe(91);
    const res = validerFormulaireDelegation({ ...base, au: '2027-02-21' }, aujourdhui);
    expect(res.valide).toBe(false);
    expect(res.erreurs.au).toContain('90 jours');
  });

  it('refuse du antérieur à aujourd’hui et au antérieur à du', () => {
    const resDu = validerFormulaireDelegation({ ...base, du: '2026-11-21' }, aujourdhui);
    expect(resDu.valide).toBe(false);
    expect(resDu.erreurs.du).toBeDefined();

    const resAu = validerFormulaireDelegation({ ...base, du: '2026-11-25', au: '2026-11-24' }, aujourdhui);
    expect(resAu.valide).toBe(false);
    expect(resAu.erreurs.au).toBeDefined();
  });

  it('refuse délégant identique au délégué', () => {
    const res = validerFormulaireDelegation({ ...base, delegue_id: 'col-1' }, aujourdhui);
    expect(res.valide).toBe(false);
    expect(res.erreurs.delegue_id).toContain('distincts');
  });

  it('accepte un motif de 300 caractères et refuse 301 caractères', () => {
    expect(validerFormulaireDelegation({ ...base, motif: 'x'.repeat(300) }, aujourdhui).valide).toBe(true);
    const ko = validerFormulaireDelegation({ ...base, motif: 'x'.repeat(301) }, aujourdhui);
    expect(ko.valide).toBe(false);
    expect(ko.erreurs.motif).toContain('300');
  });

  it('refuse une sélection par liste vide', () => {
    const ko = validerFormulaireDelegation(
      { ...base, objet_types_selection_mode: 'LISTE', objet_types: [] },
      aujourdhui,
    );
    expect(ko.valide).toBe(false);
    expect(ko.erreurs.objet_types).toBeDefined();
  });
});

describe('supervision — validerReaffectation', () => {
  it('refuse 0 collaborateur et > 10 collaborateurs, accepte 1 à 10 collaborateurs', () => {
    expect(validerReaffectation([], 'Motif valide').valide).toBe(false);
    expect(validerReaffectation(['c1'], 'Motif valide').valide).toBe(true);
    const dix = Array.from({ length: 10 }, (_, i) => `c${i + 1}`);
    expect(validerReaffectation(dix, 'Motif valide').valide).toBe(true);
    expect(validerReaffectation([...dix, 'c1'], 'Motif valide').valide).toBe(true); // dédoublonné à 10
    const onze = Array.from({ length: 11 }, (_, i) => `c${i + 1}`);
    const res11 = validerReaffectation(onze, 'Motif valide');
    expect(res11.valide).toBe(false);
    expect(res11.erreurs.collaborateur_ids).toContain('10');
  });

  it('exige un motif entre 5 et 500 caractères (espaces de bord exclus)', () => {
    expect(validerReaffectation(['c1'], '  abcd  ').valide).toBe(false);
    expect(validerReaffectation(['c1'], '  abcde ').valide).toBe(true);
    expect(validerReaffectation(['c1'], 'x'.repeat(500)).valide).toBe(true);
    const res501 = validerReaffectation(['c1'], 'x'.repeat(501));
    expect(res501.valide).toBe(false);
    expect(res501.erreurs.motif).toContain('500');
  });
});

describe('supervision — libellés et formatage', () => {
  it('réutilise STATUT_INSTANCE et couvre les origines de candidat et délégation', () => {
    expect(Object.keys(STATUT_INSTANCE)).toContain('BLOQUEE');
    expect(libelleStatutInstance('BLOQUEE')).toBe('Bloquée');
    expect(libelleStatutInstance('INCONNU')).toBe('Valeur non reconnue');

    expect(libelleOrigineCandidat('TITULAIRE')).toBe('Titulaire');
    expect(libelleOrigineCandidat('DELEGUE_AUTO')).toBe('Délégué automatique (congé)');
    expect(formatOrigineCandidat('TITULAIRE', null)).toBe('Titulaire');
    expect(formatOrigineCandidat('DELEGUE', 'Omar Belhadj')).toBe('Délégué (pour le compte de Omar Belhadj)');
    expect(formatOrigineCandidat('REAFFECTATION', null)).toBe('Réaffectation RH');

    expect(libelleOrigineDelegation('MANUELLE')).toBe('Manuelle');
    expect(libelleOrigineDelegation('AUTO_CONGE')).toBe('Automatique (congé)');
  });

  it('formate les événements historiques et leurs détails', () => {
    expect(libelleEvenementHistorique('REAFFECTEE')).toBe('Tâche réaffectée');
    expect(
      formatEvenementHistorique({
        type_evenement: 'REAFFECTEE',
        details: { nouveaux_candidats: ['Amel Trabelsi', 'Nizar Gharbi'], motif: 'Absence imprévue', debloquee: true },
      }),
    ).toBe('Tâche réaffectée — Vers : Amel Trabelsi, Nizar Gharbi · « Absence imprévue » · instance débloquée');

    expect(
      formatEvenementHistorique({
        type_evenement: 'DECISION',
        details: { action: 'REFUSER', commentaire: 'Solde insuffisant' },
      }),
    ).toBe('Décision enregistrée — Refuser — « Solde insuffisant »');

    expect(
      formatHistoriqueDetails(
        { type_evenement: 'ETAPE_SAUTEE', details: { etape_code: 'E1', raison: 'CONDITION' } },
        new Map([['E1', 'Validation directeur']]),
      ),
    ).toBe('Validation directeur (Condition non remplie)');

    expect(formatEvenementHistorique({ type_evenement: 'RELANCE', details: { nb_relances: 2 } })).toBe(
      'Relance envoyée — Relance n° 2',
    );
  });

  it('formate les délais en heures avec virgule française, les types couverts et la période 30 j', () => {
    expect(formatDelaiHeures(12.5)).toBe('12,5 h');
    expect(formatDelaiHeures(null)).toBe('—');

    const cat = { types: [{ objet_type: 'CONGE', libelle: 'Congé' }, { objet_type: 'DOCUMENT', libelle: 'Document administratif' }] } as never;
    expect(formatObjetTypesDelegation(null, cat)).toBe('Tous les types');
    expect(formatObjetTypesDelegation(['CONGE', 'DOCUMENT'], cat)).toBe('Congé, Document administratif');

    const p = periodeParDefaut30Jours(new Date('2026-11-22T12:00:00Z'));
    expect(p).toEqual({ du: '2026-10-23', au: '2026-11-22' });

    const libCat = new Map([['jours_decomptes', 'Jours décomptés']]);
    expect(libelleVariableDossier('jours_decomptes', libCat)).toBe('Jours décomptés');
    expect(libelleVariableDossier('affichage_periode', libCat)).toBe('Période affichée');
    expect(libelleVariableDossier('affichage_sla', libCat)).toBe('Échéance réglementaire');
    expect(libelleVariableDossier('champ_personnalise', libCat)).toBe('Champ personnalise');
  });
});

describe('supervision — routage des notifications WF_*', () => {
  it('route WF_TACHE_CREEE, WF_RELANCE et WF_ESCALADE vers Mes tâches', () => {
    for (const ev of ['WF_TACHE_CREEE', 'WF_RELANCE', 'WF_ESCALADE']) {
      expect(routeNotificationWorkflow({ evenement: ev, objet_type: 'CONGE', objet_id: 'obj-1' })).toBe(
        '/app/taches?objet_type=CONGE&objet_id=obj-1',
      );
    }
  });

  it('route WF_BLOQUEE vers Workflows en cours filtré BLOQUEE et WF_DELEGATION vers Délégations', () => {
    expect(routeNotificationWorkflow({ evenement: 'WF_BLOQUEE', objet_type: 'CONGE', objet_id: 'obj-1' })).toBe(
      '/app/workflows/instances?statut=BLOQUEE',
    );
    expect(routeNotificationWorkflow({ evenement: 'WF_DELEGATION', objet_type: 'DELEGATION', objet_id: 'del-1' })).toBe(
      '/app/workflows/delegations',
    );
  });

  it('laisse les autres événements ouvrir la fiche métier', () => {
    expect(routeNotificationWorkflow({ evenement: 'WF_DECISION', objet_type: 'CONGE', objet_id: 'obj-1' })).toBeNull();
    expect(routeNotificationWorkflow({ evenement: 'WF_FIN', objet_type: 'DOCUMENT', objet_id: 'doc-1' })).toBeNull();
    expect(routeNotificationWorkflow({ evenement: null })).toBeNull();
  });
});
