import { Link } from 'react-router-dom';
import {
  getCampagneDetail, getDemande, getDemandeHeuresSupDetail, getDemandeManuelleDetail, getDocument, getEvaluationDetail,
  getEvaluationsCampagne, getFicheUnite, getFormation, getFormationHistorique, getHeuresSupComptees, getPlainte,
  getSyntheseSite, getDemandesDeUnite, type QuestionEvaluation,
} from '../../api/detailApi';
import { getAlerteEcart, getAlertesEcart, LIBELLES_NIVEAU_ECART, LIBELLES_STATUT_ALERTE, LIBELLES_EVENEMENT } from '../../api/alertesEcartApi';
import {
  getDemandeAdministrativeHistorique, getDemandeAdministrativeSuivi,
} from '../../api/rhClient';
import { lireVigilance } from '../../api/presenceApi';
import { BandeauQrPapier, BandeauSignaux, lignesVigilance } from '../../components/VigilanceManuelle';
import { getHoraireSite, getPresencePointages, getPresenceSite, JOURS_SEMAINE } from '../../api/presenceApi';
import { LIBELLES_ETAT_COMPTEES, LIBELLES_STATUT_HEURES_SUP, type HeuresComptees } from '../../api/heuresSupApi';
import { LIBELLES_TYPE_CAMPAGNE, type CampaignType } from '../../api/evaluationApi';
import { ALERTE_META, appreciationFromScore, type CouleurAlerte } from '../../api/evaluationCatalog';
import { StatusBadge } from '../../components/ui';
import {
  formatDate, formatDateHeure, formatDuree, formatHeuresMinutes, formatPeriode, formatPourcentage, TIRET,
} from '../../utils/format';
import { commentaireLisible, contenuLignes, intituleDemande, periodeDemande } from '../../utils/demandes';
import { libelle, libelleAppreciation, libelleConnu, libelleTypeFormation } from '../../utils/libelles';
import { Charge } from '../Etats';
import Onglets from '../Onglets';
import DetailLink, { LienPersonne } from '../DetailLink';
import { useTitre } from '../contexte';
import { useChargement } from '../useChargement';
import { Cartes, Champ, Champs, periode31, Tableau, titreOnglet } from './commun';
import { CheminUnites } from './CollaborateurContenu';

type Props = { id: string; onglet?: string; onOnglet: (o: string) => void };

/* ─── Unité ─────────────────────────────────────────────────────────────── */

