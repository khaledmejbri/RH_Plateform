import { FormEvent, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { BandeauQrPapier } from '../components/VigilanceManuelle';
import {
  downloadSiteQrPng,
  formatDateCourteTunis,
  formatDateTunis,
  minuteTunis,
  type EtatSequencePointage,
  getPresencePointages,
  getPresenceSite,
  getPresenceSites,
  isQrExpired,
  isServiceUnavailable,
  LIBELLES_VERIFICATION_FACIALE,
  messagePresenceErreur,
  patchPresenceSite,
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
import { useConfirm } from '../components/ConfirmDialog';
import { formatNombre, SITE_INTROUVABLE, COLLABORATEUR_INTROUVABLE } from '../utils/format';
import { VALEUR_NON_RECONNUE } from '../utils/libelles';
import { Icon } from '../components/ui';
import { libelleReferentiel, optionsFiltre, useParametres } from '../utils/parametresStore';
import { useSearchParams } from 'react-router-dom';
import BarreFiltres, { AucunResultat, ChampDate, ChampSelect } from '../components/BarreFiltres';
import FiltreCollaborateur, { libelleCollaborateur } from '../components/FiltreCollaborateur';
import { getTousCollaborateurs, type CollaborateurRow } from '../api/rhClient';
import { correspond, useFiltresUrl } from '../utils/filtres';
import HoraireSitePanel from './HoraireSitePanel';
import PointagesManuelsPanel from './PointagesManuelsPanel';
import TempsTravailPanel from './TempsTravailPanel';
import VerificationFacialePanel from './VerificationFacialePanel';
import DetailLink, { LienPersonne } from '../detail/DetailLink';

type Tab = 'sites' | 'pointages' | 'temps' | 'manuels' | 'facial';
type ModalKind = 'creer' | 'regenerer' | 'revoquer' | null;

const PAGE_SIZE = 20;
const ONGLETS: Tab[] = ['sites', 'pointages', 'temps', 'manuels', 'facial'];
const FILTRES_SITES_DEFAUT = { q: '', actif: 'all', expire: '' };
const FILTRES_POINTAGES_DEFAUT = { collab: '', site: '', statut: '', type: '', du: '', au: '', sansFacial: '' };

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

function libelleStatutPointage(statut: string, libelle?: string | null): string {
  return libelle || libelleReferentiel('STATUT_POINTAGE', statut);
}

/** Motif en clair pour la RH ; le détail technique du serveur reste dans l'info-bulle (PTG-3). */
function MotifPointage({ p }: { p: PresencePointage }) {
  if (!p.statut.startsWith('REJETE_')) return <>—</>;
  return <span title={p.motifRejet ?? undefined}>{libelleStatutPointage(p.statut, p.statutLibelle)}</span>;
}

function PointageStatutBadge({ statut, libelle }: { statut: string; libelle?: string | null }) {
  const label = libelleStatutPointage(statut, libelle);
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
  useParametres();
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
  const [params, setParams] = useSearchParams();
  const ongletUrl = params.get('onglet') as Tab | null;
  // Onglet lu dans l'URL à chaque rendu : le sous-menu Pointage (D-N03) change l'URL sans recréer la page.
  const tab: Tab = ongletUrl && ONGLETS.includes(ongletUrl) ? ongletUrl : 'sites';
  /** L'onglet courant est mémorisé dans l'URL avec les filtres de la page. */
  function setTab(t: Tab) {
    setParams(
      (prev) => {
        const suivant = new URLSearchParams(prev);
        if (t === 'sites') suivant.delete('onglet');
        else suivant.set('onglet', t);
        return suivant;
      },
      { replace: true },
    );
  }
  const filtresSites = useFiltresUrl(FILTRES_SITES_DEFAUT, 's_');
  const filtresPt = useFiltresUrl(FILTRES_POINTAGES_DEFAUT, 'p_');
  const fs = filtresSites.valeurs;
  const fp = filtresPt.valeurs;
  const [collabs, setCollabs] = useState<CollaborateurRow[]>([]);
  const [collabsLoading, setCollabsLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [sites, setSites] = useState<PresenceSite[]>([]);
  const [sitesPage, setSitesPage] = useState(0);
  const [sitesTotalPages, setSitesTotalPages] = useState(1);
  const [sitesTotal, setSitesTotal] = useState(0);
  const [sitesLoading, setSitesLoading] = useState(true);
  const [sitesErr, setSitesErr] = useState<string | null>(null);
  const [deployBanner, setDeployBanner] = useState(false);

  const recherche = fs.q;
  const filtreActif = fs.actif as 'all' | 'true' | 'false';
  const filtreExpire = fs.expire === '1';

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
  const ptSiteId = fp.site;
  const ptStatut = fp.statut;
  const ptType = fp.type;
  const ptDu = fp.du;
  const ptAu = fp.au;
  const ptCollab = fp.collab;
  const ptSansFacial = fp.sansFacial === '1';

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
    return sites.filter((s) => {
      if (filtreExpire && !s.expireBientot) return false;
      return correspond([s.code, s.libelle], recherche);
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
        collaborateurId: ptCollab || undefined,
        statut: ptStatut || undefined,
        type: ptType || undefined,
        du: ptDu || undefined,
        au: ptAu || undefined,
        verificationFaciale: ptSansFacial ? ['NON_CONSENTIE', 'NON_DISPONIBLE'] : undefined,
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
  }, [ptPage, ptSiteId, ptCollab, ptStatut, ptType, ptDu, ptAu, ptSansFacial]);

  useEffect(() => {
    if (tab === 'pointages') void loadPointages();
  }, [tab, loadPointages]);

  // Liste des collaborateurs : filtre « Collaborateur » et repli d'affichage du nom.
  useEffect(() => {
    if (tab !== 'pointages' || collabs.length > 0) return;
    setCollabsLoading(true);
    void getTousCollaborateurs()
      .then(setCollabs)
      .catch(() => {})
      .finally(() => setCollabsLoading(false));
  }, [tab, collabs.length]);

  const collabParId = useMemo(() => new Map(collabs.map((c) => [c.identifiant, c])), [collabs]);

  /** « Nom (matricule) », sans jamais afficher un identifiant technique. */
  /** D-P18.c : deux pointages du même collaborateur dans la même minute → heures à la seconde. */
  const minutesPartagees = useMemo(() => {
    const compte = new Map<string, number>();
    for (const p of pointages) {
      const cle = `${p.collaborateurId}|${minuteTunis(p.serverTs)}`;
      compte.set(cle, (compte.get(cle) ?? 0) + 1);
    }
    return new Set(
      pointages.filter((p) => (compte.get(`${p.collaborateurId}|${minuteTunis(p.serverTs)}`) ?? 0) > 1).map((p) => p.id),
    );
  }, [pointages]);

  function libelleCollabPointage(p: PresencePointage): string {
    if (p.collaborateurNom) return p.collaborateurMatricule ? `${p.collaborateurNom} (${p.collaborateurMatricule})` : p.collaborateurNom;
    const c = collabParId.get(p.collaborateurId);
    if (c) return libelleCollaborateur(c);
    return COLLABORATEUR_INTROUVABLE;
  }

  const siteOptions = useMemo(
    () => sites.map((s) => ({ id: s.id, label: s.libelle })),
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
    const ok = await confirm({
      message: detail.borneActive
        ? `Remplacer la borne écran du site « ${detail.libelle} » ? L’ancien lien cessera de fonctionner.`
        : `Créer une borne écran pour le site « ${detail.libelle} » ?`,
      confirmLabel: detail.borneActive ? 'Remplacer' : 'Créer',
    });
    if (!ok) return;
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
    const ok = await confirm({
      message: `Désactiver la borne écran du site « ${detail.libelle} » ? Son lien et ses QR cesseront de fonctionner.`,
      confirmLabel: 'Désactiver',
      danger: true,
    });
    if (!ok) return;
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
    const ok = await confirm({ message: `Repasser le site « ${detail.libelle} » au QR papier ?`, confirmLabel: 'Confirmer' });
    if (!ok) return;
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
    const ok = await confirm({
      message: detail.actif
        ? `Désactiver le site « ${detail.libelle} » ? Les collaborateurs ne pourront plus y pointer.`
        : `Activer le site « ${detail.libelle} » ?`,
      confirmLabel: detail.actif ? 'Désactiver' : 'Activer',
      danger: detail.actif,
    });
    if (!ok) return;
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
    if (!isRegen) {
      const ok = await confirm({
        message: `Générer un QR pour le site « ${detail.libelle} » ? Il sera valable 90 jours.`,
        confirmLabel: 'Générer',
      });
      if (!ok) return;
    }
    if (isRegen) {
      const ok = await confirm({
        message: `Régénérer le QR du site « ${detail.libelle} » ? L’ancien QR sera immédiatement invalide : réimprimez et remplacez l’affichage sur place.`,
        confirmLabel: 'Régénérer',
        danger: true,
      });
      if (!ok) return;
    }
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
    const ok = await confirm({
      message: `Révoquer le QR du site « ${detail.libelle} » ? Aucun collaborateur ne pourra pointer tant qu’un nouveau QR n’aura pas été généré.`,
      confirmLabel: 'Révoquer',
      danger: true,
    });
    if (!ok) return;
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
                : tab === 'temps'
                  ? 'Temps de travail'
                  : tab === 'manuels'
                  ? 'Pointages manuels'
                  : tab === 'facial'
                    ? 'Vérification faciale'
                    : 'Sites et bornes'}
          </h2>
          <p className="page__lead">
            {showDetail
              ? `Code ${detail?.code ?? '…'} · zone de ${detail?.rayonMetres ?? '…'} m`
              : tab === 'pointages'
                ? 'Toutes les tentatives de pointage, acceptées ou refusées.'
                : tab === 'temps'
                  ? 'Heures travaillées par collaborateur, jour et site, d’après les pointages validés.'
                  : tab === 'manuels'
                  ? 'Pointages demandés après un refus, validés par le responsable ou les RH.'
                  : tab === 'facial'
                    ? 'Réglage, consentement et enrôlement des collaborateurs. Aucune image n’est conservée ni affichée.'
                    : 'Emplacements, codes QR et zone de pointage par site.'}
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
            className={'tab' + (tab === 'temps' ? ' tab--on' : '')}
            onClick={() => {
              setTab('temps');
              setSelectedId(null);
            }}
          >
            Temps de travail
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
          <button
            type="button"
            className={'tab' + (tab === 'facial' ? ' tab--on' : '')}
            onClick={() => {
              setTab('facial');
              setSelectedId(null);
            }}
          >
            Vérification faciale
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
          Le module de pointage est momentanément indisponible. Réessayez dans quelques instants.
        </div>
      ) : null}
      {lectureSeule && (showList || showDetail || tab === 'pointages' || tab === 'temps') ? (
        <div className="alert alert--info" role="status">
          Profil Direction — consultation uniquement.
        </div>
      ) : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}
      {actionErr ? <div className="alert alert--error">{actionErr}</div> : null}

      {showList ? (
        <div className="panel">
          <BarreFiltres
            id="sites"
            recherche={fs.q}
            onRecherche={(v) => filtresSites.definir({ q: v })}
            placeholder="Code ou libellé"
            nbResultats={sitesAffiches.length}
            nbTotal={sites.length}
            nbActifs={filtresSites.nbActifs}
            onReinitialiser={() => {
              setSitesPage(0);
              filtresSites.reinitialiser();
            }}
          >
            <ChampSelect
              id="sites-actif"
              libelle="Statut site"
              valeur={fs.actif}
              sansTous
              options={[
                { valeur: 'all', libelle: 'Tous' },
                { valeur: 'true', libelle: 'Actifs' },
                { valeur: 'false', libelle: 'Inactifs' },
              ]}
              onChange={(v) => {
                setSitesPage(0);
                filtresSites.definir({ actif: v });
              }}
            />
            <label className="checkbox-row" style={{ alignSelf: 'end' }}>
              <input
                type="checkbox"
                checked={filtreExpire}
                onChange={(e) => filtresSites.definir({ expire: e.target.checked ? '1' : '' })}
              />
              QR expirant dans moins de 30 jours
            </label>
          </BarreFiltres>

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
                  : 'Aucun résultat pour ces filtres.'}
              </p>
              {!lectureSeule && sites.length === 0 ? (
                <button type="button" className="btn btn--primary" onClick={() => setModal('creer')}>
                  Créer un site
                </button>
              ) : null}
            </div>
          ) : sitesAffiches.length > 0 ? (
            <>
              {sitesAffiches.some((s) => s.modeQr !== 'ECRAN') ? <BandeauQrPapier /> : null}
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Code</th>
                      <th>Libellé</th>
                      <th>Statut</th>
                      <th>Emplacement</th>
                      <th>Mode QR</th>
                      <th>QR</th>
                      <th>Expiration</th>
                      <th><span className="sr-only">Actions</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {sitesAffiches.map((s) => (
                      <tr key={s.id}>
                        <td className="mono">{s.code}</td>
                        <td><DetailLink type="site" id={s.id} libelle={s.libelle} /></td>
                        <td>
                          {s.actif ? (
                            <span className="badge badge--success">Actif</span>
                          ) : (
                            <span className="badge badge--default">Inactif</span>
                          )}
                        </td>
                        <td>
                          {s.latitude != null && s.longitude != null ? (
                            <span className="small">Enregistré</span>
                          ) : (
                            <span className="muted small" title="À enregistrer depuis l’application mobile RH Connect">
                              Non enregistré
                            </span>
                          )}
                        </td>
                        <td className="small">{s.modeQr === 'ECRAN' ? 'Écran' : 'Papier'}</td>
                        <td>
                          {s.hasQrActif ? (
                            <span className="small">
                              QR actif{s.qrVersion != null ? ` (version ${s.qrVersion})` : ''}
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
                  Précédent
                </button>
                <span className="muted small">
                  Page {sitesPage + 1} sur {sitesTotalPages} ({sitesTotal} sites)
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={sitesPage + 1 >= sitesTotalPages}
                  onClick={() => setSitesPage((p) => p + 1)}
                >
                  Suivant
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
                  <p className="muted small">Consultation uniquement</p>
                )}
              </section>

              <section className="mb-lg">
                <h3 className="section-title">Emplacement GPS</h3>
                {detail.latitude != null && detail.longitude != null ? (
                  <>
                    <p className="mono small">
                      {formatNombre(detail.latitude, 6)}, {formatNombre(detail.longitude, 6)}
                    </p>
                    <p className="small">
                      {detail.precisionAncrageMetres != null
                        ? `Précision de l’enregistrement : ±${formatNombre(detail.precisionAncrageMetres, 1)} m` +
                          (detail.nbMesuresAncrage ? ` (${detail.nbMesuresAncrage} mesures)` : '')
                        : 'Précision de l’enregistrement inconnue : enregistrez à nouveau l’emplacement depuis RH Connect.'}
                      {detail.emplacementUpdatedAt
                        ? ` · enregistré le ${formatDateTunis(detail.emplacementUpdatedAt)}` +
                          // L'auteur est stocké sous forme d'identifiant technique : on ne l'affiche que s'il est lisible
                          (detail.emplacementUpdatedBy && !/^[0-9a-f-]{36}$/i.test(detail.emplacementUpdatedBy)
                            ? ` par ${detail.emplacementUpdatedBy}`
                            : '')
                        : ''}
                    </p>
                  </>
                ) : (
                  <p className="muted">Non enregistré</p>
                )}
                <p className="muted small">
                  Enregistrez l’emplacement depuis RH Connect (mobile) sur le lieu d’affichage du QR : l’appareil mesure
                  pendant 15 secondes et exige une précision de{' '}
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
                {detail.modeQr !== 'ECRAN' ? <BandeauQrPapier /> : null}
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
                    QR actif (version {detail.qrVersion ?? '—'}) · valide du {formatDateCourteTunis(detail.validFrom)} au{' '}
                    {formatDateCourteTunis(detail.validUntil)} (90 jours)
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
                        onClick={() => void doGenerate(true)}
                      >
                        Régénérer
                      </button>
                      <button
                        type="button"
                        className="btn btn--danger"
                        disabled={busy}
                        onClick={() => void doRevoke()}
                      >
                        Révoquer
                      </button>
                    </>
                  ) : null}
                </div>
              </section>

              <HoraireSitePanel siteId={detail.id} lectureSeule={lectureSeule} />
            </>
          ) : null}
        </div>
      ) : null}

      {tab === 'temps' ? <TempsTravailPanel /> : null}
      {tab === 'manuels' ? <PointagesManuelsPanel lectureSeule={lectureSeule} /> : null}
      {tab === 'facial' ? <VerificationFacialePanel lectureSeule={lectureSeule} /> : null}

      {tab === 'pointages' ? (
        <div className="panel">
          <BarreFiltres
            id="pointages"
            nbResultats={ptTotal}
            nbActifs={filtresPt.nbActifs}
            onReinitialiser={() => {
              setPtPage(0);
              filtresPt.reinitialiser();
            }}
          >
            <FiltreCollaborateur
              id="pointages-collab"
              collaborateurs={collabs}
              valeur={ptCollab}
              chargement={collabsLoading}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ collab: v });
              }}
            />
            <ChampSelect
              id="pointages-site"
              libelle="Site"
              valeur={ptSiteId}
              toutesLibelle="Tous les sites"
              options={siteOptions.map((o) => ({ valeur: o.id, libelle: o.label }))}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ site: v });
              }}
            />
            <ChampSelect
              id="pointages-statut"
              libelle="Statut"
              valeur={ptStatut}
              sansTous
              options={[{ valeur: '', libelle: 'Tous les statuts' }, ...optionsFiltre('STATUT_POINTAGE')]}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ statut: v });
              }}
            />
            <ChampSelect
              id="pointages-type"
              libelle="Type"
              valeur={ptType}
              toutesLibelle="Entrée et sortie"
              options={[{ valeur: 'ENTREE', libelle: 'Entrée' }, { valeur: 'SORTIE', libelle: 'Sortie' }]}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ type: v });
              }}
            />
            <ChampDate
              id="pointages-du"
              libelle="Du"
              valeur={ptDu}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ du: v });
              }}
            />
            <ChampDate
              id="pointages-au"
              libelle="Au"
              valeur={ptAu}
              onChange={(v) => {
                setPtPage(0);
                filtresPt.definir({ au: v });
              }}
            />
            <label className="checkbox-row" style={{ alignSelf: 'end' }}>
              <input
                type="checkbox"
                checked={ptSansFacial}
                onChange={(e) => {
                  setPtPage(0);
                  filtresPt.definir({ sansFacial: e.target.checked ? '1' : '' });
                }}
              />
              Sans vérification faciale
            </label>
          </BarreFiltres>

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
            filtresPt.nbActifs > 0 ? (
              <AucunResultat
                onReinitialiser={() => {
                  setPtPage(0);
                  filtresPt.reinitialiser();
                }}
              />
            ) : (
              <div className="empty-state">
                <h3>Aucun pointage</h3>
                <p>Les pointages acceptés ou refusés apparaîtront ici.</p>
              </div>
            )
          ) : pointages.length > 0 ? (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Date / heure (Tunis)</th>
                      <th>Collaborateur</th>
                      <th>Site</th>
                      <th>Type</th>
                      <th>Statut</th>
                      <th>Vérification faciale</th>
                      <th>Distance</th>
                      <th>Précision GPS</th>
                      <th>Motif</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pointages.map((p) => (
                      <tr key={p.id}>
                        <td>{formatDateTunis(p.serverTs, minutesPartagees.has(p.id))}</td>
                        <td>{p.collaborateurNom ? <LienPersonne id={p.collaborateurId} nom={p.collaborateurNom} matricule={p.collaborateurMatricule} /> : libelleCollabPointage(p)}</td>
                        <td>{p.siteLibelle ? <DetailLink type="site" id={p.siteId} libelle={p.siteLibelle} /> : SITE_INTROUVABLE}</td>
                        <td>{p.type === 'ENTREE' ? 'Entrée' : p.type === 'SORTIE' ? 'Sortie' : VALEUR_NON_RECONNUE}</td>
                        <td>
                          <PointageStatutBadge statut={p.statut} libelle={p.statutLibelle} />
                          <PastilleSequence etat={p.etatSequence} statut={p.statut} />
                        </td>
                        <td className="small">
                          {p.verificationFaciale
                            ? (LIBELLES_VERIFICATION_FACIALE[p.verificationFaciale] ?? VALEUR_NON_RECONNUE)
                            : '—'}
                          {p.scoreFacial != null ? <span className="muted"> · score {formatNombre(p.scoreFacial, 2)}</span> : null}
                        </td>
                        <td className="muted">
                          {p.distanceMetres != null ? `${Math.round(p.distanceMetres)} m` : '—'}
                        </td>
                        <td className="muted">
                          {p.accuracyMetres != null ? `±${Math.round(p.accuracyMetres)} m` : '—'}
                        </td>
                        <td className="muted small"><MotifPointage p={p} /></td>
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
                  Précédent
                </button>
                <span className="muted small">
                  Page {ptPage + 1} sur {ptTotalPages} ({ptTotal} pointages)
                </span>
                <button
                  type="button"
                  className="btn btn--ghost"
                  disabled={ptPage + 1 >= ptTotalPages}
                  onClick={() => setPtPage((p) => p + 1)}
                >
                  Suivant
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

    </div>
  );
}

/** D-P18.b : pastille de séquence calculée par le serveur ; jamais sur un pointage rejeté. */
function PastilleSequence({ etat, statut }: { etat: EtatSequencePointage; statut: string }) {
  if (!etat || statut.toUpperCase().startsWith('REJETE')) return null;
  const meta = {
    ENTREE_SANS_SORTIE: { libelle: 'Entrée sans sortie', classe: 'badge badge--warning', aide: 'Aucune sortie ne correspond à cette entrée' },
    SORTIE_SANS_ENTREE: { libelle: 'Sortie sans entrée', classe: 'badge badge--warning', aide: 'Aucune entrée ne précède cette sortie' },
    EN_COURS: { libelle: 'En cours', classe: 'badge badge--default', aide: 'Entrée ouverte : la sortie est encore attendue' },
  }[etat];
  return (
    <div>
      <span className={meta.classe} title={meta.aide}>{meta.libelle}</span>
    </div>
  );
}
