import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as authModule from '../api/auth';
import {
  estMoisIsoValide,
  exporterTempsTravailExcel,
  messagePresenceErreur,
  PresenceApiError,
} from '../api/presenceApi';
import type { CollaborateurRow } from '../api/rhClient';
import ChampMois, { cycleMensuelDepuisMois } from '../components/ChampMois';
import SelecteurCollaborateursMulti from '../components/SelecteurCollaborateursMulti';
import { DetailProvider } from '../detail/DetailProvider';
import TempsTravailPanel, {
  ERREUR_COLLABORATEURS_EXPORT,
  ERREUR_MOIS_EXPORT,
  validerFiltresExportTempsTravail,
} from './TempsTravailPanel';

const COLLABS_EXEMPLE: CollaborateurRow[] = [
  {
    identifiant: 'col-1',
    matricule: '1424',
    prenom: 'Taoufik',
    nom: 'HASSINE',
    statut: 'ACTIF',
  },
  {
    identifiant: 'col-2',
    matricule: '1508',
    prenom: 'Sarra',
    nom: 'KHEMIRI',
    statut: 'ACTIF',
  },
];

describe('cycleMensuelDepuisMois & estMoisIsoValide', () => {
  it('calcule la fenêtre du 27 du mois précédent au 26 du mois choisi (ex. Octobre 2026 -> 27/09/2026 au 26/10/2026)', () => {
    expect(cycleMensuelDepuisMois('2026-10')).toEqual({
      du: '2026-09-27',
      au: '2026-10-26',
    });
  });

  it('gère le passage d’année en janvier (Janvier 2026 -> 27/12/2025 au 26/01/2026)', () => {
    expect(cycleMensuelDepuisMois('2026-01')).toEqual({
      du: '2025-12-27',
      au: '2026-01-26',
    });
  });

  it('rejette les formats de mois invalides', () => {
    expect(cycleMensuelDepuisMois('')).toBeNull();
    expect(cycleMensuelDepuisMois('2026-13')).toBeNull();
    expect(cycleMensuelDepuisMois('2026-00')).toBeNull();
    expect(cycleMensuelDepuisMois('10/2026')).toBeNull();
    expect(estMoisIsoValide('2026-10')).toBe(true);
    expect(estMoisIsoValide('2026-1')).toBe(false);
    expect(estMoisIsoValide('')).toBe(false);
  });
});

describe('validerFiltresExportTempsTravail', () => {
  it('exige un mois AAAA-MM valide et au moins 1 collaborateur sélectionné', () => {
    const sansRien = validerFiltresExportTempsTravail('', []);
    expect(sansRien.valide).toBe(false);
    expect(sansRien.erreurMois).toBe(ERREUR_MOIS_EXPORT);
    expect(sansRien.erreurCollaborateurs).toBe(ERREUR_COLLABORATEURS_EXPORT);

    const sansCollab = validerFiltresExportTempsTravail('2026-10', []);
    expect(sansCollab.valide).toBe(false);
    expect(sansCollab.erreurMois).toBeNull();
    expect(sansCollab.erreurCollaborateurs).toBe(ERREUR_COLLABORATEURS_EXPORT);

    const sansMois = validerFiltresExportTempsTravail('invalide', ['col-1']);
    expect(sansMois.valide).toBe(false);
    expect(sansMois.erreurMois).toBe(ERREUR_MOIS_EXPORT);
    expect(sansMois.erreurCollaborateurs).toBeNull();

    const ok = validerFiltresExportTempsTravail('2026-10', ['col-1', 'col-2']);
    expect(ok.valide).toBe(true);
    expect(ok.erreurMois).toBeNull();
    expect(ok.erreurCollaborateurs).toBeNull();
  });
});