export function UniteContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'membres', label: 'Membres' }, { id: 'demandes', label: 'Demandes en attente' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getFicheUnite(id), [id]);
  const dem = useChargement(() => getDemandesDeUnite(id), [id], actif === 'demandes');
  useTitre(etat.statut === 'ok' ? etat.data.unite.libelle : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const u = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Type">{u.unite.type_libelle}</Champ>
            <Champ label="Code"><span className="cell-meta">{u.code}</span></Champ>
            <Champ label="Chemin"><CheminUnites chemin={u.chemin} /></Champ>
            <Champ label="Rattachée à">{u.parent ? <DetailLink type="unite" id={u.parent.unite_id} libelle={u.parent.libelle} /> : 'Aucune'}</Champ>
            <Champ label="Responsable">
              {u.responsable
                ? <LienPersonne id={u.responsable.collaborateur_id} nom={u.responsable.nom_complet} matricule={u.responsable.matricule} />
                : 'Sans responsable — demandes à la RH'}
            </Champ>
            <Champ label="Effectif">{u.effectif_direct} direct, {u.effectif_total} au total</Champ>
            <Champ label="Statut">{u.actif ? 'Actif' : 'Inactif'}{u.a_rattacher ? ' · à rattacher' : ''}</Champ>
          </Champs>
          <h3 className="detail-h3">Sous-unités</h3>
          {u.sous_unites.length === 0 ? <p className="detail-vide">Aucune sous-unité.</p> : (
            <ul className="detail-liste">
              {u.sous_unites.map((s) => (
                <li key={s.unite.unite_id}>
                  <DetailLink type="unite" id={s.unite.unite_id} libelle={s.unite.libelle} />
                  <span className="cell-meta"> · {s.unite.type_libelle} · {s.effectif_total} personne(s)</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {actif === 'membres' && (
        u.membres.length === 0 ? <p className="detail-vide">Aucun membre dans cette unité.</p> : (
          <Tableau colonnes={['Nom', 'Poste', 'Rôle']}>
            {u.membres.map((m) => (
              <tr key={m.collaborateur_id}>
                <td><LienPersonne id={m.collaborateur_id} nom={m.nom_complet} matricule={m.matricule} /></td>
                <td>{m.poste_libelle ?? TIRET}</td>
                <td>{m.intitule_role ?? TIRET}</td>
              </tr>
            ))}
          </Tableau>
        )
      )}
      {actif === 'demandes' && (
        <Charge etat={dem.etat} recharger={dem.recharger} vide={(l) => l.length === 0} messageVide="Aucune demande en attente dans cette unité.">
          {(l) => (
            <Tableau colonnes={['Numéro', 'Demandeur', 'Type', 'Période']}>
              {l.map((d) => (
                <tr key={d.identifiant}>
                  <td><DetailLink type="demande" id={d.identifiant} libelle={d.numero ?? intituleDemande(d)} /></td>
                  <td><LienPersonne id={d.demandeur_identifiant} nom={d.demandeur_nom} matricule={d.demandeur_matricule} /></td>
                  <td>{intituleDemande(d)}</td>
                  <td>{periodeDemande(d)}</td>
                </tr>
              ))}
            </Tableau>
          )}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Site ──────────────────────────────────────────────────────────────── */

export function SiteContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'presence', label: 'Pointages et temps de travail' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const site = useChargement(() => getPresenceSite(id), [id]);
  const horaire = useChargement(() => getHoraireSite(id), [id], actif === 'detail');
  const pres = useChargement(async () => {
    const p = periode31();
    const [synthese, pointages] = await Promise.all([getSyntheseSite(id, p.du, p.au), getPresencePointages({ siteId: id, size: 10 })]);
    return { synthese, pointages, p };
  }, [id], actif === 'presence');
  useTitre(site.etat.statut === 'ok' ? site.etat.data.libelle : null);
  if (site.etat.statut !== 'ok') return <Charge etat={site.etat} recharger={site.recharger}>{() => null}</Charge>;
  const s = site.etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          {s.modeQr !== 'ECRAN' ? <BandeauQrPapier /> : null}
          <Champs>
            <Champ label="Code"><span className="cell-meta">{s.code}</span></Champ>
            <Champ label="Type de lieu">{libelle('typeSite', s.typeLieu)}</Champ>
            <Champ label="Zone de pointage">{s.rayonMetres} m</Champ>
            <Champ label="Mode du QR">{s.modeQr === 'ECRAN' ? 'Borne écran' : 'QR papier'}</Champ>
            <Champ label="Statut">{s.actif ? 'Actif' : 'Inactif'}</Champ>
          </Champs>
          <h3 className="detail-h3">Horaire hebdomadaire</h3>
          <Charge etat={horaire.etat} recharger={horaire.recharger}>
            {(h) => !h.avec_horaire ? <p className="detail-vide">Aucun horaire défini pour ce site.</p> : (
              <>
                <Tableau colonnes={['Jour', 'Début', 'Fin']}>
                  {h.jours.map((j) => (
                    <tr key={j.jour}>
                      <td>{JOURS_SEMAINE.find((x) => x.value === j.jour)?.label ?? TIRET}</td>
                      <td>{j.debut}</td><td>{j.fin}{j.nuit ? ' (lendemain)' : ''}</td>
                    </tr>
                  ))}
                </Tableau>
                <p className="cell-meta">Tolérances : {h.tolerance_avant_minutes} min avant, {h.tolerance_apres_minutes} min après.</p>
              </>
            )}
          </Charge>
        </>
      )}
      {actif === 'presence' && (
        <Charge etat={pres.etat} recharger={pres.recharger}>
          {({ synthese, pointages, p }) => (
            <>
              <Cartes items={[
                { label: 'Collaborateurs', valeur: synthese.collaborateurs },
                { label: 'Journées pointées', valeur: synthese.journees_pointees },
                { label: 'Temps total', valeur: formatHeuresMinutes(synthese.temps_total_minutes) },
                { label: 'Retards', valeur: `${synthese.retards_nombre} (${formatDuree(synthese.retards_cumul_minutes)})` },
                { label: 'Journées incomplètes', valeur: synthese.journees_incompletes },
              ]}
              />
              <h3 className="detail-h3">Derniers pointages</h3>
              {pointages.contenu.length === 0 ? <p className="detail-vide">Aucun pointage sur ce site.</p> : (
                <>
                  <Tableau colonnes={['Date et heure', 'Collaborateur', 'Type']}>
                    {pointages.contenu.map((pt) => (
                      <tr key={pt.id}>
                        <td>{formatDateHeure(pt.serverTs)}</td>
                        <td><LienPersonne id={pt.collaborateurId} nom={pt.collaborateurNom} matricule={pt.collaborateurMatricule} /></td>
                        <td>{pt.type === 'ENTREE' ? 'Entrée' : 'Sortie'}</td>
                      </tr>
                    ))}
                  </Tableau>
                  <p><Link to={`/app/pointage?onglet=pointages&p_site=${encodeURIComponent(id)}&p_du=${p.du}&p_au=${p.au}`}>Voir les pointages du site</Link></p>
                </>
              )}
            </>
          )}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Campagne ──────────────────────────────────────────────────────────── */

export function CampagneContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'evaluations', label: 'Évaluations' }, { id: 'alertes', label: 'Alertes d’écart' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getCampagneDetail(id), [id]);
  const evs = useChargement(() => getEvaluationsCampagne(id), [id], actif === 'evaluations');
  const als = useChargement(() => getAlertesEcart({ campagneId: id }, 0, 50), [id], actif === 'alertes');
  useTitre(etat.statut === 'ok' ? etat.data.nom : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const c = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Type">{LIBELLES_TYPE_CAMPAGNE[c.type as CampaignType] ?? TIRET}</Champ>
            <Champ label="Statut"><StatusBadge statut={c.statut} /></Champ>
            <Champ label="Période">{c.dateDebut && c.dateFin ? formatPeriode(c.dateDebut, c.dateFin) : null}</Champ>
            <Champ label="Questionnaire général">{c.templateGeneral?.nom}</Champ>
            <Champ label="Questionnaire technique">{c.templateTechnique?.nom}</Champ>
          </Champs>
          <Cartes items={[
            { label: 'Évaluations', valeur: c.compteurs.total },
            { label: 'En cours', valeur: c.compteurs.enCours },
            { label: 'Validées', valeur: c.compteurs.validees },
            { label: 'Taux de complétion', valeur: formatPourcentage(c.compteurs.tauxCompletion) },
            { label: 'Alertes ouvertes', valeur: c.alertesOuvertesParNiveau.total },
          ]}
          />
          {c.compteurs.sansQuestionnaireTechnique > 0 ? (
            <p className="alert alert--warning">{c.compteurs.sansQuestionnaireTechnique} évaluation(s) sans questionnaire technique.</p>
          ) : null}
        </>
      )}
      {actif === 'alertes' && (
        <Charge etat={als.etat} recharger={als.recharger} vide={(l) => l.elements.length === 0} messageVide="Aucune alerte d’écart dans cette campagne.">
          {(l) => (
            <Tableau colonnes={['Alerte', 'Collaborateur', 'Niveau', 'Statut', 'Détectée le']}>
              {l.elements.map((a) => (
                <tr key={a.identifiant}>
                  <td><DetailLink type="alerte" id={a.identifiant} libelle={`Alerte d’écart — ${a.collaborateurNom ?? 'collaborateur'}`}>Voir l’alerte</DetailLink></td>
                  <td>{a.collaborateurId ? <LienPersonne id={a.collaborateurId} nom={a.collaborateurNom} matricule={a.collaborateurMatricule} /> : (a.collaborateurNom ?? 'Nom indisponible')}</td>
                  <td>{LIBELLES_NIVEAU_ECART[a.niveau]}</td>
                  <td>{LIBELLES_STATUT_ALERTE[a.statut]}</td>
                  <td>{formatDate(a.detecteeLe)}</td>
                </tr>
              ))}
            </Tableau>
          )}
        </Charge>
      )}
      {actif === 'evaluations' && (
        <Charge etat={evs.etat} recharger={evs.recharger} vide={(l) => l.elements.length === 0} messageVide="Aucune évaluation dans cette campagne.">
          {(l) => (
            <>
              <Tableau colonnes={['Collaborateur', 'Responsable', 'Statut', 'Score /20', 'Évaluation']}>
                {l.elements.map((e) => (
                  <tr key={e.identifiant}>
                    <td><LienPersonne id={e.collaborateurId} nom={e.collaborateurNom ?? 'Nom indisponible'} matricule={e.collaborateurMatricule} /></td>
                    <td><LienPersonne id={e.superieurId} nom={e.superieurNom} matricule={e.superieurMatricule} /></td>
                    <td><StatusBadge statut={e.statut} /></td>
                    <td>{e.scoreSur20 ?? TIRET}</td>
                    <td><DetailLink type="evaluation" id={e.identifiant} libelle={`Évaluation de ${e.collaborateurNom ?? ''}`.trim()}>Voir</DetailLink></td>
                  </tr>
                ))}
              </Tableau>
              {l.total > l.elements.length ? <p className="cell-meta">{l.elements.length} évaluations affichées sur {l.total}.</p> : null}
            </>
          )}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Évaluation ────────────────────────────────────────────────────────── */

const EVENEMENTS_EVAL: Record<string, string> = {
  CREEE: 'Évaluation créée',
  VALIDEE_COLLABORATEUR: 'Validée par le collaborateur',
  VALIDEE_RESPONSABLE: 'Validée par le responsable',
};

function note(n?: number | null): string {
  return n == null ? TIRET : `${n}/5`;
}

function TableQuestions({ questions, technique }: { questions: QuestionEvaluation[]; technique: boolean }) {
  if (questions.length === 0) return <p className="detail-vide">Aucune question.</p>;
  return (
    <Tableau colonnes={technique
      ? ['Compétence', 'Collaborateur', 'Responsable', 'Écart', 'Commentaire du responsable']
      : ['Question', 'Réponse du collaborateur', 'Réponse du responsable', 'Commentaire du responsable']}
    >
      {questions.map((q) => (
        <tr key={q.questionId}>
          <td>{q.libelle}</td>
          {technique ? (
            <>
              <td>{note(q.noteCollaborateur)}</td>
              <td>{note(q.noteResponsable)}</td>
              <td>{q.ecart == null ? TIRET : `${q.ecart} (${LIBELLES_NIVEAU_ECART[q.niveauEcart as keyof typeof LIBELLES_NIVEAU_ECART] ?? TIRET})`}</td>
            </>
          ) : (
            <>
              <td>{q.reponseCollaborateur ?? (q.noteCollaborateur != null ? note(q.noteCollaborateur) : TIRET)}</td>
              <td>{q.reponseResponsable ?? (q.noteResponsable != null ? note(q.noteResponsable) : TIRET)}</td>
            </>
          )}
          <td>{q.commentaireResponsable ?? ''}</td>
        </tr>
      ))}
    </Tableau>
  );
}

export function EvaluationContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'historique', label: 'Historique' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getEvaluationDetail(id), [id]);
  useTitre(etat.statut === 'ok' ? `Évaluation de ${etat.data.entete.collaborateurNom ?? 'collaborateur'}` : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const d = etat.data;
  const e = d.entete;
  const couleur = e.couleurAlerte ? ALERTE_META[e.couleurAlerte as CouleurAlerte]?.court : null;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Collaborateur"><LienPersonne id={e.collaborateurId} nom={e.collaborateurNom ?? 'Nom indisponible'} matricule={e.collaborateurMatricule} /></Champ>
            <Champ label="Responsable"><LienPersonne id={e.superieurId} nom={e.superieurNom} matricule={e.superieurMatricule} /></Champ>
            <Champ label="Campagne"><DetailLink type="campagne" id={e.campagneId} libelle={e.campagneNom} /></Champ>
            <Champ label="Profil">{[e.familleMetierLibelle, e.niveauSenioriteLibelle].filter(Boolean).join(' · ')}</Champ>
            <Champ label="Statut"><StatusBadge statut={e.statut} /></Champ>
            <Champ label="Validée par le collaborateur">{e.validationCollaborateurLe ? formatDateHeure(e.validationCollaborateurLe) : null}</Champ>
            <Champ label="Validée par le responsable">{e.validationSuperieurLe ? formatDateHeure(e.validationSuperieurLe) : null}</Champ>
            <Champ label="Score">
              {d.scores?.scoreSur20 != null ? `${d.scores.scoreSur20}/20 · ${libelleAppreciation(d.scores.appreciationLibelle ?? d.scores.appreciation) ?? appreciationFromScore(d.scores.scoreSur20) ?? ''}` : null}
            </Champ>
            <Champ label="Codage couleur">{couleur}</Champ>
            <Champ label="Alerte d’écart">
              {d.alerte ? (
                <DetailLink type="alerte" id={d.alerte.identifiant} libelle="Alerte d’écart">
                  {LIBELLES_NIVEAU_ECART[d.alerte.niveau as keyof typeof LIBELLES_NIVEAU_ECART] ?? 'Alerte'} · {libelle('statut', d.alerte.statut)}
                </DetailLink>
              ) : 'Aucune'}
            </Champ>
          </Champs>
          <h3 className="detail-h3">Questions générales</h3>
          <TableQuestions questions={d.questionsGenerales} technique={false} />
          <h3 className="detail-h3">Compétences techniques</h3>
          <TableQuestions questions={d.competencesTechniques} technique />
        </>
      )}
      {actif === 'historique' && (
        d.historique.length === 0 ? <p className="detail-vide">Aucun événement.</p> : (
          <ul className="detail-liste">
            {d.historique.map((h, i) => (
              <li key={`${h.evenement}${i}`}>
                <span className="cell-meta">{formatDateHeure(h.le)}</span> — {EVENEMENTS_EVAL[h.evenement] ?? 'Événement'}
                {h.acteurNom ? ` · ${h.acteurNom}` : ''}
              </li>
            ))}
          </ul>
        )
      )}
    </Onglets>
  );
}

/* ─── Alerte d'écart ────────────────────────────────────────────────────── */

export function AlerteContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'historique', label: 'Historique' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getAlerteEcart(id), [id]);
  const ev = useChargement(
    () => getEvaluationDetail((etat.statut === 'ok' ? etat.data.evaluationId : '')),
    [etat.statut === 'ok' ? etat.data.evaluationId : ''],
    etat.statut === 'ok',
  );
  useTitre(etat.statut === 'ok' ? `Alerte d’écart — ${etat.data.collaborateurNom ?? 'collaborateur'}` : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const a = etat.data;
  const entete = ev.etat.statut === 'ok' ? ev.etat.data.entete : null;
  const collabId = a.collaborateurId ?? entete?.collaborateurId;
  const supId = a.superieurId ?? entete?.superieurId;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Collaborateur">
              {collabId ? <LienPersonne id={collabId} nom={a.collaborateurNom} matricule={a.collaborateurMatricule} />
                : `${a.collaborateurNom ?? 'Nom indisponible'}${a.collaborateurMatricule ? ` (${a.collaborateurMatricule})` : ''}`}
            </Champ>
            <Champ label="Responsable">
              {supId ? <LienPersonne id={supId} nom={a.superieurNom} matricule={entete?.superieurMatricule} /> : a.superieurNom}
            </Champ>
            <Champ label="Campagne"><DetailLink type="campagne" id={a.campagneId} libelle={a.campagneNom} /></Champ>
            <Champ label="Évaluation"><DetailLink type="evaluation" id={a.evaluationId} libelle="Évaluation">Voir l’évaluation</DetailLink></Champ>
            <Champ label="Niveau">{LIBELLES_NIVEAU_ECART[a.niveau]}</Champ>
            <Champ label="Statut">{LIBELLES_STATUT_ALERTE[a.statut]}</Champ>
            <Champ label="Détectée le">{formatDateHeure(a.detecteeLe)}</Champ>
            <Champ label="Conclusion">{a.conclusionLibelle}</Champ>
          </Champs>
          <h3 className="detail-h3">Compétences en écart</h3>
          {a.lignes.length === 0 ? <p className="detail-vide">Aucune compétence.</p> : (
            <Tableau colonnes={['Compétence', 'Collaborateur', 'Responsable', 'Écart']}>
              {a.lignes.map((l) => (
                <tr key={l.questionId}>
                  <td>{l.competence}</td>
                  <td>{l.etoilesCollaborateurActuelles}/5</td>
                  <td>{l.etoilesRoActuelles}/5</td>
                  <td>{l.ecartActuel} ({LIBELLES_NIVEAU_ECART[l.niveauActuel]})</td>
                </tr>
              ))}
            </Tableau>
          )}
        </>
      )}
      {actif === 'historique' && (
        a.historique.length === 0 ? <p className="detail-vide">Aucun événement.</p> : (
          <ul className="detail-liste">
            {a.historique.map((h) => (
              <li key={h.identifiant}>
                <span className="cell-meta">{formatDateHeure(h.le)}</span> — {h.typeEvenementLibelle ?? LIBELLES_EVENEMENT[h.typeEvenement]}
                {h.auteurLibelle ? ` · ${h.auteurLibelle}` : ''}
                {h.commentaire ? <span className="muted"> · « {h.commentaire} »</span> : null}
              </li>
            ))}
          </ul>
        )
      )}
    </Onglets>
  );
}

