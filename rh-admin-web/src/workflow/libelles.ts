/**
 * Libellés français des codes techniques du moteur de workflow (D-L01 : jamais de code brut à l'écran).
 * Un code inconnu s'affiche « Valeur non reconnue ».
 */

const INCONNU = 'Valeur non reconnue';

function lire(table: Readonly<Record<string, string>>, code: string | null | undefined): string {
  if (code === null || code === undefined || code === '') return '—';
  return table[code] ?? INCONNU;
}

const STATUT_VERSION: Readonly<Record<string, string>> = {
  BROUILLON: 'Brouillon',
  PUBLIEE: 'Publiée',
  RETIREE: 'Retirée',
};
export const libelleStatutVersion = (c: string | null | undefined) => lire(STATUT_VERSION, c);

const NIVEAU_ERREUR: Readonly<Record<string, string>> = {
  ERREUR: 'Erreur',
  AVERTISSEMENT: 'Avertissement',
};
export const libelleNiveauErreur = (c: string | null | undefined) => lire(NIVEAU_ERREUR, c);

export const TYPES_ETAPE: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'VALIDATION', libelle: 'Validation (approbation / refus)' },
  { code: 'TACHE', libelle: 'Tâche simple (à faire)' },
  { code: 'TACHES_PARALLELES', libelle: 'Tâches en parallèle' },
  { code: 'INFORMATION', libelle: 'Information seule' },
];
export const libelleTypeEtape = (c: string | null | undefined) =>
  lire(Object.fromEntries(TYPES_ETAPE.map((t) => [t.code, t.libelle])), c);

export const STATUT_INSTANCE: Readonly<Record<string, string>> = {
  EN_COURS: 'En cours',
  BLOQUEE: 'Bloquée',
  APPROUVEE: 'Approuvée',
  REFUSEE: 'Refusée',
  ANNULEE: 'Annulée',
  EXPIREE: 'Expirée',
  ERREUR: 'En erreur',
};
export const libelleStatutInstance = (c: string | null | undefined) => lire(STATUT_INSTANCE, c);

const STATUTS_OBJET_FINAUX: ReadonlySet<string> = new Set([
  'APPROUVEE',
  'REFUSEE',
  'ANNULEE',
  'EXPIREE',
  'REJETEE',
  'INTEGREE_PLAN',
  'DISPONIBLE',
]);

/** Vrai si la demande, le document ou la formation est dans un statut final : plus de tâche à traiter. */
export function statutObjetFinal(statut: string | null | undefined): boolean {
  return statut !== null && statut !== undefined && STATUTS_OBJET_FINAUX.has(statut);
}

const STATUT_ETAPE_SUIVI: Readonly<Record<string, string>> = {
  ACTIVE: 'En cours',
  TERMINEE: 'Terminée',
  SAUTEE: 'Non applicable',
  ANNULEE: 'Annulée',
  A_VENIR: 'À venir',
};
export const libelleStatutEtapeSuivi = (c: string | null | undefined) => lire(STATUT_ETAPE_SUIVI, c);

const RESULTAT_ETAPE: Readonly<Record<string, string>> = {
  APPROUVEE: 'Approuvée',
  REFUSEE: 'Refusée',
  FAITE: 'Faite',
  EXPIREE: 'Expirée',
};
export const libelleResultatEtape = (c: string | null | undefined) => lire(RESULTAT_ETAPE, c);

const RAISON_SAUT: Readonly<Record<string, string>> = {
  CONDITION: 'Condition non remplie',
  AUCUN_INTERVENANT: 'Aucun intervenant trouvé',
};
export const libelleRaisonSaut = (c: string | null | undefined) => lire(RAISON_SAUT, c);

const STATUT_ETAPE_SIMULEE: Readonly<Record<string, string>> = {
  ACTIVEE: 'Activée',
  SAUTEE: 'Sautée',
  BLOQUEE: 'Bloquée',
};
export const libelleStatutEtapeSimulee = (c: string | null | undefined) => lire(STATUT_ETAPE_SIMULEE, c);

const TYPE_CHANGEMENT: Readonly<Record<string, string>> = {
  AJOUTEE: 'Étape ajoutée',
  RETIREE: 'Étape retirée',
  MODIFIEE: 'Étape modifiée',
};
export const libelleTypeChangement = (c: string | null | undefined) => lire(TYPE_CHANGEMENT, c);

