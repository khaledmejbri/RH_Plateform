import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  exporterTempsTravailCsv,
  getPresenceSites,
  getTempsTravail,
  messagePresenceErreur,
  PresenceApiError,
  type EtatTempsTravail,
  type LigneTempsTravail,
  type PresenceSite,
  type TempsTravailReponse,
} from '../api/presenceApi';
import { getTousCollaborateurs, getUnites, type CollaborateurRow, type Unite } from '../api/rhClient';
import BarreFiltres, { AucunResultat, ChampDate, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import FiltreCollaborateur from '../components/FiltreCollaborateur';
import { EmptyState, LoadingState } from '../components/ui';
import { useFiltresUrl, type SelectionUnite } from '../utils/filtres';
import { formatDate, formatDateHeure, formatHeuresMinutes, formatPeriode, formatPersonne, SITE_INTROUVABLE } from '../utils/format';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';

const TAILLE_PAGE = 50;
const JOURS_MAX = 31;

const ETATS: { valeur: EtatTempsTravail; libelle: string; classe: string }[] = [
  { valeur: 'COMPLET', libelle: 'Complet', classe: 'badge badge--success' },
  { valeur: 'EN_COURS', libelle: 'En cours', classe: 'badge badge--default' },
  { valeur: 'INCOMPLET', libelle: 'Incomplet', classe: 'badge badge--warning' },
];

function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Aujourd'hui en date Africa/Tunis (et non celle du navigateur). */
function aujourdhuiTunis(): Date {
  const [a, m, j] = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Tunis', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date())
    .split('-')
    .map(Number);
  return new Date(a, m - 1, j);
}

function periodeDefaut(): { du: string; au: string } {
  const fin = aujourdhuiTunis();
  const debut = new Date(fin);
  debut.setDate(debut.getDate() - 6);
  return { du: iso(debut), au: iso(fin) };
}

function joursEntre(du: string, au: string): number {
  return Math.round((Date.parse(`${au}T00:00:00Z`) - Date.parse(`${du}T00:00:00Z`)) / 86_400_000) + 1;
}

/** Message de période invalide (null si la période est correcte). */
function erreurPeriode(du: string, au: string): string | null {
  if (!du || !au) return 'Renseignez le début et la fin de la période.';
  if (au < du) return 'La date de fin doit être postérieure ou égale à la date de début.';
  if (joursEntre(du, au) > JOURS_MAX) return `La période ne peut pas dépasser ${JOURS_MAX} jours.`;
  return null;
}

export function hhmm(minutes: number): string {
  return formatHeuresMinutes(minutes);
}

const FMT_HEURE = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Africa/Tunis',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const FMT_HEURE_SEC = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Africa/Tunis',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});
const FMT_JOUR = new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Tunis', year: 'numeric', month: '2-digit', day: '2-digit' });

/** Heure Tunis, suffixée « (J+1) » si l'horodatage tombe après la journée de travail. */
function heureAvecJour(isoTs: string | null | undefined, jour: string, secondes = false): string {
  if (!isoTs) return '—';
  const d = new Date(isoTs);
  if (Number.isNaN(d.getTime())) return '—';
  const heure = (secondes ? FMT_HEURE_SEC : FMT_HEURE).format(d);
  return FMT_JOUR.format(d) > jour ? `${heure} (J+1)` : heure;
}

/**
 * D-P18.c : minutes (hh:mm) portées par au moins deux instants distincts de la ligne ;
 * ces instants s'affichent alors à la seconde.
 */
function minutesAmbigues(l: LigneTempsTravail): Set<string> {
  const instants = [
    ...l.paires.flatMap((p) => [p.entree, p.sortie]),
    ...l.orphelins.map((o) => o.horodatage),
  ].filter(Boolean);
  const parMinute = new Map<string, Set<string>>();
  for (const t of instants) {
    const d = new Date(t);
    if (Number.isNaN(d.getTime())) continue;
    const cle = FMT_JOUR.format(d) + ' ' + FMT_HEURE.format(d);
    parMinute.set(cle, (parMinute.get(cle) ?? new Set()).add(t));
  }
  return new Set([...parMinute].filter(([, v]) => v.size > 1).map(([k]) => k));
}