/* ─── Demande administrative ────────────────────────────────────────────── */

export function DemandeContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'historique', label: 'Historique' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getDemande(id), [id]);
  const suivi = useChargement(() => getDemandeAdministrativeSuivi(id), [id], actif === 'detail');
  const hist = useChargement(() => getDemandeAdministrativeHistorique(id), [id], actif === 'historique');
  useTitre(etat.statut === 'ok' ? (etat.data.numero ?? intituleDemande(etat.data)) : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const d = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Numéro">{d.numero}</Champ>
            <Champ label="Type">{intituleDemande(d)}</Champ>
            <Champ label="Statut"><StatusBadge statut={d.statut} /></Champ>
            <Champ label="Période">{periodeDemande(d)}</Champ>
            <Champ label="Demandeur"><LienPersonne id={d.demandeur_identifiant} nom={d.demandeur_nom} matricule={d.demandeur_matricule} /></Champ>
            <Champ label="Unité">{d.demandeur_unite}</Champ>
            <Champ label="Valideur attendu">
              {d.valideur_attendu_nom_complet
                ? <DetailLink type="collaborateur" id={d.valideur_attendu_identifiant} libelle={d.valideur_attendu_nom_complet} />
                : null}
            </Champ>
            <Champ label="Déposée le">{formatDateHeure(d.cree_le)}</Champ>
            {contenuLignes(d).map(([l, v]) => <Champ key={l} label={l}>{v}</Champ>)}
          </Champs>
          {d.motif_refus ? <div className="callout"><p className="callout__title">Motif du refus</p><p>{d.motif_refus}</p></div> : null}
          <h3 className="detail-h3">Suivi</h3>
          <Charge etat={suivi.etat} recharger={suivi.recharger} vide={(s) => s.etapes.length === 0} messageVide="Aucune étape.">
            {(s) => (
              <>
                {s.message_explication ? <p className="cell-meta">{s.message_explication}</p> : null}
                <ol className="detail-liste">
                  {s.etapes.map((e) => (
                    <li key={e.code}>
                      {libelleConnu('etapeDemande', e.code) ?? e.libelle}{' '}
                      {e.terminee ? <StatusBadge statut="VALIDE" label="Terminée" /> : e.en_cours ? <StatusBadge statut="EN_COURS" label="En cours" /> : null}
                    </li>
                  ))}
                </ol>
              </>
            )}
          </Charge>
        </>
      )}
      {actif === 'historique' && (
        <Charge etat={hist.etat} recharger={hist.recharger} vide={(l) => l.length === 0} messageVide="Aucun historique.">
          {(l) => (
            <ul className="detail-liste">
              {l.map((h) => {
                const c = commentaireLisible(h.commentaire);
                return (
                  <li key={h.identifiant}>
                    <span className="cell-meta">{formatDateHeure(h.date_action)}</span> — {h.etape_libelle?.trim() || libelle('actionDemande', h.action)}
                    {h.acteur_nom ? ` · ${libelleConnu('acteur', h.acteur_nom) ?? h.acteur_nom}` : ''}
                    {c ? <span className="muted"> · « {c} »</span> : null}
                  </li>
                );
              })}
            </ul>
          )}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Document administratif ────────────────────────────────────────────── */

export function DocumentContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }];
  const { etat, recharger } = useChargement(() => getDocument(id), [id]);
  useTitre(etat.statut === 'ok' ? (etat.data.numero ?? libelle('typeDocument', etat.data.type_document)) : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const d = etat.data;
  return (
    <Onglets onglets={defs} actif={titreOnglet(['detail'], onglet)} onChange={onOnglet}>
      <Champs>
        <Champ label="Numéro">{d.numero}</Champ>
        <Champ label="Type">{libelle('typeDocument', d.type_document)}</Champ>
        <Champ label="Statut"><StatusBadge statut={d.statut} /></Champ>
        <Champ label="Demandeur"><LienPersonne id={d.demandeur_identifiant} nom={d.demandeur_nom} matricule={d.demandeur_matricule} /></Champ>
        <Champ label="Unité">{d.demandeur_unite}</Champ>
        <Champ label="Déposée le">{formatDateHeure(d.cree_le)}</Champ>
        <Champ label="Délai de traitement">{d.delai_sla_heures} h{d.en_retard ? ' · en retard' : ''}</Champ>
        <Champ label="Position dans la file">{d.rang_dans_file}</Champ>
        <Champ label="Commentaire du demandeur">{d.commentaire_demandeur}</Champ>
        <Champ label="Livrable">{d.reference_livrable}</Champ>
        <Champ label="Dérogation à l’ordre d’arrivée">{d.justification_derogation_fifo}</Champ>
      </Champs>
    </Onglets>
  );
}

