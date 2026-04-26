import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getCollaborateursPage,
  getDemandesAdministrativesListe,
  getDocumentsFileAttente,
  getPlaintesRh,
} from '../api/rhClient';

export default function HomePage() {
  const [plaintes,  setPlaintes]  = useState<number | null>(null);
  const [docs,      setDocs]      = useState<number | null>(null);
  const [demandes,  setDemandes]  = useState<number | null>(null);
  const [collabs,   setCollabs]   = useState<number | null>(null);
  const [err,       setErr]       = useState<string | null>(null);

  useEffect(() => {
    let cancel = false;
    void (async () => {
      try {
        const [p, d, dm, cp] = await Promise.all([
          getPlaintesRh(),
          getDocumentsFileAttente(),
          getDemandesAdministrativesListe(),
          getCollaborateursPage(0, 1),
        ]);
        if (cancel) return;
        setPlaintes(p.length);
        setDocs(d.length);
        setDemandes(dm.length);
        setCollabs(cp.total_elements);
      } catch (e) {
        if (!cancel) setErr(e instanceof Error ? e.message : 'Erreur');
      }
    })();
    return () => { cancel = true; };
  }, []);

  const n = (v: number | null) => v === null ? '…' : String(v);

  const stats = [
    {
      to: '/app/plaintes',
      value: n(plaintes),
      label: 'Plaintes',
      sub: 'Suivi et traitement',
      icon: <IconChat />,
      accent: '#7c3aed',
      bg: '#f5f3ff',
    },
    {
      to: '/app/demandes-administratives',
      value: n(demandes),
      label: 'Demandes',
      sub: 'Congés et autorisations',
      icon: <IconFile />,
      accent: '#1e40af',
      bg: '#eff6ff',
    },
    {
      to: '/app/documents-administratifs',
      value: n(docs),
      label: 'Documents',
      sub: 'Attestations et bulletins',
      icon: <IconDoc />,
      accent: '#065f46',
      bg: '#f0fdf4',
    },
    {
      to: '/app/collaborateurs',
      value: n(collabs),
      label: 'Collaborateurs',
      sub: 'Fiches et comptes',
      icon: <IconUsers />,
      accent: '#92400e',
      bg: '#fffbeb',
    },
  ];

  return (
    <div className="page">
      {/* ── Hero ── */}
      <div style={{ marginBottom: 32 }}>
        <h2 style={{ margin: 0, fontSize: 24, fontWeight: 700, color: '#0f172a' }}>
          Tableau de bord
        </h2>
        <p style={{ margin: '6px 0 0', color: '#64748b', fontSize: 14 }}>
          Vue d'ensemble de l'activité RH en temps réel.
        </p>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}

      {/* ── Stats grid ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16, marginBottom: 32 }}>
        {stats.map(s => (
          <Link key={s.to} to={s.to} style={{ textDecoration: 'none' }}>
            <div style={{
              background: 'white',
              border: '1px solid #e2e8f0',
              borderRadius: 16,
              padding: 24,
              transition: 'box-shadow 0.2s, transform 0.2s',
              cursor: 'pointer',
            }}
              onMouseEnter={e => {
                (e.currentTarget as HTMLElement).style.boxShadow = '0 8px 24px rgba(0,0,0,0.08)';
                (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)';
              }}
              onMouseLeave={e => {
                (e.currentTarget as HTMLElement).style.boxShadow = 'none';
                (e.currentTarget as HTMLElement).style.transform = 'none';
              }}>
              <div style={{
                width: 44, height: 44, borderRadius: 12,
                background: s.bg, color: s.accent,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                marginBottom: 16,
              }}>
                {s.icon}
              </div>
              <div style={{ fontSize: 32, fontWeight: 800, color: '#0f172a', lineHeight: 1, marginBottom: 6 }}>
                {s.value}
              </div>
              <div style={{ fontWeight: 600, fontSize: 15, color: '#1e293b' }}>{s.label}</div>
              <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{s.sub}</div>
            </div>
          </Link>
        ))}
      </div>

      {/* ── Quick links ── */}
      <div style={{ background: 'white', border: '1px solid #e2e8f0', borderRadius: 16, padding: 24 }}>
        <h3 style={{ margin: '0 0 16px', fontSize: 15, fontWeight: 700, color: '#0f172a' }}>
          Accès rapide
        </h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            { to: '/app/unites',                   label: 'Unités organisationnelles', icon: '🏢' },
            { to: '/app/documents-administratifs', label: 'Traiter une demande de document', icon: '📄' },
            { to: '/app/collaborateurs',           label: 'Créer un collaborateur', icon: '👤' },
          ].map(l => (
            <Link key={l.to} to={l.to} style={{
              display: 'flex', alignItems: 'center', gap: 12,
              padding: '12px 14px', borderRadius: 10,
              background: '#f8fafc', textDecoration: 'none',
              color: '#1e293b', fontSize: 14, fontWeight: 500,
              transition: 'background 0.15s',
            }}
              onMouseEnter={e => (e.currentTarget.style.background = '#f1f5f9')}
              onMouseLeave={e => (e.currentTarget.style.background = '#f8fafc')}>
              <span style={{ fontSize: 18 }}>{l.icon}</span>
              {l.label}
              <span style={{ marginLeft: 'auto', color: '#cbd5e1' }}>›</span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

function IconChat() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M21 12a8 8 0 01-8 8H8l-5 3v-3H5a8 8 0 118-8h8z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconFile() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" strokeLinecap="round" strokeLinejoin="round" /><path d="M14 2v6h6" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconDoc() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M9 12h6M9 16h6M7 4h7l5 5v11a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconUsers() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
