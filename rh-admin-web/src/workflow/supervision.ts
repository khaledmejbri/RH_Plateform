import type { CatalogueWorkflow, DelegationAdmin, HistoriqueAdmin } from '../api/workflowApi';
import { humanizeCode } from '../utils/filtres';
import { formatNombre, jourIso } from '../utils/format';
import {
  libelleActionDecision,
  libelleActionEcheance,
  libelleDecisionTache,
  libelleEvenementHistorique,
  libelleOrigineCandidat,
  libelleRaisonSaut,
  libelleStatutInstance,
  type ToneStatutWorkflow,
} from './libelles';

export const DELEGATION_DUREE_MAX_JOURS = 90;
export const DELEGATION_MOTIF_MAX = 300;
export const REAFFECTATION_COLLABS_MIN = 1;
export const REAFFECTATION_COLLABS_MAX = 10;
export const REAFFECTATION_MOTIF_MIN = 5;
export const REAFFECTATION_MOTIF_MAX = 500;

export type StatutDelegationCode = 'ANNULEE' | 'ACTIVE' | 'PLANIFIEE' | 'EXPIREE';

const STATUT_DELEGATION: Readonly<Record<StatutDelegationCode, { libelle: string; tone: ToneStatutWorkflow }>> = {
  ACTIVE: { libelle: 'Active', tone: 'success' },
  PLANIFIEE: { libelle: 'Planifiée', tone: 'info' },
  EXPIREE: { libelle: 'Expirée', tone: 'neutral' },
  ANNULEE: { libelle: 'Annulée', tone: 'danger' },
};

/**
 * Statut dérivé d'une délégation pour affichage (D-W7b-04).
 * Ordre : Annulée (`annulee_le != null`) → Active (`active === true`) → Planifiée (`du > aujourdhui`) → Expirée.
 */
export function statutDelegation(
  d: Pick<DelegationAdmin, 'annulee_le' | 'active' | 'du'>,
  aujourdhui: string = jourIso(),
): StatutDelegationCode {
  if (d.annulee_le != null && d.annulee_le !== '') return 'ANNULEE';
  if (d.active === true) return 'ACTIVE';
  if (d.du > aujourdhui) return 'PLANIFIEE';
  return 'EXPIREE';
}

export function libelleStatutDelegation(statut: StatutDelegationCode): string {
  return STATUT_DELEGATION[statut].libelle;
}

export function toneStatutDelegation(statut: StatutDelegationCode): ToneStatutWorkflow {
  return STATUT_DELEGATION[statut].tone;
}

const JOUR_ISO_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** Nombre de jours entre `du` et `au` (`AAAA-MM-JJ`), ou `null` si une date est invalide. */
export function diffJours(du: string, au: string): number | null {
  const m1 = JOUR_ISO_RE.exec(du);
  const m2 = JOUR_ISO_RE.exec(au);
  if (!m1 || !m2) return null;
  const a1 = +m1[1];
  const mo1 = +m1[2];
  const j1 = +m1[3];
  const a2 = +m2[1];
  const mo2 = +m2[2];
  const j2 = +m2[3];
  const d1 = new Date(Date.UTC(a1, mo1 - 1, j1));
  const d2 = new Date(Date.UTC(a2, mo2 - 1, j2));
  if (
    d1.getUTCFullYear() !== a1 || d1.getUTCMonth() !== mo1 - 1 || d1.getUTCDate() !== j1 ||
    d2.getUTCFullYear() !== a2 || d2.getUTCMonth() !== mo2 - 1 || d2.getUTCDate() !== j2
  ) {
    return null;
  }
  return Math.round((d2.getTime() - d1.getTime()) / 86_400_000);
}

export type ChampErreurDelegation = 'delegant_id' | 'delegue_id' | 'du' | 'au' | 'objet_types' | 'motif';

export type FormulaireDelegationInput = {
  delegant_id: string;
  delegue_id: string;
  du: string;
  au: string;
  motif: string;
  objet_types_selection_mode?: 'TOUS' | 'LISTE';
  objet_types?: ReadonlyArray<string>;
};

