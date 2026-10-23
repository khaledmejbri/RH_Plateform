import { useMemo, useState, type ComponentType } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { clearToken, getToken } from '../api/auth';
import { canWriteBackoffice, isDirectionOnly } from '../auth/jwtRoles';
import NotificationBell from './NotificationBell';
import ErrorBoundary from '../components/ErrorBoundary';

type NavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  desc: string;
  /** Visible uniquement en écriture RH/ADMIN */
  writeOnly?: boolean;
};

const navAll: NavItem[] = [
  { to: '/app/accueil',                   label: 'Accueil',                    icon: IconHome,     desc: 'Tableau de bord' },
  { to: '/app/plaintes',                  label: 'Plaintes',                   icon: IconChat,     desc: 'Suivi' },
  { to: '/app/demandes-administratives',  label: 'Demandes administratives',   icon: IconFile,     desc: 'Congés, autorisations' },
  { to: '/app/documents-administratifs',  label: 'Documents administratifs',   icon: IconDoc,      desc: 'Attestations, bulletins' },
  { to: '/app/formations',                label: 'Formations',                 icon: IconSchool,   desc: 'Plan annuel' },
  { to: '/app/evaluations',               label: 'Évaluations',                icon: IconStar,     desc: 'Campagnes & suivi' },
  { to: '/app/collaborateurs',            label: 'Collaborateurs',             icon: IconUsers,    desc: 'Fiches' },
  { to: '/app/organigramme',              label: 'Organigramme',               icon: IconTree,     desc: 'Hiérarchie visuelle' },
  { to: '/app/pointage',                  label: 'Sites de pointage',          icon: IconQr,       desc: 'Pointage QR' },
  { to: '/app/structure',                 label: 'Structure RH',               icon: IconBuilding, desc: 'Départements & unités', writeOnly: true },
  { to: '/app/unites',                    label: 'Unités (référentiel)',        icon: IconBuilding, desc: 'Référentiel brut', writeOnly: true },
];

export default function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const token = getToken();
  const direction = isDirectionOnly(token);
  const canWrite = canWriteBackoffice(token);

  const nav = useMemo(
    () => navAll.filter((item) => !item.writeOnly || canWrite),
    [canWrite],
  );

  function logout() {
    clearToken();
    navigate('/login', { replace: true });
  }

  return (
    <div className="shell">
      <button type="button" className="shell__menu-btn" aria-label="Menu"
        onClick={() => setSidebarOpen(v => !v)}>
        <span /><span /><span />
      </button>

      {sidebarOpen && (
        <button type="button" className="shell__backdrop" aria-label="Fermer"
          onClick={() => setSidebarOpen(false)} />
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
          {nav.map(item => (
            <NavLink key={item.to} to={item.to}
              className={({ isActive }) => `shell__nav-item${isActive ? ' shell__nav-item--active' : ''}`}
              onClick={() => setSidebarOpen(false)}>
              <item.icon className="shell__nav-icon" />
              <span>
                <span className="shell__nav-label">{item.label}</span>
                <span className="shell__nav-desc">{item.desc}</span>
              </span>
            </NavLink>
          ))}
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
        <main className="shell__content anim-fade-in">
          <ErrorBoundary resetKey={location.pathname}>
            <Outlet context={{ lectureSeule: direction }} />
          </ErrorBoundary>
        </main>
      </div>
    </div>
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
function IconTree({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 3v18M12 8h6a3 3 0 010 6h-6M12 8H8a3 3 0 000 6h4M12 14h5a2.5 2.5 0 010 5h-5M12 14H9a2.5 2.5 0 000 5h3" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconStar({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconQr({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M4 4h6v6H4V4zm10 0h6v6h-6V4zM4 14h6v6H4v-6zm10 2h2v2h-2v-2zm4-2h2v6h-6v-2h2v-2h2v-2zm-4 4h2v2h-2v-2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
