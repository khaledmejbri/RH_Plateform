import { describe, expect, it } from 'vitest';
import { estConflitWorkflow, WorkflowApiError } from './workflowApi';

describe('estConflitWorkflow', () => {
  it('vrai pour les 409 de version à recharger', () => {
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, 'MODIFIE_ENTRE_TEMPS'))).toBe(true);
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, 'BROUILLON_EXISTANT'))).toBe(true);
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, 'VERSION_NON_BROUILLON'))).toBe(true);
  });

  it('faux pour les autres 409 et les autres statuts', () => {
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, 'MODELE_ACTIF_EXISTANT'))).toBe(false);
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, 'MODELE_DEJA_PUBLIE'))).toBe(false);
    expect(estConflitWorkflow(new WorkflowApiError('x', 409, null))).toBe(false);
    expect(estConflitWorkflow(new WorkflowApiError('x', 422, 'MODIFIE_ENTRE_TEMPS'))).toBe(false);
    expect(estConflitWorkflow(new Error('x'))).toBe(false);
  });
});
