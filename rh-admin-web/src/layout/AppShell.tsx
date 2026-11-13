import { useEffect, useMemo, useRef, useState, type ComponentType } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { clearToken, getToken } from '../api/auth';
import { canWriteBackoffice, isDirectionOnly } from '../auth/jwtRoles';
import NotificationBell from './NotificationBell';
import ErrorBoundary from '../components/ErrorBoundary';
import { DetailProvider } from '../detail/DetailProvider';
import { demarrerParametres, rafraichirParametres, useParametres } from '../utils/parametresStore';

type Icone = ComponentType<{ className?: string }>;

type Entree = {
  id: string;
  to: string;
  label: string;
  /** Visible uniquement en écriture RH/ADMIN. */
  writeOnly?: boolean;
  /** Visible uniquement pour RH et ADMIN (plaintes, D-N01.a). */
  rhAdmin?: boolean;
  /** Visible uniquement en consultation (DIRECTION). */
  lectureOnly?: boolean;
};
type Menu =
  | ({ kind: 'lien'; icon: Icone } & Entree)
  | { kind: 'groupe'; id: string; label: string; icon: Icone; enfants: Entree[] };

/** Menu par ordre d'usage (D-N03). */
const MENU: Menu[] = [
  { kind: 'lien', id: 'accueil', to: '/app/accueil', label: 'Accueil', icon: IconHome },
  {
    kind: 'groupe', id: 'pointage', label: 'Pointage', icon: IconQr, enfants: [
      { id: 'pointages', to: '/app/pointage?onglet=pointages', label: 'Pointages' },
      { id: 'temps', to: '/app/pointage?onglet=temps', label: 'Temps de travail' },
      { id: 'heures-sup', to: '/app/heures-sup', label: 'Heures supplémentaires' },
      { id: 'manuels', to: '/app/pointage?onglet=manuels', label: 'Pointages manuels' },
      { id: 'sites', to: '/app/pointage', label: 'Sites et bornes' },
    ],
  },
  {
    kind: 'groupe', id: 'collaborateurs', label: 'Collaborateurs', icon: IconUsers, enfants: [
      { id: 'fiches', to: '/app/collaborateurs', label: 'Fiches collaborateurs' },
      { id: 'organigramme', to: '/app/organigramme', label: 'Organigramme' },
      { id: 'soldes', to: '/app/conges/soldes', label: 'Soldes de congé' },
      { id: 'import-collab', to: '/app/collaborateurs/import', label: 'Import des collaborateurs', writeOnly: true },
      { id: 'historique-imports', to: '/app/collaborateurs/historique-imports', label: 'Historique des imports', lectureOnly: true },
    ],
  },
  { kind: 'lien', id: 'demandes', to: '/app/demandes-administratives', label: 'Demandes administratives', icon: IconFile },
  { kind: 'lien', id: 'documents', to: '/app/documents-administratifs', label: 'Documents administratifs', icon: IconDoc },
  {
    kind: 'groupe', id: 'parametrage', label: 'Paramétrage', icon: IconBuilding, enfants: [
      { id: 'parametres', to: '/app/parametres', label: 'Paramètres' },
      { id: 'structure', to: '/app/structure', label: 'Structure RH', writeOnly: true },
      { id: 'organisation', to: '/app/unites', label: 'Unités', writeOnly: true },
    ],
  },
  { kind: 'lien', id: 'formations', to: '/app/formations', label: 'Formations', icon: IconSchool },
  { kind: 'lien', id: 'evaluations', to: '/app/evaluations', label: 'Évaluations', icon: IconStar },
  { kind: 'lien', id: 'plaintes', to: '/app/plaintes', label: 'Plaintes', icon: IconChat, rhAdmin: true },
];

