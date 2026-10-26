import { useEffect, useState } from 'react';
import {
  getDemandeAdministrativeHistorique,
  getDemandeAdministrativeSuivi,
  getDemandesAdministrativesListe,
  postDemandeAdministrativeRefuserRrh,
  postDemandeAdministrativeValiderRrh,
  type DemandeAdministrative,
  type DemandeAdministrativeHistorique,
  type DemandeAdministrativeSuivi,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { DialogPanel } from '../components/useDialogA11y';
import { Avatar, Chip, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, formatDateTime, type IconName, type Tone } from '../components/ui';

/** Par défaut la RH arrive sur sa file : demandes en attente de décision RRH (§ 6.1). */
type StatutFilter = '' | 'EN_VALIDATION_SUPERIEUR' | 'EN_VALIDATION_RRH' | 'APPROUVEE' | 'REFUSEE' | 'ANNULEE';

const ACTION_LIBELLES: Record<string, string> = {
  CREATION_DEMANDE: 'Création de la demande',
  SOUMISE_A_RO: 'Soumise au responsable',
  VALIDATION_RO: 'Validée par le responsable',
  REFUS_RO: 'Refusée par le responsable',
  VALIDATION_RRH: 'Transmise à la RRH',
  REFUS_RRH: 'Refusée par la RRH',
  APPROBATION_RRH: 'Approuvée par la RRH',
  ANNULATION_DEMANDEUR: 'Annulée par le demandeur',
};

export default function DemandesAdministrativesPage() {
  const lectureSeule = useLectureSeule();
  const [rows, setRows] = useState<DemandeAdministrative[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [statut, setStatut] = useState<StatutFilter>('EN_VALIDATION_RRH');
  const [typeDemande, setTypeDemande] = useState('');

  const [selected, setSelected] = useState<DemandeAdministrative | null>(null);
  const [suivi, setSuivi] = useState<DemandeAdministrativeSuivi | null>(null);
  const [historique, setHistorique] = useState<DemandeAdministrativeHistorique[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailErr, setDetailErr] = useState<string | null>(null);

  const [action, setAction] = useState<'approuver' | 'refuser' | null>(null);
  const [motif, setMotif] = useState('');
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getDemandesAdministrativesListe({
        statut: statut || undefined,
        type_demande: typeDemande || undefined,
      }));
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [statut, typeDemande]);

  async function openDetail(d: DemandeAdministrative) {
    setSelected(d);
    setSuivi(null);
    setHistorique([]);
    setDetailErr(null);
    setAction(null);
    setMsg(null);
    setDetailLoading(true);
    try {
      const [s, h] = await Promise.all([
        getDemandeAdministrativeSuivi(d.identifiant),
        getDemandeAdministrativeHistorique(d.identifiant),
      ]);
      setSuivi(s);
      setHistorique(h);
    } catch (e) {
      setDetailErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setDetailLoading(false);
    }
  }

  function closeDetail() {
    setSelected(null);
    setAction(null);
  }

  function openAction(kind: 'approuver' | 'refuser') {
    setAction(kind);
    setMotif('');
    setActionErr(null);
  }

  async function confirmAction() {
    if (!selected || !action) return;
    const texte = motif.trim();
    if (action === 'refuser' && !texte) {
      setActionErr('Le motif de refus est obligatoire.');
      return;
    }
    setActing(true);
    setActionErr(null);
    try {
      if (action === 'approuver') {
        await postDemandeAdministrativeValiderRrh(selected.identifiant);
        setMsg('Demande approuvée. Le demandeur est notifié.');
      } else {
        await postDemandeAdministrativeRefuserRrh(selected.identifiant, texte);
        setMsg('Demande refusée. Le demandeur est notifié.');
      }
      setSelected(null);
      setAction(null);
      await load();
    } catch (e) {
      setActionErr(e instanceof Error ? e.message : 'Erreur');
    } finally {
      setActing(false);
    }
  }

  function formatTypeDemande(type: string): string {
    if (type === 'AUTORISATION_SORTIE') return 'Autorisation de sortie';
    if (type === 'CONGE') return 'Congé';
    if (type === 'ORDRE_MISSION') return 'Ordre de mission';
    return type.replaceAll('_', ' ').toLowerCase().split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
  }

  function formatTypeConge(code: string | undefined): string | null {
    if (!code) return null;
    const map: Record<string, string> = {
      ANNUEL: 'Annuel',
      MALADIE: 'Maladie',
      MATERNITE: 'Maternité',
      SANS_SOLDE: 'Sans solde',
      AUTRE: 'Autre',
    };
    return map[code.toUpperCase()] ?? code;
  }

  function typeChip(type: string): { tone: Tone; icon: IconName } {
    if (type === 'AUTORISATION_SORTIE') return { tone: 'info', icon: 'clock' };
    if (type === 'CONGE') return { tone: 'warning', icon: 'calendar' };
    return { tone: 'success', icon: 'clipboard' };
  }

  function formatPeriode(d: DemandeAdministrative): string {
    if (d.type_demande === 'AUTORISATION_SORTIE') {
      const c = d.contenu as { date_jour?: string; heure_debut?: string; heure_fin?: string } | undefined;
      if (c?.date_jour) {
        const heures = c.heure_debut && c.heure_fin ? ` · ${c.heure_debut} → ${c.heure_fin}` : '';
        let duree = '';
        if (c.heure_debut && c.heure_fin) {
          const [hd, md] = c.heure_debut.split(':').map(Number);
          const [hf, mf] = c.heure_fin.split(':').map(Number);
          const mins = (hf * 60 + mf) - (hd * 60 + md);
          if (mins > 0) {
            const h = Math.floor(mins / 60), m = mins % 60;
            duree = ` (${h > 0 ? h + 'h' : ''}${m > 0 ? m + 'min' : ''})`;
          }
        }
        return `${c.date_jour}${heures}${duree}`;
      }
    }
    if (d.periode_debut && d.periode_fin) return `${d.periode_debut} → ${d.periode_fin}`;
    if (d.periode_debut) return d.periode_debut;
    return '-';
  }

  /** Champs saisis par le demandeur, hors champs déjà rendus (type de congé, période). */
  function contenuLignes(d: DemandeAdministrative): Array<[string, string]> {
    const c = (d.contenu ?? {}) as Record<string, unknown>;
    const libelles: Record<string, string> = {
      motif: 'Motif',
      lieu: 'Lieu',
      certificat: 'Certificat',
      pieces_jointes: 'Pièces jointes',
    };
    return Object.entries(libelles)
      .filter(([cle]) => c[cle] != null && c[cle] !== '' && !(Array.isArray(c[cle]) && (c[cle] as unknown[]).length === 0))
      .map(([cle, libelle]) => [libelle, Array.isArray(c[cle]) ? (c[cle] as unknown[]).join(', ') : String(c[cle])]);
  }

  const peutDecider = !lectureSeule && selected?.statut === 'EN_VALIDATION_RRH';

  return (
    <div className="page">
      <PageHeader
        title="Demandes administratives"
        lead="Congés, autorisations de sortie et ordres de mission : décision RRH après le responsable."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      <div className="toolbar-filters">
        <div>
          <label htmlFor="demandes-statut" className="field-label">Statut</label>
          <select id="demandes-statut" className="field-input" value={statut} onChange={(e) => setStatut(e.target.value as StatutFilter)}>
            <option value="">Tous les statuts</option>
            <option value="EN_VALIDATION_RRH">À décider (validation RRH)</option>
            <option value="EN_VALIDATION_SUPERIEUR">Chez le responsable</option>
            <option value="APPROUVEE">Approuvées</option>
            <option value="REFUSEE">Refusées</option>
            <option value="ANNULEE">Annulées</option>
          </select>
        </div>
        <div>
          <label htmlFor="demandes-type" className="field-label">Type</label>
          <select id="demandes-type" className="field-input" value={typeDemande} onChange={(e) => setTypeDemande(e.target.value)}>
            <option value="">Tous les types</option>
            <option value="CONGE">Congé</option>
            <option value="AUTORISATION_SORTIE">Autorisation de sortie</option>
            <option value="ORDRE_MISSION">Ordre de mission</option>
          </select>
        </div>
      </div>

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des demandes…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="clipboard" title="Aucune demande administrative">
            Aucune demande ne correspond à ces filtres.
          </EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Liste des demandes administratives</caption>
              <thead>
                <tr>
                  <th scope="col">Créée le</th>
                  <th scope="col">Type</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Période</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => {
                  const chip = typeChip(d.type_demande);
                  const typeConge =
                    d.type_demande === 'CONGE'
                      ? formatTypeConge((d.contenu as { type_conge?: string } | undefined)?.type_conge)
                      : null;
                  return (
                    <tr key={d.identifiant}>
                      <td data-label="Créée le" className="cell-meta">{formatDateTime(d.cree_le)}</td>
                      <td data-label="Type">
                        <Chip tone={chip.tone} icon={chip.icon}>
                          {formatTypeDemande(d.type_demande)}
                          {typeConge ? <span className="muted">· {typeConge}</span> : null}
                        </Chip>
                      </td>
                      <td data-label="Statut">
                        <StatusBadge statut={d.statut} />
                      </td>
                      <td data-label="Période" className="cell-meta">{formatPeriode(d)}</td>
                      <td data-label="Demandeur">
                        <span className="cell-user">
                          <Avatar name={d.demandeur_nom ?? d.demandeur_identifiant} size={28} />
                          <span>
                            <div>{d.demandeur_nom ?? '—'}</div>
                            <div className="cell-meta">
                              {[d.demandeur_matricule, d.demandeur_unite].filter(Boolean).join(' · ')}
                            </div>
                          </span>
                        </span>
                      </td>
                      <td data-label="Actions" style={{ textAlign: 'right' }}>
                        <button type="button" className="btn btn--sm btn--secondary" onClick={() => void openDetail(d)}>
                          {!lectureSeule && d.statut === 'EN_VALIDATION_RRH' ? 'Traiter' : 'Voir le détail'}
                          <Icon name="chevronRight" size={14} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && (
        <div className="modal-backdrop" role="presentation" onClick={closeDetail}>
          <DialogPanel label="Détail de la demande administrative" className="modal modal--md" onClose={closeDetail}>
            <div className="modal__head">
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>{formatTypeDemande(selected.type_demande)}</h3>
                <code className="cell-code">{selected.identifiant.slice(0, 8)}…</code>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={closeDetail}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <dl className="detail-grid">
                <div><dt>Statut</dt><dd><StatusBadge statut={selected.statut} /></dd></div>
                <div><dt>Période</dt><dd>{formatPeriode(selected)}</dd></div>
                {selected.type_demande === 'CONGE' ? (
                  <div>
                    <dt>Type de congé</dt>
                    <dd>{formatTypeConge((selected.contenu as { type_conge?: string } | undefined)?.type_conge) ?? '—'}</dd>
                  </div>
                ) : null}
                <div>
                  <dt>Demandeur</dt>
                  <dd>
                    {selected.demandeur_nom ?? '—'}
                    {selected.demandeur_matricule ? <span className="cell-meta"> · {selected.demandeur_matricule}</span> : null}
                  </dd>
                </div>
                <div><dt>Unité</dt><dd>{selected.demandeur_unite ?? '—'}</dd></div>
                <div><dt>Créée le</dt><dd>{formatDateTime(selected.cree_le)}</dd></div>
                {contenuLignes(selected).map(([libelle, valeur]) => (
                  <div key={libelle}><dt>{libelle}</dt><dd>{valeur}</dd></div>
                ))}
              </dl>

              {selected.motif_refus ? (
                <div className="callout">
                  <p className="callout__title">Motif du refus</p>
                  <p>{selected.motif_refus}</p>
                </div>
              ) : null}

              {detailLoading ? <LoadingState label="Chargement du suivi…" /> : null}
              {detailErr ? <ErrorAlert>{detailErr}</ErrorAlert> : null}

              {suivi ? (
                <>
                  <p className="section-title">Suivi</p>
                  {suivi.message_explication ? <p className="small muted" style={{ marginTop: 0 }}>{suivi.message_explication}</p> : null}
                  <ol className="small" style={{ marginTop: 0, paddingLeft: 18 }}>
                    {suivi.etapes.map((e) => (
                      <li key={e.code}>
                        {e.libelle}{' '}
                        {e.terminee ? <StatusBadge statut="VALIDE" label="Terminée" /> : e.en_cours ? <StatusBadge statut="EN_COURS" label="En cours" /> : null}
                      </li>
                    ))}
                  </ol>
                </>
              ) : null}

              {historique.length > 0 ? (
                <>
                  <p className="section-title">Historique</p>
                  <ul className="small" style={{ marginTop: 0, paddingLeft: 18 }}>
                    {historique.map((h) => (
                      <li key={h.identifiant}>
                        <span className="cell-meta">{formatDateTime(h.date_action)}</span>{' — '}
                        {ACTION_LIBELLES[h.action] ?? h.action}
                        {h.acteur_nom ? ` · ${h.acteur_nom}` : ''}
                        {h.commentaire ? <span className="muted"> · « {h.commentaire} »</span> : null}
                      </li>
                    ))}
                  </ul>
                </>
              ) : null}

              {peutDecider && action === 'refuser' ? (
                <>
                  <label htmlFor="demande-motif-refus" className="field-label">Motif du refus (obligatoire)</label>
                  <textarea
                    id="demande-motif-refus"
                    className="field-input field-input--area"
                    rows={3}
                    value={motif}
                    onChange={(e) => setMotif(e.target.value)}
                    placeholder="Expliquez la raison du refus au demandeur…"
                    aria-invalid={!!actionErr}
                    aria-describedby={actionErr ? 'demande-action-err' : undefined}
                    autoFocus
                  />
                </>
              ) : null}
              {peutDecider && action === 'approuver' ? (
                <div className="callout">
                  <p className="callout__title">Confirmer l’approbation</p>
                  <p>La demande passera au statut « Approuvée » et le demandeur sera notifié.</p>
                </div>
              ) : null}
            </div>

            {actionErr ? <p id="demande-action-err" className="alert alert--error" role="alert">{actionErr}</p> : null}

            <div className="modal__actions" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
              {peutDecider && action === null ? (
                <>
                  <button type="button" className="btn btn--ghost" onClick={() => openAction('refuser')}>Refuser</button>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <button type="button" className="btn btn--secondary" onClick={closeDetail}>Fermer</button>
                    <button type="button" className="btn btn--primary" onClick={() => openAction('approuver')}>Approuver</button>
                  </div>
                </>
              ) : peutDecider ? (
                <>
                  <button type="button" className="btn btn--secondary" onClick={() => setAction(null)}>Retour</button>
                  <button
                    type="button"
                    className={`btn ${action === 'refuser' ? 'btn--danger' : 'btn--primary'}`}
                    disabled={acting || (action === 'refuser' && !motif.trim())}
                    aria-busy={acting}
                    onClick={() => void confirmAction()}
                  >
                    {acting ? 'Envoi…' : action === 'refuser' ? 'Confirmer le refus' : 'Confirmer l’approbation'}
                  </button>
                </>
              ) : (
                <button type="button" className="btn btn--secondary" onClick={closeDetail} style={{ marginLeft: 'auto' }}>Fermer</button>
              )}
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}
