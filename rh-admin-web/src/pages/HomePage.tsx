import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getCollaborateursPage,
  getDemandesAdministrativesListe,
  getDocumentsFileAttente,
  getPlaintesRh,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';

export default function HomePage() {
  const lectureSeule = useLectureSeule();
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
      accent: '#1d4ed8',
      bg: '#eff6ff',
    },
    {
      to: '/app/demandes-administratives',
      value: n(demandes),
      label: 'Demandes',
      sub: 'Congés et autorisations',
      icon: <IconFile />,
      accent: '#0f766e',
      bg: '#f0fdfa',
    },
    {
      to: '/app/documents-administratifs',
      value: n(docs),
      label: 'Documents',
      sub: 'Attestations et bulletins',
      icon: <IconDoc />,
      accent: '#b45309',
      bg: '#fffbeb',
    },
    {
      to: '/app/collaborateurs',
      value: n(collabs),
      label: 'Collaborateurs',
      sub: 'Fiches et comptes',
      icon: <IconUsers />,
      accent: '#4338ca',
      bg: '#eef2ff',
    },
  ];

  return (
    <div className="page">
      <header className="dash-hero">
        <h2>Tableau de bord</h2>
        <p>
          {lectureSeule
            ? 'Vue d’ensemble — consultation Direction (compteurs et listes).'
            : 'Vue d’ensemble de l’activité RH.'}
        </p>
      </header>

      {err ? <div className="alert alert--error" role="alert">{err}</div> : null}

      <section className="dash-stats" aria-label="Indicateurs">
        {stats.map((s) => (
          <Link key={s.to} to={s.to} className="dash-stat card anim-lift">
            <span className="dash-stat__icon" style={{ background: s.bg, color: s.accent }} aria-hidden>
              {s.icon}
            </span>
            <div className="dash-stat__value" aria-busy={s.value === '…'}>{s.value}</div>
            <div className="dash-stat__label">{s.label}</div>
            <div className="dash-stat__sub">{s.sub}</div>
          </Link>
        ))}
      </section>

      <section className="panel" aria-labelledby="quick-title">
        <h3 id="quick-title" className="panel__title">Accès rapide</h3>
        <div className="quick-actions">
          {(lectureSeule
            ? [
                { to: '/app/documents-administratifs', label: 'File documents (consultation)', icon: <IconDoc /> },
                { to: '/app/collaborateurs', label: 'Collaborateurs', icon: <IconUsers /> },
                { to: '/app/organigramme', label: 'Organigramme', icon: <IconTree /> },
              ]
            : [
                { to: '/app/unites', label: 'Unités organisationnelles', icon: <IconBuilding /> },
                { to: '/app/documents-administratifs', label: 'Traiter une demande de document', icon: <IconDoc /> },
                { to: '/app/collaborateurs', label: 'Créer un collaborateur', icon: <IconUserPlus /> },
              ]
          ).map((l) => (
            <Link key={l.to} to={l.to} className="quick-action">
              <span aria-hidden>{l.icon}</span>
              {l.label}
              <svg className="quick-action__chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
                <path d="M9 18l6-6-6-6" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}

function IconTree() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 3v6M6 15v-3h12v3M12 9v3" strokeLinecap="round" strokeLinejoin="round" /><rect x="9" y="3" width="6" height="5" rx="1" /><rect x="3" y="15" width="6" height="5" rx="1" /><rect x="15" y="15" width="6" height="5" rx="1" /></svg>;
}
function IconBuilding() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v.01M9 13v.01M9 17v.01" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconUserPlus() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM19 8v6M22 11h-6" strokeLinecap="round" strokeLinejoin="round" /></svg>;
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
