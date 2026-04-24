import { FormEvent, useEffect, useState } from 'react';
import { getUnites, postUnite, type Unite } from '../api/rhClient';

export default function UnitesOrganisationPage() {
  const [rows, setRows] = useState<Unite[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [libelle, setLibelle] = useState('');
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getUnites());
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
      setMsg('Unite creee.');
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
          <h2 className="page__title">Unites organisationnelles</h2>
          <p className="page__lead">Structure services / directions - referentiel RH.</p>
        </div>
      </div>
      {err ? <div className="alert alert--error">{err}</div> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      <div className="panel panel--form">
        <h3 className="panel__title">Nouvelle unite</h3>
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
            placeholder="Libelle"
            value={libelle}
            onChange={(e) => setLibelle(e.target.value)}
            required
          />
          <button type="submit" className="btn btn--primary" disabled={saving}>
            Ajouter
          </button>
        </form>
      </div>

      <div className="panel">
        <h3 className="panel__title">Liste</h3>
        {loading ? (
          <p className="muted">Chargement</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Libelle</th>
                  <th>Actif</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((u) => (
                  <tr key={u.identifiant}>
                    <td className="mono">{u.code}</td>
                    <td>{u.libelle}</td>
                    <td>{u.actif ? 'Oui' : 'Non'}</td>
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