/** Identifiant de l'entrée active d'après l'adresse (y compris sur une page de détail). */
export function entreeActive(pathname: string, search: string): string {
  const p = pathname;
  if (p.startsWith('/app/pointage/sites')) return 'sites';
  if (p.startsWith('/app/pointage/manuels')) return 'manuels';
  if (p.startsWith('/app/pointage')) {
    const o = new URLSearchParams(search).get('onglet');
    return o === 'pointages' ? 'pointages' : o === 'temps' ? 'temps' : o === 'manuels' ? 'manuels' : 'sites';
  }
  if (p.startsWith('/app/heures-sup')) return 'heures-sup';
  if (p.startsWith('/app/collaborateurs/import')) return 'import-collab';
  if (p.startsWith('/app/collaborateurs/historique-imports')) return 'historique-imports';
  if (p.startsWith('/app/conges/soldes')) return 'soldes';
  if (p.startsWith('/app/collaborateurs')) return 'fiches';
  if (p.startsWith('/app/organigramme') || p.startsWith('/app/organisation')) return 'organigramme';
  if (p.startsWith('/app/formations')) return 'formations';
  if (p.startsWith('/app/demandes-administratives')) return 'demandes';
  if (p.startsWith('/app/documents-administratifs')) return 'documents';
  if (p.startsWith('/app/evaluations')) return 'evaluations';
  if (p.startsWith('/app/plaintes')) return 'plaintes';
  if (p.startsWith('/app/structure')) return 'structure';
  if (p.startsWith('/app/unites')) return 'organisation';
  if (p.startsWith('/app/parametres')) return 'parametres';
  if (p.startsWith('/app/accueil')) return 'accueil';
  return '';
}

function libelleEntree(id: string): string | undefined {
  for (const m of MENU) {
    if (m.kind === 'lien' && m.id === id) return m.label;
    if (m.kind === 'groupe') {
      const e = m.enfants.find((x) => x.id === id);
      if (e) return e.label;
    }
  }
  return undefined;
}

