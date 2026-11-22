import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { ConfirmProvider } from '../components/ConfirmDialog';
import DelegationsOnglet from '../detail/contenus/DelegationsOnglet';
import { entreeActive, MENU } from '../layout/AppShell';
import DelegationsPage from '../pages/workflow/DelegationsPage';
import PanneauEtape from '../pages/workflow/PanneauEtape';
import WorkflowsInstancesPage from '../pages/workflow/WorkflowsInstancesPage';

function fauxJwt(roles: string[]): string {
  const entete = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const charge = Buffer.from(JSON.stringify({ sub: 'u1', roles })).toString('base64url');
  return `${entete}.${charge}.sig`;
}

describe('Navigation, routes et droits W7b', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('expose le groupe Workflows sous Mes tâches (accessible RH, ADMIN et DIRECTION)', () => {
    const idxTaches = MENU.findIndex((m) => m.kind === 'lien' && m.id === 'taches');
    const idxWorkflows = MENU.findIndex((m) => m.kind === 'groupe' && m.id === 'workflows');
    expect(idxTaches).toBeGreaterThanOrEqual(0);
    expect(idxWorkflows).toBe(idxTaches + 1);

    const grp = MENU[idxWorkflows];
    expect(grp?.kind).toBe('groupe');
    if (grp?.kind === 'groupe') {
      expect(grp.label).toBe('Workflows');
      expect(grp.enfants).toHaveLength(2);
      expect(grp.enfants[0]).toMatchObject({
        id: 'workflows-instances',
        to: '/app/workflows/instances',
        label: 'Workflows en cours',
      });
      expect(grp.enfants[1]).toMatchObject({
        id: 'workflows-delegations',
        to: '/app/workflows/delegations',
        label: 'Délégations',
      });
      expect(grp.enfants.every((e) => !e.writeOnly && !e.rhAdmin && !e.lectureOnly)).toBe(true);
    }

    expect(entreeActive('/app/workflows/instances', '')).toBe('workflows-instances');
    expect(entreeActive('/app/workflows/instances', '?statut=BLOQUEE')).toBe('workflows-instances');
    expect(entreeActive('/app/workflows/delegations', '')).toBe('workflows-delegations');
    expect(entreeActive('/app/parametres/workflows', '')).toBe('workflows');
  });

  it('déclare /app/workflows/instances et /app/workflows/delegations hors RequireWriteAccess', () => {
    const tree = JSON.stringify(App(), (_k, v) => (typeof v === 'function' ? v.name || 'fn' : v));
    expect(tree).toContain('"/app/workflows/instances"');
    expect(tree).toContain('"/app/workflows/delegations"');

    // Vérifie que les deux routes ne sont pas enfants de RequireWriteAccess
    const idxWrite = tree.indexOf('"RequireWriteAccess"');
    const idxInstances = tree.indexOf('"/app/workflows/instances"');
    const idxDelegations = tree.indexOf('"/app/workflows/delegations"');
    expect(idxWrite).toBeGreaterThan(-1);
    expect(idxInstances).toBeGreaterThan(-1);
    expect(idxDelegations).toBeGreaterThan(-1);
    expect(idxInstances).toBeLessThan(idxWrite);
    expect(idxDelegations).toBeLessThan(idxWrite);
  });

  it('DelegationsOnglet fonctionne hors <Outlet> et masque les actions écriture pour DIRECTION', () => {
    const store = (token: string) => ({
      getItem: (k: string) => (k === 'rh_admin_access_token' ? token : null),
      setItem: () => undefined,
      removeItem: () => undefined,
    });

    // Jeton DIRECTION : aucun bouton d'écriture
    vi.stubGlobal('sessionStorage', store(fauxJwt(['DIRECTION'])));
    const htmlDir = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <DelegationsOnglet id="col-1" />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDir).not.toContain('Nouvelle délégation');

    // Jeton RH : bouton de création visible, toujours sans crash hors <Outlet>
    vi.stubGlobal('sessionStorage', store(fauxJwt(['RH'])));
    const htmlRh = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <DelegationsOnglet id="col-1" />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlRh).toContain('Nouvelle délégation');
  });

  it('WorkflowsInstancesPage et DelegationsPage masquent toute écriture pour DIRECTION et affichent les mentions temps réel', () => {
    const store = (token: string) => ({
      getItem: (k: string) => (k === 'rh_admin_access_token' ? token : null),
      setItem: () => undefined,
      removeItem: () => undefined,
    });

    vi.stubGlobal('sessionStorage', store(fauxJwt(['DIRECTION'])));
    const htmlInst = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/instances']}>
        <WorkflowsInstancesPage />
      </MemoryRouter>,
    );
    expect(htmlInst).toContain('Workflows en cours');
    expect(htmlInst).toContain('Temps réel');
    expect(htmlInst).toContain('Délais moyens par étape');

    const htmlDelDir = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/delegations']}>
        <ConfirmProvider>
          <DelegationsPage />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDelDir).toContain('Délégations');
    expect(htmlDelDir).not.toContain('Nouvelle délégation');

    vi.stubGlobal('sessionStorage', store(fauxJwt(['RH'])));
    const htmlDelRh = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/delegations']}>
        <ConfirmProvider>
          <DelegationsPage />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDelRh).toContain('Nouvelle délégation');
  });

  it('PanneauEtape ne contient plus la note W3 obsolète', () => {
    const html = renderToStaticMarkup(
      <PanneauEtape
        etape={{
          code: 'E1',
          libelle: 'Validation',
          type: 'VALIDATION',
          actions: ['APPROUVER', 'REFUSER'],
          delai: { duree: 24, unite: 'HEURES' },
          a_echeance: { action: 'RIEN' },
        }}
        catalogue={{
          types: [],
          variables_communes: [],
          effets: [],
          regles: [],
          operateurs: {},
          publics: [],
          fonctions: [],
          fonctions_disponibles: false,
        }}
        objetType="CONGE"
        fonctionsActives={[]}
        erreursEtape={[]}
        onChange={() => undefined}
      />,
    );
    expect(html).not.toContain('prochaine version du moteur');
  });
});
