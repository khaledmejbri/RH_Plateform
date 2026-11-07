import { FormEvent, useEffect, useState } from 'react';
import { getOrganigramme, getUnites, postUnite, type OrganigrammeNoeud, type Unite } from '../api/rhClient';
import { cheminUnite, indexerUnites } from '../utils/filtres';
import { libelleTypeNoeud, intituleResponsable } from '../utils/libelles';
import { useLectureSeule } from '../auth/useLectureSeule';
import { ErrorAlert, LoadingState } from '../components/ui';

export default function UnitesOrganisationPage() {
  const lectureSeule = useLectureSeule();
  const [rows, setRows] = useState<Unite[]>([]);
  const [responsables, setResponsables] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');
  const [saving, setSaving] = useState(false);

  const index = indexerUnites(rows);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getUnites());
      // Responsables : meilleur effort, la liste reste lisible sans eux.
      void getOrganigramme(true).then((o) => {
        const m = new Map<string, string>();
        const walk = (ns: OrganigrammeNoeud[]) => ns.forEach((n) => {
          if (n.manager) m.set(n.identifiant, `${n.manager.prenom} ${n.manager.nom} (${intituleResponsable(n.type_noeud, n.titre_poste)})`);
          walk(n.enfants);
        });
        walk(o.racines);
        setResponsables(m);
      }).catch(() => {});
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMsg(null);
    setErr(null);
    setSaving(true);
    try {
      await postUnite({ code: code.trim(), libelle: libelle.trim(), actif: true });
      setMsg('Unité créée.');
      setCode('');
      setLibelle('');
      await load();
    } catch (x) {
      setErr(x instanceof Error ? x.message : 'Erreur');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Unités organisationnelles</h2>
          <p className="page__lead">Directions, départements et services de la société.</p>
        </div>
      </div>
      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      {!lectureSeule ? (
      <div className="panel panel--form">
        <h3 className="panel__title">Nouvelle unité</h3>
        <form onSubmit={onSubmit} className="form-inline">
          <input
            className="field-input"
            placeholder="Code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            required
            maxLength={32}
          />
          <input
            className="field-input field-input--grow"
            placeholder="Libellé"
            value={libelle}
            onChange={(e) => setLibelle(e.target.value)}
            required
          />
          <button type="submit" className="btn btn--primary" disabled={saving}>
            Ajouter
          </button>
        </form>
      </div>
      ) : null}

      <div className="panel">
        <h3 className="panel__title">Liste</h3>
        {loading ? (
          <LoadingState label="Chargement des unités…" />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Libellé</th>
                  <th>Type</th>
                  <th>Rattachée à</th>
                  <th>Responsable</th>
                  <th>Statut</th>
                  <th>Code</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => (
                  <tr key={u.identifiant}>
                    <td>{u.libelle}</td>
                    <td>{libelleTypeNoeud(u.type_noeud)}</td>
                    <td>{u.parent_identifiant ? cheminUnite(index, u.parent_identifiant) : '—'}</td>
                    <td>{responsables.get(u.identifiant) ?? '—'}</td>
                    <td>{u.actif ? 'Active' : 'Inactive'}</td>
                    <td className="muted small mono">{u.code}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
