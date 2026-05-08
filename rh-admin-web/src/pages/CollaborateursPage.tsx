import { FormEvent, useEffect, useState } from 'react';
import {
  getCollaborateursPage,
  getUnites,
  postCollaborateur,
  putCollaborateur,
  type CollaborateurRow,
  type Unite,
} from '../api/rhClient';

export default function CollaborateursPage() {
  const [tab, setTab] = useState<'liste' | 'creer'>('liste');
  const [unites, setUnites] = useState<Unite[]>([]);
  const [rows, setRows] = useState<CollaborateurRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const [matricule, setMatricule] = useState('');
  const [prenom, setPrenom] = useState('');
  const [nom, setNom] = useState('');
  const [courriel, setCourriel] = useState('');
  const [posteLibelle, setPosteLibelle] = useState('');
  const [fonction, setFonction] = useState('');
  const [departementLibelle, setDepartementLibelle] = useState('');
  const [dateRecrutement, setDateRecrutement] = useState('');
  const [statut, setStatut] = useState('ACTIF');
  const [uniteId, setUniteId] = useState('');
  const [motDePasseInitial, setMotDePasseInitial] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
  }, []);

  async function loadList() {
    setLoading(true);
    setErr(null);
    try {
      const p = await getCollaborateursPage(page, 15);
      setRows(p.contenu);
      setTotal(p.total_elements);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadList();
  }, [page]);

  useEffect(() => {
    if (unites.length && !uniteId) setUniteId(unites[0].identifiant);
  }, [unites, uniteId]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setMsg(null);
    setErr(null);
    setSaving(true);
    try {
      await postCollaborateur({
        matricule: matricule.trim(),
        prenom: prenom.trim(),
        nom: nom.trim(),
        courriel_professionnel: courriel.trim(),
        poste_libelle: posteLibelle.trim() || undefined,
        fonction: fonction.trim() || undefined,
        departement_libelle: departementLibelle.trim() || undefined,
        date_recrutement: dateRecrutement || undefined,
        statut: statut.trim(),
        unite_identifiant: uniteId,
        profil_acces: 'COLLABORATEUR',
        mot_de_passe_initial: motDePasseInitial,
      });
      setMsg('Collaborateur enregistre avec succes. La creation du compte et le courriel se font en arriere-plan.');
      setMatricule('');
      setPrenom('');
      setNom('');
      setCourriel('');
      setPosteLibelle('');
      setFonction('');
      setDepartementLibelle('');
      setDateRecrutement('');
      setMotDePasseInitial('');
      setTab('liste');
      setPage(0);
      await loadList();
    } catch (x) {
      setErr(x instanceof Error ? x.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 15));

  async function archiveCollaborateur(row: CollaborateurRow) {
    if (row.statut === 'ARCHIVE') return;
    if (!window.confirm(`Archiver ${row.prenom} ${row.nom} ?`)) return;
    setErr(null);
    setMsg(null);
    try {
      await putCollaborateur(row.identifiant, { statut: 'ARCHIVE' });
      setMsg(`Collaborateur ${row.matricule} archive.`);
      await loadList();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur archivage');
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Collaborateurs</h2>
          <p className="page__lead">Referentiel : le compte mobile est cree en arriere-plan ; le collaborateur recoit un courriel si SMTP est configure.</p>
        </div>
        <div className="tabs">
          <button type="button" className={'tab' + (tab === 'liste' ? ' tab--on' : '')} onClick={() => setTab('liste')}>
            Liste
          </button>
          <button type="button" className={'tab' + (tab === 'creer' ? ' tab--on' : '')} onClick={() => setTab('creer')}>
            Creer
          </button>
        </div>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      {tab === 'liste' ? (
        <div className="panel">
          {loading ? (
            <p className="muted">Chargement</p>
          ) : (
            <>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Matricule</th>
                      <th>Nom</th>
                      <th>Email</th>
                      <th>Statut</th>
                      <th>Compte</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.identifiant}>
                        <td className="mono">{r.matricule}</td>
                        <td>
                          {r.prenom} {r.nom}
                        </td>
                        <td className="muted">{r.courriel_professionnel ?? '-'}</td>
                        <td>{r.statut}</td>
                        <td>{r.compte_utilisateur_id ? <span className="pill pill--ok">Lie</span> : '-'}</td>
                        <td>
                          <div className="page__head-actions">
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              onClick={() => archiveCollaborateur(r)}
                              disabled={r.statut === 'ARCHIVE'}
                            >
                              Archiver
                            </button>
                            <button
                              type="button"
                              className="btn btn--secondary btn--sm"
                              title="Endpoint changement mot de passe a connecter cote backend."
                              disabled
                            >
                              Changer mot de passe
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="pager">
                <button type="button" className="btn btn--ghost" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                  Prec.
                </button>
                <span className="muted small">
                  {page + 1} / {totalPages} ({total})
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
          )}
        </div>
      ) : (
        <form className="panel panel--form" onSubmit={onCreate}>
          <div className="form-grid">
            <div>
              <label className="field-label">Matricule</label>
              <input className="field-input" value={matricule} onChange={(e) => setMatricule(e.target.value)} required />
            </div>
            <div>
              <label className="field-label">Prenom</label>
              <input className="field-input" value={prenom} onChange={(e) => setPrenom(e.target.value)} required />
            </div>
            <div>
              <label className="field-label">Nom</label>
              <input className="field-input" value={nom} onChange={(e) => setNom(e.target.value)} required />
            </div>
            <div>
              <label className="field-label">Courriel</label>
              <input className="field-input" type="email" value={courriel} onChange={(e) => setCourriel(e.target.value)} required />
            </div>
            <div>
              <label className="field-label">Statut</label>
              <select className="field-input" value={statut} onChange={(e) => setStatut(e.target.value)} required>
                <option value="ACTIF">ACTIF</option>
                <option value="SUSPENDU">SUSPENDU</option>
                <option value="ARCHIVE">ARCHIVE</option>
              </select>
            </div>
            <div>
              <label className="field-label">Unite</label>
              <select className="field-input" value={uniteId} onChange={(e) => setUniteId(e.target.value)} required>
                {unites.map((u) => (
                  <option key={u.identifiant} value={u.identifiant}>
                    {u.code} - {u.libelle}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="field-label">Poste</label>
              <input className="field-input" value={posteLibelle} onChange={(e) => setPosteLibelle(e.target.value)} />
            </div>
            <div>
              <label className="field-label">Fonction</label>
              <input className="field-input" value={fonction} onChange={(e) => setFonction(e.target.value)} />
            </div>

            <div>
              <label className="field-label">Date recrutement</label>
              <input
                className="field-input"
                type="date"
                value={dateRecrutement}
                onChange={(e) => setDateRecrutement(e.target.value)}
              />
            </div>

            <div>
              <label className="field-label">Mot de passe initial</label>
              <input
                className="field-input"
                type="password"
                minLength={8}
                value={motDePasseInitial}
                onChange={(e) => setMotDePasseInitial(e.target.value)}
                required
              />
            </div>
          </div>
          <button type="submit" className="btn btn--primary" disabled={saving}>
            {saving ? '...' : 'Creer'}
          </button>
        </form>
      )}
    </div>
  );
}