describe('exporterTempsTravailExcel', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('rejette immédiatement sans appel réseau si le mois est manquant ou invalide', async () => {
    const spyFetch = vi.spyOn(authModule, 'apiFetch');
    await expect(
      exporterTempsTravailExcel({ mois: '', collaborateurIds: ['col-1'] }),
    ).rejects.toMatchObject({
      status: 400,
      code: 'MOIS_INVALIDE',
      message: ERREUR_MOIS_EXPORT,
    });
    expect(spyFetch).not.toHaveBeenCalled();
  });

  it('rejette immédiatement sans appel réseau si aucun collaborateur n’est sélectionné', async () => {
    const spyFetch = vi.spyOn(authModule, 'apiFetch');
    await expect(
      exporterTempsTravailExcel({ mois: '2026-10', collaborateurIds: ['  '] }),
    ).rejects.toMatchObject({
      status: 400,
      code: 'COLLABORATEURS_REQUIS',
      message: ERREUR_COLLABORATEURS_EXPORT,
    });
    expect(spyFetch).not.toHaveBeenCalled();
  });

  it('appelle GET /api/rh/v1/admin/presence/temps-travail/export avec mois et collaborateurIds dédupliqués et déclenche le téléchargement .xlsx', async () => {
    const fakeBlob = { size: 128, type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' } as unknown as Blob;
    const fakeResponse = {
      ok: true,
      status: 200,
      headers: {
        get: (h: string) =>
          h.toLowerCase() === 'content-disposition'
            ? 'attachment; filename="pointage-10-2026.xlsx"'
            : null,
      },
      blob: vi.fn().mockResolvedValue(fakeBlob),
    } as unknown as Response;

    const spyFetch = vi.spyOn(authModule, 'apiFetch').mockResolvedValue(fakeResponse);

    const anchorClicks: { href: string; download: string }[] = [];
    const fakeAnchor = {
      href: '',
      download: '',
      click() {
        anchorClicks.push({ href: this.href, download: this.download });
      },
      remove: vi.fn(),
    };

    vi.stubGlobal('document', {
      createElement: vi.fn(() => fakeAnchor),
      body: {
        appendChild: vi.fn(),
      },
    });
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pointage-10-2026');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

    await exporterTempsTravailExcel({
      mois: '2026-10',
      collaborateurIds: ['col-1', 'col-2', 'col-1'],
    });

    expect(spyFetch).toHaveBeenCalledTimes(1);
    const [urlAppelee, initAppele] = spyFetch.mock.calls[0];
    expect(urlAppelee).toBe(
      '/api/rh/v1/admin/presence/temps-travail/export?mois=2026-10&collaborateurIds=col-1&collaborateurIds=col-2',
    );
    expect((initAppele?.headers as Record<string, string>)?.Accept).toContain(
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    );
    expect(createObjectUrlSpy).toHaveBeenCalledWith(fakeBlob);
    expect(anchorClicks).toEqual([
      { href: 'blob:pointage-10-2026', download: 'pointage-10-2026.xlsx' },
    ]);
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:pointage-10-2026');
  });

  it('propage proprement les erreurs HTTP 400 et 403 du backend en PresenceApiError', async () => {
    const res400 = {
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      text: vi.fn().mockResolvedValue(
        JSON.stringify({ code: 'PARAMETRE_INVALIDE', message: 'Au moins un collaborateur est requis.' }),
      ),
    } as unknown as Response;
    vi.spyOn(authModule, 'apiFetch').mockResolvedValueOnce(res400);

    await expect(
      exporterTempsTravailExcel({ mois: '2026-10', collaborateurIds: ['col-1'] }),
    ).rejects.toMatchObject({
      status: 400,
      code: 'PARAMETRE_INVALIDE',
      message: 'Au moins un collaborateur est requis.',
    });

    const res403 = {
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      text: vi.fn().mockResolvedValue(
        JSON.stringify({ code: 'ACCESS_DENIED', message: 'Forbidden' }),
      ),
    } as unknown as Response;
    vi.spyOn(authModule, 'apiFetch').mockResolvedValueOnce(res403);

    try {
      await exporterTempsTravailExcel({ mois: '2026-10', collaborateurIds: ['col-1'] });
      expect.unreachable('Aurait dû lever PresenceApiError 403');
    } catch (e) {
      expect(e).toBeInstanceOf(PresenceApiError);
      expect(messagePresenceErreur(e)).toBe(
        'Accès refusé — droits insuffisants pour cette action.',
      );
    }
  });
});

describe('Rendu des composants Export Excel Temps de travail', () => {
  it('rend ChampMois en français avec les 12 mois', () => {
    const html = renderToStaticMarkup(
      <ChampMois
        id="test-mois"
        libelle="Mois de pointage *"
        valeur="2026-10"
        onChange={vi.fn()}
      />,
    );
    expect(html).toContain('Mois de pointage *');
    expect(html).toContain('Octobre');
    expect(html).toContain('2026');
  });

  it('rend SelecteurCollaborateursMulti avec les pastilles des collaborateurs sélectionnés et le compteur', () => {
    const html = renderToStaticMarkup(
      <SelecteurCollaborateursMulti
        id="test-collabs"
        collaborateurs={COLLABS_EXEMPLE}
        valeurs={['col-1', 'col-2']}
        onChange={vi.fn()}
      />,
    );
    expect(html).toContain('2 collaborateurs sélectionnés');
    expect(html).toContain('Taoufik HASSINE (1424)');
    expect(html).toContain('Sarra KHEMIRI (1508)');
    expect(html).toContain('Retirer Taoufik HASSINE (1424)');
    expect(html).toContain('Tout sélectionner');
    expect(html).toContain('Tout effacer');
  });

  it('rend TempsTravailPanel avec la section Export Excel (.xlsx) et sans bouton Exporter CSV', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <DetailProvider>
          <TempsTravailPanel />
        </DetailProvider>
      </MemoryRouter>,
    );
    expect(html).toContain('Export Excel Pointage (cycle du 27 au 26)');
    expect(html).toContain('Mois de pointage *');
    expect(html).toContain('Collaborateurs à exporter (min. 1) *');
    expect(html).toContain('Exporter Excel (.xlsx)');
    expect(html).not.toContain('Exporter CSV');
  });
});
