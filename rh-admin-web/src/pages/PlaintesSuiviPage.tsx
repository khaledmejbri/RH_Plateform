import { useEffect, useMemo, useState } from 'react';
import { getPlaintesRh, getTousCollaborateurs, getUnites, patchPlainteStatut, type CollaborateurRow, type PlainteRh, type Unite } from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';

import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { appartientSelection, correspond, indexerUnites, selectionActive, useFiltresUrl } from '../utils/filtres';
import { DialogPanel } from '../components/useDialogA11y';
import { Chip, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge } from '../components/ui';
import { formatDateHeure, formatPersonne } from '../utils/format';
import { libelle, libelleConnu } from '../utils/libelles';
import DetailLink, { LienPersonne } from '../detail/DetailLink';

/**
 * CDC v2 §M04 corrections :
 * - Filtres par type (INTERNE / EXTERNE) et statut
 * - Affichage numero_ticket, pieces_jointes, log_actions
 * - Transitions statut validées (choix limités selon statut courant)
 * - Commentaire RH obligatoire affiché si RESOLU ou FERME
 * - Badge INTERNE / EXTERNE avec couleur distincte
 */

type TypePlainte = 'INTERNE' | 'EXTERNE' | '';
type StatutPlainte = 'OUVERTES' | 'NOUVEAU' | 'EN_ANALYSE' | 'EN_TRAITEMENT' | 'RESOLU' | 'FERME' | '';

/** CDC §M04 : transitions autorisées côté front (miroir de la validation back). */
const TRANSITIONS: Record<string, string[]> = {
  NOUVEAU:        ['EN_ANALYSE'],
  EN_ANALYSE:     ['EN_TRAITEMENT'],
  EN_TRAITEMENT:  ['RESOLU'],
  RESOLU:         ['FERME'],
  FERME:          [],
};

const FILTRES_DEFAUT = { q: '', direction: '', departement: '', service: '', type: '', statut: '' };

