import { FormEvent, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  downloadSiteQrPng,
  formatDateCourteTunis,
  formatDateTunis,
  getPresencePointages,
  getPresenceSite,
  getPresenceSites,
  isQrExpired,
  isServiceUnavailable,
  LIBELLES_POINTAGE_STATUT,
  messagePresenceErreur,
  patchPresenceSite,
  POINTAGE_STATUTS_FILTRE,
  postGenerateQr,
  postGenererBorne,
  postPresenceSite,
  postRevoquerBorne,
  postRevokeQr,
  RAYON_MAX,
  RAYON_MIN,
  TYPES_LIEU,
  type PresencePointage,
  type PresenceSite,
  type TypeLieu,
} from '../api/presenceApi';
import { useLectureSeule } from '../auth/useLectureSeule';
import { Icon } from '../components/ui';
import PointagesManuelsPanel from './PointagesManuelsPanel';

type Tab = 'sites' | 'pointages' | 'manuels';
type ModalKind = 'creer' | 'regenerer' | 'revoquer' | null;

const PAGE_SIZE = 20;

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
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
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby="presence-modal-title">
        <div className="modal__head">
          <h3 id="presence-modal-title" className="modal__title" style={{ margin: 0 }}>
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

function QrExpiryBadge({ site }: { site: PresenceSite }) {
  if (!site.hasQrActif && !site.validUntil) return <span className="muted small">Aucun</span>;
  if (site.validUntil && (isQrExpired(site.validUntil) || site.qrStatut === 'EXPIRE')) {
    return (
      <span className="badge badge--default" aria-label="QR expiré">
        <span aria-hidden="true">⏱</span> Expiré
      </span>
    );
  }
  if (site.expireBientot && site.validUntil) {
    return (
      <span
        className="badge badge--warning"
        aria-label={`QR expire le ${formatDateCourteTunis(site.validUntil)}`}
      >
        <Icon name="alert" size={12} /> Expire le {formatDateCourteTunis(site.validUntil)}
      </span>
    );
  }
  if (site.hasQrActif) {
    return (
      <span className="badge badge--success" aria-label="QR actif">
        <Icon name="check" size={12} /> Actif
        {site.validUntil ? ` · ${formatDateCourteTunis(site.validUntil)}` : ''}
      </span>
    );
  }
  return <span className="muted small">Aucun</span>;
}

function PointageStatutBadge({ statut }: { statut: string }) {
  const label = LIBELLES_POINTAGE_STATUT[statut] ?? statut.replaceAll('_', ' ');
  const cls =
    statut === 'VALIDE' || statut === 'VALIDE_MANUEL'
      ? 'badge badge--success'
      : statut.startsWith('REJETE_')
        ? 'badge badge--warning'
        : 'badge badge--default';
  return (
    <span className={cls} aria-label={label}>
      {statut === 'VALIDE' || statut === 'VALIDE_MANUEL' ? <Icon name="check" size={12} /> : null}
      {statut.startsWith('REJETE_') ? <Icon name="x" size={12} /> : null}
      {label}
    </span>
  );
}