export default function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const token = getToken();
  const direction = isDirectionOnly(token);
  const canWrite = canWriteBackoffice(token);
  const menuBtn = useRef<HTMLButtonElement>(null);
  const actif = entreeActive(location.pathname, location.search);
  const parametres = useParametres();

  // Libellés paramétrés (lot 3) : chargés à la connexion, revalidés toutes les 60 s.
  useEffect(() => demarrerParametres(), []);
  // Les pages qui lisent le contexte du routeur se rendent de nouveau quand un libellé change.
  const contexteRoute = useMemo(
    () => ({ lectureSeule: direction, parametresVersion: parametres.version }),
    [direction, parametres.version],
  );

  // Menu filtré par rôle ; un groupe sans sous-entrée visible n'est pas affiché.
  const menu = useMemo(() => {
    const visible = (e: Entree) => (!e.writeOnly || canWrite) && (!e.rhAdmin || canWrite) && (!e.lectureOnly || !canWrite);
    return MENU.flatMap((m): Menu[] => {
      if (m.kind === 'lien') return visible(m) ? [m] : [];
      const enfants = m.enfants.filter(visible);
      return enfants.length > 0 ? [{ ...m, enfants }] : [];
    });
  }, [canWrite]);

  const groupeActif = menu.find((m) => m.kind === 'groupe' && m.enfants.some((e) => e.id === actif))?.id;
  const [ouverts, setOuverts] = useState<Record<string, boolean>>({});
  // À l'ouverture d'une page, son groupe est déplié.
  useEffect(() => {
    if (groupeActif) setOuverts((o) => (o[groupeActif] ? o : { ...o, [groupeActif]: true }));
  }, [groupeActif]);

  // Titre d'onglet : libellé de la sous-entrée (D-N03). Les fiches posent leur propre titre.
  useEffect(() => {
    const estFiche = /^\/app\/(collaborateurs(?!\/(import|historique-imports))|formations|plaintes|demandes-administratives|documents-administratifs|heures-sup|evaluations|organisation|pointage\/(sites|manuels))\/.+/.test(location.pathname);
    if (!estFiche) document.title = `${libelleEntree(actif) ?? 'Accueil'} — RH-Évènement`;
  }, [actif, location.pathname]);

  // Tiroir (petits écrans) : Échap le ferme et rend le focus au bouton.
  useEffect(() => {
    if (!sidebarOpen) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setSidebarOpen(false);
        menuBtn.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [sidebarOpen]);

  function logout() {
    clearToken();
    navigate('/login', { replace: true });
  }

  const lien = (e: Entree, Ic?: Icone) => (
    <Link
      key={e.id}
      to={e.to}
      aria-current={actif === e.id ? 'page' : undefined}
      className={`shell__nav-item${actif === e.id ? ' shell__nav-item--active' : ''}`}
      onClick={() => setSidebarOpen(false)}
    >
      {Ic ? <Ic className="shell__nav-icon" /> : null}
      <span className="shell__nav-label">{e.label}</span>
    </Link>
  );

  return (
    <DetailProvider>
      <div className="shell">
        <a className="skip-link" href="#contenu">Aller au contenu</a>
        <button ref={menuBtn} type="button" className="shell__menu-btn" aria-label="Ouvrir le menu" aria-expanded={sidebarOpen}
          onClick={() => setSidebarOpen(v => !v)}>
          <span /><span /><span />
        </button>

        {sidebarOpen && (
          <button type="button" className="shell__backdrop" aria-label="Fermer"
            onClick={() => { setSidebarOpen(false); menuBtn.current?.focus(); }} />
        )}

        <aside className={`shell__sidebar ${sidebarOpen ? 'shell__sidebar--open' : ''}`}>
          <div className="shell__brand">
            <div className="shell__logo" aria-hidden />
            <div>
              <div className="shell__brand-title">Plateforme RH</div>
              <div className="shell__brand-sub">
                {direction ? 'Consultation' : 'Administration'}
              </div>
            </div>
          </div>

          <nav className="shell__nav" aria-label="Navigation principale">
            {menu.map((m) => {
              if (m.kind === 'lien') return lien(m, m.icon);
              const ouvert = !!ouverts[m.id];
              const Ic = m.icon;
              return (
                <div key={m.id} className="shell__nav-groupe">
                  <button
                    type="button"
                    className={`shell__nav-groupe-btn${groupeActif === m.id ? ' shell__nav-groupe-btn--actif' : ''}`}
                    aria-expanded={ouvert}
                    aria-controls={`nav-${m.id}`}
                    onClick={() => setOuverts((o) => ({ ...o, [m.id]: !o[m.id] }))}
                  >
                    <Ic className="shell__nav-icon" />
                    <span className="shell__nav-label">{m.label}</span>
                    <span className={`shell__nav-chevron${ouvert ? ' shell__nav-chevron--ouvert' : ''}`} aria-hidden>▸</span>
                    <span className="sr-only">{ouvert ? 'déplié' : 'replié'}</span>
                  </button>
                  <div id={`nav-${m.id}`} className="shell__nav-sous" hidden={!ouvert}>
                    {m.enfants.map((e) => lien(e))}
                  </div>
                </div>
              );
            })}
          </nav>
        </aside>

        <div className="shell__main">
          <header className="shell__header">
            <h1 className="shell__header-title">
              {direction ? 'Espace Direction' : 'Espace Ressources Humaines'}
            </h1>
            <div className="shell__header-actions">
              <NotificationBell />
              <button type="button" className="btn btn--ghost" onClick={logout}>
                Déconnexion
              </button>
            </div>
          </header>
          {direction ? (
            <div className="alert alert--info" role="status" style={{ margin: '0 1.25rem', marginTop: 12 }}>
              Consultation uniquement — vous ne pouvez pas modifier les données.
            </div>
          ) : null}
          <main id="contenu" tabIndex={-1} className="shell__content anim-fade-in">
            {parametres.pret && parametres.erreur ? (
              <div className="alert alert--warning" role="alert">
                Certains libellés paramétrés sont indisponibles : des valeurs peuvent s’afficher « Valeur non reconnue ».{' '}
                <button type="button" className="btn btn--secondary btn--sm" onClick={() => void rafraichirParametres()}>Réessayer</button>
              </div>
            ) : null}
            {parametres.pret ? (
              <ErrorBoundary resetKey={location.pathname}>
                <Outlet context={contexteRoute} />
              </ErrorBoundary>
            ) : (
              <div className="loading" role="status" aria-live="polite">
                <span className="spinner" aria-hidden />
                <span>Chargement…</span>
              </div>
            )}
          </main>
        </div>
      </div>
    </DetailProvider>
  );
}

function IconHome({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M3 10.5L12 3l9 7.5V21a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1v-10.5z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconChat({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M21 12a8 8 0 01-8 8H8l-5 3v-3H5a8 8 0 118-8h8z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconFile({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" strokeLinecap="round" strokeLinejoin="round" /><path d="M14 2v6h6" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconDoc({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M9 12h6M9 16h6M7 4h7l5 5v11a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconSchool({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M22 10L12 5 2 10l10 5 10-5z" strokeLinecap="round" strokeLinejoin="round" /><path d="M6 12.5V17c0 1.5 2.7 3 6 3s6-1.5 6-3v-4.5" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconUsers({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconBuilding({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v0M9 13v0M9 17v0" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconStar({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconQr({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 2h2v2h-2v-2zm4-2h2v6h-6v-2h2v-2h2v-2zm-4 4h2v2h-2v-2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
