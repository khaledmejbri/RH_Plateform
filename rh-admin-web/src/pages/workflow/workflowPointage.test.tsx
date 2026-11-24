import type { ReactElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PresenceSite } from '../../api/presenceApi';
import type {
  ActivationWorkflow,
  CatalogueWorkflow,
  ModeleDetail,
  NotificationsDynamiquesDefinition,
  VersionDetail,
  VersionResume,
} from '../../api/workflowApi';
import { ConfirmProvider } from '../../components/ConfirmDialog';
import {
  libelleChampModifie,
  libelleElementComparaison,
  libelleTypeChangementElement,
} from '../../workflow/libelles';
import PanneauEtape from './PanneauEtape';
import PanneauNotificationsPointage, {
  normaliserNotificationsPointage,
  NOTIFICATIONS_POINTAGE_PAR_DEFAUT,
} from './PanneauNotificationsPointage';
import WorkflowEditeurPage from './WorkflowEditeurPage';
import WorkflowsListePage, { messageConfirmationBasculeMode } from './WorkflowsListePage';

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

const SITES_EXEMPLE: PresenceSite[] = [
  {
    id: '11111111-2222-3333-4444-555555555551',
    code: 'SIEGE',
    libelle: 'Siège Social',
    latitude: 36.8,
    longitude: 10.18,
    rayonMetres: 50,
    typeLieu: 'INTERIEUR',
    modeQr: 'PAPIER',
    borneActive: false,
    borneCreeLe: null,
    precisionAncrageMetres: null,
    nbMesuresAncrage: null,
    emplacementUpdatedAt: null,
    emplacementUpdatedBy: null,
    actif: true,
    qrVersion: 1,
    validFrom: null,
    validUntil: null,
    qrStatut: 'ACTIF',
    expireBientot: false,
    hasQrActif: true,
  },
  {
    id: '11111111-2222-3333-4444-555555555552',
    code: 'CHANTIER',
    libelle: 'Chantier Nord',
    latitude: 36.85,
    longitude: 10.2,
    rayonMetres: 20,
    typeLieu: 'EXTERIEUR',
    modeQr: 'ECRAN',
    borneActive: true,
    borneCreeLe: null,
    precisionAncrageMetres: null,
    nbMesuresAncrage: null,
    emplacementUpdatedAt: null,
    emplacementUpdatedBy: null,
    actif: true,
    qrVersion: 1,
    validFrom: null,
    validUntil: null,
    qrStatut: 'ACTIF',
    expireBientot: false,
    hasQrActif: true,
  },
  {
    id: '11111111-2222-3333-4444-555555555553',
    code: 'ARCHIVE',
    libelle: 'Ancien Dépôt',
    latitude: null,
    longitude: null,
    rayonMetres: 50,
    typeLieu: 'INTERIEUR',
    modeQr: 'PAPIER',
    borneActive: false,
    borneCreeLe: null,
    precisionAncrageMetres: null,
    nbMesuresAncrage: null,
    emplacementUpdatedAt: null,
    emplacementUpdatedBy: null,
    actif: false,
    qrVersion: null,
    validFrom: null,
    validUntil: null,
    qrStatut: null,
    expireBientot: false,
    hasQrActif: false,
  },
];

const CATALOGUE_COMPLET: CatalogueWorkflow = {
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
    {
      objet_type: 'POINTAGE',
      libelle: 'Pointage',
      service_proprietaire: 'svc-presence',
      chemin_effet: '',
      variables: [],
      effets: [],
      validation_humaine_obligatoire: false,
      supporte_complement: false,
    },
  ],
  variables_communes: [],
  effets: ['APPROUVER', 'REFUSER'],
  regles: [{ code: 'MANAGER_NOEUD', libelle: 'Responsable direct' }],
  operateurs: {},
  publics: ['DEMANDEUR', 'CANDIDATS', 'ROLE:RH'],
  fonctions: [],
  fonctions_disponibles: false,
};

const MODELE_POINTAGE: ModeleDetail = {
  id: 'mod-ptg-1',
  code: 'WF_POINTAGE_RO',
  libelle: 'Notification pointage RO',
  description: null,
  objet_type: 'POINTAGE',
  sous_type: null,
  actif: true,
  version_publiee_numero: 1,
  brouillon_numero: 2,
  modifie_le: '2026-11-24T09:00:00Z',
  version: 2,
  cree_le: '2026-11-24T08:00:00Z',
  brouillon_id: 'ver-ptg-2',
};

