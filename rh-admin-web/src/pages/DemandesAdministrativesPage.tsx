import { useEffect, useMemo, useState } from 'react';
import {
  getDemandeAdministrativeHistorique,
  getDemandeAdministrativeSuivi,
  getDemandesAdministrativesListe,
  getUnites,
  postDemandeAdministrativeRefuserRrh,
  postDemandeAdministrativeValiderRrh,
  RhApiError,
  type DemandeAdministrative,
  type DemandeAdministrativeHistorique,
  type DemandeAdministrativeSuivi,
  type Unite,
} from '../api/rhClient';
import { useLectureSeule } from '../auth/useLectureSeule';
import { useConfirm } from '../components/ConfirmDialog';

import BarreFiltres, { AucunResultat, ChampSelect, FiltresUnite } from '../components/BarreFiltres';
import { correspond, libelleDansSelection, unitesRetenues, useFiltresUrl } from '../utils/filtres';
import { DialogPanel } from '../components/useDialogA11y';
import { Avatar, Chip, EmptyState, ErrorAlert, Icon, LoadingState, PageHeader, StatusBadge, type IconName, type Tone } from '../components/ui';
import { formatDateHeure, formatPersonne } from '../utils/format';
import { libelle, libelleConnu, optionsDomaine } from '../utils/libelles';
import { commentaireLisible, contenuLignes, intituleDemande, periodeDemande } from '../utils/demandes';
import DetailLink, { LienPersonne } from '../detail/DetailLink';

/** Par défaut la RH arrive sur sa file : demandes en attente de décision RH (§ 6.1). */
type StatutFilter = '' | 'EN_VALIDATION_SUPERIEUR' | 'EN_VALIDATION_RRH' | 'APPROUVEE' | 'REFUSEE' | 'ANNULEE';

const FILTRES_DEFAUT = { q: '', direction: '', departement: '', service: '', statut: 'EN_VALIDATION_RRH', type: '' };

