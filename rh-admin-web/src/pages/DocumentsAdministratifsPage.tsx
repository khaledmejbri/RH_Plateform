import { useEffect, useState } from 'react';
import {
  getDocumentsFileAttente,
  postDocumentDisponible,
  postPrendreProchaineDocument,
  postDocumentRejet,
  type DemandeDocument,
} from '../api/rhClient';

export default function DocumentsAdministratifsPage() {
  const [rows, setRows] = useState<DemandeDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [pickedDemande, setPickedDemande] = useState<DemandeDocument | null>(null);
  const [showConfirmNext, setShowConfirmNext] = useState(false);
  const [confirmReason, setConfirmReason] = useState('');
  const [ref, setRef] = useState('');
  const [comment, setComment] = useState('');

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getDocumentsFileAttente());
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function prendreProchaine() {
    setMsg(null);
    setErr(null);
    try {
      const d = await postPrendreProchaineDocument();
      setMsg(`Demande prise en charge : ${d.identifiant} (${d.type_document})`);
      setPickedDemande(d);
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    }
  }

  async function handlePrendreProchaine() {
    if (rows.some((d) => d.statut === 'EN_TRAITEMENT_RH')) {
      setErr(null);
      setMsg(null);
      setShowConfirmNext(true);
      return;
    }
    await prendreProchaine();
  }

  async function confirmPrendreProchaine() {
    if (!confirmReason.trim()) {
      setErr('Veuillez expliquer pourquoi cette nouvelle demande doit être priorisée.');
      return;
    }
    setErr(null);
    setMsg(`Nouvelle demande priorisée : ${confirmReason.trim()}`);
    setShowConfirmNext(false);
    setConfirmReason('');
    await prendreProchaine();
  }

  async function marquerDisponible() {
    if (!pickedDemande || !ref.trim()) return;
    setErr(null);
    try {
      await postDocumentDisponible(pickedDemande.identifiant, ref.trim(), comment.trim() || undefined);
      setPickedDemande(null);
      setRef('');
      setComment('');
      setMsg('Document marqué comme disponible.');
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    }
  }

  async function rejeterDemande() {
    if (!pickedDemande || !comment.trim()) {
      setErr('Veuillez saisir un motif de refus dans le champ commentaire.');
      return;
    }
    setErr(null);
    try {
      await postDocumentRejet(pickedDemande.identifiant, comment.trim());
      setPickedDemande(null);
      setComment('');
      setMsg('Demande rejetée avec succès.');
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    }
  }

  function formatDate(iso: string) {
    try {
      return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
    } catch {
      return iso;
    }
  }

  return (
    <div className="page">
      <div className="page__head">
        <div>
          <h2 className="page__title">Documents administratifs</h2>
          <p className="page__lead">
            File d’attente unique (FIFO) : attestations de travail, bulletins de paie, notes administratives — aligné
            processus RRH.
          </p>
        </div>
        <div className="page__head-actions">
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            Actualiser
          </button>
          <button type="button" className="btn btn--primary" onClick={() => void handlePrendreProchaine()}>
            Prendre la prochaine demande
          </button>
        </div>
      </div>

      {err ? <div className="alert alert--error">{err}</div> : null}
      {msg ? <div className="alert alert--success">{msg}</div> : null}

      <div className="panel">
        {loading ? (
          <p className="muted">Chargement…</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Rang</th>
                  <th>Type</th>
                  <th>Statut</th>
                  <th>SLA</th>
                  <th>Retard</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.identifiant} className={d.statut === 'EN_TRAITEMENT_RH' ? 'table-row--highlight' : ''}>
                    <td>{d.rang_dans_file ?? (d.statut === 'EN_TRAITEMENT_RH' ? 'En cours' : '—')}</td>
                    <td>
                      <span className="tag">{d.type_document}</span>
                    </td>
                    <td>
                      {d.statut === 'EN_TRAITEMENT_RH' ? (
                        <span className="pill pill--primary">En traitement RH</span>
                      ) : (
                        d.statut
                      )}
                    </td>
                    <td className="muted">{d.delai_sla_heures} h</td>
                    <td>{d.en_retard ? <span className="pill pill--danger">Oui</span> : '—'}</td>
                    <td>
                      {d.statut === 'EN_TRAITEMENT_RH' ? (
                        <button type="button" className="btn btn--sm btn--primary" onClick={() => setPickedDemande(d)}>
                          Valider la demande
                        </button>
                      ) : (
                        <span className="muted small">En attente...</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 ? <p className="muted pad">File d’attente vide.</p> : null}
          </div>
        )}
      </div>

      {showConfirmNext ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setShowConfirmNext(false)}>
          <div className="modal" role="dialog" onClick={(e) => e.stopPropagation()}>
            <h3 className="modal__title">Demande en cours</h3>
            <p className="muted">
              Il y a déjà une demande en cours de traitement RH. Avant de prendre une nouvelle demande, indiquez pourquoi
              elle doit être priorisée.
            </p>
            <label className="field-label">Motif de priorité *</label>
            <textarea
              className="field-input field-input--area"
              rows={4}
              value={confirmReason}
              onChange={(e) => setConfirmReason(e.target.value)}
              placeholder="Ex. urgence métier, dossier impacté, demande urgente du manager…"
            />
            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setShowConfirmNext(false)}>
                Annuler
              </button>
              <button type="button" className="btn btn--primary" disabled={!confirmReason.trim()} onClick={() => void confirmPrendreProchaine()}>
                Confirmer et prendre la prochaine
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {pickedDemande ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setPickedDemande(null)}>
          <div className="modal" role="dialog" onClick={(e) => e.stopPropagation()}>
            <h3 className="modal__title">Détails de la demande</h3>
            <p className="muted small">ID demande : {pickedDemande.identifiant}</p>
            <div className="modal__section">
              <p>
                <strong>Type de document :</strong> {pickedDemande.type_document}
              </p>
              <p>
                <strong>Qui a envoyé :</strong> {pickedDemande.demandeur_identifiant}
              </p>
              <p>
                <strong>Département :</strong> {pickedDemande.departement_libelle ?? 'Non renseigné'}
              </p>
              <p>
                <strong>Envoyé le :</strong> {formatDate(pickedDemande.cree_le)}
              </p>
              <p>
                <strong>Motif de demande :</strong> {pickedDemande.commentaire_demandeur ?? '—'}
              </p>
            </div>
            <label className="field-label">Référence livrable *</label>
            <input
              className="field-input"
              value={ref}
              onChange={(e) => setRef(e.target.value)}
              placeholder="Ex. chemin S3, numéro de document…"
            />
            <label className="field-label">Commentaire RH</label>
            <textarea
              className="field-input field-input--area"
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
            <div className="modal__actions">
              <button type="button" className="btn btn--secondary" disabled={!comment.trim()} onClick={() => void rejeterDemande()}>
                Refuser la demande
              </button>
              <div style={{ flex: 1 }} />
              <button type="button" className="btn btn--ghost" onClick={() => setPickedDemande(null)}>
                Fermer
              </button>
              <button type="button" className="btn btn--primary" disabled={!ref.trim()} onClick={() => void marquerDisponible()}>
                Valider la demande
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