const VERSIONS_POINTAGE: VersionResume[] = [
  {
    id: 'ver-ptg-2',
    numero: 2,
    statut: 'BROUILLON',
    empreinte: null,
    modifie_le: '2026-11-24T09:00:00Z',
    modifie_par_nom: 'Amel Trabelsi',
    publie_le: null,
    retire_le: null,
    version: 1,
  },
  {
    id: 'ver-ptg-1',
    numero: 1,
    statut: 'PUBLIEE',
    empreinte: 'abc',
    modifie_le: '2026-11-24T08:30:00Z',
    modifie_par_nom: 'Amel Trabelsi',
    publie_le: '2026-11-24T08:35:00Z',
    retire_le: null,
    version: 2,
  },
];

const VERSION_POINTAGE_BROUILLON: VersionDetail = {
  id: 'ver-ptg-2',
  modele_id: 'mod-ptg-1',
  numero: 2,
  statut: 'BROUILLON',
  definition: {
    format: 1,
    objet_type: 'POINTAGE',
    options: { separation_des_taches: false },
    etapes: [],
    notifications_dynamiques: {
      notifier_ro: true,
      portee_sites: 'SITES_SELECTIONNES',
      site_ids: ['11111111-2222-3333-4444-555555555551'],
      types_pointage: ['ENTREE', 'SORTIE'],
      canal: 'WEBSOCKET',
    },
  },
  empreinte: null,
  version: 1,
  modifie_le: '2026-11-24T09:00:00Z',
  modifie_par_nom: 'Amel Trabelsi',
  publie_le: null,
  erreurs: [],
};

const ACTIVATIONS_EXEMPLE: ActivationWorkflow[] = [
  {
    objet_type: 'CONGE',
    libelle: 'Congé',
    mode: 'MOTEUR',
    modifie_le: '2026-11-20T08:00:00Z',
    version: 1,
  },
  {
    objet_type: 'POINTAGE',
    libelle: 'Pointage',
    mode: 'HERITE',
    modifie_le: '2026-11-24T08:00:00Z',
    version: 0,
  },
];

function collecterElements(noeud: unknown, pred: (el: ReactElement) => boolean, acc: ReactElement[] = []): ReactElement[] {
  if (!noeud || typeof noeud !== 'object') return acc;
  if (Array.isArray(noeud)) {
    for (const item of noeud) collecterElements(item, pred, acc);
    return acc;
  }
  const el = noeud as ReactElement<{ children?: unknown }>;
  if ('type' in el && 'props' in el) {
    if (pred(el)) acc.push(el);
    if (el.props?.children !== undefined) collecterElements(el.props.children, pred, acc);
  }
  return acc;
}