export default function DemandesAdministrativesPage() {
  const lectureSeule = useLectureSeule();
  const confirm = useConfirm();
  const [rows, setRows] = useState<DemandeAdministrative[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const filtres = useFiltresUrl(FILTRES_DEFAUT);
  const f = filtres.valeurs;
  const statut = f.statut as StatutFilter;
  const typeDemande = f.type;
  const [unites, setUnites] = useState<Unite[]>([]);
  useEffect(() => { void getUnites().then(setUnites).catch(() => {}); }, []);

  const [selected, setSelected] = useState<DemandeAdministrative | null>(null);
  const [suivi, setSuivi] = useState<DemandeAdministrativeSuivi | null>(null);
  const [historique, setHistorique] = useState<DemandeAdministrativeHistorique[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailErr, setDetailErr] = useState<string | null>(null);

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
  }

  /** Une seule fenêtre « Êtes-vous sûr ? » porte le motif, l'envoi et l'erreur (D-L03). */
  async function decider(kind: 'approuver' | 'refuser') {
    if (!selected) return;
    const cible = selected;
    const qui = `${intituleDemande(cible)}${cible.numero ? ` ${cible.numero}` : ''} de ${formatPersonne(cible.demandeur_nom, cible.demandeur_matricule)}`;
    let conflit: string | null = null;
    const ok = await confirm({
      message: kind === 'approuver' ? `Approuver la demande : ${qui} ?` : `Refuser la demande : ${qui} ?`,
      confirmLabel: kind === 'approuver' ? 'Approuver' : 'Refuser',
      danger: kind === 'refuser',
      motif: kind === 'refuser'
        ? { label: 'Motif du refus', obligatoire: true, placeholder: 'Expliquez la raison du refus au demandeur…' }
        : undefined,
      envoyer: async (texte) => {
        try {
          if (kind === 'approuver') await postDemandeAdministrativeValiderRrh(cible.identifiant);
          else await postDemandeAdministrativeRefuserRrh(cible.identifiant, texte);
        } catch (e) {
          if (e instanceof RhApiError && e.status === 409) conflit = e.message;
          throw e;
        }
      },
      echecDefinitif: () => conflit !== null,
    });
    if (conflit) {
      // La demande a changé entre-temps : on referme la fenêtre, on affiche l'erreur et on recharge la liste.
      // load() remet l'erreur à zéro : le message est posé après le rechargement.
      setSelected(null);
      await load();
      setErr(conflit);
      return;
    }
    if (!ok) return;
    setMsg(kind === 'approuver' ? 'Demande approuvée. Le demandeur est notifié.' : 'Demande refusée. Le demandeur est notifié.');
    setSelected(null);
    await load();
  }

  function formatTypeDemande(type: string): string {
    return libelle('typeDemande', type);
  }

  /** « Congé annuel » : le libellé du type de congé est complet. */
  function formatTypeConge(code: string | undefined): string | null {
    return code ? libelle('typeConge', code) : null;
  }



  function typeChip(type: string): { tone: Tone; icon: IconName } {
    if (type === 'AUTORISATION_SORTIE') return { tone: 'info', icon: 'clock' };
    if (type === 'CONGE') return { tone: 'warning', icon: 'calendar' };
    return { tone: 'success', icon: 'clipboard' };
  }



  const retenues = useMemo(() => unitesRetenues(unites, f), [unites, f]);
  // Statut et type sont filtrés par le serveur ; recherche et unité côté client (liste complète renvoyée).
  const affichees = useMemo(
    () =>
      rows.filter(
        (d) =>
          correspond([d.numero, d.demandeur_nom, d.demandeur_matricule, d.demandeur_unite, intituleDemande(d)], f.q) &&
          libelleDansSelection(retenues, d.demandeur_unite),
      ),
    [rows, f.q, retenues],
  );

  const peutDecider = !lectureSeule && selected?.statut === 'EN_VALIDATION_RRH';

  return (
    <div className="page">
      <PageHeader
        title="Demandes administratives"
        lead="Congés, autorisations de sortie et ordres de mission : décision de la RH après le responsable."
        actions={
          <button type="button" className="btn btn--secondary" onClick={() => void load()} disabled={loading}>
            <Icon name="refresh" />
            Actualiser
          </button>
        }
      />

      <BarreFiltres
        id="demandes"
        recherche={f.q}
        onRecherche={(v) => filtres.definir({ q: v })}
        libelleRecherche="Demandeur"
        placeholder="Numéro, nom, matricule, unité…"
        nbResultats={affichees.length}
        nbTotal={rows.length}
        nbActifs={filtres.nbActifs}
        onReinitialiser={filtres.reinitialiser}
      >
        <ChampSelect
          id="demandes-statut"
          libelle="Statut"
          valeur={f.statut}
          toutesLibelle="Tous les statuts"
          options={[
            { valeur: 'EN_VALIDATION_RRH', libelle: 'À décider (validation RH)' },
            { valeur: 'EN_VALIDATION_SUPERIEUR', libelle: 'Chez le responsable' },
            { valeur: 'APPROUVEE', libelle: 'Approuvées' },
            { valeur: 'REFUSEE', libelle: 'Refusées' },
            { valeur: 'ANNULEE', libelle: 'Annulées' },
          ]}
          onChange={(v) => filtres.definir({ statut: v })}
        />
        <ChampSelect
          id="demandes-type"
          libelle="Type"
          valeur={f.type}
          toutesLibelle="Tous les types"
          options={optionsDomaine('typeDemande')}
          onChange={(v) => filtres.definir({ type: v })}
        />
        <FiltresUnite idPrefixe="demandes" unites={unites} selection={f} onChange={(sel) => filtres.definir(sel)} />
      </BarreFiltres>
      {f.statut === FILTRES_DEFAUT.statut ? (
        <p className="muted small" role="status">
          Filtre actif : demandes à décider. Choisissez « Tous les statuts » pour voir l’ensemble des demandes.
        </p>
      ) : null}

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}
      {msg ? <div className="alert alert--success" role="status"><Icon name="check" />{msg}</div> : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des demandes…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="clipboard" title="Aucune demande administrative">
            Aucune demande ne correspond à ces filtres.
          </EmptyState>
        ) : affichees.length === 0 ? (
          <AucunResultat onReinitialiser={filtres.reinitialiser} />
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Liste des demandes administratives</caption>
              <thead>
                <tr>
                  <th scope="col">Numéro</th>
                  <th scope="col">Créée le</th>
                  <th scope="col">Type</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Période</th>
                  <th scope="col">Demandeur</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {affichees.map((d) => {
                  const chip = typeChip(d.type_demande);
                  const typeConge =
                    d.type_demande === 'CONGE'
                      ? formatTypeConge((d.contenu as { type_conge?: string } | undefined)?.type_conge)
                      : null;
                  return (
                    <tr key={d.identifiant}>
                      <td data-label="Numéro" className="cell-meta"><DetailLink type="demande" id={d.identifiant} libelle={d.numero} /></td>
                      <td data-label="Créée le" className="cell-meta">{formatDateHeure(d.cree_le)}</td>
                      <td data-label="Type">
                        <Chip tone={chip.tone} icon={chip.icon}>
                          {typeConge ?? formatTypeDemande(d.type_demande)}
                        </Chip>
                      </td>
                      <td data-label="Statut">
                        <StatusBadge statut={d.statut} />
                      </td>
                      <td data-label="Période" className="cell-meta">{periodeDemande(d)}</td>
                      <td data-label="Demandeur">
                        <span className="cell-user">
                          <Avatar name={d.demandeur_nom ?? '?'} size={28} />
                          <span>
                            <div><LienPersonne id={d.demandeur_identifiant} nom={d.demandeur_nom} matricule={d.demandeur_matricule} /></div>
                            <div className="cell-meta">{d.demandeur_unite ?? ''}</div>
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
                <h3 className="modal__title" style={{ margin: 0 }}>{intituleDemande(selected)}</h3>
                <p className="cell-meta" style={{ margin: 0 }}>
                  {[selected.numero, formatPersonne(selected.demandeur_nom, selected.demandeur_matricule)].filter(Boolean).join(' · ')}
                </p>
              </div>
              <button type="button" className="modal__close" aria-label="Fermer" onClick={closeDetail}>
                <Icon name="x" size={18} />
              </button>
            </div>

            <div className="modal__scroll">
              <dl className="detail-grid">
                <div><dt>Statut</dt><dd><StatusBadge statut={selected.statut} /></dd></div>
                <div><dt>Période</dt><dd>{periodeDemande(selected)}</dd></div>
                {selected.type_demande === 'CONGE' ? (
                  <div>
                    <dt>Type de congé</dt>
                    <dd>{formatTypeConge((selected.contenu as { type_conge?: string } | undefined)?.type_conge) ?? '—'}</dd>
                  </div>
                ) : null}
                <div>
                  <dt>Demandeur</dt>
                  <dd>
                    {formatPersonne(selected.demandeur_nom, selected.demandeur_matricule)}
                  </dd>
                </div>
                <div><dt>Unité</dt><dd>{selected.demandeur_unite ?? '—'}</dd></div>
                <div><dt>Créée le</dt><dd>{formatDateHeure(selected.cree_le)}</dd></div>
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
                        {libelleConnu('etapeDemande', e.code) ?? e.libelle}{' '}
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
                    {historique.map((h) => {
                      const commentaire = commentaireLisible(h.commentaire);
                      return (
                        <li key={h.identifiant}>
                          <span className="cell-meta">{formatDateHeure(h.date_action)}</span>{' — '}
                          {h.etape_libelle?.trim() || libelle('actionDemande', h.action)}
                          {h.acteur_nom ? ` · ${libelleConnu('acteur', h.acteur_nom) ?? h.acteur_nom}` : ''}
                          {commentaire ? <span className="muted"> · « {commentaire} »</span> : null}
                        </li>
                      );
                    })}
                  </ul>
                </>
              ) : null}

            </div>

            <div className="modal__actions" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
              {peutDecider ? (
                <>
                  <button type="button" className="btn btn--ghost" onClick={() => void decider('refuser')}>Refuser</button>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <button type="button" className="btn btn--secondary" onClick={closeDetail}>Fermer</button>
                    <button type="button" className="btn btn--primary" onClick={() => void decider('approuver')}>Approuver</button>
                  </div>
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
