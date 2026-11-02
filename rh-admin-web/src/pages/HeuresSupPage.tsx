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
import { getCollaborateursPage, getUnites, type CollaborateurRow } from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';

type Onglet = 'a-valider' | 'historique' | 'comptees';
type Decision = { genre: 'refuser' | 'annuler'; demande: DemandeHeuresSup };

const PAGE_SIZE = 20;
const DUREES_OUVERTURE = [0, 30, 60, 90, 120, 150, 180, 210, 240];

function libelleDuree(min: number): string {
  if (min <= 0) return 'Aucune';
  const h = Math.floor(min / 60);
  const m = min % 60;
  return h > 0 ? `${h} h${m ? ` ${String(m).padStart(2, '0')}` : ''}` : `${m} min`;
}

function nomCollaborateur(c: CollaborateurRow): string {
  return `${c.prenom ?? ''} ${c.nom ?? c.name ?? ''}`.trim() || c.matricule;
}

function sansAccent(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();
}

function dateIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
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
  return <span className={cls}>{LIBELLES_STATUT_HEURES_SUP[statut] ?? statut}</span>;
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
        Page {page + 1} / {totalPages}
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
  const dates =
    d.date_debut === d.date_fin
      ? formatDateCourteTunis(d.date_debut)
      : `${formatDateCourteTunis(d.date_debut)} → ${formatDateCourteTunis(d.date_fin)}`;
  const parts: string[] = [];
  if (d.ouverture_avant_minutes > 0) parts.push(`${libelleDuree(d.ouverture_avant_minutes)} avant`);
  if (d.ouverture_apres_minutes > 0) parts.push(`${libelleDuree(d.ouverture_apres_minutes)} après`);
  return `${dates} · ${parts.join(' + ') || '—'}`;
}

