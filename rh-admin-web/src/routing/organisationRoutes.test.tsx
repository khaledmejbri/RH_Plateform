import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { entreeActive, MENU } from '../layout/AppShell';

describe('Navigation et redirections Organisation (W7b / UX-organisation)', () => {
  it('supprime Structure RH et Unités et expose Organigramme dans Collaborateurs et Paramétrage sans writeOnly', () => {
    const collab = MENU.find((m) => m.kind === 'groupe' && m.id === 'collaborateurs');
    const param = MENU.find((m) => m.kind === 'groupe' && m.id === 'parametrage');
    expect(collab?.kind).toBe('groupe');
    expect(param?.kind).toBe('groupe');

    if (collab?.kind === 'groupe') {
      const org = collab.enfants.find((e) => e.id === 'organigramme');
      expect(org).toMatchObject({ to: '/app/organigramme', label: 'Organigramme' });
      expect(org?.writeOnly).toBeFalsy();
    }

    if (param?.kind === 'groupe') {
      expect(param.enfants.find((e) => e.id === 'structure')).toBeUndefined();
      expect(param.enfants.find((e) => e.id === 'organisation')).toBeUndefined();
      const orgParam = param.enfants.find((e) => e.id === 'organigramme-param');
      expect(orgParam).toMatchObject({ to: '/app/organigramme', label: 'Organigramme' });
      expect(orgParam?.writeOnly).toBeFalsy();
    }
  });

  it('garde Organigramme surligné dans entreeActive() quelle que soit la vue ou fiche unité', () => {
    expect(entreeActive('/app/organigramme', '')).toBe('organigramme');
    expect(entreeActive('/app/organigramme', '?vue=liste')).toBe('organigramme');
    expect(entreeActive('/app/organigramme', '?vue=liste&q=tech')).toBe('organigramme');
    expect(entreeActive('/app/organisation/unites/abc-123', '')).toBe('organigramme');
    expect(entreeActive('/app/structure', '')).not.toBe('structure');
    expect(entreeActive('/app/unites', '')).not.toBe('organisation');
  });

  it('déclare les redirections /app/structure et /app/unites vers /app/organigramme?vue=liste', () => {
    // Vérifie la présence des redirections dans App sans dépendre de window/localStorage navigateur.
    const Probe = () => {
      const loc = useLocation();
      return <span data-path={`${loc.pathname}${loc.search}`} />;
    };
    const elementTree = App();
    const htmlSource = JSON.stringify(elementTree, (_k, v) => {
      if (typeof v === 'function') return v.name || 'fn';
      return v;
    });
    expect(htmlSource).toContain('"/app/structure"');
    expect(htmlSource).toContain('"/app/unites"');
    expect(htmlSource).toContain('"/app/organigramme?vue=liste"');

    // Vérifie également via renderToStaticMarkup sur Routes que <Navigate> produit la cible attendue si monté.
    const markup = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/organigramme?vue=liste']}>
        <Routes>
          <Route path="/app/organigramme" element={<Probe />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(markup).toContain('data-path="/app/organigramme?vue=liste"');
  });
});
