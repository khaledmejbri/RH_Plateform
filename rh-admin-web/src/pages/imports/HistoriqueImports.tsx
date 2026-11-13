import { useCallback, useEffect, useState } from 'react';
import {
  getHistorique,
  getLignesHistorique,
  messageImport,
  type ImportResume,
  type LigneHistorique,
} from '../../api/importsApi';
import type { PageContrat } from '../../api/metierApi';
import { ChampSelect } from '../../components/BarreFiltres';
import { Chip, EmptyState, Icon, LoadingState, type Tone } from '../../components/ui';
import { DialogPanel } from '../../components/useDialogA11y';
import { formatDateHeure, TIRET } from '../../utils/format';

const TAILLE = 20;

function resumeTotaux(t: ImportResume['totaux']): string {
  const parts: string[] = [];
  const ajoute = (n: number | null | undefined, label: string) => { if (n !== null && n !== undefined) parts.push(`${n} ${label}`); };
  ajoute(t.creations ?? t.creees, 'créées');
  ajoute(t.mises_a_jour, 'mises à jour');
  ajoute(t.ajustements, 'ajustements');
  ajoute(t.inchangees, 'inchangées');
  ajoute(t.rejetees ?? t.erreurs, 'rejetées');
  return parts.length ? parts.join(' · ') : TIRET;
}

function tone(action: string): Tone {
  if (action === 'REJETEE') return 'danger';
  if (action === 'CREATION') return 'success';
  return 'info';
}

