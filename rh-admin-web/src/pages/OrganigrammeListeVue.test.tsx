import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { OrganigrammeNoeud } from '../api/rhClient';
import { DetailProvider } from '../detail/DetailProvider';
import OrganigrammeListeVue from './OrganigrammeListeVue';

const NOEUD_EXEMPLE: OrganigrammeNoeud = {
  identifiant: 'srv-1',
  code: 'SRV-QA',
  libelle: 'Service Qualité',
  type_noeud: 'Service',
  parent_identifiant: 'dept-1',
  actif: true,
  manager: { identifiant: 'm-1', matricule: 'CS-QA', prenom: 'Nadia', nom: 'Ayari' },
  membres: [{ identifiant: 'c-1', matricule: 'COL-1', prenom: 'Sarra', nom: 'Khemiri' }],
  enfants: [],
};

const PARENT_EXEMPLE: OrganigrammeNoeud = {
  identifiant: 'dept-1',
  code: 'DEPT-TECH',
  libelle: 'Département Technique',
  type_noeud: 'Département',
  parent_identifiant: null,
  actif: true,
  manager: null,
  membres: [],
  enfants: [NOEUD_EXEMPLE],
};

function rendre(override: Partial<Parameters<typeof OrganigrammeListeVue>[0]> = {}) {
  const tousNoeuds = override.tousNoeuds ?? [PARENT_EXEMPLE, NOEUD_EXEMPLE];
  return renderToStaticMarkup(
    <MemoryRouter>
      <DetailProvider>
        <OrganigrammeListeVue
          tousNoeuds={tousNoeuds}
          indexNoeuds={new Map(tousNoeuds.map((n) => [n.identifiant, n]))}
          collaborateursActifs={12}
          loading={false}
          err={null}
          searchQuery=""
          lectureSeule={false}
          saving={false}
          moving={false}
          dgExiste={true}
          selectedId={null}
          onReload={vi.fn()}
          onResetSearch={vi.fn()}
          onOpenCreer={vi.fn()}
          onSelect={vi.fn()}
          onOpenManager={vi.fn()}
          onOpenEditer={vi.fn()}
          onOpenRattacher={vi.fn()}
          {...override}
        />
      </DetailProvider>
    </MemoryRouter>,
  );
}

describe('OrganigrammeListeVue', () => {
  it('gère les quatre états (chargement, erreur, vide, données)', () => {
    expect(rendre({ loading: true })).toContain('Chargement de la liste des unités');
    expect(rendre({ tousNoeuds: [], err: 'Panne réseau' })).toContain('Impossible de charger l’organigramme');
    expect(rendre({ tousNoeuds: [] })).toContain('Aucune unité organisationnelle');
    expect(rendre({ searchQuery: 'inexistant' })).toContain('Aucune unité correspondante');

    const html = rendre();
    expect(html).toContain('Service Qualité');
    expect(html).toContain('Département Technique');
    expect(html).toContain('Nadia Ayari');
    expect(html).toContain('Actions');
  });

  it('masque toutes les actions d’écriture pour DIRECTION (lectureSeule)', () => {
    const html = rendre({ lectureSeule: true });
    expect(html).toContain('Service Qualité');
    expect(html).not.toContain('Modifier');
    expect(html).not.toContain('Rattacher…');
    expect(html).not.toContain('Responsable</button>');
    expect(html).not.toContain('+ Enfant');
  });
});
