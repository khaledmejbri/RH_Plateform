import { FormEvent, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  getDemandesHeuresSup,
  getHeuresComptees,
  LIBELLES_ETAT_COMPTEES,
  LIBELLES_STATUT_HEURES_SUP,
  postAnnulerHeuresSup,
  postCreerDemandeHeuresSup,
  postRefuserHeuresSup,
  postValiderHeuresSup,
  telechargerHeuresSupCsv,
  type DemandeHeuresSup,
  type FiltreComptees,
  type HeuresComptees,
  type StatutHeuresSup,
} from '../api/heuresSupApi';
import {
  codeErreur,
  formatDateCourteTunis,
  formatDateTunis,
  getPresenceSites,
  messageErreurCode,
  type PagePresence,
  type PresenceSite,
} from '../api/presenceApi';
import { getTousCollaborateurs, getUnites, type CollaborateurRow } from '../api/rhClient';
import { useSearchParams } from 'react-router-dom';
import BarreFiltres, { ChampDate, ChampSelect } from '../components/BarreFiltres';
import FiltreCollaborateur from '../components/FiltreCollaborateur';
import { useFiltresUrl } from '../utils/filtres';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';
import { SaisieDate } from '../components/ChampsFr';
import { formatDuree, formatHeure, formatPeriode, formatPersonne, jourIso, ressembleAUnIdentifiant } from '../utils/format';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';
import DetailLink, { LienPersonne } from '../detail/DetailLink';

type Onglet = 'a-valider' | 'historique' | 'comptees';

const PAGE_SIZE = 20;
const ONGLETS: Onglet[] = ['a-valider', 'historique', 'comptees'];
const FILTRES_DEFAUT = { du: '', au: '', site: '', collab: '', direction: '', statut: '' };
const DUREES_OUVERTURE = [0, 30, 60, 90, 120, 150, 180, 210, 240];

function libelleDuree(min: number): string {
  return min <= 0 ? 'Aucune' : formatDuree(min);
}

/** Numéro lisible de la demande ; jamais un identifiant technique. */
function numeroDemande(d: DemandeHeuresSup): string {
  if (d.numero) return d.numero;
  return d.reference && !ressembleAUnIdentifiant(d.reference) ? d.reference : '';
}

/** « la demande HS-202611-0001 (Sarra Khemiri) » pour les messages et confirmations. */
function intituleDemande(d: DemandeHeuresSup): string {
  const num = numeroDemande(d);
  const noms = (d.beneficiaires ?? []).map((b) => formatPersonne(b.nom, b.matricule)).join(', ');
  return `la demande d’heures supplémentaires${num ? ` ${num}` : ''}${noms ? ` (${noms})` : ''}`;
}

/** Auteur d'une décision : le nom fourni par le serveur, jamais un identifiant. */
function auteur(v: string | null): string {
  return v && !ressembleAUnIdentifiant(v) ? v : 'Personne introuvable';
}

function nomCollaborateur(c: CollaborateurRow): string {
  return `${c.prenom ?? ''} ${c.nom ?? c.name ?? ''}`.trim() || c.matricule;
}

function sansAccent(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="hs-modal-title">
        <div className="modal__head">
          <h3 id="hs-modal-title" className="modal__title" style={{ margin: 0 }}>
            {title}
          </h3>
          <button type="button" className="btn btn--ghost btn--sm" onClick={onClose} aria-label="Fermer">
            Fermer
          </button>
        </div>
        <div className="modal__body">{children}</div>
      </div>
    </div>
  );
}

function StatutBadge({ statut }: { statut: StatutHeuresSup }) {
  const cls =
    statut === 'VALIDEE'
      ? 'badge badge--success'
      : statut === 'EN_ATTENTE'
        ? 'badge badge--info'
        : statut === 'REFUSEE'
          ? 'badge badge--warning'
          : 'badge badge--default';
  return <span className={cls}>{LIBELLES_STATUT_HEURES_SUP[statut] ?? VALEUR_NON_RECONNUE}</span>;
}

