import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  getCollaborateursPage,
  getDemandesAdministrativesListe,
  getDocumentsFileAttente,
  getPlaintesRh,
} from '../api/rhClient';

export default function HomePage() {
  const [plaintes, setPlaintes] = useState<number | null>(null);
  const [docs, setDocs] = useState<number | null>(null);
  const [demandes, setDemandes] = useState<number | null>(null);
  const [collabs, setCollabs] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);

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
    return () => {
      cancel = true;
    };
  }, []);

  const dash = (n: number | null) => (n === null ? '...' : String(n));

  return (
    <div className="page">
      <div className="page__hero">
        <h2 className="page__title">Bienvenue</h2>
        <p className="page__lead">Tableau de bord RH : plaintes, demandes, documents, collaborateurs.</p>
      </div>
      {err ? <div className="alert alert--error">{err}</div> : null}
      <div className="grid-stats">
        <Link to="/app/plaintes" className="stat-card anim-lift">
          <div className="stat-card__accent" style={{ background: 'var(--accent-plaintes)' }} />
          <div className="stat-card__value">{dash(plaintes)}</div>
          <div className="stat-card__title">Plaintes</div>
          <div className="stat-card__subtitle">Suivi et traitement</div>
        </Link>
        <Link to="/app/demandes-administratives" className="stat-card anim-lift">
          <div className="stat-card__accent" style={{ background: 'var(--accent-demandes)' }} />
          <div className="stat-card__value">{dash(demandes)}</div>
          <div className="stat-card__title">Demandes admin.</div>
          <div className="stat-card__subtitle">Conges et autorisations</div>
        </Link>
        <Link to="/app/documents-administratifs" className="stat-card anim-lift">
          <div className="stat-card__accent" style={{ background: 'var(--accent-docs)' }} />
          <div className="stat-card__value">{dash(docs)}</div>
          <div className="stat-card__title">Documents</div>
          <div className="stat-card__subtitle">File FIFO</div>
        </Link>
        <Link to="/app/collaborateurs" className="stat-card anim-lift">
          <div className="stat-card__accent" style={{ background: 'var(--accent-users)' }} />
          <div className="stat-card__value">{dash(collabs)}</div>
          <div className="stat-card__title">Collaborateurs</div>
          <div className="stat-card__subtitle">Referentiel</div>
        </Link>
      </div>
      <section className="panel panel--soft">
        <h3 className="panel__title">Acces rapide</h3>
        <ul className="quick-links">
          <li>
            <Link to="/app/unites">Unites organisationnelles</Link>
          </li>
          <li>
            <Link to="/app/documents-administratifs">Traiter la file documents</Link>
          </li>
          <li>
            <Link to="/app/collaborateurs">Creer un collaborateur</Link>
          </li>
        </ul>
      </section>
    </div>
  );
}
