import { FormEvent, useCallback, useEffect, useState } from 'react';
import {
  formatDateTunis,
  getDemandesManuelles,
  LIBELLES_DEMANDE_MANUELLE,
  messagePresenceErreur,
  postRefuserDemandeManuelle,
  postValiderDemandeManuelle,
  type DemandeManuelle,
  type StatutDemandeManuelle,
} from '../api/presenceApi';

/** Seuil d'alerte RH (D-P04) — l'API renvoie déjà `alerteSeuil` ; affiché pour l'explication. */
const SEUIL_MENSUEL = 3;

function StatutBadge({ statut }: { statut: StatutDemandeManuelle }) {
  const cls =
    statut === 'VALIDEE' ? 'badge badge--success' : statut === 'REFUSEE' ? 'badge badge--warning' : 'badge badge--default';
  return <span className={cls}>{LIBELLES_DEMANDE_MANUELLE[statut] ?? statut}</span>;
}

/**
 * Onglet RH « Pointages manuels » (D-P04 — SCRUM-38) : toutes les demandes, compteur mensuel par
 * collaborateur avec alerte au-delà de 3, décision RH quand aucun responsable n'est trouvé.
 */
export default function PointagesManuelsPanel({ lectureSeule }: { lectureSeule: boolean }) {
  const [statut, setStatut] = useState<StatutDemandeManuelle | ''>('EN_ATTENTE');
  const [demandes, setDemandes] = useState<DemandeManuelle[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [refusId, setRefusId] = useState<string | null>(null);
  const [motif, setMotif] = useState('');

  const charger = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      setDemandes(await getDemandesManuelles(statut));
    } catch (e) {
      setErr(messagePresenceErreur(e, 'Impossible de charger les demandes'));
    } finally {
      setLoading(false);
    }
  }, [statut]);

  useEffect(() => {
    void charger();
  }, [charger]);

  async function valider(d: DemandeManuelle) {
    setBusyId(d.id);
    setErr(null);
    try {
      const r = await postValiderDemandeManuelle(d.id);
      setMsg(
        `Pointage manuel de ${d.collaborateurNom ?? 'ce collaborateur'} validé` +
          (r.alerteSeuil ? ` — attention : ${r.manuelsDuMois} pointages manuels ce mois-ci.` : '.'),
      );
      await charger();
    } catch (e) {
      setErr(messagePresenceErreur(e));
    } finally {
      setBusyId(null);
    }
  }

  async function refuser(e: FormEvent) {
    e.preventDefault();
    if (!refusId) return;
    setBusyId(refusId);
    setErr(null);
    try {
      await postRefuserDemandeManuelle(refusId, motif.trim());
      setMsg('Pointage manuel refusé.');
      setRefusId(null);
      setMotif('');
      await charger();
    } catch (er) {
      setErr(messagePresenceErreur(er));
    } finally {
      setBusyId(null);
    }
  }

  const alertes = demandes.filter((d) => d.alerteSeuil);

  return (
    <div className="panel">
      <div className="form-grid mb-md">
        <label>
          Statut
          <select value={statut} onChange={(e) => setStatut(e.target.value as StatutDemandeManuelle | '')}>
            <option value="EN_ATTENTE">En attente</option>
            <option value="VALIDEE">Validées</option>
            <option value="REFUSEE">Refusées</option>
            <option value="">Toutes</option>
          </select>
        </label>
      </div>

      {msg ? (
        <div className="alert alert--success" role="status">
          {msg}
        </div>
      ) : null}
      {err ? (
        <div className="alert alert--error" role="alert">
          {err}
        </div>
      ) : null}
      {alertes.length > 0 ? (
        <div className="alert alert--warning" role="status">
          Plus de {SEUIL_MENSUEL} pointages manuels validés ce mois-ci pour :{' '}
          {[...new Set(alertes.map((d) => d.collaborateurNom ?? d.collaborateurId))].join(', ')}.
        </div>
      ) : null}

      {loading ? (
        <p className="muted">Chargement…</p>
      ) : demandes.length === 0 ? (
        <p className="muted">Aucune demande de pointage manuel pour ce filtre.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Collaborateur</th>
                <th>Pointage demandé</th>
                <th>Justification</th>
                <th>Refus automatique</th>
                <th>Valideur</th>
                <th>Statut</th>
                <th>Manuels du mois</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {demandes.map((d) => (
                <tr key={d.id}>
                  <td>{d.collaborateurNom ?? <span className="mono">{d.collaborateurId}</span>}</td>
                  <td>
                    {d.type === 'SORTIE' ? 'Sortie' : 'Entrée'} · {formatDateTunis(d.heureDeclaree)}
                  </td>
                  <td className="small">{d.justification}</td>
                  <td className="muted small">{d.motifRejetInitial ?? '—'}</td>
                  <td className="small">
                    {d.validationParRh ? 'RH (aucun responsable)' : d.valideurNom ?? '—'}
                    {d.decidePar ? <div className="muted">Décidé par {d.decidePar}</div> : null}
                    {d.motifRefus ? <div className="muted">Motif : {d.motifRefus}</div> : null}
                  </td>
                  <td>
                    <StatutBadge statut={d.statut} />
                  </td>
                  <td>
                    {d.manuelsDuMois ?? 0}
                    {d.alerteSeuil ? (
                      <span className="badge badge--warning" style={{ marginLeft: 6 }}>
                        &gt; {SEUIL_MENSUEL}
                      </span>
                    ) : null}
                  </td>
                  <td>
                    {d.statut === 'EN_ATTENTE' && !lectureSeule ? (
                      <div className="page__head-actions">
                        <button
                          type="button"
                          className="btn btn--primary btn--sm"
                          disabled={busyId === d.id}
                          onClick={() => void valider(d)}
                        >
                          Valider
                        </button>
                        <button
                          type="button"
                          className="btn btn--secondary btn--sm"
                          disabled={busyId === d.id}
                          onClick={() => {
                            setRefusId(d.id);
                            setMotif('');
                          }}
                        >
                          Refuser
                        </button>
                      </div>
                    ) : (
                      <span className="muted small">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {refusId ? (
        <form className="panel mt-md" onSubmit={refuser}>
          <label>
            Motif du refus *
            <textarea required maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} rows={3} />
          </label>
          <div className="page__head-actions mt-sm">
            <button type="button" className="btn btn--ghost" onClick={() => setRefusId(null)}>
              Annuler
            </button>
            <button type="submit" className="btn btn--primary" disabled={!motif.trim() || busyId === refusId}>
              Confirmer le refus
            </button>
          </div>
        </form>
      ) : null}
    </div>
  );
}
