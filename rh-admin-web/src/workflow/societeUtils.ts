/**
 * Fonctions pures des paramètres de société (D-W7-11) :
 * conversion heures (virgule française) ⇄ minutes, contrôle de répartition journalière.
 */

export const JOURS_SEMAINE: ReadonlyArray<{ code: string; libelle: string }> = [
  { code: 'LUN', libelle: 'Lundi' },
  { code: 'MAR', libelle: 'Mardi' },
  { code: 'MER', libelle: 'Mercredi' },
  { code: 'JEU', libelle: 'Jeudi' },
  { code: 'VEN', libelle: 'Vendredi' },
  { code: 'SAM', libelle: 'Samedi' },
  { code: 'DIM', libelle: 'Dimanche' },
];

/** Bornes du serveur (SocieteParametresService.validerBornes / validerDurees). */
export const DUREE_JOUR_MAX_MINUTES = 720;
export const HEBDO_MIN_MINUTES = 60;
export const HEBDO_MAX_MINUTES = 3600;
export const HEBDO_PAS_MINUTES = 30;
export const SORTIE_MIN_MINUTES = 30;
export const SORTIE_MAX_MINUTES = 480;
export const SORTIE_PAS_MINUTES = 15;

/** Message d'erreur si la durée hebdomadaire (minutes) est hors bornes ; null si valide. */
export function erreurDureeHebdo(minutes: number | null): string | null {
  if (
    minutes === null ||
    minutes < HEBDO_MIN_MINUTES ||
    minutes > HEBDO_MAX_MINUTES ||
    minutes % HEBDO_PAS_MINUTES !== 0
  ) {
    return 'La durée hebdomadaire doit être comprise entre 1 h et 60 h, par pas de 30 minutes.';
  }
  return null;
}

/** Message d'erreur si la durée maximale de sortie (minutes) est hors bornes ; null si valide. */
export function erreurDureeSortie(minutes: number): string | null {
  if (
    !Number.isInteger(minutes) ||
    minutes < SORTIE_MIN_MINUTES ||
    minutes > SORTIE_MAX_MINUTES ||
    minutes % SORTIE_PAS_MINUTES !== 0
  ) {
    return 'La durée maximale d’une autorisation de sortie doit être comprise entre 30 et 480 minutes, par pas de 15.';
  }
  return null;
}

/** Convertit une saisie en heures (ex. "40", "7,5", "8.25") en minutes entières ; null si invalide ou négatif. */
export function heuresVersMinutes(texte: string): number | null {
  const propre = texte.trim().replace(/\s+/g, '').replace(',', '.');
  if (!propre) return null;
  if (!/^\d+(?:\.\d+)?$/.test(propre)) return null;
  const heures = Number(propre);
  if (!Number.isFinite(heures) || heures < 0) return null;
  return Math.round(heures * 60);
}

/** Convertit des minutes entières en heures au format français à virgule (ex. 450 → "7,5", 2400 → "40"). */
export function minutesVersHeuresTexte(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined || !Number.isFinite(minutes)) return '';
  const heures = Math.round((minutes / 60) * 100) / 100;
  return heures.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

export type ControleRepartition = {
  valide: boolean;
  sommeMinutes: number;
  ecartMinutes: number;
  message: string | null;
};

/**
 * Contrôle en direct que la somme des durées des jours travaillés égale la durée hebdomadaire (D-W7-11).
 * Chaque jour travaillé doit avoir une durée strictement positive (<= 12 h = 720 min, bornes du serveur).
 */
export function controlerRepartition(
  joursTravailles: ReadonlyArray<string>,
  dureeHebdoMinutes: number,
  dureeJourMinutes: Readonly<Record<string, number>>,
): ControleRepartition {
  if (joursTravailles.length === 0) {
    return {
      valide: false,
      sommeMinutes: 0,
      ecartMinutes: -dureeHebdoMinutes,
      message: 'Sélectionnez au moins un jour travaillé.',
    };
  }
  let somme = 0;
  for (const jour of joursTravailles) {
    const val = dureeJourMinutes[jour] ?? 0;
    if (!Number.isFinite(val) || val <= 0 || val > DUREE_JOUR_MAX_MINUTES) {
      const lib = JOURS_SEMAINE.find((j) => j.code === jour)?.libelle ?? jour;
      return {
        valide: false,
        sommeMinutes: somme,
        ecartMinutes: somme - dureeHebdoMinutes,
        message: `La durée du ${lib.toLowerCase()} doit être strictement positive et ne pas dépasser 12 h.`,
      };
    }
    somme += Math.round(val);
  }
  const ecart = somme - dureeHebdoMinutes;
  if (ecart !== 0) {
    return {
      valide: false,
      sommeMinutes: somme,
      ecartMinutes: ecart,
      message: `La somme des jours (${minutesVersHeuresTexte(somme)} h) ne correspond pas à la durée hebdomadaire (${minutesVersHeuresTexte(dureeHebdoMinutes)} h).`,
    };
  }
  return { valide: true, sommeMinutes: somme, ecartMinutes: 0, message: null };
}
