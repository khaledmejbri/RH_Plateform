import type { OrganigrammeNoeud } from '../api/rhClient';
import { EmptyState, Icon, LoadingState, type IconName, type Tone } from '../components/ui';
import DetailLink from '../detail/DetailLink';
import { intituleResponsable, libelleTypeNoeud } from '../utils/libelles';
import {
  calculerCompteursStructure,
  estARattacher,
  matchesSearch,
  NIVEAU_DG,
  niveauType,
  typesCreables,
} from '../utils/organigramme';

type Props = {
  tousNoeuds: OrganigrammeNoeud[];
  indexNoeuds: Map<string, OrganigrammeNoeud>;
  collaborateursActifs: number;
  loading: boolean;
  err: string | null;
  searchQuery: string;
  lectureSeule: boolean;
  saving: boolean;
  moving: boolean;
  dgExiste: boolean;
  selectedId: string | null;
  onReload: () => void;
  onResetSearch: () => void;
  onOpenCreer: (parent: OrganigrammeNoeud | null) => void;
  onSelect: (n: OrganigrammeNoeud) => void;
  onOpenManager: (n: OrganigrammeNoeud) => void;
  onOpenEditer: (n: OrganigrammeNoeud) => void;
  onOpenRattacher: (n: OrganigrammeNoeud) => void;
};

function StatCard({ icon, value, label, tone }: { icon: IconName; value: number; label: string; tone: Tone }) {
  return (
    <div className="dash-stat card">
      <span className={`dash-stat__icon chip--${tone}`} aria-hidden>
        <Icon name={icon} size={20} />
      </span>
      <div className="dash-stat__value">{value}</div>
      <div className="dash-stat__label">{label}</div>
    </div>
  );
}