export type ValidationDelegation = {
  valide: boolean;
  erreurs: Partial<Record<ChampErreurDelegation, string>>;
  premiereErreur: string | null;
};

/**
 * Validation client alignée sur `DelegationService.java` (D-W7b-04) :
 * - `du >= aujourdhui`, `au >= du`, `diffJours(du, au) <= 90` (`du + 90` accepté, `du + 91` refusé)
 * - `delegant_id !== delegue_id`
 * - `motif` ≤ 300 caractères
 */
export function validerFormulaireDelegation(
  form: FormulaireDelegationInput,
  aujourdhui: string = jourIso(),
): ValidationDelegation {
  const erreurs: Partial<Record<ChampErreurDelegation, string>> = {};
  const delegant = form.delegant_id.trim();
  const delegue = form.delegue_id.trim();

  if (!delegant) {
    erreurs.delegant_id = 'Sélectionnez le collaborateur délégant.';
  }
  if (!delegue) {
    erreurs.delegue_id = 'Sélectionnez le collaborateur délégué.';
  } else if (delegant && delegant === delegue) {
    erreurs.delegue_id = 'Le délégant et le délégué doivent être distincts.';
  }

  const duValide = diffJours(form.du, form.du) !== null;
  const auValide = diffJours(form.au, form.au) !== null;

  if (!duValide) {
    erreurs.du = 'Indiquez une date de début valide (jj/mm/aaaa).';
  } else if (form.du < aujourdhui) {
    erreurs.du = 'La date de début ne peut pas être antérieure à aujourd’hui.';
  }

  if (!auValide) {
    erreurs.au = 'Indiquez une date de fin valide (jj/mm/aaaa).';
  } else if (duValide) {
    if (form.au < form.du) {
      erreurs.au = 'La date de fin doit être postérieure ou égale à la date de début.';
    } else {
      const jours = diffJours(form.du, form.au);
      if (jours !== null && jours > DELEGATION_DUREE_MAX_JOURS) {
        erreurs.au = `La durée d’une délégation ne peut pas dépasser ${DELEGATION_DUREE_MAX_JOURS} jours.`;
      }
    }
  }

  if (form.objet_types_selection_mode === 'LISTE' && (!form.objet_types || form.objet_types.length === 0)) {
    erreurs.objet_types = 'Sélectionnez au moins un type de demande ou choisissez « Tous les types ».';
  }

  if (form.motif.trim().length > DELEGATION_MOTIF_MAX) {
    erreurs.motif = `Le motif ne peut pas dépasser ${DELEGATION_MOTIF_MAX} caractères.`;
  }

  const liste = Object.values(erreurs).filter(Boolean);
  return {
    valide: liste.length === 0,
    erreurs,
    premiereErreur: liste[0] ?? null,
  };
}

export type ValidationReaffectation = {
  valide: boolean;
  erreurs: Partial<Record<'collaborateur_ids' | 'motif', string>>;
  premiereErreur: string | null;
};

/**
 * Validation client de la réaffectation RH alignée sur `ReaffectationService.java` (D-W7b-03) :
 * - 1 à 10 collaborateurs uniques
 * - motif obligatoire entre 5 et 500 caractères (espaces de bord exclus)
 */
export function validerReaffectation(
  collaborateurIds: ReadonlyArray<string>,
  motif: string,
): ValidationReaffectation {
  const erreurs: Partial<Record<'collaborateur_ids' | 'motif', string>> = {};
  const uniques = Array.from(new Set(collaborateurIds.map((id) => id.trim()).filter(Boolean)));

  if (uniques.length < REAFFECTATION_COLLABS_MIN) {
    erreurs.collaborateur_ids = 'Sélectionnez au moins un collaborateur.';
  } else if (uniques.length > REAFFECTATION_COLLABS_MAX) {
    erreurs.collaborateur_ids = `Vous pouvez sélectionner au maximum ${REAFFECTATION_COLLABS_MAX} collaborateurs.`;
  }

  const len = motif.trim().length;
  if (len < REAFFECTATION_MOTIF_MIN) {
    erreurs.motif = `Le motif doit contenir au moins ${REAFFECTATION_MOTIF_MIN} caractères.`;
  } else if (len > REAFFECTATION_MOTIF_MAX) {
    erreurs.motif = `Le motif ne peut pas dépasser ${REAFFECTATION_MOTIF_MAX} caractères.`;
  }

  const liste = Object.values(erreurs).filter(Boolean);
  return {
    valide: liste.length === 0,
    erreurs,
    premiereErreur: liste[0] ?? null,
  };
}

