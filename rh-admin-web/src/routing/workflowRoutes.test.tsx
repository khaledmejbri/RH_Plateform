import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CollaborateurRow } from '../api/rhClient';
import type {
  CatalogueWorkflow,
  DelegationAdmin,
  IndicateursWorkflow,
  InstanceDetailAdmin,
  InstanceResume,
  PageWorkflow,
} from '../api/workflowApi';
import App from '../App';
import { ConfirmProvider } from '../components/ConfirmDialog';
import DelegationsOnglet from '../detail/contenus/DelegationsOnglet';
import { entreeActive, MENU } from '../layout/AppShell';
import DelegationFormCard from '../pages/workflow/DelegationFormCard';
import DelegationsPage from '../pages/workflow/DelegationsPage';
import DelegationsTableau from '../pages/workflow/DelegationsTableau';
import { InstanceDetailContenu } from '../pages/workflow/InstanceDetailModal';
import PanneauEtape from '../pages/workflow/PanneauEtape';
import WorkflowsInstancesPage from '../pages/workflow/WorkflowsInstancesPage';

function fauxJwt(roles: string[]): string {
  const entete = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const charge = Buffer.from(JSON.stringify({ sub: 'u1', roles })).toString('base64url');
  return `${entete}.${charge}.sig`;
}

function storeAvecToken(token: string) {
  return {
    getItem: (k: string) => (k === 'rh_admin_access_token' ? token : null),
    setItem: () => undefined,
    removeItem: () => undefined,
  };
}

const CATALOGUE_EXEMPLE: CatalogueWorkflow = {
  types: [
    {
      objet_type: 'CONGE',
      libelle: 'Congé',
      service_proprietaire: 'svc-referentiel-rh',
      chemin_effet: '/internal/referentiel/v1/workflow/effets',
      variables: [
        { nom: 'jours_decomptes', type: 'DECIMAL', libelle: 'Jours décomptés', valeurs: null, source_valeurs: null },
      ],
      effets: ['APPROUVER', 'REFUSER'],
      validation_humaine_obligatoire: true,
      supporte_complement: true,
    },
  ],
  variables_communes: [],
  effets: ['APPROUVER', 'REFUSER'],
  regles: [],
  operateurs: {},
  publics: [],
  fonctions: [],
  fonctions_disponibles: false,
};

const COLLABS_EXEMPLE: CollaborateurRow[] = [
  {
    identifiant: 'col-1',
    matricule: 'COL-1',
    prenom: 'Sarra',
    nom: 'Khemiri',
    statut: 'ACTIF',
  },
  {
    identifiant: 'col-2',
    matricule: 'CS-1',
    prenom: 'Omar',
    nom: 'Belhadj',
    statut: 'ACTIF',
  },
];

const DELEGATIONS_EXEMPLE: DelegationAdmin[] = [
  {
    id: 'del-active',
    delegant_id: 'col-1',
    delegant_nom: 'Sarra Khemiri',
    delegue_id: 'col-2',
    delegue_nom: 'Omar Belhadj',
    du: '2026-11-20',
    au: '2026-11-30',
    objet_types: ['CONGE'],
    motif: 'Mission',
    origine: 'MANUELLE',
    active: true,
    creee_le: '2026-11-19T10:00:00Z',
    annulee_le: null,
  },
  {
    id: 'del-planifiee',
    delegant_id: 'col-1',
    delegant_nom: 'Sarra Khemiri',
    delegue_id: 'col-2',
    delegue_nom: 'Omar Belhadj',
    du: '2026-12-01',
    au: '2026-12-10',
    objet_types: null,
    motif: 'Congé annuel',
    origine: 'AUTO_CONGE',
    active: false,
    creee_le: '2026-11-20T10:00:00Z',
    annulee_le: null,
  },
  {
    id: 'del-expiree',
    delegant_id: 'col-2',
    delegant_nom: 'Omar Belhadj',
    delegue_id: 'col-1',
    delegue_nom: 'Sarra Khemiri',
    du: '2026-10-01',
    au: '2026-10-10',
    objet_types: null,
    motif: null,
    origine: 'MANUELLE',
    active: false,
    creee_le: '2026-09-28T10:00:00Z',
    annulee_le: null,
  },
  {
    id: 'del-annulee',
    delegant_id: 'col-2',
    delegant_nom: 'Omar Belhadj',
    delegue_id: 'col-1',
    delegue_nom: 'Sarra Khemiri',
    du: '2026-11-20',
    au: '2026-11-25',
    objet_types: ['CONGE'],
    motif: 'Annulée par RH',
    origine: 'MANUELLE',
    active: false,
    creee_le: '2026-11-18T10:00:00Z',
    annulee_le: '2026-11-21T09:00:00Z',
  },
];

