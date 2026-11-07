import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getTousCollaborateurs,
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
        // D-T01 : chaque carte compte exactement ce que l'écran ouvert affiche avec le même filtre.
        const [p, d, dm, cp] = await Promise.all([
          getPlaintesRh(),
          getDocumentsFileAttente(),
          getDemandesAdministrativesListe({ statut: 'EN_VALIDATION_RRH' }),
          getTousCollaborateurs(),
        ]);
        if (cancel) return;
        setPlaintes(p.filter((x) => x.statut !== 'FERME').length);
        setDocs(d.filter((x) => x.statut === 'EN_ATTENTE_FILE' || x.statut === 'EN_TRAITEMENT_RH').length);
        setDemandes(dm.filter((x) => x.statut === 'EN_VALIDATION_RRH').length);
        setCollabs(cp.filter((x) => x.statut.toUpperCase() === 'ACTIF').length);
      } catch (e) {
        if (!cancel) setErr(e instanceof Error ? e.message : 'Erreur');
      }
    })();
    return () => { cancel = true; };
  }, []);

  const n = (v: number | null) => v === null ? '…' : String(v);

  const stats = [
    {
      to: '/app/plaintes?statut=OUVERTES',
      value: n(plaintes),
      label: 'Plaintes ouvertes',
      sub: 'Non fermées',
      icon: <IconChat />,
      accent: '#1d4ed8',
      bg: '#eff6ff',
    },
    {
      to: '/app/demandes-administratives?statut=EN_VALIDATION_RRH',
      value: n(demandes),
      label: 'Demandes à décider',
      sub: 'Congés et autorisations',
      icon: <IconFile />,
      accent: '#0f766e',
      bg: '#f0fdfa',
    },
    {
      to: '/app/documents-administratifs?statut=A_TRAITER',
      value: n(docs),
      label: 'Documents en file',
      sub: 'En attente ou en traitement',
      icon: <IconDoc />,
      accent: '#b45309',
      bg: '#fffbeb',
    },
    {
      to: '/app/collaborateurs?statut=ACTIF',
      value: n(collabs),
      label: 'Collaborateurs actifs',
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
            ? 'Vue d’ensemble en consultation (compteurs et listes).'
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
          {[
            { to: '/app/demandes-administratives?statut=EN_VALIDATION_RRH', label: lectureSeule ? 'Demandes en attente de décision' : 'Demandes à traiter', icon: <IconFile /> },
            { to: '/app/documents-administratifs?statut=A_TRAITER', label: lectureSeule ? 'File des documents' : 'Documents à traiter', icon: <IconDoc /> },
            { to: '/app/pointage?onglet=manuels', label: lectureSeule ? 'Pointages manuels' : 'Pointages manuels à valider', icon: <IconClock /> },
            { to: '/app/evaluations?onglet=alertes', label: 'Alertes d’écart', icon: <IconAlert /> },
            { to: '/app/heures-sup', label: lectureSeule ? 'Heures supplémentaires' : 'Heures supplémentaires à valider', icon: <IconClock /> },
          ].map((l) => (
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

function IconClock() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
}
function IconAlert() {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z" strokeLinecap="round" strokeLinejoin="round" /></svg>;
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