export default function PlaintesSuiviPage() {
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
  const [rows, setRows]         = useState<PlainteRh[]>([]);
  const [loading, setLoading]   = useState(true);
  const [err, setErr]           = useState<string | null>(null);
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const filterType = f.type as TypePlainte;
  const filterStatut = f.statut as StatutPlainte;
  const [unites, setUnites] = useState<Unite[]>([]);
  const [collabs, setCollabs] = useState<CollaborateurRow[]>([]);
  const [collabsErr, setCollabsErr] = useState(false);
  useEffect(() => {
    void getUnites().then(setUnites).catch(() => {});
    void getTousCollaborateurs().then(setCollabs).catch(() => setCollabsErr(true));
  }, []);

  const [edit, setEdit]         = useState<PlainteRh | null>(null);
  /** Détail consultable par tous (Direction, plaintes clôturées) ; formulaire de transition seulement si RH + transition possible. */
  const peutTraiter = !!edit && !lectureSeule && (TRANSITIONS[edit.statut]?.length ?? 0) > 0;
  const [newStatut, setNewStatut] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [saving, setSaving]     = useState(false);
  const [saveErr, setSaveErr]   = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setErr(null);
    try {
      setRows(await getPlaintesRh(filterType || undefined, filterStatut && filterStatut !== 'OUVERTES' ? filterStatut : undefined));
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Erreur chargement');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [filterType, filterStatut]);

  const indexUnites = useMemo(() => indexerUnites(unites), [unites]);
  const uniteAuteur = useMemo(
    () => new Map(collabs.map((c) => [c.identifiant, c.unite?.identifiant])),
    [collabs],
  );
  // Type et statut : serveur ; recherche et unité de l auteur : client.
  const affichees = useMemo(
    () =>
      rows.filter(
        (p) =>
          (filterStatut !== 'OUVERTES' || p.statut !== 'FERME') &&
          correspond([p.auteur_nom, p.auteur_matricule, p.numero_ticket, p.titre, p.description], f.q) &&
          (collabsErr || !selectionActive(f) || appartientSelection(indexUnites, uniteAuteur.get(p.auteur_identifiant), f)),
      ),
    [rows, f, indexUnites, uniteAuteur, collabsErr],
  );

  function openEdit(p: PlainteRh) {
    const opts = TRANSITIONS[p.statut] ?? [];
    setEdit(p);
    setNewStatut(opts[0] ?? p.statut);
    setCommentaire(p.commentaire_rh ?? '');
    setSaveErr(null);
  }

  async function saveEdit() {
    if (!edit) return;
    const commentaireRequired = newStatut === 'RESOLU' || newStatut === 'FERME';
    if (commentaireRequired && !commentaire.trim()) {
      setSaveErr('Un commentaire RH est obligatoire pour ce statut.');
      return;
    }
    const ok = await confirm({
      message: `Passer la plainte ${edit.numero_ticket} (« ${edit.titre} ») au statut « ${libelle('statut', newStatut)} » ?`,
      confirmLabel: 'Confirmer',
    });
    if (!ok) return;
    setSaving(true);
    setSaveErr(null);
    try {
      await patchPlainteStatut(edit.identifiant, {
        statut: newStatut,
        commentaire_rh: commentaire.trim() || undefined,
      });
      setEdit(null);
      void load();
    } catch (e) {
      setSaveErr(e instanceof Error ? e.message : 'Erreur enregistrement');
    } finally {
      setSaving(false);
    }
  }

  const STATUTS_PLAINTE = ['NOUVEAU', 'EN_ANALYSE', 'EN_TRAITEMENT', 'RESOLU', 'FERME'];

  return (
    <div className="page">
      <PageHeader
        title="Suivi des plaintes"
        lead="Interne → RH uniquement · Externe → RH et services concernés."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <BarreFiltres
        id="plaintes"
        recherche={f.q}
        onRecherche={(v) => filtres.definir({ q: v })}
        libelleRecherche="Auteur ou plainte"
        placeholder="Nom, matricule, numéro, titre…"
        nbResultats={affichees.length}
        nbTotal={rows.length}
        nbActifs={filtres.nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="plaintessuivi-f1"
          libelle="Type"
          valeur={f.type}
          toutesLibelle="Tous les types"
          options={[{ valeur: 'INTERNE', libelle: 'Interne' }, { valeur: 'EXTERNE', libelle: 'Externe' }]}
          onChange={(v) => filtres.definir({ type: v })}
        />
        <ChampSelect
          id="plaintessuivi-f2"
          libelle="Statut"
          valeur={f.statut}
          toutesLibelle="Tous les statuts"
          options={[
            { valeur: 'OUVERTES', libelle: 'Ouvertes (non fermées)' },
            ...STATUTS_PLAINTE.map((v) => ({ valeur: v, libelle: libelle('statut', v) })),
          ]}
          onChange={(v) => filtres.definir({ statut: v })}
        />
        <FiltresUnite idPrefixe="plaintes" unites={unites} selection={f} onChange={(sel) => filtres.definir(sel)} />
      </BarreFiltres>
      {collabsErr ? (
        <div className="alert alert--warning" role="status">
          Liste des collaborateurs indisponible : le filtre par direction, département ou service n'est pas appliqué.
        </div>
      ) : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des plaintes…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="inbox" title="Aucune plainte">Aucune plainte ne correspond à ces filtres.</EmptyState>
        ) : affichees.length === 0 ? (
          <AucunResultat onReinitialiser={filtres.reinitialiser} />
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Plaintes</caption>
              <thead>
                <tr>
                  <th scope="col">Numéro</th>
                  <th scope="col">Date</th>
                  <th scope="col">Type</th>
                  <th scope="col">Auteur</th>
                  <th scope="col">Titre</th>
                  <th scope="col">Pièces jointes</th>
                  <th scope="col">Statut</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {affichees.map((p) => (
                  <tr key={p.identifiant}>
                    <td data-label="Numéro"><span className="cell-meta"><DetailLink type="plainte" id={p.identifiant} libelle={p.numero_ticket} /></span></td>
                    <td data-label="Date" className="cell-meta">{formatDateHeure(p.cree_le)}</td>
                    <td data-label="Type"><TypeChip type={p.type_plainte} /></td>
                    <td data-label="Auteur">
                      <div><LienPersonne id={p.auteur_identifiant} nom={p.auteur_nom} matricule={p.auteur_matricule} /></div>
                    </td>
                    <td data-label="Titre" className="cell-main">
                      <div className="cell-main__title">{p.titre}</div>
                      <div className="cell-main__sub">{p.description}</div>
                    </td>
                    <td data-label="PJ">
                      {(p.pieces_jointes?.length ?? 0) > 0 ? (
                        <span className="cell-meta" title={`${p.pieces_jointes.length} fichier${p.pieces_jointes.length > 1 ? 's' : ''}`}>
                          <Icon name="paperclip" size={14} /> {p.pieces_jointes.length}
                        </span>
                      ) : (
                        <span className="cell-meta">—</span>
                      )}
                    </td>
                    <td data-label="Statut"><StatusBadge statut={p.statut} /></td>
                    <td data-label="Action" className="cell-actions">
                      <button type="button" className="btn btn--sm btn--secondary" onClick={() => openEdit(p)}>
                        {!lectureSeule && (TRANSITIONS[p.statut]?.length ?? 0) > 0 ? 'Traiter' : 'Voir le détail'}
                        <Icon name="chevronRight" size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {edit && (
        <div className="modal-backdrop" role="presentation" onClick={() => setEdit(null)}>
          <DialogPanel label={`Traiter — ${edit.titre}`} className="modal modal--md" onClose={() => setEdit(null)}>
            <div className="modal__head">
              <div>
                <h3 className="modal__title" style={{ margin: 0 }}>{edit.titre}</h3>
                <span className="cell-meta">{edit.numero_ticket}</span>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={() => setEdit(null)}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                <TypeChip type={edit.type_plainte} />
                <StatusBadge statut={edit.statut} />
              </div>

              <dl className="detail-grid">
                <div>
                  <dt>Auteur</dt>
                  <dd>{formatPersonne(edit.auteur_nom, edit.auteur_matricule)}</dd>
                </div>
                <div><dt>Déposée le</dt><dd>{formatDateHeure(edit.cree_le)}</dd></div>
                {edit.type_plainte === 'EXTERNE' ? (
                  <div>
                    <dt>Validation du responsable</dt>
                    <dd><StatusBadge statut={VALIDATION_RO[edit.validation_ro ?? '']?.statut ?? 'EN_ATTENTE_VALIDATION'}
                      label={VALIDATION_RO[edit.validation_ro ?? '']?.label ?? '—'} /></dd>
                  </div>
                ) : null}
              </dl>

              <p className="small" style={{ marginTop: 0 }}>{edit.description}</p>

              {(edit.pieces_jointes?.length ?? 0) > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <p className="section-title">Pièces jointes ({edit.pieces_jointes.length})</p>
                  <div className="attachments">
                    {edit.pieces_jointes.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="attachment">
                        <Icon name="paperclip" size={14} />
                        Fichier {i + 1}
                      </a>
                    ))}
                  </div>
                </div>
              )}

              {edit.transcription_audio && (
                <div className="callout callout--info">
                  <p className="callout__title"><Icon name="mic" size={14} /> Transcription vocale</p>
                  <p>{edit.transcription_audio}</p>
                </div>
              )}

              {peutTraiter ? (<>
              <label htmlFor="plaintessuivi-f3" className="field-label">Nouveau statut</label>
              <select id="plaintessuivi-f3" className="field-input" value={newStatut} onChange={(e) => setNewStatut(e.target.value)}
                aria-describedby="plainte-transition-help">
                {(TRANSITIONS[edit.statut] ?? []).map((s) => (
                  <option key={s} value={s}>{libelle('statut', s)}</option>
                ))}
              </select>
              <p id="plainte-transition-help" className="cell-meta" style={{ marginTop: -8, marginBottom: 14 }}>
                Transition : {libelle('statut', edit.statut)} → {libelle('statut', newStatut)}
              </p>

              <label htmlFor="plaintessuivi-f4" className="field-label">
                Commentaire RH{' '}
                {(newStatut === 'RESOLU' || newStatut === 'FERME')
                  ? <span style={{ color: 'var(--color-destructive)' }}>(obligatoire)</span>
                  : <span className="muted">(optionnel)</span>}
              </label>
              <textarea id="plaintessuivi-f4"
                className="field-input field-input--area"
                rows={4}
                value={commentaire}
                onChange={(e) => setCommentaire(e.target.value)}
                placeholder="Message visible dans le suivi collaborateur…"
                aria-invalid={!!saveErr}
              />
              </>) : null}

              {/* § 9.2 : journal d'actions — qui a changé le statut, quand, commentaire (SCRUM-13). */}
              <div style={{ marginBottom: 8 }}>
                <p className="section-title">Journal</p>
                <ol className="timeline">
                  <li>
                    <strong>Dépôt de la plainte</strong>
                    <span className="timeline__meta">{formatDateHeure(edit.cree_le)}{edit.auteur_nom ? ` · ${edit.auteur_nom}` : ''}</span>
                  </li>
                  {(edit.log_actions ?? []).map((log, i) => (
                    <li key={i}>
                      <strong>
                        {log.action === 'VALIDATION_RO' ? 'Validation du responsable · ' : ''}
                        {log.ancien_statut ? libelle('statut', log.ancien_statut) : 'Création'} → {libelle('statut', log.nouveau_statut)}
                      </strong>
                      <span className="timeline__meta">
                        {log.horodatage ? formatDateHeure(log.horodatage) : ''}
                        {' · '}{log.acteur_nom ?? libelleConnu('acteur', log.acteur_id) ?? 'Acteur non tracé'}
                      </span>
                      {log.commentaire && <div className="muted">{log.commentaire}</div>}
                    </li>
                  ))}
                </ol>
              </div>
            </div>

            {saveErr ? <ErrorAlert>{saveErr}</ErrorAlert> : null}

            <div className="modal__actions">
              <button type="button" className="btn btn--ghost" onClick={() => setEdit(null)}>{peutTraiter ? 'Annuler' : 'Fermer'}</button>
              {peutTraiter ? (
                <button type="button" className="btn btn--primary" disabled={saving} aria-busy={saving} onClick={() => void saveEdit()}>
                  {saving ? 'Enregistrement…' : 'Confirmer la transition'}
                </button>
              ) : null}
            </div>
          </DialogPanel>
        </div>
      )}
    </div>
  );
}

const VALIDATION_RO: Record<string, { statut: string; label: string }> = {
  EN_ATTENTE: { statut: 'EN_ATTENTE_VALIDATION', label: 'En attente du responsable' },
  VALIDEE: { statut: 'VALIDE', label: 'Validée par le responsable' },
  NON_REQUISE: { statut: 'TERMINEE', label: 'Prise en charge directe RH' },
};

function TypeChip({ type }: { type: string }) {
  return type === 'EXTERNE'
    ? <Chip tone="warning" icon="building">Externe</Chip>
    : <Chip tone="info" icon="lock">Interne</Chip>;
}
