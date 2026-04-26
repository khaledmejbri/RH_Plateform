import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { clearToken } from '../api/auth';
import NotificationBell from './NotificationBell';

const nav = [
  { to: '/app/accueil',                   label: 'Accueil',                    icon: IconHome,     desc: 'Tableau de bord' },
  { to: '/app/plaintes',                  label: 'Plaintes',                   icon: IconChat,     desc: 'Suivi et traitement' },
  { to: '/app/demandes-administratives',  label: 'Demandes administratives',   icon: IconFile,     desc: 'Congés, autorisations' },
  { to: '/app/documents-administratifs',  label: 'Documents administratifs',   icon: IconDoc,      desc: 'Attestations, bulletins' },
  { to: '/app/collaborateurs',            label: 'Collaborateurs',             icon: IconUsers,    desc: 'Fiches et comptes' },
  { to: '/app/unites',                    label: 'Unités organisationnelles',  icon: IconBuilding, desc: 'Structure RH' },
];

export default function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const navigate = useNavigate();

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
        {/* Brand */}
        <div className="shell__brand">
          <div className="shell__logo" aria-hidden />
          <div>
            <div className="shell__brand-title">Plateforme RH</div>
            <div className="shell__brand-sub">Administration</div>
          </div>
        </div>

        {/* Nav */}
        <nav className="shell__nav">
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
          <h1 className="shell__header-title">Espace Ressources Humaines</h1>
          <div className="shell__header-actions">
            <NotificationBell />
            <button type="button" className="btn btn--ghost" onClick={logout}>
              Déconnexion
            </button>
          </div>
        </header>
        <main className="shell__content anim-fade-in">
          <Outlet />
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
function IconUsers({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconBuilding({ className }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v0M9 13v0M9 17v0" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