describe('PanneauNotificationsPointage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('rend les valeurs par défaut avec case décochée et Toute la société sélectionnée', () => {
    const html = renderToStaticMarkup(
      <PanneauNotificationsPointage
        valeur={NOTIFICATIONS_POINTAGE_PAR_DEFAUT}
        sites={SITES_EXEMPLE}
        onChange={() => undefined}
      />,
    );

    expect(html).toContain('Notifier le responsable (RO) à chaque pointage');
    expect(html).toContain('Désactivé : aucune notification au RO');
    expect(html).toContain('Toute la société');
    expect(html).toContain('Sites sélectionnés');
    expect(html).toContain('Entrée');
    expect(html).toContain('Sortie');
    expect(html).toContain('Application uniquement (temps réel)');
    expect(html).toContain('Application et courriel');
    expect(html).not.toContain('Siège Social');
  });

  it('affiche la liste des sites actifs quand SITES_SELECTIONNES est choisi et masque les sites inactifs', () => {
    const valeur: NotificationsDynamiquesDefinition = {
      notifier_ro: true,
      portee_sites: 'SITES_SELECTIONNES',
      site_ids: ['11111111-2222-3333-4444-555555555551'],
      types_pointage: ['ENTREE'],
      canal: 'BOTH',
    };
    const html = renderToStaticMarkup(
      <PanneauNotificationsPointage valeur={valeur} sites={SITES_EXEMPLE} onChange={() => undefined} />,
    );

    expect(html).toContain('Activé : chaque pointage valide');
    expect(html).toContain('Siège Social');
    expect(html).toContain('(SIEGE)');
    expect(html).toContain('Chantier Nord');
    expect(html).toContain('(CHANTIER)');
    expect(html).not.toContain('Ancien Dépôt');
  });

  it('émet les mises à jour exactes lors de la bascule Toute la société / Sites sélectionnés et du cochage des sites', () => {
    const onChange = vi.fn();
    const arbre = PanneauNotificationsPointage({
      valeur: {
        notifier_ro: true,
        portee_sites: 'SITES_SELECTIONNES',
        site_ids: ['11111111-2222-3333-4444-555555555551'],
        types_pointage: ['ENTREE', 'SORTIE'],
        canal: 'WEBSOCKET',
      },
      sites: SITES_EXEMPLE,
      onChange,
    });

    const inputs = collecterElements(arbre, (el) => el.type === 'input');
    const radioSociete = inputs.find((i) => i.props.id === 'notif-portee-societe');
    const checkSite2 = inputs.find((i) => i.props.id === 'notif-site-11111111-2222-3333-4444-555555555552');
    const checkNotifier = inputs.find((i) => i.props.id === 'notif-pointage-notifier-ro');

    expect(radioSociete).toBeDefined();
    expect(checkSite2).toBeDefined();
    expect(checkNotifier).toBeDefined();

    checkSite2!.props.onChange({ target: { checked: true } });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        site_ids: [
          '11111111-2222-3333-4444-555555555551',
          '11111111-2222-3333-4444-555555555552',
        ],
      }),
    );

    radioSociete!.props.onChange({ target: { checked: true } });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        portee_sites: 'TOUTE_LA_SOCIETE',
        site_ids: [],
      }),
    );

    checkNotifier!.props.onChange({ target: { checked: false } });
    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ notifier_ro: false }));
  });

  it('affiche les erreurs serveur SITES_REQUIS et TYPES_POINTAGE_REQUIS en français', () => {
    const html = renderToStaticMarkup(
      <PanneauNotificationsPointage
        valeur={{
          notifier_ro: true,
          portee_sites: 'SITES_SELECTIONNES',
          site_ids: [],
          types_pointage: [],
          canal: 'WEBSOCKET',
        }}
        sites={SITES_EXEMPLE}
        erreurs={[
          {
            chemin: 'notifications_dynamiques.site_ids',
            code: 'SITES_REQUIS',
            niveau: 'ERREUR',
            message: 'Sélectionnez au moins un site de pointage ou choisissez Toute la société.',
          },
          {
            chemin: 'notifications_dynamiques.types_pointage',
            code: 'TYPES_POINTAGE_REQUIS',
            niveau: 'ERREUR',
            message: 'Sélectionnez au moins un type de pointage (Entrée ou Sortie).',
          },
        ]}
        onChange={() => undefined}
      />,
    );

    expect(html).toContain('Sélectionnez au moins un site de pointage ou choisissez Toute la société.');
    expect(html).toContain('Sélectionnez au moins un type de pointage (Entrée ou Sortie).');
  });

  it('désactive tous les contrôles lorsque disabled=true', () => {
    const arbre = PanneauNotificationsPointage({
      valeur: {
        notifier_ro: true,
        portee_sites: 'SITES_SELECTIONNES',
        site_ids: ['11111111-2222-3333-4444-555555555551'],
        types_pointage: ['ENTREE', 'SORTIE'],
        canal: 'WEBSOCKET',
      },
      sites: SITES_EXEMPLE,
      disabled: true,
      onChange: () => undefined,
    });

    const inputs = collecterElements(arbre, (el) => el.type === 'input' || el.type === 'select');
    expect(inputs.length).toBeGreaterThanOrEqual(7);
    expect(inputs.every((el) => el.props.disabled === true)).toBe(true);
  });

  it('normalise proprement une valeur nulle ou partielle', () => {
    expect(normaliserNotificationsPointage(null)).toEqual(NOTIFICATIONS_POINTAGE_PAR_DEFAUT);
  });
});

