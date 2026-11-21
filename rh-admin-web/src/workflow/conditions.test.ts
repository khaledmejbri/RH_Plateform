import { describe, expect, it } from 'vitest';
import type { CatalogueWorkflow, ConditionWorkflow } from '../api/workflowApi';
import {
  compterFeuillesCondition,
  profondeurCondition,
  resumeCondition,
} from './conditions';

const CATALOGUE_TEST: Pick<CatalogueWorkflow, 'types' | 'variables_communes'> = {
  types: [
    {
      objet_type: 'CONGE',
      libelle: 'Congé',
      service_proprietaire: 'svc-referentiel-rh',
      chemin_effet: '/internal/referentiel/v1/workflow/effets',
      effets: ['APPROUVER'],
      validation_humaine_obligatoire: true,
      supporte_complement: true,
      variables: [
        { nom: 'jours_decomptes', type: 'DECIMAL', libelle: 'Jours décomptés', valeurs: null, source_valeurs: null },
        { nom: 'type_conge', type: 'LISTE', libelle: 'Type de congé', valeurs: null, source_valeurs: null },
        { nom: 'piece_jointe_fournie', type: 'BOOLEEN', libelle: 'Pièce jointe fournie', valeurs: null, source_valeurs: null },
      ],
    },
  ],
  variables_communes: [
    { nom: 'demandeur_est_manager', type: 'BOOLEEN', libelle: 'Le demandeur est manager', valeurs: null, source_valeurs: null },
  ],
};

describe('conditions — resumeCondition, profondeur et comptage (D-W7-06)', () => {
  it('formate une condition ET avec nombre et liste traduite en français', () => {
    const cond: ConditionWorkflow = {
      et: [
        { champ: 'jours_decomptes', op: 'sup', valeur: 10 },
        { champ: 'type_conge', op: 'dans', valeur: ['ANNUEL'] },
      ],
    };
    const res = resumeCondition(cond, CATALOGUE_TEST, { ANNUEL: 'Annuel' }, 'CONGE');
    expect(res).toBe('si Jours décomptés > 10 et Type de congé parmi Annuel');
  });

  it('formate une négation, un booléen et un opérateur sans valeur', () => {
    const cond: ConditionWorkflow = {
      ou: [
        { non: { champ: 'piece_jointe_fournie', op: 'egal', valeur: true } },
        { champ: 'type_conge', op: 'vide' },
      ],
    };
    const res = resumeCondition(cond, CATALOGUE_TEST, undefined, 'CONGE');
    expect(res).toBe('si non (Pièce jointe fournie = Oui) ou Type de congé est vide');
  });

  it('calcule correctement la profondeur et le nombre de feuilles', () => {
    const cond: ConditionWorkflow = {
      et: [
        { champ: 'jours_decomptes', op: 'sup', valeur: 5 },
        {
          ou: [
            { non: { champ: 'demandeur_est_manager', op: 'egal', valeur: true } },
            { champ: 'type_conge', op: 'egal', valeur: 'ANNUEL' },
          ],
        },
      ],
    };
    expect(profondeurCondition(cond)).toBe(4);
    expect(compterFeuillesCondition(cond)).toBe(3);
  });

  it('retourne un texte lisible quand la condition est absente', () => {
    expect(resumeCondition(null, CATALOGUE_TEST)).toContain('Toujours appliquée');
  });
});
