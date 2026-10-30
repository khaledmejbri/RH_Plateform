import { FormEvent, useCallback, useEffect, useState, type ReactNode } from 'react';
import { formatDateCourteTunis, formatDateTunis } from '../api/presenceApi';
import {
  codeErreurFaciale,
  ENROLEMENT_STATUTS_FILTRE,
  getConsentementDetail,
  getConsentements,
  getEnrolement,
  getEnrolements,
  getReglageFacial,
  LIBELLES_AUDIT_ACTION,
  LIBELLES_ENROLEMENT_STATUT,
  messageFacialeErreur,
  MOTIF_MAX,
  MOTIF_MIN,
  postConsentement,
  postDeblocageEnrolement,
  putReglageFacial,
  SEUIL_MAX,
  SEUIL_MIN,
  TEXTE_CONSENTEMENT_MAX,
  TEXTE_CONSENTEMENT_MIN,
  type ConsentementDetail,
  type ConsentementVersion,
  type Enrolement,
  type PageEnrolements,
  type ReglageFacial,
} from '../api/verificationFacialeApi';

const PAGE_SIZE = 20;

type FieldErrors = Partial<Record<'statut' | 'date' | 'seuil' | 'cadre' | 'version', string>>;

function champDeLErreur(code: string | null): keyof FieldErrors | null {
  switch (code) {
    case 'SEUIL_HORS_PLAGE':
      return 'seuil';
    case 'DATE_MISE_EN_SERVICE_REQUISE':
      return 'date';
    case 'CONFIRMATION_CADRE_LEGAL_REQUISE':
      return 'cadre';
    case 'VERSION_CONSENTEMENT_NON_PUBLIEE':
      return 'version';
    default:
      return null;
  }
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
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="facial-modal-title">
        <div className="modal__head">
          <h3 id="facial-modal-title" className="modal__title" style={{ margin: 0 }}>
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

function StatutBadge({ statut }: { statut: string }) {
  const cls =
    statut === 'ENROLE'
      ? 'badge badge--success'
      : statut === 'A_REENROLER'
        ? 'badge badge--warning'
        : 'badge badge--default';
  return <span className={cls}>{LIBELLES_ENROLEMENT_STATUT[statut] ?? statut.replaceAll('_', ' ')}</span>;
}

/** Réglage société : statut, date de mise en service, seuil, cadre légal (D-P06). */
function ReglageSection({
  lectureSeule,
  onChanged,
}: {
  lectureSeule: boolean;
  onChanged: () => void;
}) {
  const [reglage, setReglage] = useState<ReglageFacial | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  const [statut, setStatut] = useState<'ACTIVE' | 'INACTIVE'>('INACTIVE');
  const [date, setDate] = useState('');
  const [seuil, setSeuil] = useState('92');
  const [cadre, setCadre] = useState(false);

  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formErr, setFormErr] = useState<string | null>(null);

  const appliquer = useCallback((r: ReglageFacial) => {
    setReglage(r);
    setStatut(r.statut);
    setDate(r.dateMiseEnService ? r.dateMiseEnService.slice(0, 10) : '');
    setSeuil(String(r.seuilScore));
    setCadre(false);
  }, []);

  const charger = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      appliquer(await getReglageFacial());
    } catch (e) {
      setReglage(null);
      setErr(messageFacialeErreur(e, 'Impossible de charger le réglage'));
    } finally {
      setLoading(false);
    }
  }, [appliquer]);

  useEffect(() => {
    void charger();
  }, [charger]);

  async function enregistrer(e: FormEvent) {
    e.preventDefault();
    if (lectureSeule) return;
    setBusy(true);
    setMsg(null);
    setFormErr(null);
    setFieldErrors({});
    try {
      const r = await putReglageFacial({
        statut,
        date_mise_en_service: date || null,
        seuil_score: Number(seuil),
        cadre_legal_valide: cadre,
      });
      appliquer(r);
      setMsg('Réglage enregistré.');
      onChanged();
    } catch (error) {
      const champ = champDeLErreur(codeErreurFaciale(error));
      const texte = messageFacialeErreur(error, 'Enregistrement impossible');
      if (champ) setFieldErrors({ [champ]: texte });
      else setFormErr(texte);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="mb-lg">
      <h3 className="section-title">Réglage de la vérification faciale</h3>
      <div className="alert alert--warning" role="note">
        Avant toute activation (C-L-07) : la formalité auprès de l’INPDP et la licence du modèle de reconnaissance
        doivent être validées par le juriste. Tant que le réglage est inactif, aucune donnée faciale n’est
        traitée.
      </div>

      {loading ? <p className="muted">Chargement du réglage…</p> : null}
      {err ? (
        <div className="alert alert--error">
          {err}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {reglage && !loading ? (
        <>
          <p className="small">
            État :{' '}
            {reglage.statut === 'INACTIVE' ? (
              <span className="badge badge--default">Inactive</span>
            ) : reglage.controleExige ? (
              <span className="badge badge--success">Active — contrôle exigé au pointage</span>
            ) : (
              <span className="badge badge--warning">Active — enrôlement ouvert, contrôle pas encore exigé</span>
            )}
            {reglage.versionConsentementCourante ? (
              <>
                {' '}
                · texte de consentement courant : <strong>{reglage.versionConsentementCourante}</strong>
              </>
            ) : (
              ' · aucun texte de consentement publié'
            )}
          </p>
          {reglage.cadreLegalConfirmeLe ? (
            <p className="muted small">
              Cadre légal confirmé le {formatDateTunis(reglage.cadreLegalConfirmeLe)}
              {reglage.cadreLegalConfirmePar ? ` par ${reglage.cadreLegalConfirmePar}` : ''}.
            </p>
          ) : null}
          {reglage.modifieLe ? (
            <p className="muted small">
              Dernière modification le {formatDateTunis(reglage.modifieLe)}
              {reglage.modifiePar ? ` par ${reglage.modifiePar}` : ''}.
            </p>
          ) : null}

          {msg ? <div className="alert alert--success">{msg}</div> : null}
          {formErr ? <div className="alert alert--error">{formErr}</div> : null}

          <form className="form-grid mb-md" onSubmit={enregistrer} noValidate>
            <label>
              Statut
              <select
                value={statut}
                disabled={lectureSeule || busy}
                onChange={(e) => setStatut(e.target.value as 'ACTIVE' | 'INACTIVE')}
              >
                <option value="INACTIVE">Inactive</option>
                <option value="ACTIVE">Active</option>
              </select>
            </label>
            <label>
              Date de mise en service {statut === 'ACTIVE' ? '*' : ''}
              <input
                type="date"
                value={date}
                disabled={lectureSeule || busy}
                aria-invalid={fieldErrors.date ? true : undefined}
                onChange={(e) => setDate(e.target.value)}
              />
              {fieldErrors.date ? <span className="text-danger small">{fieldErrors.date}</span> : null}
              <span className="muted small">
                Avant cette date, les collaborateurs peuvent s’enrôler mais aucun contrôle n’est exigé.
              </span>
            </label>
            <label>
              Seuil de reconnaissance ({SEUIL_MIN} à {SEUIL_MAX})
              <input
                type="number"
                min={SEUIL_MIN}
                max={SEUIL_MAX}
                value={seuil}
                disabled={lectureSeule || busy}
                aria-invalid={fieldErrors.seuil ? true : undefined}
                onChange={(e) => setSeuil(e.target.value)}
              />
              {fieldErrors.seuil ? <span className="text-danger small">{fieldErrors.seuil}</span> : null}
            </label>
            <div>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={cadre}
                  disabled={lectureSeule || busy}
                  onChange={(e) => setCadre(e.target.checked)}
                />
                Cadre légal validé (INPDP, texte, durées, licence du modèle) {statut === 'ACTIVE' ? '*' : ''}
              </label>
              {fieldErrors.cadre ? <span className="text-danger small">{fieldErrors.cadre}</span> : null}
            </div>
            {fieldErrors.version ? (
              <div className="alert alert--error" role="alert">
                {fieldErrors.version}
              </div>
            ) : null}
            {!lectureSeule ? (
              <div>
                <button type="submit" className="btn btn--primary" disabled={busy}>
                  Enregistrer le réglage
                </button>
              </div>
            ) : null}
          </form>
        </>
      ) : null}
    </section>
  );
}

/** Versions du texte de consentement : liste, lecture du texte, publication (D-P07). */
function ConsentementsSection({
  lectureSeule,
  refreshKey,
  onPublished,
}: {
  lectureSeule: boolean;
  refreshKey: number;
  onPublished: () => void;
}) {
  const [versions, setVersions] = useState<ConsentementVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [lecture, setLecture] = useState<ConsentementDetail | null>(null);
  const [lectureErr, setLectureErr] = useState<string | null>(null);

  const [modal, setModal] = useState(false);
  const [version, setVersion] = useState('');
  const [texte, setTexte] = useState('');
  const [busy, setBusy] = useState(false);
  const [pubErr, setPubErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      setVersions(await getConsentements());
    } catch (e) {
      setVersions([]);
      setErr(messageFacialeErreur(e, 'Impossible de charger les versions'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger, refreshKey]);

  async function lire(v: string) {
    setLectureErr(null);
    try {
      setLecture(await getConsentementDetail(v));
    } catch (e) {
      setLecture(null);
      setLectureErr(messageFacialeErreur(e, 'Impossible de lire cette version'));
    }
  }

  async function publier(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setPubErr(null);
    try {
      await postConsentement({ version: version.trim(), texte: texte.trim() });
      setModal(false);
      setMsg(`Version « ${version.trim()} » publiée : elle devient la version courante, chaque collaborateur devra l’accepter à nouveau.`);
      setVersion('');
      setTexte('');
      await charger();
      onPublished();
    } catch (error) {
      setPubErr(messageFacialeErreur(error, 'Publication impossible'));
    } finally {
      setBusy(false);
    }
  }

  const texteTrop = texte.trim().length;

  return (
    <section className="mb-lg">
      <h3 className="section-title">Texte de consentement</h3>
      <p className="muted small">
        Les versions publiées ne sont jamais modifiées ni supprimées : elles prouvent le texte accepté par chaque
        collaborateur.
      </p>
      {msg ? <div className="alert alert--success">{msg}</div> : null}
      {err ? (
        <div className="alert alert--error">
          {err}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {loading ? (
        <p className="muted">Chargement des versions…</p>
      ) : versions.length === 0 && !err ? (
        <div className="empty-state">
          <h3>Aucun texte publié</h3>
          <p>Publiez une première version pour pouvoir activer la vérification faciale.</p>
        </div>
      ) : versions.length > 0 ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Publiée le</th>
                <th>Publiée par</th>
                <th>Statut</th>
                <th>Texte</th>
              </tr>
            </thead>
            <tbody>
              {versions.map((v) => (
                <tr key={v.version}>
                  <td className="mono">{v.version}</td>
                  <td>{formatDateTunis(v.publieLe)}</td>
                  <td>{v.publiePar ?? '—'}</td>
                  <td>
                    {v.courante ? (
                      <span className="badge badge--success">Courante</span>
                    ) : (
                      <span className="muted small">Ancienne</span>
                    )}
                  </td>
                  <td>
                    <button type="button" className="btn btn--secondary btn--sm" onClick={() => void lire(v.version)}>
                      Lire
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {!lectureSeule ? (
        <div className="page__head-actions mt-sm">
          <button type="button" className="btn btn--secondary" onClick={() => setModal(true)}>
            Publier une nouvelle version
          </button>
        </div>
      ) : null}

      {lectureErr ? <div className="alert alert--error mt-sm">{lectureErr}</div> : null}
      {lecture ? (
        <Modal title={`Texte de la version ${lecture.version}`} onClose={() => setLecture(null)}>
          <p className="muted small">
            Publiée le {formatDateTunis(lecture.publieLe)}
            {lecture.publiePar ? ` par ${lecture.publiePar}` : ''}.
          </p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{lecture.texte}</p>
        </Modal>
      ) : null}

      {modal ? (
        <Modal title="Publier une version du texte" onClose={() => !busy && setModal(false)}>
          <form onSubmit={publier}>
            <div className="alert alert--warning" role="note">
              Cette version deviendra la version courante : <strong>tous les collaborateurs enrôlés devront
              l’accepter à nouveau</strong> à leur prochaine connexion, et ceux qui la refusent seront désenrôlés.
            </div>
            {pubErr ? <div className="alert alert--error">{pubErr}</div> : null}
            <label>
              Nom de la version *
              <input
                required
                maxLength={32}
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                placeholder="ex. v1, 2026-11"
              />
            </label>
            <label>
              Texte *
              <textarea
                required
                rows={10}
                maxLength={TEXTE_CONSENTEMENT_MAX}
                value={texte}
                onChange={(e) => setTexte(e.target.value)}
              />
              <span className="muted small">
                {texteTrop} / {TEXTE_CONSENTEMENT_MAX} caractères (minimum {TEXTE_CONSENTEMENT_MIN}).
              </span>
            </label>
            <div className="page__head-actions mt-md">
              <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => setModal(false)}>
                Annuler
              </button>
              <button type="submit" className="btn btn--primary" disabled={busy}>
                Publier
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </section>
  );
}

/** Détail d'un enrôlement avec historique d'audit et déblocage RH (D-P07 / D-P10). */
function EnrolementDetail({
  id,
  lectureSeule,
  onClose,
  onChanged,
}: {
  id: string;
  lectureSeule: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [data, setData] = useState<Enrolement | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [modal, setModal] = useState(false);
  const [motif, setMotif] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      setData(await getEnrolement(id));
    } catch (e) {
      setData(null);
      setErr(messageFacialeErreur(e, 'Impossible de charger le détail'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void charger();
  }, [charger]);

  async function debloquer(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setActionErr(null);
    try {
      await postDeblocageEnrolement(id, motif.trim());
      setModal(false);
      setMotif('');
      setMsg('Ré-enrôlement autorisé : le collaborateur et son responsable sont prévenus.');
      await charger();
      onChanged();
    } catch (error) {
      setActionErr(messageFacialeErreur(error, 'Déblocage impossible'));
    } finally {
      setBusy(false);
    }
  }

  const motifLen = motif.trim().length;
  const motifValide = motifLen >= MOTIF_MIN && motifLen <= MOTIF_MAX;

  return (
    <div>
      <div className="page__head-actions mb-md">
        <button type="button" className="btn btn--ghost" onClick={onClose}>
          ← Retour à la liste
        </button>
      </div>
      {loading ? <p className="muted">Chargement du détail…</p> : null}
      {err ? (
        <div className="alert alert--error">
          {err}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      {data && !loading ? (
        <>
          <h3 className="section-title">{data.collaborateurNom ?? 'Collaborateur'}</h3>
          <dl className="detail-dl mb-md">
            <div>
              <dt>Statut</dt>
              <dd>
                <StatutBadge statut={data.statut} />
              </dd>
            </div>
            <div>
              <dt>Version de texte acceptée</dt>
              <dd>{data.versionConsentement ?? '—'}</dd>
            </div>
            <div>
              <dt>Enrôlé le</dt>
              <dd>{formatDateTunis(data.enroleLe)}</dd>
            </div>
            <div>
              <dt>Dernière décision</dt>
              <dd>{formatDateTunis(data.decideLe)}</dd>
            </div>
            <div>
              <dt>Appareil enrôlé</dt>
              <dd className="mono">{data.deviceIdMasque ? `…${data.deviceIdMasque}` : '—'}</dd>
            </div>
            {data.statut === 'A_REENROLER' ? (
              <div>
                <dt>Nouvel appareil demandé</dt>
                <dd className="mono">{data.deviceIdDemandeMasque ? `…${data.deviceIdDemandeMasque}` : '—'}</dd>
              </div>
            ) : null}
            <div>
              <dt>Appareil</dt>
              <dd>{data.appareilIncompatible ? 'Déclaré incompatible' : 'Compatible'}</dd>
            </div>
            <div>
              <dt>Échecs consécutifs</dt>
              <dd>
                {data.echecsConsecutifs}
                {data.bloqueJusqua ? ` · bloqué jusqu’à ${formatDateTunis(data.bloqueJusqua)}` : ''}
              </dd>
            </div>
          </dl>

          {data.statut === 'A_REENROLER' ? (
            <div className="alert alert--info" role="status">
              Ce collaborateur a demandé à se ré-enrôler (nouvel appareil ou gabarit perdu
              {data.demandeReenrolementLe ? `, le ${formatDateTunis(data.demandeReenrolementLe)}` : ''}). Autoriser
              le ré-enrôlement invalide l’ancien appareil.
            </div>
          ) : null}

          {!lectureSeule && data.statut === 'A_REENROLER' ? (
            <div className="page__head-actions mb-md">
              <button
                type="button"
                className="btn btn--warning"
                disabled={busy}
                onClick={() => {
                  setActionErr(null);
                  setModal(true);
                }}
              >
                Autoriser un ré-enrôlement
              </button>
            </div>
          ) : null}

          <h3 className="section-title">Historique</h3>
          {data.historique.length === 0 ? (
            <p className="muted small">Aucun événement enregistré.</p>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date (Tunis)</th>
                    <th>Événement</th>
                    <th>Statut</th>
                    <th>Acteur</th>
                    <th>Version</th>
                    <th>Motif</th>
                  </tr>
                </thead>
                <tbody>
                  {data.historique.map((h, i) => (
                    <tr key={`${h.horodatage ?? ''}-${i}`}>
                      <td>{formatDateTunis(h.horodatage)}</td>
                      <td>{LIBELLES_AUDIT_ACTION[h.action] ?? h.action.replaceAll('_', ' ')}</td>
                      <td className="small">
                        {h.statutAvant || h.statutApres
                          ? `${LIBELLES_ENROLEMENT_STATUT[h.statutAvant ?? ''] ?? h.statutAvant ?? '—'} → ${
                              LIBELLES_ENROLEMENT_STATUT[h.statutApres ?? ''] ?? h.statutApres ?? '—'
                            }`
                          : '—'}
                      </td>
                      <td>
                        {h.acteur ?? '—'}
                        {h.acteurRole ? <span className="muted small"> ({h.acteurRole})</span> : null}
                      </td>
                      <td className="muted">{h.version ?? '—'}</td>
                      <td className="muted small">{h.motif ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : null}

      {modal ? (
        <Modal title="Autoriser un ré-enrôlement" onClose={() => !busy && setModal(false)}>
          <form onSubmit={debloquer}>
            <p>
              Le collaborateur repassera en « Non enrôlé » : l’appareil actuel sera invalidé et il devra refaire son
              enrôlement. Le motif est conservé dans l’historique.
            </p>
            {actionErr ? (
              <div className="alert alert--error" role="alert">
                {actionErr}
              </div>
            ) : null}
            <label>
              Motif * ({MOTIF_MIN} à {MOTIF_MAX} caractères)
              <textarea
                required
                rows={4}
                maxLength={MOTIF_MAX}
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
              />
              <span className="muted small">
                {motifLen} / {MOTIF_MAX}
              </span>
            </label>
            <div className="page__head-actions mt-md">
              <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => setModal(false)}>
                Annuler
              </button>
              <button type="submit" className="btn btn--primary" disabled={busy || !motifValide}>
                Autoriser
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}

/** Liste des enrôlements (noms, statut en français, appareil masqué). */
function EnrolementsSection({
  lectureSeule,
  refreshKey,
  onChanged,
}: {
  lectureSeule: boolean;
  refreshKey: number;
  onChanged: () => void;
}) {
  const [statut, setStatut] = useState('');
  const [q, setQ] = useState('');
  const [qSaisie, setQSaisie] = useState('');
  const [incompatible, setIncompatible] = useState(false);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PageEnrolements | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      setData(
        await getEnrolements({
          statut: statut || undefined,
          q,
          appareilIncompatible: incompatible,
          page,
          size: PAGE_SIZE,
        }),
      );
    } catch (e) {
      setData(null);
      setErr(messageFacialeErreur(e, 'Impossible de charger les enrôlements'));
    } finally {
      setLoading(false);
    }
  }, [statut, q, incompatible, page]);

  useEffect(() => {
    void charger();
  }, [charger, refreshKey]);

  if (selected) {
    return (
      <section>
        <EnrolementDetail
          id={selected}
          lectureSeule={lectureSeule}
          onClose={() => setSelected(null)}
          onChanged={() => {
            onChanged();
            void charger();
          }}
        />
      </section>
    );
  }

  const lignes = data?.contenu ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);

  return (
    <section>
      <h3 className="section-title">Enrôlements</h3>
      <p className="muted small">
        Seuls les collaborateurs qui ont ouvert RH Connect depuis l’activation apparaissent ici : un collaborateur
        qui n’a encore jamais ouvert l’application n’a pas de ligne.
      </p>

      <form
        className="form-grid mb-md"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setQ(qSaisie);
        }}
      >
        <label>
          Recherche
          <input
            type="search"
            value={qSaisie}
            onChange={(e) => setQSaisie(e.target.value)}
            placeholder="Nom du collaborateur"
          />
        </label>
        <label>
          Statut
          <select
            value={statut}
            onChange={(e) => {
              setPage(0);
              setStatut(e.target.value);
            }}
          >
            {ENROLEMENT_STATUTS_FILTRE.map((o) => (
              <option key={o.value || 'all'} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox-row" style={{ alignSelf: 'end' }}>
          <input
            type="checkbox"
            checked={incompatible}
            onChange={(e) => {
              setPage(0);
              setIncompatible(e.target.checked);
            }}
          />
          Appareil incompatible
        </label>
        <div style={{ alignSelf: 'end' }}>
          <button type="submit" className="btn btn--secondary">
            Rechercher
          </button>
        </div>
      </form>

      {err ? (
        <div className="alert alert--error">
          {err}{' '}
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => void charger()}>
            Réessayer
          </button>
        </div>
      ) : null}

      {loading ? (
        <p className="muted">Chargement des enrôlements…</p>
      ) : lignes.length === 0 && !err ? (
        <div className="empty-state">
          <h3>Aucun enrôlement</h3>
          <p>Aucun collaborateur ne correspond à ces filtres, ou personne n’a encore ouvert l’application.</p>
        </div>
      ) : lignes.length > 0 ? (
        <>
          <p className="muted small" aria-live="polite">
            {data?.totalElements ?? lignes.length} résultat{(data?.totalElements ?? 0) > 1 ? 's' : ''}
          </p>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Collaborateur</th>
                  <th>Statut</th>
                  <th>Texte accepté</th>
                  <th>Enrôlé le</th>
                  <th>Appareil</th>
                  <th>Échecs</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {lignes.map((l) => (
                  <tr key={l.id}>
                    <td>{l.collaborateurNom ?? '—'}</td>
                    <td>
                      <StatutBadge statut={l.statut} />
                      {l.appareilIncompatible ? (
                        <span className="badge badge--default" style={{ marginLeft: 6 }}>
                          Appareil incompatible
                        </span>
                      ) : null}
                    </td>
                    <td className="muted">{l.versionConsentement ?? '—'}</td>
                    <td className="muted">{l.enroleLe ? formatDateCourteTunis(l.enroleLe) : '—'}</td>
                    <td className="mono muted small">{l.deviceIdMasque ? `…${l.deviceIdMasque}` : '—'}</td>
                    <td className="muted">
                      {l.echecsConsecutifs}
                      {l.bloqueJusqua ? ' (bloqué)' : ''}
                    </td>
                    <td>
                      <button type="button" className="btn btn--secondary btn--sm" onClick={() => setSelected(l.id)}>
                        Voir
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager">
            <button
              type="button"
              className="btn btn--ghost"
              disabled={page <= 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Préc.
            </button>
            <span className="muted small">
              {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              className="btn btn--ghost"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Suiv.
            </button>
          </div>
        </>
      ) : null}
    </section>
  );
}

/** 4e onglet de la page Pointage : vérification faciale (D-P06 à D-P10, SCRUM-47). */
export default function VerificationFacialePanel({ lectureSeule }: { lectureSeule: boolean }) {
  // Incrémenté après une écriture pour recharger les autres sections.
  const [refreshKey, setRefreshKey] = useState(0);
  const rafraichir = useCallback(() => setRefreshKey((k) => k + 1), []);

  return (
    <div className="panel">
      {lectureSeule ? (
        <div className="alert alert--info" role="status">
          Consultation uniquement.
        </div>
      ) : null}
      <ReglageSection lectureSeule={lectureSeule} onChanged={rafraichir} />
      <ConsentementsSection lectureSeule={lectureSeule} refreshKey={refreshKey} onPublished={rafraichir} />
      <EnrolementsSection lectureSeule={lectureSeule} refreshKey={refreshKey} onChanged={rafraichir} />
    </div>
  );
}