export default function HeuresSupPage() {
  const lectureSeule = useLectureSeule();
  const [onglet, setOnglet] = useState<Onglet>('a-valider');

  /* Filtres communs */
  const [du, setDu] = useState('');
  const [au, setAu] = useState('');
  const [siteId, setSiteId] = useState('');
  const [collaborateurId, setCollaborateurId] = useState('');
  const [directionId, setDirectionId] = useState('');
  const [statut, setStatut] = useState('');

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

  const [decision, setDecision] = useState<Decision | null>(null);
  const [creerOuvert, setCreerOuvert] = useState(false);

  useEffect(() => {
    let annule = false;
    (async () => {
      try {
        const [s, c, u] = await Promise.all([
          getPresenceSites({ size: 200 }),
          getCollaborateursPage(0, 500),
          getUnites(),
        ]);
        if (annule) return;
        setSites(s.contenu);
        setCollaborateurs(c.contenu ?? []);
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
    setBusyId(d.id);
    setErr(null);
    setMsg(null);
    try {
      await postValiderHeuresSup(d.id);
      setMsg(`Demande ${d.reference} validée.`);
      await charger();
    } catch (e) {
      setErr(messageErreurCode(e));
      if (codeErreur(e) === 'STATUT_INCOMPATIBLE') await charger();
    } finally {
      setBusyId(null);
    }
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
            Demandes d’ouverture du pointage hors horaire, validation RH et heures comptées par collaborateur.
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
          Profil Direction — consultation et export uniquement.
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
        <div className="form-grid mb-md">
          <label>
            Du
            <input type="date" value={du} max={au || undefined} onChange={(e) => changerFiltre(() => setDu(e.target.value))} />
          </label>
          <label>
            Au
            <input type="date" value={au} min={du || undefined} onChange={(e) => changerFiltre(() => setAu(e.target.value))} />
          </label>
          <label>
            Site
            <select value={siteId} onChange={(e) => changerFiltre(() => setSiteId(e.target.value))}>
              <option value="">Tous les sites</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.libelle}
                </option>
              ))}
            </select>
          </label>
          <label>
            Collaborateur
            <select value={collaborateurId} onChange={(e) => changerFiltre(() => setCollaborateurId(e.target.value))}>
              <option value="">Tous les collaborateurs</option>
              {collaborateurs.map((c) => (
                <option key={c.identifiant} value={c.identifiant}>
                  {nomCollaborateur(c)} ({c.matricule})
                </option>
              ))}
            </select>
          </label>
          <label>
            Direction
            <select value={directionId} onChange={(e) => changerFiltre(() => setDirectionId(e.target.value))}>
              <option value="">Toutes les directions</option>
              {directions.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.libelle}
                </option>
              ))}
            </select>
          </label>
          {onglet === 'historique' ? (
            <label>
              Statut
              <select value={statut} onChange={(e) => changerFiltre(() => setStatut(e.target.value))}>
                <option value="">Tous les statuts</option>
                {(Object.keys(LIBELLES_STATUT_HEURES_SUP) as StatutHeuresSup[]).map((s) => (
                  <option key={s} value={s}>
                    {LIBELLES_STATUT_HEURES_SUP[s]}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
        </div>

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
                        <th>Demande</th>
                        <th>État</th>
                      </tr>
                    </thead>
                    <tbody>
                      {comptees.contenu.map((h, i) => (
                        <tr key={`${h.collaborateur_id}-${h.site_id}-${h.journee}-${i}`}>
                          <td>
                            {h.nom ?? '—'}
                            {h.matricule ? <div className="muted small mono">{h.matricule}</div> : null}
                          </td>
                          <td>{h.site_libelle ?? '—'}</td>
                          <td>{formatDateCourteTunis(h.journee)}</td>
                          <td className="small">
                            {h.horaire_debut && h.horaire_fin ? `${h.horaire_debut}–${h.horaire_fin}` : '—'}
                          </td>
                          <td className="small">
                            {h.plage_avant ? <div>Avant : {h.plage_avant}</div> : null}
                            {h.plage_apres ? <div>Après : {h.plage_apres}</div> : null}
                            {!h.plage_avant && !h.plage_apres ? '—' : null}
                          </td>
                          <td className="small">{h.entree ? formatDateTunis(h.entree) : '—'}</td>
                          <td className="small">{h.sortie ? formatDateTunis(h.sortie) : '—'}</td>
                          <td className="mono">{h.heures_comptees}</td>
                          <td className="mono small">{h.reference_demande ?? '—'}</td>
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
                              {LIBELLES_ETAT_COMPTEES[h.etat] ?? h.etat}
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
                    <th>Référence</th>
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
                        {d.reference}
                        {d.creee_validee ? <div className="muted">créée validée</div> : null}
                      </td>
                      <td>{d.site_libelle ?? '—'}</td>
                      <td className="small">
                        {plageTexte(d)}
                        {d.jours?.length ? <div className="muted">{d.jours.length} jour(s) ouvert(s)</div> : null}
                      </td>
                      <td className="small">
                        {(d.beneficiaires ?? []).map((b) => b.nom ?? b.matricule ?? b.collaborateur_id).join(', ') || '—'}
                        {d.demandeur_nom ? <div className="muted">Demandeur : {d.demandeur_nom}</div> : null}
                      </td>
                      <td className="small">{d.motif}</td>
                      <td>
                        <StatutBadge statut={d.statut} />
                      </td>
                      <td className="small muted">
                        {d.decide_par ? <div>Décidé par {d.decide_par} · {formatDateTunis(d.decide_le)}</div> : null}
                        {d.motif_refus ? <div>Motif du refus : {d.motif_refus}</div> : null}
                        {d.annule_par ? <div>Annulé par {d.annule_par} · {formatDateTunis(d.annule_le)}</div> : null}
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
                                onClick={() => setDecision({ genre: 'refuser', demande: d })}
                              >
                                Refuser
                              </button>
                            </div>
                          ) : annulable(d) ? (
                            <button
                              type="button"
                              className="btn btn--danger btn--sm"
                              disabled={busyId === d.id}
                              onClick={() => setDecision({ genre: 'annuler', demande: d })}
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

      {decision ? (
        <DecisionModal
          decision={decision}
          onClose={() => setDecision(null)}
          onDone={async (texte) => {
            setDecision(null);
            setMsg(texte);
            await charger();
          }}
        />
      ) : null}

      {creerOuvert && !lectureSeule ? (
        <CreationModal
          sites={sites}
          collaborateurs={collaborateurs}
          onClose={() => setCreerOuvert(false)}
          onCreated={async (reference) => {
            setCreerOuvert(false);
            setMsg(`Demande ${reference} créée et validée. Les bénéficiaires sont notifiés.`);
            await charger();
          }}
        />
      ) : null}
    </div>
  );
}

/* ─── Refus (motif obligatoire) et annulation (motif facultatif) ───────── */

function DecisionModal({
  decision,
  onClose,
  onDone,
}: {
  decision: Decision;
  onClose: () => void;
  onDone: (message: string) => Promise<void>;
}) {
  const refus = decision.genre === 'refuser';
  const [motif, setMotif] = useState('');
  const [champErr, setChampErr] = useState<string | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function soumettre(e: FormEvent) {
    e.preventDefault();
    setErreur(null);
    if (refus && !motif.trim()) {
      setChampErr('Le motif est obligatoire.');
      return;
    }
    setChampErr(null);
    setBusy(true);
    try {
      if (refus) {
        await postRefuserHeuresSup(decision.demande.id, motif.trim());
        await onDone(`Demande ${decision.demande.reference} refusée.`);
      } else {
        await postAnnulerHeuresSup(decision.demande.id, motif);
        await onDone(`Demande ${decision.demande.reference} annulée. Les bénéficiaires sont notifiés.`);
      }
    } catch (er) {
      if (codeErreur(er) === 'MOTIF_OBLIGATOIRE') setChampErr(messageErreurCode(er));
      else setErreur(messageErreurCode(er));
      setBusy(false);
    }
  }

  return (
    <Modal title={refus ? `Refuser la demande ${decision.demande.reference}` : `Annuler la demande ${decision.demande.reference}`} onClose={onClose}>
      <form onSubmit={soumettre}>
        {erreur ? (
          <div className="alert alert--error" role="alert">
            {erreur}
          </div>
        ) : null}
        {!refus ? (
          <p className="small">
            La demande validée sera annulée pour tous les bénéficiaires, qui seront notifiés. La plage ne doit pas avoir
            commencé.
          </p>
        ) : null}
        <label>
          {refus ? 'Motif du refus (obligatoire)' : 'Motif de l’annulation (facultatif)'}
          <textarea
            value={motif}
            rows={3}
            maxLength={500}
            aria-invalid={champErr ? true : undefined}
            onChange={(e) => {
              setMotif(e.target.value);
              setChampErr(null);
            }}
          />
        </label>
        {champErr ? <span className="text-danger small">{champErr}</span> : null}
        <div className="modal__actions mt-md">
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Retour
          </button>
          <button type="submit" className={refus ? 'btn btn--primary' : 'btn btn--danger'} disabled={busy}>
            {busy ? 'Envoi…' : refus ? 'Refuser la demande' : 'Annuler la demande'}
          </button>
        </div>
      </form>
    </Modal>
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
  onCreated: (reference: string) => Promise<void>;
}) {
  const [site, setSite] = useState('');
  const [choisis, setChoisis] = useState<Set<string>>(new Set());
  const [recherche, setRecherche] = useState('');
  const [dateDebut, setDateDebut] = useState(dateIso(new Date()));
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
      await onCreated(d?.reference ?? '');
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
            <input
              type="date"
              value={dateDebut}
              aria-invalid={champs.dates ? true : undefined}
              onChange={(e) => {
                setDateDebut(e.target.value);
                setChamps((c) => ({ ...c, dates: undefined }));
              }}
            />
          </label>
          <label>
            Date de fin (facultative, 31 jours max)
            <input
              type="date"
              value={dateFin}
              min={dateDebut || undefined}
              aria-invalid={champs.dates ? true : undefined}
              onChange={(e) => {
                setDateFin(e.target.value);
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
                    {nomCollaborateur(c)} <span className="muted mono">({c.matricule})</span>
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