function hms(ambigues: Set<string>, isoTs: string | null | undefined, jour: string): string {
  if (!isoTs) return '—';
  const d = new Date(isoTs);
  if (Number.isNaN(d.getTime())) return '—';
  return heureAvecJour(isoTs, jour, ambigues.has(FMT_JOUR.format(d) + ' ' + FMT_HEURE.format(d)));
}

const formatJour = formatDate;

function EtatBadge({ etat, jusqua }: { etat: EtatTempsTravail; jusqua?: string | null }) {
  const e = ETATS.find((x) => x.valeur === etat);
  // D-P18.d : « En cours » indique l'instant où la journée basculera en « Incomplet ».
  const d = etat === 'EN_COURS' && jusqua ? new Date(jusqua) : null;
  const libelle = d && !Number.isNaN(d.getTime())
    ? `En cours jusqu’au ${formatDateHeure(jusqua)}`
    : (e?.libelle ?? VALEUR_NON_RECONNUE);
  return <span className={e?.classe ?? 'badge badge--default'}>{libelle}</span>;
}

function libelleUnite(l: LigneTempsTravail): string {
  return l.service_libelle || l.departement_libelle || l.direction_libelle || '—';
}

const Manuel = () => <span className="muted small"> (manuel)</span>;

function Horaires({ ligne }: { ligne: LigneTempsTravail }) {
  const { paires, orphelins, jour } = ligne;
  if (paires.length === 0 && orphelins.length === 0) return <span className="muted">—</span>;
  const amb = minutesAmbigues(ligne);
  const h = (t: string | null | undefined) => hms(amb, t, jour);
  return (
    <div>
      {paires.map((p, i) => (
        <div key={`p${i}`}>
          {h(p.entree)}
          {p.entree_manuelle ? <Manuel /> : null}
          {' → '}
          {h(p.sortie)}
          {p.sortie_manuelle ? <Manuel /> : null}
        </div>
      ))}
      {orphelins.map((o, i) => (
        <div
          key={`o${i}`}
          className="muted"
          style={{ fontStyle: 'italic' }}
          title="Pointage sans entrée ou sans sortie correspondante : non compté dans le total"
        >
          {o.type === 'SORTIE' ? `— → ${h(o.horodatage)}` : `${h(o.horodatage)} → —`}
          {o.manuel ? <Manuel /> : null}
        </div>
      ))}
    </div>
  );
}

function filtresDefaut() {
  return { ...periodeDefaut(), collab: '', site: '', direction: '', departement: '', service: '', etat: '' };
}

/**
 * Onglet « Temps de travail » (D-P17) : une ligne par collaborateur × jour × site, calculée par
 * le serveur. Lecture et export autorisés pour la DIRECTION (aucune écriture ici).
 */