const DETAIL_INSTANCE_EXEMPLE: InstanceDetailAdmin = {
  id: 'inst-1',
  objet_type: 'CONGE',
  objet_libelle: 'Congé',
  objet_numero: 'DEM-202611-0001',
  objet_id: 'dem-1',
  modele_code: 'CONGE_STD',
  modele_version_numero: 2,
  demandeur_id: 'col-1',
  demandeur_nom: 'Sarra Khemiri',
  statut: 'BLOQUEE',
  etape_courante_code: 'VALIDATION_RH',
  etape_courante_libelle: 'Validation RH',
  demarre_le: '2026-11-20T08:00:00Z',
  termine_le: null,
  resultat_motif: null,
  variables: {
    jours_decomptes: 3,
    affichage_periode: 'du 01/12/2026 au 03/12/2026',
    note_xss: '<script>alert(1)</script>',
  },
  etapes: [
    {
      id: 'ee-1',
      etape_code: 'VALIDATION_RO',
      libelle: 'Validation responsable',
      ordre: 1,
      statut: 'TERMINEE',
      raison_saut: null,
      resultat: 'APPROUVEE',
      nb_renvois: 0,
      active_le: '2026-11-20T08:00:00Z',
      terminee_le: '2026-11-20T10:00:00Z',
    },
    {
      id: 'ee-2',
      etape_code: 'VALIDATION_RH',
      libelle: 'Validation RH',
      ordre: 2,
      statut: 'ACTIVE',
      raison_saut: null,
      resultat: null,
      nb_renvois: 0,
      active_le: '2026-11-20T10:00:00Z',
      terminee_le: null,
    },
  ],
  taches: [
    {
      id: 't-traitee',
      etape_execution_id: 'ee-1',
      code_tache: 'VALIDATION_RO',
      type: 'VALIDATION',
      statut: 'TRAITEE',
      libelle: 'Validation responsable',
      obligatoire: true,
      echeance_le: '2026-11-21T08:00:00Z',
      prochaine_relance_le: null,
      en_retard: false,
      nb_relances: 0,
      escalade_a_faire_le: null,
      escalade_le: null,
      version: 1,
      decision: 'APPROUVEE',
      decideur_nom: 'Omar Belhadj',
      decide_le: '2026-11-20T10:00:00Z',
      pour_nom: null,
      commentaire: 'Accordé',
      candidats: [],
    },
    {
      id: 't-ouverte',
      etape_execution_id: 'ee-2',
      code_tache: 'VALIDATION_RH',
      type: 'VALIDATION',
      statut: 'A_TRAITER',
      libelle: 'Validation RH',
      obligatoire: true,
      echeance_le: '2026-11-21T10:00:00Z',
      prochaine_relance_le: null,
      en_retard: true,
      nb_relances: 1,
      escalade_a_faire_le: null,
      escalade_le: null,
      version: 3,
      decision: null,
      decideur_nom: null,
      decide_le: null,
      pour_nom: null,
      commentaire: null,
      candidats: [
        {
          collaborateur_id: 'col-3',
          compte_id: 'u-3',
          nom: 'Nadia Ayari',
          origine: 'DELEGUE',
          pour_collaborateur_id: 'col-2',
          pour_nom: 'Omar Belhadj',
          ajoute_le: '2026-11-20T10:00:00Z',
          retire_le: null,
        },
        {
          collaborateur_id: 'col-4',
          compte_id: 'u-4',
          nom: 'Amel Trabelsi',
          origine: 'TITULAIRE',
          pour_collaborateur_id: null,
          pour_nom: null,
          ajoute_le: '2026-11-20T10:00:00Z',
          retire_le: '2026-11-21T09:00:00Z',
        },
      ],
    },
  ],
  historique: [
    {
      id: 'h-1',
      tache_id: 't-ouverte',
      type_evenement: 'RELANCE',
      acteur_compte_id: null,
      acteur_nom: 'Système',
      pour_nom: null,
      details: { nb_relances: 1 },
      horodatage: '2026-11-21T09:00:00Z',
    },
  ],
};

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

    const idxWrite = tree.indexOf('"RequireWriteAccess"');
    const idxInstances = tree.indexOf('"/app/workflows/instances"');
    const idxDelegations = tree.indexOf('"/app/workflows/delegations"');
    expect(idxWrite).toBeGreaterThan(-1);
    expect(idxInstances).toBeGreaterThan(-1);
    expect(idxDelegations).toBeGreaterThan(-1);
    expect(idxInstances).toBeLessThan(idxWrite);
    expect(idxDelegations).toBeLessThan(idxWrite);
  });

  it('InstanceDetailContenu masque Réaffecter pour DIRECTION, l’affiche pour RH sur A_TRAITER et échappe les variables', () => {
    const htmlDir = renderToStaticMarkup(
      <MemoryRouter>
        <InstanceDetailContenu
          detail={DETAIL_INSTANCE_EXEMPLE}
          lectureSeule={true}
          catalogue={CATALOGUE_EXEMPLE}
        />
      </MemoryRouter>,
    );
    expect(htmlDir).not.toContain('>Réaffecter</button>');
    expect(htmlDir).toContain('en attente d’une réaffectation par un gestionnaire RH');
    expect(htmlDir).toContain('Nadia Ayari');
    expect(htmlDir).toContain('Délégué (pour le compte de Omar Belhadj)');
    expect(htmlDir).toContain('Amel Trabelsi');
    expect(htmlDir).toContain('Retiré le');
    expect(htmlDir).toContain('Période affichée');
    expect(htmlDir).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(htmlDir).not.toContain('<script>alert(1)</script>');

    const htmlRh = renderToStaticMarkup(
      <MemoryRouter>
        <InstanceDetailContenu
          detail={DETAIL_INSTANCE_EXEMPLE}
          lectureSeule={false}
          catalogue={CATALOGUE_EXEMPLE}
          onOuvrirReaffectation={() => undefined}
        />
      </MemoryRouter>,
    );
    const matches = htmlRh.match(/>Réaffecter<\/button>/g) ?? [];
    expect(matches).toHaveLength(1);
  });

  it('DelegationsTableau masque Actions/Annuler pour DIRECTION et ne l’affiche que sur Active/Planifiée pour RH', () => {
    const htmlDir = renderToStaticMarkup(
      <MemoryRouter>
        <DelegationsTableau
          elements={DELEGATIONS_EXEMPLE}
          catalogue={CATALOGUE_EXEMPLE}
          lectureSeule={true}
          aujourdhui="2026-11-22"
        />
      </MemoryRouter>,
    );
    expect(htmlDir).toContain('Sarra Khemiri');
    expect(htmlDir).toContain('Automatique (congé)');
    expect(htmlDir).not.toContain('>Actions<');
    expect(htmlDir).not.toContain('>Annuler</button>');

    const htmlRh = renderToStaticMarkup(
      <MemoryRouter>
        <DelegationsTableau
          elements={DELEGATIONS_EXEMPLE}
          catalogue={CATALOGUE_EXEMPLE}
          lectureSeule={false}
          aujourdhui="2026-11-22"
          onAnnuler={() => undefined}
        />
      </MemoryRouter>,
    );
    expect(htmlRh).toContain('>Actions<');
    const boutonsAnnuler = htmlRh.match(/>Annuler<\/button>/g) ?? [];
    expect(boutonsAnnuler).toHaveLength(2);
  });

  it('DelegationFormCard pré-remplit le délégant initial et expose les bornes du formulaire', () => {
    const html = renderToStaticMarkup(
      <DelegationFormCard
        idPrefixe="test-del"
        collaborateurs={COLLABS_EXEMPLE}
        collabChargement={false}
        catalogue={CATALOGUE_EXEMPLE}
        delegantIdInitial="col-1"
        onCree={() => undefined}
        onAnnulerForm={() => undefined}
      />,
    );
    expect(html).toContain('value="Sarra Khemiri (COL-1)"');
    expect(html).toContain('Tous les types de demande');
    expect(html).toContain('Types sélectionnés uniquement');
    expect(html).toContain('maxLength="300"');
  });

  it('DelegationsOnglet fonctionne hors <Outlet> et masque les actions d’écriture pour DIRECTION', () => {
    const donneesOnglet = {
      donnees: { elements: DELEGATIONS_EXEMPLE.slice(0, 2), page: 0, taille: 50, total: 2 },
      recues: { elements: DELEGATIONS_EXEMPLE.slice(2, 4), page: 0, taille: 50, total: 2 },
      catalogue: CATALOGUE_EXEMPLE,
    };

    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['DIRECTION'])));
    const htmlDir = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <DelegationsOnglet id="col-1" initialDonnees={donneesOnglet} />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDir).toContain('Délégations données');
    expect(htmlDir).toContain('Délégations reçues');
    expect(htmlDir).not.toContain('Nouvelle délégation');
    expect(htmlDir).not.toContain('>Annuler</button>');
    expect(htmlDir).not.toContain('>Actions<');

    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['RH'])));
    const htmlRh = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <DelegationsOnglet id="col-1" initialDonnees={donneesOnglet} />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlRh).toContain('Nouvelle délégation');
    expect(htmlRh).toContain('>Annuler</button>');
  });

  it('WorkflowsInstancesPage et DelegationsPage affichent les données et masquent toute écriture pour DIRECTION', () => {
    const indicateurs: IndicateursWorkflow = {
      en_cours: 4,
      en_retard: 1,
      bloquees: 1,
      par_etape: [
        { modele_code: 'CONGE_STD', etape_code: 'VALIDATION_RH', libelle: 'Validation RH', delai_moyen_heures: 12.5, nb: 3 },
      ],
    };
    const instancesPage: PageWorkflow<InstanceResume> = {
      elements: [
        {
          id: 'inst-1',
          objet_type: 'CONGE',
          objet_libelle: 'Congé',
          objet_numero: 'DEM-202611-0001',
          objet_id: 'dem-1',
          modele_code: 'CONGE_STD',
          demandeur_id: 'col-1',
          demandeur_nom: 'Sarra Khemiri',
          statut: 'BLOQUEE',
          etape_courante_code: 'VALIDATION_RH',
          etape_courante_libelle: 'Validation RH',
          echeance_le: '2026-11-21T10:00:00Z',
          en_retard: true,
          demarre_le: '2026-11-20T08:00:00Z',
          termine_le: null,
        },
      ],
      page: 0,
      taille: 20,
      total: 1,
    };
    const delegationsPage: PageWorkflow<DelegationAdmin> = {
      elements: DELEGATIONS_EXEMPLE,
      page: 0,
      taille: 20,
      total: DELEGATIONS_EXEMPLE.length,
    };

    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['DIRECTION'])));
    const htmlInst = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/instances']}>
        <WorkflowsInstancesPage
          initialIndicateurs={indicateurs}
          initialDonnees={instancesPage}
          initialCatalogue={CATALOGUE_EXEMPLE}
        />
      </MemoryRouter>,
    );
    expect(htmlInst).toContain('Workflows en cours');
    expect(htmlInst).toContain('Temps réel');
    expect(htmlInst).toContain('Délais moyens par étape');
    expect(htmlInst).toContain('12,5 h');
    expect(htmlInst).toContain('DEM-202611-0001');
    expect(htmlInst).not.toContain('Réaffecter');
    expect(htmlInst).not.toContain('Nouvelle délégation');
    expect(htmlInst).not.toContain('>Annuler</button>');

    const htmlDelDir = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/delegations']}>
        <ConfirmProvider>
          <DelegationsPage initialDonnees={delegationsPage} initialCatalogue={CATALOGUE_EXEMPLE} />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDelDir).toContain('Délégations');
    expect(htmlDelDir).toContain('Sarra Khemiri');
    expect(htmlDelDir).not.toContain('Nouvelle délégation');
    expect(htmlDelDir).not.toContain('>Actions<');
    expect(htmlDelDir).not.toContain('>Annuler</button>');

    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['RH'])));
    const htmlDelRh = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/workflows/delegations']}>
        <ConfirmProvider>
          <DelegationsPage initialDonnees={delegationsPage} initialCatalogue={CATALOGUE_EXEMPLE} />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlDelRh).toContain('Nouvelle délégation');
    expect(htmlDelRh).toContain('>Annuler</button>');
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
        catalogue={CATALOGUE_EXEMPLE}
        objetType="CONGE"
        fonctionsActives={[]}
        erreursEtape={[]}
        onChange={() => undefined}
      />,
    );
    expect(html).not.toContain('prochaine version du moteur');
  });
});