/** Libellé d'un candidat avec mention « pour le compte de … » si présent. */
export function formatOrigineCandidat(origine: string | null | undefined, pourNom?: string | null): string {
  const base = libelleOrigineCandidat(origine);
  const nom = (pourNom ?? '').trim();
  return nom ? `${base} (pour le compte de ${nom})` : base;
}

function str(v: unknown): string {
  return typeof v === 'string' ? v.trim() : v == null ? '' : String(v);
}

/** Résumé lisible en français d'une ligne d'historique admin (sans HTML ni code brut). */
export function formatHistoriqueDetails(
  h: Pick<HistoriqueAdmin, 'type_evenement' | 'details'>,
  libellesEtapes: ReadonlyMap<string, string> = new Map(),
): string {
  const d = h.details ?? {};
  switch (h.type_evenement) {
    case 'INSTANCE_DEMARREE': {
      const modele = str(d.modele_code);
      const version = d.version != null ? `v${String(d.version)}` : '';
      return [modele ? `Modèle ${modele}` : '', version].filter(Boolean).join(' · ');
    }
    case 'ETAPE_SAUTEE': {
      const code = str(d.etape_code);
      const nom = (code && libellesEtapes.get(code)) || code;
      const raison = str(d.raison);
      return [nom, raison ? `(${libelleRaisonSaut(raison)})` : ''].filter(Boolean).join(' ');
    }
    case 'DECISION': {
      const action = str(d.action);
      const decision = str(d.decision);
      const lib = action ? libelleActionDecision(action) : decision ? libelleDecisionTache(decision) : '';
      const com = str(d.commentaire);
      return [lib, com ? `« ${com} »` : ''].filter(Boolean).join(' — ');
    }
    case 'REAFFECTEE': {
      const noms = Array.isArray(d.nouveaux_candidats) ? d.nouveaux_candidats.map(str).filter(Boolean) : [];
      const motif = str(d.motif);
      const debloquee = d.debloquee === true ? 'instance débloquée' : '';
      return [
        noms.length > 0 ? `Vers : ${noms.join(', ')}` : '',
        motif ? `« ${motif} »` : '',
        debloquee,
      ].filter(Boolean).join(' · ');
    }
    case 'ESCALADEE': {
      const noms = Array.isArray(d.nouveaux_candidats) ? d.nouveaux_candidats.map(str).filter(Boolean) : [];
      return noms.length > 0 ? `Vers : ${noms.join(', ')}` : '';
    }
    case 'RELANCE': {
      const nb = d.nb_relances;
      return nb != null ? `Relance n° ${String(nb)}` : '';
    }
    case 'ECHEANCE_ATTEINTE': {
      const action = str(d.action);
      return action ? libelleActionEcheance(action) : '';
    }
    case 'DELEGATION_APPLIQUEE': {
      const orig = str(d.origine);
      return orig ? libelleOrigineCandidat(orig) : '';
    }
    case 'EFFET_REJETE': {
      const msg = str(d.message);
      const code = str(d.code);
      return msg || code;
    }
    case 'INSTANCE_TERMINEE': {
      const st = str(d.statut);
      return st ? libelleStatutInstance(st) : '';
    }
    case 'INSTANCE_ANNULEE':
    case 'TACHE_EXPIREE':
    case 'COMPLEMENT_EXPIRE': {
      const motif = str(d.motif);
      return motif ? `« ${motif} »` : '';
    }
    case 'INSTANCE_ERREUR': {
      const t = d.tentatives;
      return t != null ? `Après ${String(t)} tentative(s)` : '';
    }
    default:
      return '';
  }
}