export default function TempsTravailPanel() {
  const defauts = useMemo(filtresDefaut, []);
  const filtres = useFiltresUrl(defauts, 't_');
  const f = filtres.valeurs;
  const [page, setPage] = useState(0);
  const [collabs, setCollabs] = useState<CollaborateurRow[]>([]);
  const [collabsLoading, setCollabsLoading] = useState(false);
  const [unites, setUnites] = useState<Unite[]>([]);
  const [sites, setSites] = useState<PresenceSite[]>([]);
  const [data, setData] = useState<TempsTravailReponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [erreurDates, setErreurDates] = useState<string | null>(null);
  const [exportEnCours, setExportEnCours] = useState(false);
  const [exportErr, setExportErr] = useState<string | null>(null);

  const selection: SelectionUnite = { direction: f.direction, departement: f.departement, service: f.service };
  const uniteId = f.service || f.departement || f.direction;
  const erreurLocale = erreurPeriode(f.du, f.au);
  const erreurAffichee = erreurLocale ?? erreurDates;

  useEffect(() => {
    setCollabsLoading(true);
    void getTousCollaborateurs()
      .then(setCollabs)
      .catch(() => {})
      .finally(() => setCollabsLoading(false));
    void getUnites().then(setUnites).catch(() => {});
    void getPresenceSites({ size: 200 })
      .then((p) => setSites(p.contenu))
      .catch(() => {});
  }, []);

  const charger = useCallback(async () => {
    if (erreurPeriode(f.du, f.au)) {
      setData(null);
      setErr(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setErr(null);
    setErreurDates(null);
    try {
      setData(
        await getTempsTravail(
          { du: f.du, au: f.au, collaborateurId: f.collab, siteId: f.site, uniteId, etat: f.etat },
          page,
          TAILLE_PAGE,
        ),
      );
    } catch (e) {
      setData(null);
      if (e instanceof PresenceApiError && (e.code === 'PERIODE_TROP_LONGUE' || e.code === 'PERIODE_INVALIDE')) {
        setErreurDates(e.message);
      } else {
        setErr(messagePresenceErreur(e, 'Impossible de charger le temps de travail'));
      }
    } finally {
      setLoading(false);
    }
  }, [f.du, f.au, f.collab, f.site, uniteId, f.etat, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

  function changer(c: Partial<Record<keyof typeof defauts, string>>) {
    setPage(0);
    filtres.definir(c);
  }

  async function exporter() {
    if (erreurLocale) return;
    setExportEnCours(true);
    setExportErr(null);
    try {
      await exporterTempsTravailCsv({ du: f.du, au: f.au, collaborateurId: f.collab, siteId: f.site, uniteId, etat: f.etat });
    } catch (e) {
      if (e instanceof PresenceApiError && (e.code === 'PERIODE_TROP_LONGUE' || e.code === 'PERIODE_INVALIDE')) {
        setErreurDates(e.message);
      } else {
        setExportErr(messagePresenceErreur(e, 'Export impossible'));
      }
    } finally {
      setExportEnCours(false);
    }
  }

  const lignes = data?.lignes.content ?? [];
  const totalPages = data?.lignes.totalPages ?? 0;
  const siteOptions = sites
    .filter((s) => s.actif || s.id === f.site)
    .map((s) => ({ valeur: s.id, libelle: s.libelle || s.code }))
    .sort((a, b) => a.libelle.localeCompare(b.libelle, 'fr'));
  const nomCollab = (n: string | null, m: string | null) => formatPersonne(n, m);

  return (
    <div className="panel">
      <BarreFiltres
        id="temps-travail"
        nbResultats={data?.nb_lignes ?? 0}
        nbActifs={filtres.nbActifs}
        onReinitialiser={() => {
          setPage(0);
          filtres.reinitialiser();
        }}
      >
        <ChampDate id="tt-du" libelle="Du" valeur={f.du} max={f.au || undefined} onChange={(v) => changer({ du: v })} />
        <ChampDate id="tt-au" libelle="Au" valeur={f.au} min={f.du || undefined} onChange={(v) => changer({ au: v })} />
        <FiltreCollaborateur
          id="tt-collab"
          collaborateurs={collabs}
          valeur={f.collab}
          chargement={collabsLoading}
          onChange={(v) => changer({ collab: v })}
        />
        <ChampSelect id="tt-site" libelle="Site" valeur={f.site} options={siteOptions} toutesLibelle="Tous les sites" onChange={(v) => changer({ site: v })} />
        <FiltresUnite
          idPrefixe="tt-unite"
          unites={unites}
          selection={selection}
          onChange={(s) => changer({ direction: s.direction, departement: s.departement, service: s.service })}
        />
        <ChampSelect
          id="tt-etat"
          libelle="État"
          valeur={f.etat}
          options={ETATS.map((e) => ({ valeur: e.valeur, libelle: e.libelle }))}
          toutesLibelle="Tous les états"
          onChange={(v) => changer({ etat: v })}
        />
        <button
          type="button"
          className="btn btn--secondary"
          style={{ alignSelf: 'end' }}
          disabled={exportEnCours || !!erreurLocale}
          onClick={() => void exporter()}
        >
          {exportEnCours ? 'Export…' : 'Exporter CSV'}
        </button>
      </BarreFiltres>

      {!erreurLocale ? (
        <p className="muted small" style={{ margin: '4px 0 0' }}>Période affichée : {formatPeriode(f.du, f.au)}</p>
      ) : null}
      {erreurAffichee ? (
        <div className="alert alert--error" role="alert">
          {erreurAffichee}
        </div>
      ) : null}
      {exportErr ? <div className="alert alert--error">{exportErr}</div> : null}
      {err ? (
        <div className="alert alert--error">
          {err}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {loading ? (
        <LoadingState label="Chargement du temps de travail…" />
      ) : data && data.nb_lignes === 0 ? (
        filtres.nbActifs > 0 ? (
          <AucunResultat
            onReinitialiser={() => {
              setPage(0);
              filtres.reinitialiser();
            }}
          />
        ) : (
          <EmptyState icon="inbox" title="Aucun pointage retenu sur cette période">
            Seuls les pointages validés (y compris manuels) sont comptés.
          </EmptyState>
        )
      ) : data ? (
        <>
          <div
            role="status"
            aria-label="Totaux du résultat filtré"
            style={{ display: 'flex', gap: 24, flexWrap: 'wrap', margin: '12px 0' }}
          >
            <div>
              <div className="muted small">Total travaillé</div>
              <strong>{hhmm(data.total_minutes)}</strong>
            </div>
            <div>
              <div className="muted small">Journées</div>
              <strong>{data.nb_lignes}</strong>
            </div>
            <div>
              <div className="muted small">Journées incomplètes</div>
              <strong>{data.nb_incompletes ?? 0}</strong>
            </div>
            <div>
              <div className="muted small">En cours</div>
              <strong>{data.nb_en_cours ?? 0}</strong>
            </div>
          </div>

          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Collaborateur</th>
                  <th>Unité</th>
                  <th>Site</th>
                  <th>Jour</th>
                  <th>Entrées / sorties</th>
                  <th>Première entrée</th>
                  <th>Dernière sortie</th>
                  <th>Total</th>
                  <th>État</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((l) => (
                  <tr key={`${l.collaborateur_id}|${l.site_id}|${l.jour}`}>
                    <td>
                      {nomCollab(l.collaborateur_nom, l.collaborateur_matricule)}
                    </td>
                    <td>{libelleUnite(l)}</td>
                    <td>{l.site_libelle ?? SITE_INTROUVABLE}</td>
                    <td>{formatJour(l.jour)}</td>
                    <td>
                      <Horaires ligne={l} />
                    </td>
                    <td>{hms(minutesAmbigues(l), l.premiere_entree, l.jour)}</td>
                    <td>{hms(minutesAmbigues(l), l.derniere_sortie, l.jour)}</td>
                    <td>{hhmm(l.minutes_travaillees)}</td>
                    <td>
                      <EtatBadge etat={l.etat} jusqua={l.en_cours_jusqu_a} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager">
            <button type="button" className="btn btn--ghost" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
              Précédent
            </button>
            <span className="muted small">
              Page {page + 1} sur {Math.max(totalPages, 1)} ({data.lignes.totalElements} journées)
            </span>
            <button type="button" className="btn btn--ghost" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
              Suivant
            </button>
          </div>

          {data.synthese.length > 0 ? (
            <>
              <h3 className="mt-md">Synthèse par collaborateur</h3>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Collaborateur</th>
                      <th>Jours</th>
                      <th>Incomplètes</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.synthese.map((s) => (
                      <tr key={s.collaborateur_id}>
                        <td>
                          {nomCollab(s.collaborateur_nom, s.collaborateur_matricule)}
                        </td>
                        <td>{s.nb_jours}</td>
                        <td>{s.nb_incompletes ?? 0}</td>
                        <td>{hhmm(s.minutes)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