describe('WorkflowEditeurPage — POINTAGE, DIRECTION et non-régression CONGE', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('affiche le panneau POINTAGE, masque Simulation et propose Passer en Circuit paramétré pour RH en mode HERITE', () => {
    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['RH'])));
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/parametres/workflows/mod-ptg-1']}>
        <ConfirmProvider>
          <WorkflowEditeurPage
            initialCatalogue={CATALOGUE_COMPLET}
            initialModele={MODELE_POINTAGE}
            initialVersions={VERSIONS_POINTAGE}
            initialVersionActive={VERSION_POINTAGE_BROUILLON}
            initialActivationPointage={ACTIVATIONS_EXEMPLE[1]}
            initialSitesPointage={SITES_EXEMPLE}
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );

    expect(html).toContain('Notifications dynamiques de pointage');
    expect(html).toContain('Notifier le responsable (RO) à chaque pointage');
    expect(html).toContain('Circuit standard');
    expect(html).toContain('Passer en Circuit paramétré');
    expect(html).toContain('Enregistrer');
    expect(html).toContain('Publier');
    expect(html).toContain('>Éditeur</button>');
    expect(html).toContain('>Versions (2)</button>');
    expect(html).toContain('>Journal</button>');
    expect(html).not.toContain('>Simulation</button>');
    expect(html).not.toContain('Séparation des tâches');
    expect(html).not.toContain('Liste des étapes');
  });

  it('passe en lecture seule complète pour DIRECTION (DG01) sans aucun bouton d’édition ni activation', () => {
    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['DIRECTION'])));
    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/parametres/workflows/mod-ptg-1']}>
        <ConfirmProvider>
          <WorkflowEditeurPage
            initialCatalogue={CATALOGUE_COMPLET}
            initialModele={MODELE_POINTAGE}
            initialVersions={VERSIONS_POINTAGE}
            initialVersionActive={VERSION_POINTAGE_BROUILLON}
            initialActivationPointage={ACTIVATIONS_EXEMPLE[1]}
            initialSitesPointage={SITES_EXEMPLE}
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );

    expect(html).toContain('Notifications dynamiques de pointage');
    expect(html).toContain('Circuit standard');
    expect(html).not.toContain('Passer en Circuit paramétré');
    expect(html).not.toContain('>Enregistrer</button>');
    expect(html).not.toContain('>Publier</button>');
    expect(html).not.toContain('>Valider</button>');
    expect(html).not.toContain('Supprimer brouillon');
    expect(html).not.toContain('Nouvelle version (brouillon)');
    expect(html).toContain('disabled=""');
  });

  it('conserve strictement l’éditeur d’étapes et l’onglet Simulation pour CONGE (non-régression)', () => {
    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['RH'])));
    const modeleConge: ModeleDetail = {
      id: 'mod-conge-1',
      code: 'CONGE_STD',
      libelle: 'Validation congé',
      description: null,
      objet_type: 'CONGE',
      sous_type: null,
      actif: true,
      version_publiee_numero: 1,
      brouillon_numero: 2,
      modifie_le: '2026-11-20T09:00:00Z',
      version: 1,
      cree_le: '2026-11-20T08:00:00Z',
      brouillon_id: 'ver-conge-2',
    };
    const versionConge: VersionDetail = {
      id: 'ver-conge-2',
      modele_id: 'mod-conge-1',
      numero: 2,
      statut: 'BROUILLON',
      definition: {
        format: 1,
        objet_type: 'CONGE',
        options: { separation_des_taches: true },
        etapes: [
          {
            code: 'VAL_RO',
            libelle: 'Validation RO',
            type: 'VALIDATION',
            actions: ['APPROUVER', 'REFUSER'],
            delai: { duree: 24, unite: 'HEURES' },
            a_echeance: { action: 'RIEN' },
          },
        ],
      },
      empreinte: null,
      version: 1,
      modifie_le: '2026-11-20T09:00:00Z',
      modifie_par_nom: 'Amel Trabelsi',
      publie_le: null,
      erreurs: [],
    };

    const html = renderToStaticMarkup(
      <MemoryRouter initialEntries={['/app/parametres/workflows/mod-conge-1']}>
        <ConfirmProvider>
          <WorkflowEditeurPage
            initialCatalogue={CATALOGUE_COMPLET}
            initialModele={modeleConge}
            initialVersions={[]}
            initialVersionActive={versionConge}
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );

    expect(html).toContain('>Simulation</button>');
    expect(html).toContain('Séparation des tâches');
    expect(html).toContain('Étapes (1)');
    expect(html).toContain('Validation RO');
    expect(html).not.toContain('Notifications dynamiques de pointage');
    expect(html).not.toContain('État du circuit Pointage');
  });
});