function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (p: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="pager">
      <button type="button" className="btn btn--secondary btn--sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Précédent
      </button>
      <span className="small">
        Page {page + 1} sur {totalPages}
      </span>
      <button
        type="button"
        className="btn btn--secondary btn--sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Suivant
      </button>
    </div>
  );
}

/** Demande validée dont la fenêtre n'a pas encore ouvert (indication ; le serveur tranche en 409). */
function annulable(d: DemandeHeuresSup): boolean {
  if (d.statut !== 'VALIDEE') return false;
  if (!d.debut_plage_ts) return true;
  const t = Date.parse(d.debut_plage_ts);
  return !Number.isFinite(t) || t > Date.now();
}

function plageTexte(d: DemandeHeuresSup): string {
  const dates = formatPeriode(d.date_debut, d.date_fin);
  const parts: string[] = [];
  if (d.ouverture_avant_minutes > 0) parts.push(`${libelleDuree(d.ouverture_avant_minutes)} avant`);
  if (d.ouverture_apres_minutes > 0) parts.push(`${libelleDuree(d.ouverture_apres_minutes)} après`);
  return `${dates} · ${parts.join(' + ') || '—'}`;
}

export default function HeuresSupPage() {
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const ongletUrl = params.get('onglet') as Onglet | null;
  const [onglet, setOngletEtat] = useState<Onglet>(ongletUrl && ONGLETS.includes(ongletUrl) ? ongletUrl : 'a-valider');
  /** L'onglet est mémorisé dans l'URL avec les filtres (rafraîchissement conservé). */
  function setOnglet(o: Onglet) {
    setOngletEtat(o);
    setParams(
      (prev) => {
        const suivant = new URLSearchParams(prev);
        if (o === 'a-valider') suivant.delete('onglet');
        else suivant.set('onglet', o);
        return suivant;
      },
      { replace: true },
    );
  }

  /* Filtres communs */
  const filtres = useFiltresUrl(FILTRES_DEFAUT, 'h_');
  const { du, au, site: siteId, collab: collaborateurId, direction: directionId, statut } = filtres.valeurs;

  /* Référentiels de filtres */
  const [sites, setSites] = useState<PresenceSite[]>([]);
  const [collaborateurs, setCollaborateurs] = useState<CollaborateurRow[]>([]);
  const [directions, setDirections] = useState<{ id: string; libelle: string }[]>([]);
  const [refsErr, setRefsErr] = useState<string | null>(null);

  /* Données */
  const [demandes, setDemandes] = useState<PagePresence<DemandeHeuresSup> | null>(null);
  const [comptees, setComptees] = useState<PagePresence<HeuresComptees> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [exportBusy, setExportBusy] = useState(false);

  const [creerOuvert, setCreerOuvert] = useState(false);

  useEffect(() => {
    let annule = false;
    (async () => {
      try {
        const [s, c, u] = await Promise.all([
          getPresenceSites({ size: 200 }),
          getTousCollaborateurs(),
          getUnites(),
        ]);
        if (annule) return;
        setSites(s.contenu);
        setCollaborateurs(c);
        setDirections(
          u
            .filter((x) => sansAccent(x.type_noeud ?? '') === 'DIRECTION')
            .map((x) => ({ id: x.identifiant, libelle: x.libelle })),
        );
      } catch (e) {
        if (!annule) setRefsErr(messageErreurCode(e, 'Listes de filtres indisponibles'));
      }
    })();
    return () => {
      annule = true;
    };
  }, []);

  const filtreComptees: FiltreComptees = useMemo(
    () => ({ du, au, siteId, collaborateurId, directionId }),
    [du, au, siteId, collaborateurId, directionId],
  );

  const periodeComptees = Boolean(du && au);

  const charger = useCallback(async () => {
    setErr(null);
    if (onglet === 'comptees') {
      if (!periodeComptees) {
        setComptees(null);
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        setComptees(await getHeuresComptees(filtreComptees, page, PAGE_SIZE));
      } catch (e) {
        setErr(messageErreurCode(e, 'Impossible de charger les heures comptées'));
        setComptees(null);
      } finally {
        setLoading(false);
      }
      return;
    }
    setLoading(true);
    try {
      setDemandes(
        await getDemandesHeuresSup(
          { ...filtreComptees, statut: onglet === 'a-valider' ? 'EN_ATTENTE' : statut },
          page,
          PAGE_SIZE,
        ),
      );
    } catch (e) {
      setErr(messageErreurCode(e, 'Impossible de charger les demandes'));
      setDemandes(null);
    } finally {
      setLoading(false);
    }
  }, [onglet, periodeComptees, filtreComptees, statut, page]);

  useEffect(() => {
    void charger();
  }, [charger]);

  function changerFiltre(maj: () => void) {
    maj();
    setPage(0);
    setMsg(null);
  }

  function changerOnglet(o: Onglet) {
    setOnglet(o);
    setPage(0);
    setMsg(null);
    setErr(null);
  }

  async function valider(d: DemandeHeuresSup) {
    if (!(await confirm({ message: `Valider ${intituleDemande(d)} ?`, confirmLabel: 'Valider' }))) return;
    setBusyId(d.id);
    setErr(null);
    setMsg(null);
    try {
      await postValiderHeuresSup(d.id);
      setMsg(`La demande${numeroDemande(d) ? ` ${numeroDemande(d)}` : ''} est validée.`);
      await charger();
    } catch (e) {
      const message = messageErreurCode(e);
      // charger() remet l'erreur à zéro : le message est posé après le rechargement.
      if (codeErreur(e) === 'STATUT_INCOMPATIBLE') await charger();
      setErr(message);
    } finally {
      setBusyId(null);
    }
  }

  /** Refus (motif obligatoire) ou annulation (motif facultatif) : une seule fenêtre porte motif, envoi et erreur. */
  async function decider(genre: 'refuser' | 'annuler', d: DemandeHeuresSup) {
    const refus = genre === 'refuser';
    let conflit: string | null = null;
    const ok = await confirm({
      message: `${refus ? 'Refuser' : 'Annuler'} ${intituleDemande(d)} ?`,
      confirmLabel: refus ? 'Refuser' : 'Annuler la demande',
      danger: true,
      motif: { label: refus ? 'Motif du refus' : 'Motif de l’annulation', obligatoire: refus },
      envoyer: async (motif) => {
        try {
          if (refus) await postRefuserHeuresSup(d.id, motif);
          else await postAnnulerHeuresSup(d.id, motif);
        } catch (e) {
          const message = messageErreurCode(e);
          if (codeErreur(e) === 'STATUT_INCOMPATIBLE') conflit = message;
          throw new Error(message);
        }
      },
      echecDefinitif: () => conflit !== null,
    });
    if (conflit) {
      // La demande a changé entre-temps : fenêtre fermée, erreur affichée, liste rechargée.
      // charger() remet l'erreur à zéro : le message est posé après le rechargement.
      await charger();
      setErr(conflit);
      return;
    }
    if (!ok) return;
    setMsg(refus ? 'La demande a été refusée.' : 'La demande a été annulée. Les bénéficiaires sont notifiés.');
    await charger();
  }

  async function exporter() {
    setExportBusy(true);
    setErr(null);
    try {
      await telechargerHeuresSupCsv(filtreComptees);
    } catch (e) {
      setErr(messageErreurCode(e, 'Export impossible'));
    } finally {
      setExportBusy(false);
    }
  }

  const enAttente = onglet === 'a-valider';
  const titreVide =
    onglet === 'a-valider'
      ? 'Aucune demande d’heures supplémentaires à valider.'
      : 'Aucune demande ne correspond aux filtres.';

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Heures supplémentaires</h2>
          <p className="page__lead">
            Demandes d’ouverture du pointage hors horaire, validation par la RH et heures comptées par collaborateur.
          </p>
        </div>
        <div className="tabs">
          <button type="button" className={'tab' + (onglet === 'a-valider' ? ' tab--on' : '')} onClick={() => changerOnglet('a-valider')}>
            À valider
          </button>
          <button type="button" className={'tab' + (onglet === 'historique' ? ' tab--on' : '')} onClick={() => changerOnglet('historique')}>
            Historique
          </button>
          <button type="button" className={'tab' + (onglet === 'comptees' ? ' tab--on' : '')} onClick={() => changerOnglet('comptees')}>
            Heures comptées
          </button>
          {!lectureSeule ? (
            <button type="button" className="btn btn--primary" onClick={() => setCreerOuvert(true)}>
              Créer une demande validée
            </button>
          ) : null}
        </div>
      </div>

      {lectureSeule ? (
        <div className="alert alert--info" role="status">
          Consultation uniquement : vous pouvez lire les demandes et exporter les heures comptées.
        </div>
      ) : null}
      {refsErr ? (
        <div className="alert alert--warning" role="status">
          {refsErr} — les filtres site, collaborateur et direction peuvent être incomplets.
        </div>
      ) : null}
      {msg ? (
        <div className="alert alert--success" role="status">
          {msg}
        </div>
      ) : null}
      {err ? (
        <div className="alert alert--error" role="alert">
          {err}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      <div className="panel">
        <BarreFiltres
          id="hs"
          nbResultats={onglet === 'comptees' ? (comptees?.totalElements ?? 0) : (demandes?.totalElements ?? 0)}
          nbActifs={filtres.nbActifs}
          onReinitialiser={() => changerFiltre(filtres.reinitialiser)}
        >
          <ChampDate id="hs-du" libelle="Du" valeur={du} max={au} onChange={(v) => changerFiltre(() => filtres.definir({ du: v }))} />
          <ChampDate id="hs-au" libelle="Au" valeur={au} min={du} onChange={(v) => changerFiltre(() => filtres.definir({ au: v }))} />
          <ChampSelect
            id="hs-site"
            libelle="Site"
            valeur={siteId}
            options={sites.map((x) => ({ valeur: x.id, libelle: x.libelle }))}
            onChange={(v) => changerFiltre(() => filtres.definir({ site: v }))}
          />
          <FiltreCollaborateur
            id="hs-collab"
            collaborateurs={collaborateurs}
            valeur={collaborateurId}
            onChange={(v) => changerFiltre(() => filtres.definir({ collab: v }))}
          />
          <ChampSelect
            id="hs-direction"
            libelle="Direction"
            valeur={directionId}
            options={directions.map((d) => ({ valeur: d.id, libelle: d.libelle }))}
            onChange={(v) => changerFiltre(() => filtres.definir({ direction: v }))}
          />
          {onglet === 'historique' ? (
            <ChampSelect
              id="hs-statut"
              libelle="Statut"
              valeur={statut}
              toutesLibelle="Tous les statuts"
              options={(Object.keys(LIBELLES_STATUT_HEURES_SUP) as StatutHeuresSup[]).map((x) => ({
                valeur: x,
                libelle: LIBELLES_STATUT_HEURES_SUP[x],
              }))}
              onChange={(v) => changerFiltre(() => filtres.definir({ statut: v }))}
            />
          ) : null}
        </BarreFiltres>

        {onglet === 'comptees' ? (
          <>
            <div className="page__head-actions mb-md">
              <button
                type="button"
                className="btn btn--secondary"
                disabled={!periodeComptees || exportBusy}
                title={!periodeComptees ? 'Choisissez une période (93 jours maximum)' : undefined}
                onClick={() => void exporter()}
              >
                {exportBusy ? 'Export en cours…' : 'Exporter en CSV'}
              </button>
              <span className="muted small">Période de 93 jours maximum.</span>
            </div>
            {!periodeComptees ? (
              <div className="empty-state">
                <h3>Choisissez une période</h3>
                <p>Renseignez les dates « Du » et « Au » pour afficher les heures comptées.</p>
              </div>
            ) : loading ? (
              <p className="muted">Chargement des heures comptées…</p>
            ) : !err && comptees && comptees.contenu.length === 0 ? (
              <div className="empty-state">
                <h3>Aucune heure comptée</h3>
                <p>Aucune heure supplémentaire comptée pour cette période et ces filtres.</p>
              </div>
            ) : comptees && comptees.contenu.length > 0 ? (
              <>
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Collaborateur</th>
                        <th>Site</th>
                        <th>Date</th>
                        <th>Horaire du jour</th>
                        <th>Plage validée</th>
                        <th>Entrée</th>
                        <th>Sortie</th>
                        <th>Heures comptées</th>
                        <th>Numéro de la demande</th>
                        <th>État</th>
                      </tr>
                    </thead>
                    <tbody>
                      {comptees.contenu.map((h, i) => (
                        <tr key={`${h.collaborateur_id}-${h.site_id}-${h.journee}-${i}`}>
                          <td>
                            <LienPersonne id={h.collaborateur_id} nom={h.nom} matricule={h.matricule} />
                          </td>
                          <td>{h.site_libelle ? <DetailLink type="site" id={h.site_id} libelle={h.site_libelle} /> : '—'}</td>
                          <td>{formatDateCourteTunis(h.journee)}</td>
                          <td className="small">
                            {h.horaire_debut && h.horaire_fin ? `${formatHeure(h.horaire_debut)} – ${formatHeure(h.horaire_fin)}` : '—'}
                          </td>
                          <td className="small">
                            {h.plage_avant ? <div>Avant : {h.plage_avant}</div> : null}
                            {h.plage_apres ? <div>Après : {h.plage_apres}</div> : null}
                            {!h.plage_avant && !h.plage_apres ? '—' : null}
                          </td>
                          <td className="small">{h.entree ? formatDateTunis(h.entree) : '—'}</td>
                          <td className="small">{h.sortie ? formatDateTunis(h.sortie) : '—'}</td>
                          <td className="mono">{h.heures_comptees}</td>
                          <td className="mono small">{h.reference_demande && !ressembleAUnIdentifiant(h.reference_demande) ? h.reference_demande : '—'}</td>
                          <td>
                            <span
                              className={
                                h.etat === 'COMPLET'
                                  ? 'badge badge--success'
                                  : h.etat === 'INCOMPLET'
                                    ? 'badge badge--warning'
                                    : 'badge badge--info'
                              }
                            >
                              {LIBELLES_ETAT_COMPTEES[h.etat] ?? VALEUR_NON_RECONNUE}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pager page={comptees.page} totalPages={comptees.totalPages} onChange={setPage} />
              </>
            ) : null}
          </>
        ) : loading ? (
          <p className="muted">Chargement des demandes…</p>
        ) : !err && demandes && demandes.contenu.length === 0 ? (
          <div className="empty-state">
            <h3>Aucune demande</h3>
            <p>{titreVide}</p>
          </div>
        ) : demandes && demandes.contenu.length > 0 ? (
          <>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Numéro</th>
                    <th>Site</th>
                    <th>Plage demandée</th>
                    <th>Bénéficiaires</th>
                    <th>Motif</th>
                    <th>Statut</th>
                    <th>Suivi</th>
                    {!lectureSeule ? <th>Actions</th> : null}
                  </tr>
                </thead>
                <tbody>
                  {demandes.contenu.map((d) => (
                    <tr key={d.id}>
                      <td className="mono small">
                        <DetailLink type="heures-sup" id={d.id} libelle={numeroDemande(d)} />
                        {d.creee_validee ? <div className="muted">créée validée</div> : null}
                      </td>
                      <td>{d.site_libelle ? <DetailLink type="site" id={d.site_id} libelle={d.site_libelle} /> : '—'}</td>
                      <td className="small">
                        {plageTexte(d)}
                        {d.jours?.length ? <div className="muted">{d.jours.length} jour{d.jours.length > 1 ? 's' : ''} ouvert{d.jours.length > 1 ? 's' : ''}</div> : null}
                      </td>
                      <td className="small">
                        {(d.beneficiaires ?? []).length === 0 ? '—' : (d.beneficiaires ?? []).map((b, i) => <span key={b.collaborateur_id}>{i > 0 ? ', ' : ''}<LienPersonne id={b.collaborateur_id} nom={b.nom} matricule={b.matricule} /></span>)}
                        {d.demandeur_nom ? <div className="muted">Demandeur : {auteur(d.demandeur_nom)}</div> : null}
                      </td>
                      <td className="small">{d.motif}</td>
                      <td>
                        <StatutBadge statut={d.statut} />
                      </td>
                      <td className="small muted">
                        {d.decide_par ? <div>Décidé par {auteur(d.decide_par)} · {formatDateTunis(d.decide_le)}</div> : null}
                        {d.motif_refus ? <div>Motif du refus : {d.motif_refus}</div> : null}
                        {d.annule_par ? <div>Annulé par {auteur(d.annule_par)} · {formatDateTunis(d.annule_le)}</div> : null}
                        {d.motif_annulation ? <div>Motif : {d.motif_annulation}</div> : null}
                        {!d.decide_par && !d.annule_par ? formatDateTunis(d.cree_le) : null}
                      </td>
                      {!lectureSeule ? (
                        <td>
                          {d.statut === 'EN_ATTENTE' ? (
                            <div className="page__head-actions">
                              <button
                                type="button"
                                className="btn btn--primary btn--sm"
                                disabled={busyId === d.id}
                                onClick={() => void valider(d)}
                              >
                                Valider
                              </button>
                              <button
                                type="button"
                                className="btn btn--secondary btn--sm"
                                disabled={busyId === d.id}
                                onClick={() => void decider('refuser', d)}
                              >
                                Refuser
                              </button>
                            </div>
                          ) : annulable(d) ? (
                            <button
                              type="button"
                              className="btn btn--danger btn--sm"
                              disabled={busyId === d.id}
                              onClick={() => void decider('annuler', d)}
                            >
                              Annuler
                            </button>
                          ) : (
                            <span className="muted small">—</span>
                          )}
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={demandes.page} totalPages={demandes.totalPages} onChange={setPage} />
            {enAttente ? null : (
              <p className="muted small mt-sm">
                « Annuler » n’est proposé que pour une demande validée dont la fenêtre de pointage n’a pas encore ouvert.
              </p>
            )}
          </>
        ) : null}
      </div>

      {creerOuvert && !lectureSeule ? (
        <CreationModal
          sites={sites}
          collaborateurs={collaborateurs}
          onClose={() => setCreerOuvert(false)}
          onCreated={async (numero) => {
            setCreerOuvert(false);
            setMsg(`Demande${numero ? ` ${numero}` : ''} créée et validée. Les bénéficiaires sont notifiés.`);
            await charger();
          }}
        />
      ) : null}
    </div>
  );
}

/* ─── Création directement validée (RH / ADMIN, D-P13.c) ───────────────── */

type ChampCreation = 'site' | 'collaborateurs' | 'dates' | 'ouverture' | 'motif';

function champDuCode(code: string | undefined): ChampCreation | null {
  switch (code) {
    case 'SITE_INTROUVABLE':
    case 'SITE_SANS_HORAIRE':
      return 'site';
    case 'COLLABORATEUR_INVALIDE':
    case 'HORS_PERIMETRE':
    case 'AUTO_VALIDATION_INTERDITE':
    case 'CHEVAUCHEMENT_HEURES_SUP':
      return 'collaborateurs';
    case 'PERIODE_INVALIDE':
    case 'PLAGE_TROP_LONGUE':
    case 'AUCUN_JOUR_OUVERT':
    case 'PLAGE_DEJA_COMMENCEE':
    case 'HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN':
      return 'dates';
    case 'DUREE_HEURES_SUP_INVALIDE':
    case 'OUVERTURE_ABSENTE':
    case 'HORAIRES_CHEVAUCHANTS':
      return 'ouverture';
    case 'MOTIF_OBLIGATOIRE':
      return 'motif';
    default:
      return null;
  }
}

function CreationModal({
  sites,
  collaborateurs,
  onClose,
  onCreated,
}: {
  sites: PresenceSite[];
  collaborateurs: CollaborateurRow[];
  onClose: () => void;
  onCreated: (numero: string) => Promise<void>;
}) {
  const [site, setSite] = useState('');
  const [choisis, setChoisis] = useState<Set<string>>(new Set());
  const [recherche, setRecherche] = useState('');
  const confirm = useConfirm();
  const [dateDebut, setDateDebut] = useState(jourIso());
  const [dateFin, setDateFin] = useState('');
  const [avant, setAvant] = useState(0);
  const [apres, setApres] = useState(0);
  const [motif, setMotif] = useState('');
  const [champs, setChamps] = useState<Partial<Record<ChampCreation, string>>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const visibles = useMemo(() => {
    const q = sansAccent(recherche.trim());
    return collaborateurs.filter(
      (c) => !q || sansAccent(`${nomCollaborateur(c)} ${c.matricule}`).includes(q),
    );
  }, [collaborateurs, recherche]);

  function basculer(id: string) {
    setChoisis((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
    setChamps((c) => ({ ...c, collaborateurs: undefined }));
  }

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setErreur(null);
    const locales: Partial<Record<ChampCreation, string>> = {};
    if (!site) locales.site = 'Choisissez un site.';
    if (choisis.size === 0) locales.collaborateurs = 'Choisissez au moins un collaborateur (50 maximum).';
    else if (choisis.size > 50) locales.collaborateurs = '50 collaborateurs au maximum par demande.';
    if (!dateDebut) locales.dates = 'Renseignez la date de début.';
    if (!motif.trim()) locales.motif = 'Le motif est obligatoire.';
    if (avant === 0 && apres === 0) locales.ouverture = 'Renseignez une ouverture avant et/ou après l’horaire.';
    if (Object.keys(locales).length > 0) {
      setChamps(locales);
      return;
    }
    setChamps({});
    const ok = await confirm({
      message: `Créer et valider une demande d’heures supplémentaires pour ${choisis.size} collaborateur${choisis.size > 1 ? 's' : ''} ? Les bénéficiaires seront notifiés.`,
      confirmLabel: 'Créer et valider',
    });
    if (!ok) return;
    setBusy(true);
    try {
      const d = await postCreerDemandeHeuresSup({
        collaborateur_ids: [...choisis],
        site_id: site,
        date_debut: dateDebut,
        date_fin: dateFin || undefined,
        ouverture_avant_minutes: avant || undefined,
        ouverture_apres_minutes: apres || undefined,
        motif: motif.trim(),
      });
      await onCreated(d ? numeroDemande(d) : '');
    } catch (er) {
      const champ = champDuCode(codeErreur(er));
      if (champ) setChamps({ [champ]: messageErreurCode(er) });
      else setErreur(messageErreurCode(er));
      setBusy(false);
    }
  }

  const sitesAvecHoraire = sites.filter((s) => s.actif);

  return (
    <Modal title="Créer une demande d’heures supplémentaires validée" onClose={onClose}>
      <form onSubmit={soumettre}>
        {erreur ? (
          <div className="alert alert--error" role="alert">
            {erreur}
          </div>
        ) : null}
        <p className="muted small">
          La demande est créée directement validée et les bénéficiaires sont notifiés. Vous ne pouvez pas en être
          bénéficiaire.
        </p>
        <div className="form-grid">
          <label>
            Site
            <select
              value={site}
              aria-invalid={champs.site ? true : undefined}
              onChange={(e) => {
                setSite(e.target.value);
                setChamps((c) => ({ ...c, site: undefined }));
              }}
            >
              <option value="">Choisir un site…</option>
              {sitesAvecHoraire.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.libelle}
                </option>
              ))}
            </select>
            {champs.site ? <span className="text-danger small">{champs.site}</span> : null}
          </label>
          <label>
            Date de début
            <SaisieDate
              valeur={dateDebut}
              onChange={(v) => {
                setDateDebut(v);
                setChamps((c) => ({ ...c, dates: undefined }));
              }}
            />
          </label>
          <label>
            Date de fin (facultative, 31 jours max)
            <SaisieDate
              valeur={dateFin}
              min={dateDebut || undefined}
              onChange={(v) => {
                setDateFin(v);
                setChamps((c) => ({ ...c, dates: undefined }));
              }}
            />
          </label>
        </div>
        {champs.dates ? <p className="text-danger small">{champs.dates}</p> : null}

        <div className="form-grid mt-sm">
          <label>
            Ouverture avant l’horaire
            <select
              value={avant}
              onChange={(e) => {
                setAvant(Number(e.target.value));
                setChamps((c) => ({ ...c, ouverture: undefined }));
              }}
            >
              {DUREES_OUVERTURE.map((m) => (
                <option key={m} value={m}>
                  {libelleDuree(m)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Ouverture après l’horaire
            <select
              value={apres}
              onChange={(e) => {
                setApres(Number(e.target.value));
                setChamps((c) => ({ ...c, ouverture: undefined }));
              }}
            >
              {DUREES_OUVERTURE.map((m) => (
                <option key={m} value={m}>
                  {libelleDuree(m)}
                </option>
              ))}
            </select>
          </label>
        </div>
        {champs.ouverture ? <p className="text-danger small">{champs.ouverture}</p> : null}

        <fieldset className="mt-sm" style={{ border: 0, padding: 0 }}>
          <legend>
            Bénéficiaires ({choisis.size} sélectionné{choisis.size > 1 ? 's' : ''})
          </legend>
          <input
            type="search"
            placeholder="Rechercher un nom ou un matricule"
            value={recherche}
            aria-label="Rechercher un collaborateur"
            onChange={(e) => setRecherche(e.target.value)}
          />
          <div style={{ maxHeight: 180, overflowY: 'auto', marginTop: 6 }}>
            {collaborateurs.length === 0 ? (
              <p className="muted small">Aucun collaborateur disponible.</p>
            ) : visibles.length === 0 ? (
              <p className="muted small">Aucun collaborateur ne correspond à la recherche.</p>
            ) : (
              visibles.map((c) => (
                <label key={c.identifiant} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input type="checkbox" checked={choisis.has(c.identifiant)} onChange={() => basculer(c.identifiant)} />
                  <span className="small">
                    {formatPersonne(nomCollaborateur(c), c.matricule)}
                  </span>
                </label>
              ))
            )}
          </div>
          {champs.collaborateurs ? <span className="text-danger small">{champs.collaborateurs}</span> : null}
        </fieldset>

        <label className="mt-sm" style={{ display: 'block' }}>
          Motif (obligatoire)
          <textarea
            value={motif}
            rows={2}
            maxLength={500}
            aria-invalid={champs.motif ? true : undefined}
            onChange={(e) => {
              setMotif(e.target.value);
              setChamps((c) => ({ ...c, motif: undefined }));
            }}
          />
          {champs.motif ? <span className="text-danger small">{champs.motif}</span> : null}
        </label>

        <div className="modal__actions mt-md">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Annuler
          </button>
          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? 'Création…' : 'Créer et valider'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
