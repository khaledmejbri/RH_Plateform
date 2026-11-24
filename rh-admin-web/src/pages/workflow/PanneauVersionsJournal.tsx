import { useCallback, useEffect, useState } from 'react';
import {
  comparerVersionsWorkflow,
  getJournalModeleWorkflow,
  messageErreurWorkflow,
  type ComparaisonVersions,
  type JournalModele,
  type PageWorkflow,
  type VersionResume,
} from '../../api/workflowApi';
import { ErrorAlert, LoadingState } from '../../components/ui';
import { formatDateHeure } from '../../utils/format';
import {
  libelleActionJournal,
  libelleChampModifie,
  libelleElementComparaison,
  libelleStatutVersion,
  libelleTypeChangementElement,
} from '../../workflow/libelles';

export function PanneauVersions({
  versions,
  versionActiveId,
  onChoisirVersion,
}: {
  versions: VersionResume[];
  versionActiveId: string | null;
  onChoisirVersion: (versionId: string) => void;
}) {
  const [sourceId, setSourceId] = useState('');
  const [cibleId, setCibleId] = useState('');
  const [diff, setDiff] = useState<ComparaisonVersions | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, setEnCours] = useState(false);

  async function comparer() {
    if (!sourceId || !cibleId || sourceId === cibleId) return;
    setEnCours(true);
    setErreur(null);
    try {
      const res = await comparerVersionsWorkflow(sourceId, cibleId);
      setDiff(res);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
      setDiff(null);
    } finally {
      setEnCours(false);
    }
  }

  return (
    <div className="card">
      <h3 style={{ marginBottom: 10 }}>Versions du modèle</h3>
      <div className="table-wrap" style={{ marginBottom: 18 }}>
        <table className="table">
          <thead>
            <tr>
              <th>Version</th>
              <th>Statut</th>
              <th>Modifiée le</th>
              <th>Auteur</th>
              <th>Publiée le</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id}>
                <td><strong>v{v.numero}</strong></td>
                <td>
                  <span
                    className={`status status--${
                      v.statut === 'PUBLIEE' ? 'success' : v.statut === 'BROUILLON' ? 'warning' : 'neutral'
                    }`}
                  >
                    {libelleStatutVersion(v.statut)}
                  </span>
                </td>
                <td>{formatDateHeure(v.modifie_le)}</td>
                <td>{v.modifie_par_nom ?? '—'}</td>
                <td>{v.publie_le ? formatDateHeure(v.publie_le) : '—'}</td>
                <td>
                  <button
                    type="button"
                    className="btn btn--secondary btn--sm"
                    disabled={v.id === versionActiveId}
                    onClick={() => onChoisirVersion(v.id)}
                  >
                    {v.id === versionActiveId ? 'Ouverte' : 'Ouvrir'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {versions.length >= 2 ? (
        <section>
          <h4 style={{ marginBottom: 8 }}>Comparer deux versions</h4>
          {erreur ? <ErrorAlert>{erreur}</ErrorAlert> : null}
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'end', marginBottom: 12 }}>
            <div>
              <label className="field-label" htmlFor="cmp-source">Version source</label>
              <select
                id="cmp-source"
                className="field-input"
                value={sourceId}
                onChange={(e) => setSourceId(e.target.value)}
              >
                <option value="">Choisir…</option>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>v{v.numero} ({libelleStatutVersion(v.statut)})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="field-label" htmlFor="cmp-cible">Version cible</label>
              <select
                id="cmp-cible"
                className="field-input"
                value={cibleId}
                onChange={(e) => setCibleId(e.target.value)}
              >
                <option value="">Choisir…</option>
                {versions.map((v) => (
                  <option key={v.id} value={v.id}>v{v.numero} ({libelleStatutVersion(v.statut)})</option>
                ))}
              </select>
            </div>
            <button
              type="button"
              className="btn btn--primary"
              disabled={!sourceId || !cibleId || sourceId === cibleId || enCours}
              onClick={() => void comparer()}
            >
              {enCours ? 'Comparaison…' : 'Comparer'}
            </button>
          </div>

          {diff ? (
            diff.etapes.length === 0 ? (
              <p className="muted">Aucune différence entre ces deux versions.</p>
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Étape</th>
                      <th>Changement</th>
                      <th>Champs modifiés</th>
                    </tr>
                  </thead>
                  <tbody>
                    {diff.etapes.map((d) => (
                      <tr key={d.code}>
                        <td><strong>{libelleElementComparaison(d.code)}</strong></td>
                        <td>{libelleTypeChangementElement(d.code, d.type_changement)}</td>
                        <td>{d.champs_modifies.length > 0 ? d.champs_modifies.map(libelleChampModifie).join(', ') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

export function PanneauJournalModele({ modeleId }: { modeleId: string }) {
  const [page, setPage] = useState(0);
  const [donnees, setDonnees] = useState<PageWorkflow<JournalModele> | null>(null);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);

  const charger = useCallback(async (p: number) => {
    setChargement(true);
    setErreur(null);
    try {
      const res = await getJournalModeleWorkflow(modeleId, p, 20);
      setDonnees(res);
      setPage(p);
    } catch (e) {
      setErreur(messageErreurWorkflow(e));
    } finally {
      setChargement(false);
    }
  }, [modeleId]);

  useEffect(() => {
    void charger(0);
  }, [charger]);

  if (chargement) return <div className="card"><LoadingState /></div>;
  if (erreur) {
    return (
      <div className="card">
        <ErrorAlert>
          {erreur}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger(page)}>
            Réessayer
          </button>
        </ErrorAlert>
      </div>
    );
  }
  if (!donnees || donnees.elements.length === 0) {
    return <div className="card"><p className="muted">Aucune entrée dans le journal de ce modèle.</p></div>;
  }

  const totalPages = Math.max(1, Math.ceil(donnees.total / donnees.taille));

  return (
    <div className="card">
      <h3 style={{ marginBottom: 10 }}>Journal du modèle</h3>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Action</th>
              <th>Auteur</th>
            </tr>
          </thead>
          <tbody>
            {donnees.elements.map((j) => (
              <tr key={j.id}>
                <td>{formatDateHeure(j.horodatage)}</td>
                <td>{libelleActionJournal(j.action)}</td>
                <td>{j.auteur_nom ?? 'Système'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 ? (
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}>
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            disabled={page === 0}
            onClick={() => void charger(page - 1)}
          >
            Précédent
          </button>
          <span className="small muted">Page {page + 1} / {totalPages}</span>
          <button
            type="button"
            className="btn btn--secondary btn--sm"
            disabled={page + 1 >= totalPages}
            onClick={() => void charger(page + 1)}
          >
            Suivant
          </button>
        </div>
      ) : null}
    </div>
  );
}
