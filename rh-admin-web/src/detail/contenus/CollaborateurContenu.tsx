import { Link } from 'react-router-dom';
import {
  getAlertesDuCollaborateur, getCollaborateurFiche, getDemandesDuCollaborateur, getDocumentsDuCollaborateur,
  getEvaluationsDuCollaborateur, getFormationsDuCollaborateur, getHierarchie, getPlaintesDuCollaborateur,
  getPresenceCollaborateur, type DemandeFormationCiblee, type Hierarchie,
} from '../../api/detailApi';
import { getDemandesHeuresSup } from '../../api/heuresSupApi';
import { libelleProfilAcces } from '../../api/rhClient';
import { libelleNiveauSeniorite } from '../../api/evaluationCatalog';
import { LIBELLES_STATUT_HEURES_SUP } from '../../api/heuresSupApi';
import { StatusBadge } from '../../components/ui';
import {
  formatDate, formatDateHeure, formatDuree, formatHeuresMinutes, formatNote, nomComplet, TIRET,
} from '../../utils/format';
import { intituleDemande, periodeDemande } from '../../utils/demandes';
import { libelle, libelleTypeFormation } from '../../utils/libelles';
import { Charge } from '../Etats';
import Onglets from '../Onglets';
import DetailLink, { LienPersonne } from '../DetailLink';
import { peutVoirPlaintes, useTitre } from '../contexte';
import { useChargement } from '../useChargement';
import { Cartes, Champ, Champs, periode31, Tableau, titreOnglet } from './commun';
import type { PersonneResume, UniteResume } from '../../api/detailApi';

type Props = { id: string; nom?: string; onglet?: string; onOnglet: (o: string) => void };

export default function CollaborateurContenu({ id, onglet, onOnglet }: Props) {
  const plaintes = peutVoirPlaintes();
  const defs = [
    { id: 'profil', label: 'Profil' },
    { id: 'demandes', label: 'Demandes administratives' },
    { id: 'documents', label: 'Documents' },
    { id: 'formations', label: 'Formations' },
    { id: 'pointages', label: 'Pointages' },
    { id: 'temps', label: 'Temps de travail et heures sup' },
    { id: 'evaluations', label: 'Évaluations' },
    ...(plaintes ? [{ id: 'plaintes', label: 'Plaintes' }] : []),
    { id: 'hierarchie', label: 'Hiérarchie' },
  ];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);

  const fiche = useChargement(() => getCollaborateurFiche(id), [id]);
  const hier = useChargement(() => getHierarchie(id), [id], actif === 'profil' || actif === 'hierarchie');
  const presence = useChargement(() => {
    const p = periode31();
    return getPresenceCollaborateur(id, p.du, p.au);
  }, [id], actif === 'pointages' || actif === 'temps');

  const f = fiche.etat.statut === 'ok' ? fiche.etat.data : null;
  useTitre(f ? nomComplet(f) : null);

  if (fiche.etat.statut === 'erreur') {
    return <Charge etat={fiche.etat} recharger={fiche.recharger}>{() => null}</Charge>;
  }

  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'profil' && (
        <Charge etat={fiche.etat} recharger={fiche.recharger}>
          {(c) => (
            <Champs>
              <Champ label="Nom">{nomComplet(c)}</Champ>
              <Champ label="Matricule">{c.matricule}</Champ>
              <Champ label="Poste">{c.poste_libelle}</Champ>
              <Champ label="Intitulé de rôle">
                {hier.etat.statut === 'ok' ? hier.etat.data.collaborateur.intitule_role : null}
              </Champ>
              <Champ label="Famille · niveau">
                {[c.famille_metier_libelle, libelleNiveauSeniorite(c.niveau_seniorite)].filter(Boolean).join(' · ')}
              </Champ>
              <Champ label="Unité">
                {hier.etat.statut === 'ok' ? <CheminUnites chemin={hier.etat.data.chemin} /> : (c.unite?.libelle ?? null)}
              </Champ>
              <Champ label="Supérieur">
                {hier.etat.statut === 'ok' ? <Personne p={hier.etat.data.responsable_direct} vide="Aucun supérieur" /> : null}
              </Champ>
              <Champ label="Statut">{libelle('statutCollaborateur', c.statut)}</Champ>
              <Champ label="Courriel professionnel">{c.courriel_professionnel}</Champ>
              <Champ label="Date de recrutement">{c.date_recrutement ? formatDate(c.date_recrutement) : null}</Champ>
              <Champ label="Ancienneté">{anciennete(c.date_recrutement)}</Champ>
              <Champ label="Droits d’accès">{c.profil_acces ? libelleProfilAcces(c.profil_acces) : null}</Champ>
            </Champs>
          )}
        </Charge>
      )}
      {actif === 'demandes' && <OngletDemandes id={id} />}
      {actif === 'documents' && <OngletDocuments id={id} />}
      {actif === 'formations' && <OngletFormations id={id} />}
      {actif === 'pointages' && <OngletPointages id={id} etat={presence.etat} recharger={presence.recharger} />}
      {actif === 'temps' && <OngletTemps id={id} etat={presence.etat} recharger={presence.recharger} />}
      {actif === 'evaluations' && <OngletEvaluations id={id} />}
      {actif === 'plaintes' && plaintes && <OngletPlaintes id={id} />}
      {actif === 'hierarchie' && (
        <Charge etat={hier.etat} recharger={hier.recharger}>{(h) => <OngletHierarchie h={h} />}</Charge>
      )}
    </Onglets>
  );
}