export function formatEvenementHistorique(
  h: Pick<HistoriqueAdmin, 'type_evenement' | 'details'>,
  libellesEtapes?: ReadonlyMap<string, string>,
): string {
  const titre = libelleEvenementHistorique(h.type_evenement);
  const detail = formatHistoriqueDetails(h, libellesEtapes);
  return detail ? `${titre} — ${detail}` : titre;
}

const LIBELLES_VARIABLES_AFFICHAGE: Readonly<Record<string, string>> = {
  affichage_type: 'Type affiché',
  affichage_periode: 'Période affichée',
  affichage_duree: 'Durée affichée',
  affichage_sla: 'Échéance réglementaire',
  affichage_heure: 'Heure affichée',
  affichage_signaux: 'Signaux de vigilance',
  affichage_lieu: 'Lieu affiché',
  affichage_organisme: 'Organisme affiché',
};

/** Libellé français d'une variable du dossier (catalogue prioritaire, puis clés `affichage_*`, puis humanisation). */
export function libelleVariableDossier(
  cle: string,
  libellesCatalogue: ReadonlyMap<string, string> = new Map(),
): string {
  return libellesCatalogue.get(cle) ?? LIBELLES_VARIABLES_AFFICHAGE[cle] ?? (humanizeCode(cle) || cle);
}

/** Libellé des types d'objet couverts par une délégation (`null` / vide = tous). */
export function formatObjetTypesDelegation(
  objetTypes: ReadonlyArray<string> | null | undefined,
  catalogue: Pick<CatalogueWorkflow, 'types'> | null | undefined,
): string {
  if (!objetTypes || objetTypes.length === 0) return 'Tous les types';
  const index = new Map((catalogue?.types ?? []).map((t) => [t.objet_type, t.libelle]));
  return objetTypes.map((code) => index.get(code) ?? code).join(', ');
}

/** Période des 30 derniers jours (`du`, `au` au format ISO `AAAA-MM-JJ`, Africa/Tunis). */
export function periodeParDefaut30Jours(ref: Date = new Date()): { du: string; au: string } {
  const au = jourIso(ref);
  const debut = new Date(ref.getTime() - 30 * 24 * 3600 * 1000);
  return { du: jourIso(debut), au };
}

/** Formatage français d'un délai moyen en heures (ex. « 12,5 h »). */
export function formatDelaiHeures(heures: number | null | undefined): string {
  if (heures === null || heures === undefined || Number.isNaN(heures)) return '—';
  return `${formatNombre(heures, 2)} h`;
}

/** Événements workflow qui ouvrent « Mes tâches » depuis la cloche (D-W7b-06). */
export const EVENEMENTS_VERS_TACHES: ReadonlySet<string> = new Set([
  'WF_TACHE_CREEE',
  'WF_RELANCE',
  'WF_ESCALADE',
]);

/**
 * Calcule la route cible d'une notification workflow depuis la cloche (D-W7b-06),
 * ou `null` pour laisser `NotificationBell` ouvrir l'objet métier via `DetailProvider`.
 */
export function routeNotificationWorkflow(n: {
  evenement?: string | null;
  objet_type?: string | null;
  objet_id?: string | null;
}): string | null {
  const ev = n.evenement;
  if (!ev) return null;
  if (ev === 'WF_BLOQUEE') return '/app/workflows/instances?statut=BLOQUEE';
  if (ev === 'WF_DELEGATION') return '/app/workflows/delegations';
  if (EVENEMENTS_VERS_TACHES.has(ev)) {
    const q = new URLSearchParams();
    if (n.objet_type) q.set('objet_type', n.objet_type);
    if (n.objet_id) q.set('objet_id', n.objet_id);
    const qs = q.toString();
    return `/app/taches${qs ? `?${qs}` : ''}`;
  }
  return null;
}