/** Historique des imports confirmés (lecture seule, visible aussi par la DIRECTION). */
export default function HistoriqueImports({ typeInitial = '' }: { typeInitial?: string }) {
  const [type, setType] = useState(typeInitial);
  const [page, setPage] = useState(0);
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; data?: PageContrat<ImportResume>; message?: string }>({ statut: 'chargement' });
  const [detail, setDetail] = useState<ImportResume | null>(null);

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    try {
      setEtat({ statut: 'ok', data: await getHistorique(type, page, TAILLE) });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageImport(e) });
    }
  }, [type, page]);

  useEffect(() => { void charger(); }, [charger]);

  const data = etat.data;
  const pages = data ? Math.max(1, data.total_pages) : 1;

  return (
    <section aria-label="Historique des imports">
      <div style={{ maxWidth: 260 }}>
        <ChampSelect
          id="historique-type"
          libelle="Type d’import"
          valeur={type}
          toutesLibelle="Tous les types"
          options={[{ valeur: 'COLLABORATEURS', libelle: 'Collaborateurs' }, { valeur: 'SOLDES', libelle: 'Soldes' }]}
          onChange={(v) => { setType(v); setPage(0); }}
        />
      </div>
      {etat.statut === 'chargement' ? <LoadingState label="Chargement de l’historique…" /> : null}
      {etat.statut === 'erreur' ? (
        <div className="alert alert--error" role="alert">
          {etat.message}{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
        </div>
      ) : null}
      {data && data.contenu.length === 0 ? (
        <EmptyState icon="file" title="Aucun import">Aucun import n’a encore été confirmé.</EmptyState>
      ) : null}
      {data && data.contenu.length > 0 ? (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Imports confirmés</caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Type</th>
                <th scope="col">Auteur</th>
                <th scope="col">Fichier</th>
                <th scope="col">Résultat</th>
                <th scope="col"><span className="sr-only">Détail</span></th>
              </tr>
            </thead>
            <tbody>
              {data.contenu.map((i) => (
                <tr key={i.import_id}>
                  <td>{formatDateHeure(i.confirme_le)}</td>
                  <td>{i.type_import_libelle}</td>
                  <td>{i.auteur_nom ?? TIRET}</td>
                  <td>{i.nom_fichier}</td>
                  <td>{resumeTotaux(i.totaux)}</td>
                  <td style={{ textAlign: 'right' }}>
                    <button type="button" className="btn btn--sm btn--secondary" onClick={() => setDetail(i)}>
                      Voir le détail <Icon name="chevronRight" size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {data && pages > 1 ? (
        <div className="pager">
          <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
          <span>Page {page + 1} sur {pages}</span>
          <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
        </div>
      ) : null}
      {detail ? <DetailImport resume={detail} onClose={() => setDetail(null)} /> : null}
    </section>
  );
}

function DetailImport({ resume, onClose }: { resume: ImportResume; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const [etat, setEtat] = useState<{ statut: 'chargement' | 'erreur' | 'ok'; data?: PageContrat<LigneHistorique>; message?: string }>({ statut: 'chargement' });

  const charger = useCallback(async () => {
    setEtat((e) => ({ ...e, statut: 'chargement' }));
    try {
      setEtat({ statut: 'ok', data: await getLignesHistorique(resume.import_id, page) });
    } catch (e) {
      setEtat({ statut: 'erreur', message: messageImport(e) });
    }
  }, [resume.import_id, page]);

  useEffect(() => { void charger(); }, [charger]);

  const data = etat.data;
  const pages = data ? Math.max(1, data.total_pages) : 1;

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <DialogPanel label="Détail d’un import" className="modal modal--lg" onClose={onClose}>
        <div className="modal__head">
          <div>
            <h3 className="modal__title" style={{ margin: 0 }}>{resume.type_import_libelle} · {resume.nom_fichier}</h3>
            <p className="cell-meta" style={{ margin: 0 }}>
              {formatDateHeure(resume.confirme_le)} · {resume.auteur_nom ?? TIRET}
              {resume.motif_global ? ` · Motif : ${resume.motif_global}` : ''}
            </p>
          </div>
          <button type="button" className="modal__close" aria-label="Fermer" onClick={onClose}><Icon name="x" size={18} /></button>
        </div>
        <div className="modal__scroll">
          {etat.statut === 'chargement' && !data ? <LoadingState label="Chargement du détail…" /> : null}
          {etat.statut === 'erreur' ? (
            <div className="alert alert--error" role="alert">
              {etat.message}{' '}
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
            </div>
          ) : null}
          {data && data.contenu.length === 0 ? <p className="detail-vide">Aucune ligne enregistrée.</p> : null}
          {data && data.contenu.length > 0 ? (
            <div className="table-wrap">
              <table className="data-table">
                <caption className="sr-only">Lignes de l’import</caption>
                <thead>
                  <tr>
                    <th scope="col">Ligne</th><th scope="col">Matricule</th><th scope="col">Action</th><th scope="col">Détail</th>
                  </tr>
                </thead>
                <tbody>
                  {data.contenu.map((l) => (
                    <tr key={l.numero_ligne}>
                      <td>{l.numero_ligne}</td>
                      <td>{l.matricule ?? TIRET}</td>
                      <td><Chip tone={tone(l.action)}>{l.action_libelle}</Chip></td>
                      <td>
                        {l.erreurs_libelle || l.changements.length > 0 ? (
                          <ul style={{ margin: 0, paddingLeft: '1rem' }}>
                            {l.erreurs_libelle ? <li className="field-error">{l.erreurs_libelle}</li> : null}
                            {l.changements.map((c, i) => (
                              <li key={i}>{c.champ_libelle} : {c.avant ? c.avant : TIRET} → {c.apres ? c.apres : TIRET}</li>
                            ))}
                          </ul>
                        ) : TIRET}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          {data && pages > 1 ? (
            <div className="pager">
              <button type="button" className="btn btn--ghost btn--sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</button>
              <span>Page {page + 1} sur {pages}</span>
              <button type="button" className="btn btn--ghost btn--sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Suivant</button>
            </div>
          ) : null}
        </div>
        <div className="modal__actions">
          <button type="button" className="btn btn--secondary" onClick={onClose}>Fermer</button>
        </div>
      </DialogPanel>
    </div>
  );
}