/* ─── Formation ─────────────────────────────────────────────────────────── */

export function FormationContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'historique', label: 'Historique' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getFormation(id), [id]);
  const hist = useChargement(() => getFormationHistorique(id), [id], actif === 'historique');
  useTitre(etat.statut === 'ok' ? (etat.data.numero ?? libelleTypeFormation(etat.data.type_formation)) : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const f = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Numéro">{f.numero}</Champ>
            <Champ label="Formation">{libelleTypeFormation(f.type_formation)}</Champ>
            <Champ label="Statut"><StatusBadge statut={f.statut} /></Champ>
            <Champ label="Demandeur"><LienPersonne id={f.demandeur_identifiant} nom={f.demandeur_nom} matricule={f.demandeur_matricule} /></Champ>
            <Champ label="Origine">{libelle('origineFormation', f.origine)}</Champ>
            <Champ label="Cible">
              {f.cible === 'UNITE'
                ? <DetailLink type="unite" id={f.unite_cible_identifiant} libelle={f.unite_cible_libelle} />
                : (f.collaborateurs_cibles ?? []).map((c, i) => (
                  <span key={c.identifiant}>{i > 0 ? ', ' : ''}<LienPersonne id={c.identifiant} nom={c.nom} matricule={c.matricule} /></span>
                ))}
            </Champ>
            <Champ label="Organisme">{f.organisme}</Champ>
            <Champ label="Durée">{f.duree_heures} h</Champ>
            <Champ label="Coût estimé">{f.cout_estime != null ? `${f.cout_estime}` : null}</Champ>
            <Champ label="Objectifs">{f.objectifs_pedagogiques}</Champ>
            <Champ label="Justification">{f.justification}</Champ>
            <Champ label="Commentaire RH">{f.commentaire_rh}</Champ>
            <Champ label="Déposée le">{formatDateHeure(f.cree_le)}</Champ>
          </Champs>
        </>
      )}
      {actif === 'historique' && (
        <Charge etat={hist.etat} recharger={hist.recharger} vide={(l) => l.length === 0} messageVide="Aucun historique.">
          {(l) => (
            <ul className="detail-liste">
              {l.map((h) => (
                <li key={h.identifiant}>
                  <span className="cell-meta">{formatDateHeure(h.date_action)}</span> — {h.etape_libelle?.trim() || 'Étape'}
                  {h.acteur_nom ? ` · ${h.acteur_nom}` : ''}
                  {commentaireLisible(h.commentaire) ? <span className="muted"> · « {commentaireLisible(h.commentaire)} »</span> : null}
                </li>
              ))}
            </ul>
          )}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Plainte ───────────────────────────────────────────────────────────── */

export function PlainteContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'journal', label: 'Journal' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getPlainte(id), [id]);
  useTitre(etat.statut === 'ok' ? etat.data.numero_ticket : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const p = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <>
          <Champs>
            <Champ label="Numéro">{p.numero_ticket}</Champ>
            <Champ label="Titre">{p.titre}</Champ>
            <Champ label="Type">{libelle('typePlainte', p.type_plainte)}</Champ>
            <Champ label="Statut"><StatusBadge statut={p.statut} /></Champ>
            <Champ label="Auteur"><LienPersonne id={p.auteur_identifiant} nom={p.auteur_nom} matricule={p.auteur_matricule} /></Champ>
            <Champ label="Déposée le">{formatDateHeure(p.cree_le)}</Champ>
            <Champ label="Description">{p.description}</Champ>
            <Champ label="Pièces jointes">{p.pieces_jointes.length > 0 ? `${p.pieces_jointes.length} pièce(s)` : 'Aucune'}</Champ>
            <Champ label="Commentaire RH">{p.commentaire_rh}</Champ>
          </Champs>
        </>
      )}
      {actif === 'journal' && (
        p.log_actions.length === 0 ? <p className="detail-vide">Aucune action enregistrée.</p> : (
          <ul className="detail-liste">
            {p.log_actions.map((a, i) => (
              <li key={i}>
                <span className="cell-meta">{formatDateHeure(a.horodatage)}</span>
                {a.nouveau_statut ? ` — ${libelle('statut', a.nouveau_statut)}` : ''}
                {a.acteur_nom ? ` · ${a.acteur_nom}` : ''}
                {commentaireLisible(a.commentaire) ? <span className="muted"> · « {commentaireLisible(a.commentaire)} »</span> : null}
              </li>
            ))}
          </ul>
        )
      )}
    </Onglets>
  );
}