export function CheminUnites({ chemin }: { chemin: UniteResume[] }) {
  if (chemin.length === 0) return <>Unité à rattacher</>;
  return (
    <>
      {chemin.map((u, i) => (
        <span key={u.unite_id}>
          {i > 0 ? ' › ' : ''}
          <DetailLink type="unite" id={u.unite_id} libelle={u.libelle} />
        </span>
      ))}
    </>
  );
}

function Personne({ p, vide }: { p: PersonneResume | null; vide: string }) {
  if (!p) return <>{vide}</>;
  return <LienPersonne id={p.collaborateur_id} nom={p.nom_complet} matricule={p.matricule} />;
}

function OngletHierarchie({ h }: { h: Hierarchie }) {
  return (
    <>
      <Champs>
        <Champ label="Chemin"><CheminUnites chemin={h.chemin} /></Champ>
        <Champ label="Responsable direct"><Personne p={h.responsable_direct} vide="Aucun responsable" /></Champ>
        <Champ label="Valideur de ses demandes">
          <Personne p={h.valideur} vide={h.valideur_est_rh ? 'Aucun supérieur — demandes à la RH' : 'Aucun valideur'} />
        </Champ>
        <Champ label="Nœuds managés">
          {h.noeuds_manages.length === 0 ? 'Aucun' : h.noeuds_manages.map((u, i) => (
            <span key={u.unite_id}>{i > 0 ? ', ' : ''}<DetailLink type="unite" id={u.unite_id} libelle={u.libelle} /></span>
          ))}
        </Champ>
      </Champs>
      <h3 className="detail-h3">Collaborateurs encadrés</h3>
      {h.collaborateurs_encadres.length === 0 ? (
        <p className="detail-vide">Ne manage aucun collaborateur.</p>
      ) : (
        <ul className="detail-liste">
          {h.collaborateurs_encadres.map((p) => (
            <li key={p.collaborateur_id}>
              <Personne p={p} vide="" />
              {p.poste_libelle ? <span className="cell-meta"> · {p.poste_libelle}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function OngletDemandes({ id }: { id: string }) {
  const { etat, recharger } = useChargement(() => getDemandesDuCollaborateur(id), [id]);
  return (
    <Charge etat={etat} recharger={recharger} vide={(l) => l.length === 0} messageVide="Aucune demande administrative.">
      {(l) => (
        <Tableau colonnes={['Numéro', 'Type', 'Période', 'Statut', 'Déposée le']}>
          {l.map((d) => (
            <tr key={d.identifiant}>
              <td><DetailLink type="demande" id={d.identifiant} libelle={d.numero ?? intituleDemande(d)} /></td>
              <td>{intituleDemande(d)}</td>
              <td>{periodeDemande(d)}</td>
              <td><StatusBadge statut={d.statut} /></td>
              <td>{formatDateHeure(d.cree_le)}</td>
            </tr>
          ))}
        </Tableau>
      )}
    </Charge>
  );
}

function OngletDocuments({ id }: { id: string }) {
  const { etat, recharger } = useChargement(() => getDocumentsDuCollaborateur(id), [id]);
  return (
    <Charge etat={etat} recharger={recharger} vide={(l) => l.length === 0} messageVide="Aucune demande de document.">
      {(l) => (
        <Tableau colonnes={['Numéro', 'Type', 'Statut', 'Déposée le']}>
          {l.map((d) => (
            <tr key={d.identifiant}>
              <td><DetailLink type="document" id={d.identifiant} libelle={d.numero ?? libelle('typeDocument', d.type_document)} /></td>
              <td>{libelle('typeDocument', d.type_document)}</td>
              <td><StatusBadge statut={d.statut} /></td>
              <td>{formatDateHeure(d.cree_le)}</td>
            </tr>
          ))}
        </Tableau>
      )}
    </Charge>
  );
}

function roleFormation(f: DemandeFormationCiblee, id: string): string {
  return f.demandeur_identifiant === id ? 'Demandeur' : 'Participant';
}

function OngletFormations({ id }: { id: string }) {
  const { etat, recharger } = useChargement(() => getFormationsDuCollaborateur(id), [id]);
  return (
    <Charge etat={etat} recharger={recharger} vide={(l) => l.length === 0} messageVide="Aucune formation demandée ou suivie.">
      {(l) => (
        <Tableau colonnes={['Numéro', 'Formation', 'Organisme', 'Rôle', 'Statut']}>
          {l.map((f) => (
            <tr key={f.identifiant}>
              <td><DetailLink type="formation" id={f.identifiant} libelle={f.numero ?? libelleTypeFormation(f.type_formation)} /></td>
              <td>{libelleTypeFormation(f.type_formation)}</td>
              <td>{f.organisme}</td>
              <td>{roleFormation(f, id)}</td>
              <td><StatusBadge statut={f.statut} /></td>
            </tr>
          ))}
        </Tableau>
      )}
    </Charge>
  );
}

type EtatPresence = ReturnType<typeof useChargement<Awaited<ReturnType<typeof getPresenceCollaborateur>>>>['etat'];

function OngletPointages({ id, etat, recharger }: { id: string; etat: EtatPresence; recharger: () => void }) {
  return (
    <Charge
      etat={etat}
      recharger={recharger}
      vide={(p) => p.journees.every((j) => j.pointages.length === 0) && p.pointages_manuels.length === 0}
      messageVide="Aucun pointage sur la période (31 derniers jours)."
    >
      {(p) => {
        const lignes = p.journees.flatMap((j) => j.pointages.map((pt) => ({ j, pt })));
        return (
          <>
            <p className="cell-meta">Période : {formatDate(p.synthese.du)} au {formatDate(p.synthese.au)}</p>
            {lignes.length > 0 ? (
              <>
                <Tableau colonnes={['Date et heure', 'Site', 'Type', 'Statut', 'Motif']}>
                  {lignes.map(({ j, pt }) => (
                    <tr key={pt.id}>
                      <td>{formatDateHeure(pt.server_ts)}{pt.manuel ? ' (manuel)' : ''}</td>
                      <td><DetailLink type="site" id={j.site_id} libelle={j.site_libelle} /></td>
                      <td>{pt.type_libelle ?? TIRET}</td>
                      <td>{pt.statut_libelle ?? TIRET}</td>
                      <td>{pt.motif_rejet ?? ''}</td>
                    </tr>
                  ))}
                </Tableau>
                <p>
                  <Link to={`/app/pointage?onglet=pointages&p_collab=${encodeURIComponent(id)}&p_du=${p.synthese.du}&p_au=${p.synthese.au}`}>
                    Voir les {lignes.length} pointages
                  </Link>
                </p>
              </>
            ) : null}
            {p.pointages_manuels.length > 0 ? (
              <>
                <h3 className="detail-h3">Pointages manuels</h3>
                <Tableau colonnes={['Numéro', 'Type', 'Heure déclarée', 'Statut']}>
                  {p.pointages_manuels.map((m) => (
                    <tr key={m.id}>
                      <td><DetailLink type="pointage-manuel" id={m.id} libelle={m.numero ?? 'Pointage manuel'} /></td>
                      <td>{m.type === 'ENTREE' ? 'Entrée' : m.type === 'SORTIE' ? 'Sortie' : TIRET}</td>
                      <td>{formatDateHeure(m.heure_declaree)}</td>
                      <td><StatusBadge statut={m.statut} /></td>
                    </tr>
                  ))}
                </Tableau>
              </>
            ) : null}
          </>
        );
      }}
    </Charge>
  );
}

function OngletTemps({ id, etat, recharger }: { id: string; etat: EtatPresence; recharger: () => void }) {
  const hs = useChargement(() => {
    const p = periode31();
    return getDemandesHeuresSup({ collaborateurId: id, du: p.du, au: p.au }, 0, 50);
  }, [id]);
  return (
    <>
      <Charge etat={etat} recharger={recharger}>
        {(p) => {
          const s = p.synthese;
          return (
            <>
              <Cartes items={[
                { label: 'Journées pointées', valeur: s.journees_pointees },
                { label: 'Temps total', valeur: formatHeuresMinutes(s.temps_total_minutes) },
                { label: 'Retards', valeur: `${s.retards_nombre} (${formatDuree(s.retards_cumul_minutes)})` },
                { label: 'Journées incomplètes', valeur: s.journees_incompletes },
                { label: 'Heures supplémentaires', valeur: formatHeuresMinutes(s.heures_sup_minutes) },
              ]}
              />
              {p.journees.length === 0 ? (
                <p className="detail-vide">Aucune journée pointée sur la période (31 derniers jours).</p>
              ) : (
                <Tableau colonnes={['Jour', 'Site', 'Temps travaillé', 'Retard', 'État']}>
                  {p.journees.map((j) => (
                    <tr key={`${j.journee}|${j.site_id}`}>
                      <td>{formatDate(j.journee)}</td>
                      <td><DetailLink type="site" id={j.site_id} libelle={j.site_libelle} /></td>
                      <td>{formatHeuresMinutes(j.temps_minutes)}</td>
                      <td>{j.retard_minutes == null ? TIRET : j.retard_minutes === 0 ? 'À l’heure' : formatDuree(j.retard_minutes)}</td>
                      <td>{j.etat_libelle ?? TIRET}</td>
                    </tr>
                  ))}
                </Tableau>
              )}
            </>
          );
        }}
      </Charge>
      <h3 className="detail-h3">Demandes d’heures supplémentaires</h3>
      <Charge etat={hs.etat} recharger={hs.recharger} vide={(l) => l.contenu.length === 0} messageVide="Aucune demande d’heures supplémentaires sur la période.">
        {(l) => (
          <Tableau colonnes={['Numéro', 'Site', 'Période', 'Statut']}>
            {l.contenu.map((d) => (
              <tr key={d.id}>
                <td><DetailLink type="heures-sup" id={d.id} libelle={d.numero ?? 'Heures supplémentaires'} /></td>
                <td><DetailLink type="site" id={d.site_id} libelle={d.site_libelle} /></td>
                <td>{formatDate(d.date_debut)}{d.date_fin !== d.date_debut ? ` au ${formatDate(d.date_fin)}` : ''}</td>
                <td>{LIBELLES_STATUT_HEURES_SUP[d.statut] ?? TIRET}</td>
              </tr>
            ))}
          </Tableau>
        )}
      </Charge>
    </>
  );
}

function OngletEvaluations({ id }: { id: string }) {
  const ev = useChargement(() => getEvaluationsDuCollaborateur(id), [id]);
  const al = useChargement(() => getAlertesDuCollaborateur(id), [id]);
  return (
    <>
      <Charge etat={ev.etat} recharger={ev.recharger} vide={(l) => l.length === 0} messageVide="Aucune évaluation.">
        {(l) => (
          <Tableau colonnes={['Campagne', 'Statut', 'Score /20', 'Évaluation']}>
            {l.map((e) => (
              <tr key={e.identifiant}>
                <td><DetailLink type="campagne" id={e.campagneId} libelle={e.campagneNom} /></td>
                <td><StatusBadge statut={e.statut} /></td>
                <td>{e.scoreSur20 == null ? TIRET : formatNote(e.scoreSur20, 20, 0)}</td>
                <td><DetailLink type="evaluation" id={e.identifiant} libelle={`Évaluation ${e.campagneNom ?? ''}`.trim()}>Voir l’évaluation</DetailLink></td>
              </tr>
            ))}
          </Tableau>
        )}
      </Charge>
      <h3 className="detail-h3">Alertes d’écart</h3>
      <Charge etat={al.etat} recharger={al.recharger} vide={(l) => l.elements.length === 0} messageVide="Aucune alerte d’écart.">
        {(l) => (
          <Tableau colonnes={['Campagne', 'Niveau', 'Statut', 'Détectée le']}>
            {l.elements.map((a) => (
              <tr key={a.identifiant}>
                <td><DetailLink type="alerte" id={a.identifiant} libelle={`Alerte d’écart ${a.campagneNom ?? ''}`.trim()}>{a.campagneNom ?? 'Alerte d’écart'}</DetailLink></td>
                <td>{niveauAlerteLibelle(a.niveau)}</td>
                <td>{libelle('statut', a.statut)}</td>
                <td>{formatDate(a.detecteeLe)}</td>
              </tr>
            ))}
          </Tableau>
        )}
      </Charge>
    </>
  );
}

function niveauAlerteLibelle(n: string): string {
  return ({ ORANGE: 'Alerte', ROUGE: 'Critique', URGENTE: 'Urgente', ALIGNE: 'Aligné' } as Record<string, string>)[n] ?? 'Valeur non reconnue';
}

function OngletPlaintes({ id }: { id: string }) {
  const { etat, recharger } = useChargement(() => getPlaintesDuCollaborateur(id), [id]);
  return (
    <Charge etat={etat} recharger={recharger} vide={(l) => l.length === 0} messageVide="Aucune plainte déposée.">
      {(l) => (
        <Tableau colonnes={['Numéro', 'Titre', 'Type', 'Statut', 'Déposée le']}>
          {l.map((p) => (
            <tr key={p.identifiant}>
              <td><DetailLink type="plainte" id={p.identifiant} libelle={p.numero_ticket} /></td>
              <td>{p.titre}</td>
              <td>{libelle('typePlainte', p.type_plainte)}</td>
              <td><StatusBadge statut={p.statut} /></td>
              <td>{formatDateHeure(p.cree_le)}</td>
            </tr>
          ))}
        </Tableau>
      )}
    </Charge>
  );
}


/** Ancienneté affichée (années et mois révolus) depuis la date de recrutement ; simple affichage, aucune règle métier. */
function anciennete(iso?: string | null): string | null {
  if (!iso) return null;
  const d = new Date(`${iso.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let mois = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
  if (now.getDate() < d.getDate()) mois -= 1;
  if (mois < 0) return null;
  const a = Math.floor(mois / 12);
  const m = mois % 12;
  const parts = [a > 0 ? `${a} an${a > 1 ? 's' : ''}` : '', m > 0 ? `${m} mois` : ''].filter(Boolean);
  return parts.length ? parts.join(' et ') : 'Moins d’un mois';
}
