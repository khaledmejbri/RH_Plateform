import { useSyncExternalStore } from 'react';
import { getLibelles, SERVICES, type ServiceParametre, type ValeurLibelle } from '../api/parametresApi';
import { DOMAINES_PARAMETRES, remplacerLibelles, VALEUR_NON_RECONNUE, type Domaine } from './libelles';

/**
 * Table de libellés alimentée par les trois `GET .../parametres/libelles` (lot 3, ADR § 7) :
 * chargée à la connexion, revalidée toutes les 60 s (ETag) et à l'ouverture des formulaires.
 * Aucune liste de secours : un service coupé laisse ses libellés vides (« Valeur non reconnue »).
 */

/** Référentiel de l'API → domaine de la table de libellés. */
const DOMAINE_PAR_REFERENTIEL: Record<string, Domaine> = {
  FAMILLE_METIER: 'familleMetier',
  NIVEAU_SENIORITE: 'niveauSeniorite',
  TYPE_DOCUMENT: 'typeDocument',
  TYPE_CONGE: 'typeConge',
  TYPE_DEMANDE: 'typeDemande',
  TYPE_PLAINTE: 'typePlainte',
  PROFIL_ACCES: 'profilAcces',
  STATUT_COLLABORATEUR: 'statutCollaborateur',
  NIVEAU_ALERTE: 'niveauAlerte',
  CONCLUSION_ALERTE: 'conclusionAlerte',
  TYPE_QUESTION: 'typeQuestion',
  ECHELLE_ETOILES: 'echelleEtoiles',
  APPRECIATION: 'appreciation',
  ETAT_ECART: 'etatEcart',
};

type EtatService = { etag: string | null; referentiels: Record<string, ValeurLibelle[]> };

type Instantane = {
  /** Première tentative terminée (réussie ou non). */
  pret: boolean;
  /** Au moins un service n'a pas répondu à la dernière tentative. */
  erreur: boolean;
  /** Incrémenté quand le contenu change : sert de clé de rendu. */
  version: number;
  referentiels: Record<string, ValeurLibelle[]>;
};

const services: Record<ServiceParametre, EtatService> = {
  referentiel: { etag: null, referentiels: {} },
  evaluation: { etag: null, referentiels: {} },
  presence: { etag: null, referentiels: {} },
};

let instantane: Instantane = { pret: false, erreur: false, version: 0, referentiels: {} };
const abonnes = new Set<() => void>();
let enCours: Promise<void> | null = null;

function publier(changement: Partial<Instantane>) {
  instantane = { ...instantane, ...changement };
  abonnes.forEach((f) => f());
}

function fusionner(): Record<string, ValeurLibelle[]> {
  const tout: Record<string, ValeurLibelle[]> = {};
  for (const s of SERVICES) Object.assign(tout, services[s].referentiels);
  return tout;
}

function reconstruireLibelles(tout: Record<string, ValeurLibelle[]>) {
  const tables = new Map<Domaine, Record<string, string>>(DOMAINES_PARAMETRES.map((d) => [d, {}]));
  for (const [ref, valeurs] of Object.entries(tout)) {
    const domaine = DOMAINE_PAR_REFERENTIEL[ref];
    const table = domaine ? tables.get(domaine) : undefined;
    if (!table) continue;
    for (const v of valeurs) table[v.code] = v.libelle;
  }
  tables.forEach((table, domaine) => remplacerLibelles(domaine, table));
}

async function charger(): Promise<void> {
  const resultats = await Promise.allSettled(SERVICES.map((s) => getLibelles(s, services[s].etag)));
  let change = false;
  let erreur = false;
  resultats.forEach((r, i) => {
    const etat = services[SERVICES[i]];
    if (r.status === 'fulfilled') {
      if (r.value.statut === 'modifie') {
        etat.etag = r.value.etag;
        etat.referentiels = r.value.libelles.referentiels ?? {};
        change = true;
      }
    } else {
      erreur = true;
    }
  });
  if (change) {
    const tout = fusionner();
    reconstruireLibelles(tout);
    publier({ pret: true, erreur, version: instantane.version + 1, referentiels: tout });
  } else if (!instantane.pret || instantane.erreur !== erreur) {
    publier({ pret: true, erreur });
  }
}

/** Relit les libellés maintenant (ouverture d'un formulaire, après une écriture) ; les appels simultanés sont fusionnés. */
export function rafraichirParametres(): Promise<void> {
  if (!enCours) {
    enCours = charger().finally(() => {
      enCours = null;
    });
  }
  return enCours;
}