describe('WorkflowsListePage, libellés & PanneauEtape (POINTAGE + non-régression CONGE)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adapte le message de bascule pour POINTAGE et conserve celui de CONGE', () => {
    expect(messageConfirmationBasculeMode('POINTAGE', 'MOTEUR')).toContain('notifications dynamiques de pointage');
    expect(messageConfirmationBasculeMode('POINTAGE', 'HERITE')).toContain('notifications dynamiques de pointage');
    expect(messageConfirmationBasculeMode('CONGE', 'MOTEUR')).toContain('nouvelles demandes de ce type');
  });

  it('masque le champ Sous-type à la création quand objetType === POINTAGE et le garde visible pour CONGE', () => {
    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['RH'])));
    const htmlPointage = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <WorkflowsListePage
            initialCatalogue={CATALOGUE_COMPLET}
            initialActivations={ACTIVATIONS_EXEMPLE}
            initialModeles={[MODELE_POINTAGE]}
            initialFormOuvert={true}
            initialObjetType="POINTAGE"
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlPointage).toContain('Créer un modèle de workflow');
    expect(htmlPointage).not.toContain('id="wf-new-sous"');
    expect(htmlPointage).toContain('Notification pointage RO');

    const htmlConge = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <WorkflowsListePage
            initialCatalogue={CATALOGUE_COMPLET}
            initialActivations={ACTIVATIONS_EXEMPLE}
            initialModeles={[]}
            initialFormOuvert={true}
            initialObjetType="CONGE"
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );
    expect(htmlConge).toContain('id="wf-new-sous"');
  });

  it('masque les boutons d’écriture de WorkflowsListePage pour DIRECTION', () => {
    vi.stubGlobal('sessionStorage', storeAvecToken(fauxJwt(['DIRECTION'])));
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ConfirmProvider>
          <WorkflowsListePage
            initialCatalogue={CATALOGUE_COMPLET}
            initialActivations={ACTIVATIONS_EXEMPLE}
            initialModeles={[MODELE_POINTAGE]}
          />
        </ConfirmProvider>
      </MemoryRouter>,
    );

    expect(html).toContain('Pointage');
    expect(html).toContain('Notification pointage RO');
    expect(html).toContain('Consulter');
    expect(html).not.toContain('Nouveau modèle');
    expect(html).not.toContain('Passer en Circuit paramétré');
    expect(html).not.toContain('>Désactiver</button>');
  });

  it('traduit les champs de notifications_dynamiques et NOTIFICATIONS_DYNAMIQUES en français', () => {
    expect(libelleChampModifie('notifier_ro')).toBe('Notification au responsable (RO)');
    expect(libelleChampModifie('portee_sites')).toBe('Portée des sites');
    expect(libelleChampModifie('site_ids')).toBe('Sites sélectionnés');
    expect(libelleChampModifie('types_pointage')).toBe('Types de pointage');
    expect(libelleChampModifie('canal')).toBe('Canal de notification');
    expect(libelleElementComparaison('NOTIFICATIONS_DYNAMIQUES')).toBe('Notifications dynamiques');
    expect(libelleTypeChangementElement('NOTIFICATIONS_DYNAMIQUES', 'MODIFIEE')).toBe('Configuration modifiée');
  });

  it('conserve PanneauEtape fonctionnel sur CONGE', () => {
    const html = renderToStaticMarkup(
      <PanneauEtape
        etape={{
          code: 'E1',
          libelle: 'Validation manager',
          type: 'VALIDATION',
          actions: ['APPROUVER', 'REFUSER'],
          delai: { duree: 24, unite: 'HEURES' },
          a_echeance: { action: 'RIEN' },
        }}
        catalogue={CATALOGUE_COMPLET}
        objetType="CONGE"
        fonctionsActives={[]}
        erreursEtape={[]}
        onChange={() => undefined}
      />,
    );
    expect(html).toContain('Validation manager');
    expect(html).toContain('Notifications');
    expect(html).toContain('Délais et escalade');
  });
});