const ELEMENT_COMPARAISON: Readonly<Record<string, string>> = {
  NOTIFICATIONS_DYNAMIQUES: 'Notifications dynamiques',
};

/** Libellé d'un élément dans le tableau de comparaison de versions (étape ou bloc dynamique). */
export function libelleElementComparaison(code: string): string {
  return ELEMENT_COMPARAISON[code] ?? code;
}

/** Libellé du type de changement adapté aux blocs non-étapes comme `NOTIFICATIONS_DYNAMIQUES`. */
export function libelleTypeChangementElement(code: string, typeChangement: string | null | undefined): string {
  if (code === 'NOTIFICATIONS_DYNAMIQUES' && typeChangement === 'MODIFIEE') {
    return 'Configuration modifiée';
  }
  return libelleTypeChangement(typeChangement);
}

const CHAMP_MODIFIE: Readonly<Record<string, string>> = {
  libelle: 'Libellé',
  type: 'Type d’étape',
  condition: 'Condition',
  intervenants: 'Intervenants',
  roles: 'Rôles',
  repli_si_vide: 'Repli si aucun intervenant',
  taches: 'Tâches parallèles',
  public: 'Destinataires',
  actions: 'Actions permises',
  controle_avant_approbation: 'Contrôle avant approbation',
  commentaire_obligatoire_si: 'Commentaire obligatoire',
  delai: 'Délai',
  relances: 'Relances',
  a_echeance: 'Action à l’échéance',
  si_approuve: 'Transition si approuvé',
  si_refuse: 'Transition si refusé',
  notifications: 'Notifications',
  notifier_ro: 'Notification au responsable (RO)',
  portee_sites: 'Portée des sites',
  site_ids: 'Sites sélectionnés',
  types_pointage: 'Types de pointage',
  canal: 'Canal de notification',
  NOTIFICATIONS_DYNAMIQUES: 'Notifications dynamiques',
};
export const libelleChampModifie = (c: string | null | undefined) => lire(CHAMP_MODIFIE, c);

const ACTION_JOURNAL: Readonly<Record<string, string>> = {
  CREATION: 'Création du modèle',
  MODIFICATION: 'Modification',
  PUBLICATION: 'Publication d’une version',
  RETRAIT: 'Retrait d’une version',
  ACTIVATION: 'Activation du modèle',
  DESACTIVATION: 'Désactivation du modèle',
  SUPPRESSION_BROUILLON: 'Suppression d’un brouillon',
  BASCULE_MODE: 'Changement de circuit',
};
export const libelleActionJournal = (c: string | null | undefined) => lire(ACTION_JOURNAL, c);

const PUBLIC_FIXE: Readonly<Record<string, string>> = {
  DEMANDEUR: 'Le demandeur',
  CANDIDATS: 'Les intervenants de l’étape',
  'ROLE:RH': 'Les ressources humaines',
  'ROLE:ADMIN': 'Les administrateurs',
  MANAGER_NOEUD: 'Le responsable de l’unité du demandeur',
};

/** Libellé d'un public de notification ; `FONCTION:<code>` est résolu par la liste des fonctions actives. */
export function libellePublic(code: string, fonctions: ReadonlyArray<{ code: string; libelle: string }> = []): string {
  if (code.startsWith('FONCTION:')) {
    const c = code.slice('FONCTION:'.length);
    const f = fonctions.find((x) => x.code === c);
    return f ? `Fonction « ${f.libelle} »` : 'Fonction inconnue';
  }
  return PUBLIC_FIXE[code] ?? INCONNU;
}

const ROLE: Readonly<Record<string, string>> = {
  RH: 'Ressources humaines',
  ADMIN: 'Administrateur',
};
export const libelleRole = (c: string | null | undefined) => lire(ROLE, c);

const ACTION_DECISION: Readonly<Record<string, string>> = {
  APPROUVER: 'Approuver',
  REFUSER: 'Refuser',
  RENVOYER: 'Demander un complément',
  FAIT: 'Marquer comme fait',
};
export function libelleActionDecision(c: string | null | undefined): string {
  return lire(ACTION_DECISION, c);
}