/* ─── Heures supplémentaires ────────────────────────────────────────────── */

export function HeuresSupContenu({ id, onglet, onOnglet }: Props) {
  const defs = [{ id: 'detail', label: 'Détail' }, { id: 'comptees', label: 'Heures comptées' }];
  const actif = titreOnglet(defs.map((d) => d.id), onglet);
  const { etat, recharger } = useChargement(() => getDemandeHeuresSupDetail(id), [id]);
  const cpt = useChargement(() => getHeuresSupComptees(id), [id], actif === 'comptees');
  useTitre(etat.statut === 'ok' ? (etat.data.numero ?? 'Heures supplémentaires') : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const d = etat.data;
  return (
    <Onglets onglets={defs} actif={actif} onChange={onOnglet}>
      {actif === 'detail' && (
        <Champs>
          <Champ label="Numéro">{d.numero}</Champ>
          <Champ label="Statut">{LIBELLES_STATUT_HEURES_SUP[d.statut] ?? TIRET}</Champ>
          <Champ label="Site"><DetailLink type="site" id={d.site_id} libelle={d.site_libelle} /></Champ>
          <Champ label="Période">{formatPeriode(d.date_debut, d.date_fin)}</Champ>
          <Champ label="Ouverture">{d.ouverture_avant_minutes} min avant, {d.ouverture_apres_minutes} min après l’horaire</Champ>
          <Champ label="Demandeur">{d.demandeur_nom}</Champ>
          <Champ label="Bénéficiaires">
            {d.beneficiaires.map((b, i) => (
              <span key={b.collaborateur_id}>{i > 0 ? ', ' : ''}<LienPersonne id={b.collaborateur_id} nom={b.nom} matricule={b.matricule} /></span>
            ))}
          </Champ>
          <Champ label="Motif">{d.motif}</Champ>
          <Champ label="Décision">{d.decide_le ? `${formatDateHeure(d.decide_le)}${d.decide_par ? ` · ${d.decide_par}` : ''}` : null}</Champ>
          <Champ label="Motif du refus">{d.motif_refus}</Champ>
          <Champ label="Motif d’annulation">{d.motif_annulation}</Champ>
          <Champ label="Déposée le">{d.cree_le ? formatDateHeure(d.cree_le) : null}</Champ>
        </Champs>
      )}
      {actif === 'comptees' && (
        <Charge
          etat={cpt.etat}
          recharger={cpt.recharger}
          vide={(r) => (Array.isArray(r) ? r : r.contenu).length === 0}
          messageVide="Aucune heure comptée pour cette demande."
        >
          {(r) => {
            const l: HeuresComptees[] = Array.isArray(r) ? r : r.contenu;
            return (
              <Tableau colonnes={['Bénéficiaire', 'Jour', 'Heures comptées', 'État']}>
                {l.map((h) => (
                  <tr key={`${h.collaborateur_id}|${h.journee}`}>
                    <td><LienPersonne id={h.collaborateur_id} nom={h.nom} matricule={h.matricule} /></td>
                    <td>{formatDate(h.journee)}</td>
                    <td>{formatHeuresMinutes(h.minutes_comptees)}</td>
                    <td>{LIBELLES_ETAT_COMPTEES[h.etat] ?? TIRET}</td>
                  </tr>
                ))}
              </Tableau>
            );
          }}
        </Charge>
      )}
    </Onglets>
  );
}

/* ─── Pointage manuel ───────────────────────────────────────────────────── */

export function PointageManuelContenu({ id, onglet, onOnglet }: Props) {
  const { etat, recharger } = useChargement(() => getDemandeManuelleDetail(id), [id]);
  useTitre(etat.statut === 'ok' ? (etat.data.numero ?? 'Pointage manuel') : null);
  if (etat.statut !== 'ok') return <Charge etat={etat} recharger={recharger}>{() => null}</Charge>;
  const d = etat.data;
  const r = d.pointage_rejete;
  const v = lireVigilance(d as unknown as Record<string, unknown>);
  return (
    <Onglets onglets={[{ id: 'detail', label: 'Détail' }]} actif={titreOnglet(['detail'], onglet)} onChange={onOnglet}>
      <BandeauSignaux signaux={v.signaux} />
      <Champs>
        <Champ label="Numéro">{d.numero}</Champ>
        <Champ label="Collaborateur"><LienPersonne id={d.collaborateur_id} nom={d.collaborateur_nom} matricule={d.collaborateur_matricule} /></Champ>
        <Champ label="Unité">{d.unite_libelle}</Champ>
        <Champ label="Site">{d.site_libelle}</Champ>
        <Champ label="Type">{d.type === 'ENTREE' ? 'Entrée' : d.type === 'SORTIE' ? 'Sortie' : null}</Champ>
        <Champ label="Heure de la tentative">{d.heure_declaree ? formatDateHeure(d.heure_declaree) : null}</Champ>
        <Champ label="Justification">{d.justification}</Champ>
        <Champ label="Statut"><StatusBadge statut={d.statut} /></Champ>
        <Champ label="Valideur">
          {d.valideur
            ? <DetailLink type="collaborateur" id={d.valideur.collaborateur_id} libelle={d.valideur.nom_complet} />
            : 'Validation par les RH'}
        </Champ>
        <Champ label="Décision">{d.decide_le ? `${formatDateHeure(d.decide_le)}${d.decide_par ? ` · ${d.decide_par}` : ''}` : null}</Champ>
        <Champ label="Motif du refus">{d.motif_refus}</Champ>
        <Champ label="Pointages manuels ce mois-ci">
          {d.manuels_du_mois != null ? `${d.manuels_du_mois}${d.seuil_mois != null ? ` (seuil d’alerte : ${d.seuil_mois})` : ''}` : null}
        </Champ>
        {lignesVigilance(v, d.statut).map((l) => <Champ key={l.label} label={l.label}>{l.valeur}</Champ>)}
      </Champs>
      {r ? (
        <>
          <h3 className="detail-h3">Pointage refusé d’origine</h3>
          <Champs>
            <Champ label="Statut">{r.statut_libelle}</Champ>
            <Champ label="Motif">{r.motif_rejet}</Champ>
            <Champ label="Distance">{r.distance_metres != null ? `${Math.round(r.distance_metres)} m` : null}</Champ>
            <Champ label="Instant">{r.server_ts ? formatDateHeure(r.server_ts) : null}</Champ>
          </Champs>
        </>
      ) : null}
    </Onglets>
  );
}