export default function PointageSitesPage() {
  const lectureSeule = useLectureSeule();
  const [tab, setTab] = useState<Tab>('sites');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [sites, setSites] = useState<PresenceSite[]>([]);
  const [sitesPage, setSitesPage] = useState(0);
  const [sitesTotalPages, setSitesTotalPages] = useState(1);
  const [sitesTotal, setSitesTotal] = useState(0);
  const [sitesLoading, setSitesLoading] = useState(true);
  const [sitesErr, setSitesErr] = useState<string | null>(null);
  const [deployBanner, setDeployBanner] = useState(false);

  const [recherche, setRecherche] = useState('');
  const [filtreActif, setFiltreActif] = useState<'all' | 'true' | 'false'>('all');
  const [filtreExpire, setFiltreExpire] = useState(false);

  const [detail, setDetail] = useState<PresenceSite | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailErr, setDetailErr] = useState<string | null>(null);

  const [msg, setMsg] = useState<string | null>(null);
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [modal, setModal] = useState<ModalKind>(null);
  const [createCode, setCreateCode] = useState('');
  const [createLibelle, setCreateLibelle] = useState('');
  const [createTypeLieu, setCreateTypeLieu] = useState<TypeLieu>('EXTERIEUR');
  const [createRayon, setCreateRayon] = useState('');
  const [editTypeLieu, setEditTypeLieu] = useState<TypeLieu>('EXTERIEUR');
  const [editRayon, setEditRayon] = useState('');
  const [lienBorne, setLienBorne] = useState<string | null>(null);

  const [pointages, setPointages] = useState<PresencePointage[]>([]);
  const [ptPage, setPtPage] = useState(0);
  const [ptTotalPages, setPtTotalPages] = useState(1);
  const [ptTotal, setPtTotal] = useState(0);
  const [ptLoading, setPtLoading] = useState(false);
  const [ptErr, setPtErr] = useState<string | null>(null);
  const [ptSiteId, setPtSiteId] = useState('');
  const [ptStatut, setPtStatut] = useState('');
  const [ptType, setPtType] = useState('');
  const [ptDu, setPtDu] = useState('');
  const [ptAu, setPtAu] = useState('');

  const loadSites = useCallback(async () => {
    setSitesLoading(true);
    setSitesErr(null);
    try {
      const page = await getPresenceSites({
        page: sitesPage,
        size: PAGE_SIZE,
        actif: filtreActif === 'all' ? null : filtreActif === 'true',
      });
      setSites(page.contenu);
      setSitesTotal(page.totalElements);
      setSitesTotalPages(Math.max(1, page.totalPages));
      setDeployBanner(false);
    } catch (e) {
      setSites([]);
      setSitesErr(messagePresenceErreur(e, 'Impossible de charger les sites'));
      setDeployBanner(isServiceUnavailable(e));
    } finally {
      setSitesLoading(false);
    }
  }, [sitesPage, filtreActif]);

  useEffect(() => {
    void loadSites();
  }, [loadSites]);

  const sitesAffiches = useMemo(() => {
    const q = recherche.trim().toLowerCase();
    return sites.filter((s) => {
      if (filtreExpire && !s.expireBientot) return false;
      if (!q) return true;
      return s.code.toLowerCase().includes(q) || s.libelle.toLowerCase().includes(q);
    });
  }, [sites, recherche, filtreExpire]);

  const loadDetail = useCallback(async (id: string) => {
    setDetailLoading(true);
    setDetailErr(null);
    try {
      const site = await getPresenceSite(id);
      setDetail(site);
    } catch (e) {
      setDetail(null);
      setDetailErr(messagePresenceErreur(e, 'Impossible de charger la fiche'));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedId) void loadDetail(selectedId);
    else setDetail(null);
  }, [selectedId, loadDetail]);

  useEffect(() => {
    if (!detail) return;
    setEditTypeLieu(detail.typeLieu);
    setEditRayon(String(detail.rayonMetres));
  }, [detail]);

  const loadPointages = useCallback(async () => {
    setPtLoading(true);
    setPtErr(null);
    try {
      const page = await getPresencePointages({
        page: ptPage,
        size: PAGE_SIZE,
        siteId: ptSiteId || undefined,
        statut: ptStatut || undefined,
        type: ptType || undefined,
        du: ptDu || undefined,
        au: ptAu || undefined,
      });
      setPointages(page.contenu);
      setPtTotal(page.totalElements);
      setPtTotalPages(Math.max(1, page.totalPages));
      setDeployBanner(false);
    } catch (e) {
      setPointages([]);
      setPtErr(messagePresenceErreur(e, 'Impossible de charger les pointages'));
      setDeployBanner(isServiceUnavailable(e));
    } finally {
      setPtLoading(false);
    }
  }, [ptPage, ptSiteId, ptStatut, ptType, ptDu, ptAu]);

  useEffect(() => {
    if (tab === 'pointages') void loadPointages();
  }, [tab, loadPointages]);

  const siteOptions = useMemo(
    () => sites.map((s) => ({ id: s.id, label: `${s.code} — ${s.libelle}` })),
    [sites],
  );

  function openSite(id: string) {
    setMsg(null);
    setActionErr(null);
    setSelectedId(id);
  }

  function backToList() {
    setSelectedId(null);
    setDetail(null);
    setDetailErr(null);
    setMsg(null);
    setActionErr(null);
    void loadSites();
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setActionErr(null);
    setMsg(null);
    try {
      const created = await postPresenceSite({
        code: createCode.trim(),
        libelle: createLibelle.trim(),
        type_lieu: createTypeLieu,
        ...(createRayon.trim() ? { rayon_metres: Number(createRayon) } : {}),
      });
      setModal(null);
      setCreateCode('');
      setCreateLibelle('');
      setCreateTypeLieu('EXTERIEUR');
      setCreateRayon('');
      setMsg(`Site « ${created.libelle} » créé.`);
      setSitesPage(0);
      await loadSites();
      openSite(created.id);
    } catch (err) {
      setActionErr(messagePresenceErreur(err, 'Création impossible'));
    } finally {
      setBusy(false);
    }
  }

  async function enregistrerZone(e: FormEvent) {
    e.preventDefault();
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      const updated = await patchPresenceSite(detail.id, {
        type_lieu: editTypeLieu,
        rayon_metres: Number(editRayon),
      });
      setDetail(updated);
      setMsg(`Zone de pointage mise à jour : ${updated.rayonMetres} m.`);
      void loadSites();
    } catch (err) {
      setActionErr(messagePresenceErreur(err));
    } finally {
      setBusy(false);
    }
  }

  async function genererBorne() {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      const { chemin } = await postGenererBorne(detail.id);
      setLienBorne(`${window.location.origin}${chemin}`);
      setMsg('Borne écran créée. Ouvrez le lien sur l’écran du site ; l’ancien lien éventuel ne fonctionne plus.');
      await loadDetail(detail.id);
    } catch (err) {
      setActionErr(messagePresenceErreur(err));
    } finally {
      setBusy(false);
    }
  }

  async function revoquerBorne() {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      await postRevoquerBorne(detail.id);
      setLienBorne(null);
      setMsg('Borne désactivée : son lien et ses QR ne fonctionnent plus.');
      await loadDetail(detail.id);
    } catch (err) {
      setActionErr(messagePresenceErreur(err));
    } finally {
      setBusy(false);
    }
  }

  async function passerEnModePapier() {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      const updated = await patchPresenceSite(detail.id, { mode_qr: 'PAPIER' });
      setDetail(updated);
      setMsg('Site repassé en QR papier.');
    } catch (err) {
      setActionErr(messagePresenceErreur(err));
    } finally {
      setBusy(false);
    }
  }

  async function toggleActif() {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      const updated = await patchPresenceSite(detail.id, { actif: !detail.actif });
      setDetail(updated);
      setMsg(updated.actif ? 'Site activé.' : 'Site désactivé.');
      void loadSites();
    } catch (err) {
      setActionErr(messagePresenceErreur(err));
    } finally {
      setBusy(false);
    }
  }

  async function doGenerate(isRegen: boolean) {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    setMsg(null);
    try {
      const result = await postGenerateQr(detail.id);
      setModal(null);
      setMsg(
        isRegen
          ? `QR régénéré (v${result.qrVersion}) — valide jusqu’au ${formatDateCourteTunis(result.validUntil)}. Téléchargez et réimprimez.`
          : `QR généré (v${result.qrVersion}) — valide 90 jours jusqu’au ${formatDateCourteTunis(result.validUntil)}.`,
      );
      await loadDetail(detail.id);
      void loadSites();
    } catch (err) {
      setActionErr(messagePresenceErreur(err, 'Génération QR impossible'));
    } finally {
      setBusy(false);
    }
  }

  async function doDownload() {
    if (!detail || lectureSeule || !detail.hasQrActif) return;
    setBusy(true);
    setActionErr(null);
    try {
      await downloadSiteQrPng(detail.id, `qr-${detail.code}.png`);
      setMsg('Téléchargement du code QR démarré.');
    } catch (err) {
      setActionErr(messagePresenceErreur(err, 'Téléchargement impossible'));
    } finally {
      setBusy(false);
    }
  }

  async function doRevoke() {
    if (!detail || lectureSeule) return;
    setBusy(true);
    setActionErr(null);
    try {
      await postRevokeQr(detail.id);
      setModal(null);
      setMsg('QR révoqué — les scans avec l’ancien code seront refusés.');
      await loadDetail(detail.id);
      void loadSites();
    } catch (err) {
      setActionErr(messagePresenceErreur(err, 'Révocation impossible'));
    } finally {
      setBusy(false);
    }
  }

  const showList = tab === 'sites' && !selectedId;
  const showDetail = tab === 'sites' && !!selectedId;

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">
            {showDetail
              ? detail?.libelle ?? 'Fiche site'
              : tab === 'pointages'
                ? 'Pointages'
                : tab === 'manuels'
                  ? 'Pointages manuels'
                  : 'Sites de pointage'}
          </h2>
          <p className="page__lead">
            {showDetail
              ? `Code ${detail?.code ?? '…'} · zone de ${detail?.rayonMetres ?? '…'} m`
              : tab === 'pointages'
                ? 'Consultation des essais VALIDÉ / REJETÉ (support et anti-fraude).'
                : tab === 'manuels'
                  ? 'Pointages demandés après un refus, validés par le responsable ou les RH.'
                  : 'Emplacements QR et zone de pointage par site'}
          </p>
        </div>
        <div className="tabs">
          <button
            type="button"
            className={'tab' + (tab === 'sites' && !selectedId ? ' tab--on' : tab === 'sites' ? ' tab--on' : '')}
            onClick={() => {
              setTab('sites');
              setSelectedId(null);
            }}
          >
            Sites
          </button>
          <button
            type="button"
            className={'tab' + (tab === 'pointages' ? ' tab--on' : '')}
            onClick={() => {
              setTab('pointages');
              setSelectedId(null);
            }}
          >
            Pointages
          </button>
          <button
            type="button"
            className={'tab' + (tab === 'manuels' ? ' tab--on' : '')}
            onClick={() => {
              setTab('manuels');
              setSelectedId(null);
            }}
          >
            Pointages manuels
          </button>
          {showList && !lectureSeule ? (
            <button type="button" className="btn btn--primary" onClick={() => setModal('creer')}>
              Créer un site
            </button>
          ) : null}
        </div>
      </div>

      {deployBanner ? (
        <div className="alert alert--info" role="status">
          Module pointage en cours de déploiement — le service présence peut être temporairement indisponible.
        </div>
      ) : null}
      {lectureSeule && (showList || showDetail || tab === 'pointages') ? (
        <div className="alert alert--info" role="status">
          Profil Direction — consultation uniquement.
        </div>
      ) : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}
      {actionErr ? <div className="alert alert--error">{actionErr}</div> : null}

      {showList ? (
        <div className="panel">
          <div className="form-grid mb-md">
            <label>
              Recherche
              <input
                type="search"
                value={recherche}
                onChange={(e) => setRecherche(e.target.value)}
                placeholder="Code ou libellé"
              />
            </label>
            <label>
              Statut site
              <select
                value={filtreActif}
                onChange={(e) => {
                  setSitesPage(0);
                  setFiltreActif(e.target.value as 'all' | 'true' | 'false');
                }}
              >
                <option value="all">Tous</option>
                <option value="true">Actifs</option>
                <option value="false">Inactifs</option>
              </select>
            </label>
            <label className="checkbox-row" style={{ alignSelf: 'end' }}>
              <input
                type="checkbox"
                checked={filtreExpire}
                onChange={(e) => setFiltreExpire(e.target.checked)}
              />
              QR expire ≤ 30 j
            </label>
          </div>

          {sitesErr ? (
            <div className="alert alert--error">
              {sitesErr}{' '}
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadSites()}>
                Réessayer
              </button>
            </div>
          ) : null}

          {sitesLoading ? (
            <p className="muted">Chargement des sites…</p>
          ) : sitesAffiches.length === 0 && !sitesErr ? (
            <div className="empty-state">
              <h3>Aucun site de pointage</h3>
              <p>
                {sites.length === 0
                  ? 'Créez un site pour générer un QR et poser l’emplacement GPS (mobile RH).'
                  : 'Aucun site ne correspond aux filtres.'}
              </p>
              {!lectureSeule && sites.length === 0 ? (
                <button type="button" className="btn btn--primary" onClick={() => setModal('creer')}>
                  Créer un site
                </button>
              ) : null}
            </div>
          ) : sitesAffiches.length > 0 ? (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Code</th>
                      <th>Libellé</th>
                      <th>Statut</th>
                      <th>Emplacement</th>
                      <th>QR</th>
                      <th>Expiration</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sitesAffiches.map((s) => (
                      <tr key={s.id}>
                        <td className="mono">{s.code}</td>
                        <td>{s.libelle}</td>
                        <td>
                          {s.actif ? (
                            <span className="badge badge--success">Actif</span>
                          ) : (
                            <span className="badge badge--default">Inactif</span>
                          )}
                        </td>
                        <td>
                          {s.latitude != null && s.longitude != null ? (
                            <span className="small">Posé</span>
                          ) : (
                            <span className="muted small" title="Configurer via mobile RH">
                              Non posé
                            </span>
                          )}
                        </td>
                        <td>
                          {s.hasQrActif ? (
                            <span className="small">
                              Actif{s.qrVersion != null ? ` v${s.qrVersion}` : ''}
                            </span>
                          ) : (
                            <span className="muted small">Aucun</span>
                          )}
                        </td>
                        <td>
                          <QrExpiryBadge site={s} />
                        </td>
                        <td>
                          <button type="button" className="btn btn--secondary btn--sm" onClick={() => openSite(s.id)}>
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
                  disabled={sitesPage <= 0}
                  onClick={() => setSitesPage((p) => p - 1)}
                >
                  Préc.
                </button>
                <span className="muted small">
                  {sitesPage + 1} / {sitesTotalPages} ({sitesTotal})
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={sitesPage + 1 >= sitesTotalPages}
                  onClick={() => setSitesPage((p) => p + 1)}
                >
                  Suiv.
                </button>
              </div>
            </>
          ) : null}
        </div>
      ) : null}

      {showDetail ? (
        <div className="panel">
          <div className="page__head-actions mb-md">
            <button type="button" className="btn btn--ghost" onClick={backToList}>
              ← Retour à la liste
            </button>
          </div>

          {detailLoading ? <p className="muted">Chargement de la fiche…</p> : null}
          {detailErr ? (
            <div className="alert alert--error">
              {detailErr}{' '}
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => selectedId && void loadDetail(selectedId)}
              >
                Réessayer
              </button>
            </div>
          ) : null}

          {detail && !detailLoading ? (
            <>
              <div className="inline-row mb-md">
                <span className="mono muted">{detail.code}</span>
                {detail.actif ? (
                  <span className="badge badge--success">Actif</span>
                ) : (
                  <span className="badge badge--default">Inactif</span>
                )}
                <QrExpiryBadge site={detail} />
              </div>

              {detail.hasQrActif && detail.expireBientot && detail.validUntil && !isQrExpired(detail.validUntil) ? (
                <div className="alert alert--warning" role="status">
                  Ce QR expire le {formatDateCourteTunis(detail.validUntil)}. Renouvelez-le puis réimprimez
                  l’affichage.
                </div>
              ) : null}
              {detail.validUntil && isQrExpired(detail.validUntil) ? (
                <div className="alert alert--error" role="alert">
                  QR expiré — les collaborateurs ne peuvent plus pointer sur ce site.
                </div>
              ) : null}

              <section className="mb-lg">
                <h3 className="section-title">Identité</h3>
                <dl className="detail-dl">
                  <div>
                    <dt>Code</dt>
                    <dd className="mono">{detail.code}</dd>
                  </div>
                  <div>
                    <dt>Libellé</dt>
                    <dd>{detail.libelle}</dd>
                  </div>
                  <div>
                    <dt>Type de lieu</dt>
                    <dd>{TYPES_LIEU.find((t) => t.value === detail.typeLieu)?.label ?? detail.typeLieu}</dd>
                  </div>
                  <div>
                    <dt>Rayon de pointage</dt>
                    <dd>{detail.rayonMetres} m</dd>
                  </div>
                </dl>
                {!lectureSeule ? (
                  <form className="form-grid mb-md" onSubmit={enregistrerZone}>
                    <label>
                      Type de lieu
                      <select
                        value={editTypeLieu}
                        onChange={(e) => {
                          const t = e.target.value as TypeLieu;
                          setEditTypeLieu(t);
                          setEditRayon(String(TYPES_LIEU.find((x) => x.value === t)?.rayonDefaut ?? editRayon));
                        }}
                      >
                        {TYPES_LIEU.map((t) => (
                          <option key={t.value} value={t.value}>
                            {t.label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Rayon (m)
                      <input
                        type="number"
                        required
                        min={RAYON_MIN}
                        max={RAYON_MAX}
                        value={editRayon}
                        onChange={(e) => setEditRayon(e.target.value)}
                      />
                    </label>
                    <div>
                      <button
                        type="submit"
                        className="btn btn--secondary btn--sm"
                        disabled={
                          busy ||
                          (editTypeLieu === detail.typeLieu && Number(editRayon) === detail.rayonMetres)
                        }
                      >
                        Enregistrer la zone
                      </button>
                    </div>
                    <p className="muted small">
                      Un collaborateur est accepté s’il est à moins de {editRayon || '—'} m du QR et si son GPS est
                      au moins aussi précis. Validez le rayon sur place avec l’écran « Test terrain » de RH Connect.
                    </p>
                  </form>
                ) : null}
                {!lectureSeule ? (
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={busy}
                    onClick={() => void toggleActif()}
                  >
                    {detail.actif ? 'Désactiver le site' : 'Activer le site'}
                  </button>
                ) : (
                  <p className="muted small">Lecture seule</p>
                )}
              </section>

              <section className="mb-lg">
                <h3 className="section-title">Emplacement GPS</h3>
                {detail.latitude != null && detail.longitude != null ? (
                  <>
                    <p className="mono small">
                      {detail.latitude.toFixed(6)}, {detail.longitude.toFixed(6)}
                    </p>
                    <p className="small">
                      {detail.precisionAncrageMetres != null
                        ? `Précision de la pose : ±${detail.precisionAncrageMetres.toFixed(1)} m` +
                          (detail.nbMesuresAncrage ? ` (${detail.nbMesuresAncrage} mesures)` : '')
                        : 'Précision de la pose inconnue : reposez l’emplacement depuis RH Connect.'}
                      {detail.emplacementUpdatedAt
                        ? ` · posé le ${formatDateTunis(detail.emplacementUpdatedAt)}` +
                          // L'auteur est stocké sous forme d'identifiant technique : on ne l'affiche que s'il est lisible
                          (detail.emplacementUpdatedBy && !/^[0-9a-f-]{36}$/i.test(detail.emplacementUpdatedBy)
                            ? ` par ${detail.emplacementUpdatedBy}`
                            : '')
                        : ''}
                    </p>
                  </>
                ) : (
                  <p className="muted">Non posé</p>
                )}
                <p className="muted small">
                  Posez l’emplacement depuis RH Connect (mobile) sur le lieu d’affichage du QR : l’appareil mesure
                  pendant 15 s et exige une précision de{' '}
                  {TYPES_LIEU.find((t) => t.value === detail.typeLieu)?.precisionPose ?? 10} m.
                </p>
              </section>

              <section className="mb-lg">
                <h3 className="section-title">Mode d’affichage du QR</h3>
                <p className="small">
                  {detail.modeQr === 'ECRAN'
                    ? `Borne écran — le QR change toutes les 30 s${
                        detail.borneActive && detail.borneCreeLe
                          ? ` (borne créée le ${formatDateTunis(detail.borneCreeLe)})`
                          : ''
                      }.`
                    : 'QR papier — imprimé, valable 90 jours.'}
                </p>
                {detail.modeQr === 'ECRAN' && !detail.borneActive ? (
                  <div className="alert alert--warning" role="status">
                    Aucune borne active : les collaborateurs ne peuvent pas pointer sur ce site.
                  </div>
                ) : null}
                {lienBorne ? (
                  <div className="alert alert--info" role="status">
                    Lien de la borne (affiché une seule fois) :{' '}
                    <a href={lienBorne} target="_blank" rel="noreferrer" className="mono small">
                      {lienBorne}
                    </a>
                  </div>
                ) : null}
                {!lectureSeule ? (
                  <div className="page__head-actions mt-sm">
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm"
                      disabled={busy}
                      onClick={() => void genererBorne()}
                    >
                      {detail.borneActive ? 'Remplacer la borne écran' : 'Créer une borne écran'}
                    </button>
                    {detail.borneActive ? (
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        disabled={busy}
                        onClick={() => void revoquerBorne()}
                      >
                        Désactiver la borne
                      </button>
                    ) : null}
                    {detail.modeQr === 'ECRAN' ? (
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        disabled={busy}
                        onClick={() => void passerEnModePapier()}
                      >
                        Revenir au QR papier
                      </button>
                    ) : null}
                  </div>
                ) : null}
              </section>

              <section>
                <h3 className="section-title">Code QR</h3>
                {detail.hasQrActif ? (
                  <p className="small">
                    Version {detail.qrVersion ?? '—'} · valide du {formatDateTunis(detail.validFrom)} au{' '}
                    {formatDateTunis(detail.validUntil)} (90 jours)
                  </p>
                ) : (
                  <p className="muted small">Aucun QR actif — générez un code pour l’afficher sur site.</p>
                )}

                <div className="page__head-actions mt-sm">
                  {!lectureSeule && !detail.hasQrActif ? (
                    <button
                      type="button"
                      className="btn btn--primary"
                      disabled={busy || !detail.actif}
                      title={!detail.actif ? 'Activez le site avant de générer un QR' : undefined}
                      onClick={() => void doGenerate(false)}
                    >
                      Générer QR
                    </button>
                  ) : null}

                  {!lectureSeule ? (
                    <button
                      type="button"
                      className={detail.hasQrActif ? 'btn btn--primary' : 'btn btn--secondary'}
                      disabled={busy || !detail.hasQrActif}
                      title={
                        !detail.hasQrActif
                          ? 'Générez d’abord un QR'
                          : 'Télécharger le PNG du QR actif'
                      }
                      onClick={() => void doDownload()}
                    >
                      Télécharger code QR
                    </button>
                  ) : (
                    <span className="muted small">Téléchargement réservé RH / ADMIN</span>
                  )}

                  {!lectureSeule && detail.hasQrActif ? (
                    <>
                      <button
                        type="button"
                        className="btn btn--warning"
                        disabled={busy}
                        onClick={() => setModal('regenerer')}
                      >
                        Régénérer
                      </button>
                      <button
                        type="button"
                        className="btn btn--danger"
                        disabled={busy}
                        onClick={() => setModal('revoquer')}
                      >
                        Révoquer
                      </button>
                    </>
                  ) : null}
                </div>
              </section>
            </>
          ) : null}
        </div>
      ) : null}

      {tab === 'manuels' ? <PointagesManuelsPanel lectureSeule={lectureSeule} /> : null}

      {tab === 'pointages' ? (
        <div className="panel">
          <div className="form-grid mb-md">
            <label>
              Site
              <select
                value={ptSiteId}
                onChange={(e) => {
                  setPtPage(0);
                  setPtSiteId(e.target.value);
                }}
              >
                <option value="">Tous les sites</option>
                {siteOptions.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Statut
              <select
                value={ptStatut}
                onChange={(e) => {
                  setPtPage(0);
                  setPtStatut(e.target.value);
                }}
              >
                {POINTAGE_STATUTS_FILTRE.map((o) => (
                  <option key={o.value || 'all'} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Type
              <select
                value={ptType}
                onChange={(e) => {
                  setPtPage(0);
                  setPtType(e.target.value);
                }}
              >
                <option value="">Entrée et sortie</option>
                <option value="ENTREE">Entrée</option>
                <option value="SORTIE">Sortie</option>
              </select>
            </label>
            <label>
              Du
              <input
                type="date"
                value={ptDu}
                onChange={(e) => {
                  setPtPage(0);
                  setPtDu(e.target.value);
                }}
              />
            </label>
            <label>
              Au
              <input
                type="date"
                value={ptAu}
                onChange={(e) => {
                  setPtPage(0);
                  setPtAu(e.target.value);
                }}
              />
            </label>
          </div>

          {ptErr ? (
            <div className="alert alert--error">
              {ptErr}{' '}
              <button type="button" className="btn btn--ghost btn--sm" onClick={() => void loadPointages()}>
                Réessayer
              </button>
            </div>
          ) : null}

          {ptLoading ? (
            <p className="muted">Chargement des pointages…</p>
          ) : pointages.length === 0 && !ptErr ? (
            <div className="empty-state">
              <h3>Aucun pointage pour ces filtres</h3>
              <p>Les essais validés et rejetés apparaîtront ici dès que le service sera actif.</p>
            </div>
          ) : pointages.length > 0 ? (
            <>
              <p className="muted small" aria-live="polite">
                {ptTotal} résultat{ptTotal > 1 ? 's' : ''}
              </p>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Date / heure (Tunis)</th>
                      <th>Collaborateur</th>
                      <th>Site</th>
                      <th>Type</th>
                      <th>Statut</th>
                      <th>Distance</th>
                      <th>Précision GPS</th>
                      <th>Motif</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pointages.map((p) => (
                      <tr key={p.id}>
                        <td>{formatDateTunis(p.serverTs)}</td>
                        <td className="mono">{p.collaborateurId || '—'}</td>
                        <td>{p.siteLibelle ?? p.siteId ?? '—'}</td>
                        <td>{p.type === 'ENTREE' ? 'Entrée' : p.type === 'SORTIE' ? 'Sortie' : p.type}</td>
                        <td>
                          <PointageStatutBadge statut={p.statut} />
                        </td>
                        <td className="muted">
                          {p.distanceMetres != null ? `${Math.round(p.distanceMetres)} m` : '—'}
                        </td>
                        <td className="muted">
                          {p.accuracyMetres != null ? `±${Math.round(p.accuracyMetres)} m` : '—'}
                        </td>
                        <td className="muted small">{p.motifRejet ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="pager">
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={ptPage <= 0}
                  onClick={() => setPtPage((p) => p - 1)}
                >
                  Préc.
                </button>
                <span className="muted small">
                  {ptPage + 1} / {ptTotalPages} ({ptTotal})
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={ptPage + 1 >= ptTotalPages}
                  onClick={() => setPtPage((p) => p + 1)}
                >
                  Suiv.
                </button>
              </div>
            </>
          ) : null}
        </div>
      ) : null}

      {modal === 'creer' ? (
        <Modal title="Créer un site de pointage" onClose={() => !busy && setModal(null)}>
          <form onSubmit={onCreate}>
            <div className="form-grid">
              <label>
                Code *
                <input
                  required
                  value={createCode}
                  onChange={(e) => setCreateCode(e.target.value)}
                  placeholder="ex. SIEGE-A"
                  maxLength={64}
                />
              </label>
              <label>
                Libellé *
                <input
                  required
                  value={createLibelle}
                  onChange={(e) => setCreateLibelle(e.target.value)}
                  placeholder="ex. Entrée principale"
                  maxLength={200}
                />
              </label>
            </div>
            <div className="form-grid">
              <label>
                Type de lieu *
                <select value={createTypeLieu} onChange={(e) => setCreateTypeLieu(e.target.value as TypeLieu)}>
                  {TYPES_LIEU.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label} — {t.rayonDefaut} m
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Rayon (m)
                <input
                  type="number"
                  min={RAYON_MIN}
                  max={RAYON_MAX}
                  value={createRayon}
                  onChange={(e) => setCreateRayon(e.target.value)}
                  placeholder={`Par défaut ${TYPES_LIEU.find((t) => t.value === createTypeLieu)?.rayonDefaut} m`}
                />
              </label>
            </div>
            <p className="muted small">
              Rayon de {RAYON_MIN} à {RAYON_MAX} m. L’emplacement GPS se pose ensuite via RH Connect, sur place.
            </p>
            <div className="page__head-actions mt-md">
              <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => setModal(null)}>
                Annuler
              </button>
              <button type="submit" className="btn btn--primary" disabled={busy}>
                Créer
              </button>
            </div>
          </form>
        </Modal>
      ) : null}

      {modal === 'regenerer' ? (
        <Modal title="Remplacer le QR ?" onClose={() => !busy && setModal(null)}>
          <p>
            L’ancien QR sera <strong>immédiatement</strong> invalide. Réimprimez et remplacez l’affichage sur
            site avant de communiquer.
          </p>
          <div className="page__head-actions mt-md">
            <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => setModal(null)}>
              Annuler
            </button>
            <button
              type="button"
              className="btn btn--warning"
              disabled={busy}
              onClick={() => void doGenerate(true)}
            >
              Régénérer
            </button>
          </div>
        </Modal>
      ) : null}

      {modal === 'revoquer' ? (
        <Modal title="Révoquer le QR ?" onClose={() => !busy && setModal(null)}>
          <p>
            Le QR actif sera révoqué immédiatement. Aucun collaborateur ne pourra pointer sur ce site tant
            qu’un nouveau QR n’aura pas été généré.
          </p>
          <div className="page__head-actions mt-md">
            <button type="button" className="btn btn--ghost" disabled={busy} onClick={() => setModal(null)}>
              Annuler
            </button>
            <button type="button" className="btn btn--danger" disabled={busy} onClick={() => void doRevoke()}>
              Révoquer
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