const STATUT_TACHE: Readonly<Record<string, string>> = {
  A_TRAITER: 'À traiter',
  RESERVEE: 'En cours d’enregistrement',
  TRAITEE: 'Traitée',
  ANNULEE: 'Annulée',
  EXPIREE: 'Expirée',
};
export const libelleStatutTache = (c: string | null | undefined) => lire(STATUT_TACHE, c);

const TYPE_TACHE: Readonly<Record<string, string>> = {
  VALIDATION: 'Validation',
  TACHE: 'Tâche',
  COMPLEMENT: 'Complément',
};
export const libelleTypeTache = (c: string | null | undefined) => lire(TYPE_TACHE, c);

const DECISION_TACHE: Readonly<Record<string, string>> = {
  APPROUVEE: 'Approuvée',
  REFUSEE: 'Refusée',
  RENVOYEE: 'Renvoyée pour complément',
  FAITE: 'Faite',
};
export const libelleDecisionTache = (c: string | null | undefined) => lire(DECISION_TACHE, c);

const ORIGINE_CANDIDAT: Readonly<Record<string, string>> = {
  TITULAIRE: 'Titulaire',
  DELEGUE: 'Délégué',
  DELEGUE_AUTO: 'Délégué automatique (congé)',
  ESCALADE: 'Escalade',
  REAFFECTATION: 'Réaffectation RH',
};
export const libelleOrigineCandidat = (c: string | null | undefined) => lire(ORIGINE_CANDIDAT, c);

const ORIGINE_DELEGATION: Readonly<Record<string, string>> = {
  MANUELLE: 'Manuelle',
  AUTO_CONGE: 'Automatique (congé)',
};
export const libelleOrigineDelegation = (c: string | null | undefined) => lire(ORIGINE_DELEGATION, c);

const TYPE_EVENEMENT_HISTORIQUE: Readonly<Record<string, string>> = {
  INSTANCE_DEMARREE: 'Workflow démarré',
  ETAPE_SAUTEE: 'Étape non applicable',
  DECISION: 'Décision enregistrée',
  COMPLEMENT_EXPIRE: 'Délai de complément expiré',
  TACHE_EXPIREE: 'Tâche expirée',
  INSTANCE_TERMINEE: 'Workflow terminé',
  EFFET_REJETE: 'Enregistrement métier refusé',
  DELEGATION_APPLIQUEE: 'Délégation appliquée',
  RELANCE: 'Relance envoyée',
  ECHEANCE_ATTEINTE: 'Échéance atteinte',
  ESCALADEE: 'Tâche escaladée',
  INSTANCE_ERREUR: 'Instance en erreur',
  INSTANCE_ANNULEE: 'Workflow annulé',
  REAFFECTEE: 'Tâche réaffectée',
};
export const libelleEvenementHistorique = (c: string | null | undefined) => lire(TYPE_EVENEMENT_HISTORIQUE, c);

const ACTION_ECHEANCE: Readonly<Record<string, string>> = {
  RIEN: 'Aucune action automatique',
  ESCALADER: 'Escalade',
  EXPIRER: 'Expiration',
  REFUSER_AUTO: 'Refus automatique',
  APPROUVER_AUTO: 'Approbation automatique',
};
export const libelleActionEcheance = (c: string | null | undefined) => lire(ACTION_ECHEANCE, c);

export type ToneStatutWorkflow = 'success' | 'danger' | 'warning' | 'info' | 'neutral' | 'progress';

const TONE_STATUT_INSTANCE: Readonly<Record<string, ToneStatutWorkflow>> = {
  EN_COURS: 'progress',
  BLOQUEE: 'danger',
  APPROUVEE: 'success',
  REFUSEE: 'danger',
  ANNULEE: 'neutral',
  EXPIREE: 'warning',
  ERREUR: 'danger',
};
export function toneStatutInstance(c: string | null | undefined): ToneStatutWorkflow {
  return (c ? TONE_STATUT_INSTANCE[c] : undefined) ?? 'neutral';
}

const TONE_STATUT_TACHE: Readonly<Record<string, ToneStatutWorkflow>> = {
  A_TRAITER: 'warning',
  RESERVEE: 'progress',
  TRAITEE: 'success',
  ANNULEE: 'neutral',
  EXPIREE: 'danger',
};
export function toneStatutTache(c: string | null | undefined): ToneStatutWorkflow {
  return (c ? TONE_STATUT_TACHE[c] : undefined) ?? 'neutral';
}