/** À la connexion : première lecture puis revalidation toutes les 60 s. Retourne l'arrêt. */
export function demarrerParametres(): () => void {
  void rafraichirParametres();
  const minuteur = window.setInterval(() => void rafraichirParametres(), 60_000);
  return () => window.clearInterval(minuteur);
}

function abonner(f: () => void) {
  abonnes.add(f);
  return () => {
    abonnes.delete(f);
  };
}

/** État de la table de libellés ; le composant se rend de nouveau quand elle change. */
export function useParametres(): Instantane {
  return useSyncExternalStore(abonner, () => instantane);
}

export type OptionParametre = { valeur: string; libelle: string; actif: boolean };

function trier(valeurs: ValeurLibelle[]): ValeurLibelle[] {
  return [...valeurs].sort((a, b) => a.ordre - b.ordre || a.libelle.localeCompare(b.libelle, 'fr'));
}

/** Valeurs actives d'un référentiel, dans l'ordre choisi : listes des formulaires (D-R02.d). */
export function valeursActives(referentiel: string): ValeurLibelle[] {
  return trier((instantane.referentiels[referentiel] ?? []).filter((v) => v.actif));
}

/** Libellé courant d'une valeur d'un référentiel ; inconnu → « Valeur non reconnue ». */
export function libelleReferentiel(referentiel: string, code: string | null | undefined): string {
  if (code === null || code === undefined || code === '') return '—';
  const cle = code.trim().toUpperCase();
  const v = (instantane.referentiels[referentiel] ?? []).find((x) => x.code.toUpperCase() === cle);
  return v ? v.libelle : VALEUR_NON_RECONNUE;
}

/** Options d'un formulaire : actives seulement, plus la valeur déjà portée par l'objet édité si elle est inactive. */
export function optionsFormulaire(referentiel: string, valeurCourante?: string | null): OptionParametre[] {
  const options: OptionParametre[] = valeursActives(referentiel).map((v) => ({ valeur: v.code, libelle: v.libelle, actif: true }));
  if (valeurCourante && !options.some((o) => o.valeur === valeurCourante)) {
    const inactive = (instantane.referentiels[referentiel] ?? []).find((v) => v.code === valeurCourante);
    options.push({
      valeur: valeurCourante,
      libelle: inactive ? `${inactive.libelle} (inactif)` : libelleReferentiel(referentiel, valeurCourante),
      actif: false,
    });
  }
  return options;
}

/** Options d'un filtre : valeurs actives + valeurs inactives encore utilisées (mention « inactif »). */
export function optionsFiltre(referentiel: string, utilisees: Iterable<string> = []): Array<{ valeur: string; libelle: string }> {
  const usage = new Set(utilisees);
  const options = trier(instantane.referentiels[referentiel] ?? [])
    .filter((v) => v.actif || usage.has(v.code))
    .map((v) => ({ valeur: v.code, libelle: v.actif ? v.libelle : `${v.libelle} (inactif)` }));
  for (const code of usage) {
    if (!options.some((o) => o.valeur === code)) options.push({ valeur: code, libelle: libelleReferentiel(referentiel, code) });
  }
  return options;
}

/** Valeur complète (couleur, seuil, pièce jointe…) servie par les libellés. */
export function valeurReferentiel(referentiel: string, code: string | null | undefined): ValeurLibelle | undefined {
  if (!code) return undefined;
  return (instantane.referentiels[referentiel] ?? []).find((v) => v.code === code);
}

/** Couleur paramétrée d'un statut dans SON référentiel ; absente ou neutre → null (le ton par défaut s'applique). */
export function couleurStatut(referentiel: string, code: string | null | undefined): string | null {
  if (!code) return null;
  const cle = code.trim().toUpperCase();
  const c = (instantane.referentiels[referentiel] ?? []).find((v) => v.code.toUpperCase() === cle)?.couleur;
  return c && c.toUpperCase() !== 'NEUTRE' ? c : null;
}

/** Niveaux d'alerte d'écart (actifs ou non), du seuil le plus haut au plus bas. */
export function niveauxAlerte(): ValeurLibelle[] {
  return [...(instantane.referentiels.NIVEAU_ALERTE ?? [])].sort((a, b) => (b.seuil_ecart ?? 0) - (a.seuil_ecart ?? 0));
}