/** Vue Liste de l'organigramme : compteurs par type D-H04 et tableau filtrable des unités. */
export default function OrganigrammeListeVue({
  tousNoeuds,
  indexNoeuds,
  collaborateursActifs,
  loading,
  err,
  searchQuery,
  lectureSeule,
  saving,
  moving,
  dgExiste,
  selectedId,
  onReload,
  onResetSearch,
  onOpenCreer,
  onSelect,
  onOpenManager,
  onOpenEditer,
  onOpenRattacher,
}: Props) {
  const q = searchQuery.trim().toLowerCase();
  const compteurs = calculerCompteursStructure(tousNoeuds, collaborateursActifs);
  const lignes = q ? tousNoeuds.filter((n) => matchesSearch(n, q)) : tousNoeuds;

  if (loading) {
    return (
      <div className="panel">
        <LoadingState label="Chargement de la liste des unités…" />
      </div>
    );
  }

  if (err && tousNoeuds.length === 0) {
    return (
      <div className="empty-state">
        <h3>Impossible de charger l’organigramme.</h3>
        <button type="button" className="btn btn--primary" onClick={onReload}>
          Réessayer
        </button>
      </div>
    );
  }

  return (
    <>
      <section className="dash-stats" aria-label="Compteurs par type d’unité">
        <StatCard icon="building" value={compteurs.directionGenerale} label="Direction générale" tone="info" />
        <StatCard icon="briefcase" value={compteurs.directions} label="Directions" tone="info" />
        <StatCard icon="building" value={compteurs.departements} label="Départements" tone="progress" />
        <StatCard icon="clipboard" value={compteurs.services} label="Services" tone="progress" />
        {compteurs.aRattacher > 0 ? (
          <StatCard icon="alert" value={compteurs.aRattacher} label="À rattacher" tone="warning" />
        ) : null}
        <StatCard icon="target" value={compteurs.sansResponsable} label="Unités sans responsable" tone="warning" />
        <StatCard icon="users" value={compteurs.collaborateursActifs} label="Collaborateurs actifs" tone="neutral" />
      </section>

      {tousNoeuds.length === 0 ? (
        <div className="panel">
          <EmptyState
            icon="building"
            title={lectureSeule ? 'Aucune structure organisationnelle publiée.' : 'Aucune unité organisationnelle.'}
            action={
              !lectureSeule ? (
                <button type="button" className="btn btn--primary" onClick={() => onOpenCreer(null)}>
                  Créer la Direction Générale
                </button>
              ) : undefined
            }
          >
            {lectureSeule
              ? 'Contactez la RH pour publier l’organigramme.'
              : 'Commencez par le sommet de la hiérarchie : la Direction Générale.'}
          </EmptyState>
        </div>
      ) : lignes.length === 0 ? (
        <div className="panel">
          <EmptyState
            icon="search"
            title="Aucune unité correspondante"
            action={
              <button type="button" className="btn btn--secondary btn--sm" onClick={onResetSearch}>
                Réinitialiser les filtres
              </button>
            }
          >
            Modifiez votre recherche pour afficher d’autres unités, responsables ou collaborateurs.
          </EmptyState>
        </div>
      ) : (
        <div className="panel">
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Unité</th>
                  <th>Type</th>
                  <th>Parent</th>
                  <th>Responsable</th>
                  <th>Collaborateurs</th>
                  <th>Statut</th>
                  {!lectureSeule ? <th>Actions</th> : null}
                </tr>
              </thead>
              <tbody>
                {lignes.map((n) => {
                  const parent = n.parent_identifiant ? indexNoeuds.get(n.parent_identifiant) : undefined;
                  const estDg = niveauType(n.type_noeud) === NIVEAU_DG;
                  const aRattacher = estARattacher(n);
                  const peutAjouterEnfant = typesCreables(n, dgExiste).length > 0;
                  const isSelected = selectedId === n.identifiant;

                  return (
                    <tr
                      key={n.identifiant}
                      className={isSelected ? 'org-list-row--selected' : undefined}
                      onClick={() => onSelect(n)}
                    >
                      <td>
                        <div>
                          <DetailLink type="unite" id={n.identifiant} libelle={n.libelle} />
                          {aRattacher ? (
                            <span className="badge badge--warning" style={{ marginLeft: 8 }}>
                              À rattacher
                            </span>
                          ) : null}
                        </div>
                        <div className="cell-meta mono">{n.code}</div>
                      </td>
                      <td>{libelleTypeNoeud(n.type_noeud)}</td>
                      <td>
                        {parent ? (
                          <DetailLink type="unite" id={parent.identifiant} libelle={parent.libelle} />
                        ) : estDg ? (
                          <span className="muted">Sommet</span>
                        ) : (
                          <span className="muted">À rattacher</span>
                        )}
                      </td>
                      <td>
                        {n.manager ? (
                          <div>
                            <DetailLink
                              type="collaborateur"
                              id={n.manager.identifiant}
                              libelle={`${n.manager.prenom} ${n.manager.nom}`}
                            />
                            <div className="cell-meta">
                              {intituleResponsable(n.type_noeud, n.titre_poste)} · {n.manager.matricule}
                            </div>
                          </div>
                        ) : (
                          <span className="badge badge--warning">Sans responsable</span>
                        )}
                      </td>
                      <td>{n.membres.length}</td>
                      <td>
                        <span className={`status status--${n.actif ? 'success' : 'neutral'}`}>
                          {n.actif ? 'Actif' : 'Inactif'}
                        </span>
                      </td>
                      {!lectureSeule ? (
                        <td onClick={(e) => e.stopPropagation()}>
                          <div className="org-list-actions">
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              disabled={saving || moving}
                              onClick={() => onOpenManager(n)}
                            >
                              {n.manager ? 'Responsable' : 'Assigner'}
                            </button>
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              disabled={saving || moving}
                              onClick={() => onOpenEditer(n)}
                            >
                              Modifier
                            </button>
                            {!estDg ? (
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                disabled={saving || moving}
                                onClick={() => onOpenRattacher(n)}
                              >
                                Rattacher…
                              </button>
                            ) : null}
                            {peutAjouterEnfant ? (
                              <button
                                type="button"
                                className="btn btn--ghost btn--sm"
                                disabled={saving || moving}
                                title={`Ajouter un sous-élément sous « ${n.libelle} »`}
                                onClick={() => onOpenCreer(n)}
                              >
                                + Enfant
                              </button>
                            ) : null}
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
