/**
 * Module unique de formatage de la Plateforme RH (D-L01.a et D-L01.c) :
 * dates jj/mm/aaaa, heures hh:mm (Africa/Tunis), nombres à virgule, durées, personnes.
 * Aucune page ne doit formater une date ou un nombre par elle-même.
 */

const FUSEAU = 'Africa/Tunis';
const JOUR_ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
const HEURE_ISO = /^(\d{2}):(\d{2})(?::\d{2})?$/;

export const TIRET = '—';
export const COLLABORATEUR_INTROUVABLE = 'Collaborateur introuvable';
export const SITE_INTROUVABLE = 'Site introuvable';

function parties(d: Date): Record<string, string> {
  const out: Record<string, string> = {};
  for (const p of new Intl.DateTimeFormat('fr-FR', {
    timeZone: FUSEAU,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(d)) {
    out[p.type] = p.value;
  }
  return out;
}

function enDate(iso: string): Date | null {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Date seule `jj/mm/aaaa`. Un jour civil `AAAA-MM-JJ` est reformaté sans décalage de fuseau. */
export function formatDate(iso?: string | null): string {
  if (!iso) return TIRET;
  const jour = JOUR_ISO.exec(iso);
  if (jour) return `${jour[3]}/${jour[2]}/${jour[1]}`;
  const d = enDate(iso);
  if (!d) return TIRET;
  const p = parties(d);
  return `${p.day}/${p.month}/${p.year}`;
}

/** Date et heure `jj/mm/aaaa hh:mm` (secondes seulement sur demande, cas D-P18.c). */
export function formatDateHeure(iso?: string | null, avecSecondes = false): string {
  if (!iso) return TIRET;
  if (JOUR_ISO.test(iso)) return formatDate(iso);
  const d = enDate(iso);
  if (!d) return TIRET;
  const p = parties(d);
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}${avecSecondes ? `:${p.second}` : ''}`;
}

/** Heure `hh:mm` d'un instant ISO ou d'une heure locale `hh:mm[:ss]`. */
export function formatHeure(valeur?: string | null, avecSecondes = false): string {
  if (!valeur) return TIRET;
  const h = HEURE_ISO.exec(valeur);
  if (h) return `${h[1]}:${h[2]}`;
  const d = enDate(valeur);
  if (!d) return TIRET;
  const p = parties(d);
  return `${p.hour}:${p.minute}${avecSecondes ? `:${p.second}` : ''}`;
}

/** Jour civil `AAAA-MM-JJ` (Africa/Tunis) d'un instant : valeur technique des champs de filtre, jamais affichée. */
export function jourIso(d: Date = new Date()): string {
  const p = parties(d);
  return `${p.year}-${p.month}-${p.day}`;
}

function nbJoursInclus(debut: string, fin: string): number | null {
  const a = JOUR_ISO.exec(debut.slice(0, 10));
  const b = JOUR_ISO.exec(fin.slice(0, 10));
  if (!a || !b) return null;
  const ms = Date.UTC(+b[1], +b[2] - 1, +b[3]) - Date.UTC(+a[1], +a[2] - 1, +a[3]);
  return Math.round(ms / 86_400_000) + 1;
}

/** « du 06/11/2026 au 08/11/2026 (3 jours) » ; un seul jour : « 06/11/2026 (1 jour) ». */
export function formatPeriode(debut?: string | null, fin?: string | null): string {
  if (!debut && !fin) return TIRET;
  if (!debut || !fin) return formatDate(debut ?? fin);
  const n = nbJoursInclus(debut, fin);
  const suffixe = n && n > 0 ? ` (${n} ${n > 1 ? 'jours' : 'jour'})` : '';
  if (formatDate(debut) === formatDate(fin)) return `${formatDate(debut)}${suffixe}`;
  return `du ${formatDate(debut)} au ${formatDate(fin)}${suffixe}`;
}

/** Nombre à virgule décimale française (« 13,5 »). */
export function formatNombre(n?: number | null, decimales = 1): string {
  if (n === null || n === undefined || Number.isNaN(n)) return TIRET;
  return n.toLocaleString('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: decimales });
}

/** « 4,2 / 5 » */
export function formatNote(n?: number | null, sur = 5, decimales = 2): string {
  if (n === null || n === undefined || Number.isNaN(n)) return TIRET;
  return `${formatNombre(n, decimales)} / ${sur}`;
}

/** « 17 % » (espace insécable avant le symbole). */
export function formatPourcentage(n?: number | null, decimales = 0): string {
  if (n === null || n === undefined || Number.isNaN(n)) return TIRET;
  return `${formatNombre(n, decimales)} %`;
}

/** Durée en minutes : « 2 h 30 », « 45 min », « 3 h ». */
export function formatDuree(minutes?: number | null): string {
  if (minutes === null || minutes === undefined || Number.isNaN(minutes)) return TIRET;
  const total = Math.max(0, Math.round(minutes));
  const h = Math.floor(total / 60);
  const m = total % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, '0')}`;
}

/** Durée en minutes au format `hh:mm` (tableaux de temps de travail). */
export function formatHeuresMinutes(minutes?: number | null): string {
  if (minutes === null || minutes === undefined || Number.isNaN(minutes)) return TIRET;
  const total = Math.max(0, Math.round(minutes));
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

/** « Prénom Nom (matricule) » dans un tableau ; sans matricule, le nom seul. */
export function formatPersonne(nom?: string | null, matricule?: string | null): string {
  const n = (nom ?? '').trim();
  if (!n) return COLLABORATEUR_INTROUVABLE;
  return matricule ? `${n} (${matricule})` : n;
}

export type PersonneLisible = { nom: string; matricule: string | null };

type FicheNommable = {
  identifiant: string;
  matricule?: string | null;
  prenom?: string | null;
  nom?: string | null;
  name?: string | null;
};

export function nomComplet(c: Pick<FicheNommable, 'prenom' | 'nom' | 'name'>): string {
  return [c.prenom, c.nom ?? c.name].map((s) => (s ?? '').trim()).filter(Boolean).join(' ');
}

/** Index identifiant de fiche → nom lisible, construit depuis les fiches déjà chargées. */
export function indexerPersonnes(fiches: ReadonlyArray<FicheNommable>): Map<string, PersonneLisible> {
  const m = new Map<string, PersonneLisible>();
  for (const c of fiches) {
    const nom = nomComplet(c);
    if (nom) m.set(c.identifiant, { nom, matricule: c.matricule ?? null });
  }
  return m;
}

/** Libellé d'une personne retrouvée par identifiant ; introuvable → « Collaborateur introuvable », jamais l'identifiant. */
export function personneParId(
  index: ReadonlyMap<string, PersonneLisible>,
  id?: string | null,
  repli: string = COLLABORATEUR_INTROUVABLE,
): string {
  const p = id ? index.get(id) : undefined;
  return p ? formatPersonne(p.nom, p.matricule) : repli;
}

/** Nom seul (pour une phrase) retrouvé par identifiant. */
export function nomParId(
  index: ReadonlyMap<string, PersonneLisible>,
  id?: string | null,
  repli: string = COLLABORATEUR_INTROUVABLE,
): string {
  return (id ? index.get(id)?.nom : undefined) ?? repli;
}

/** Motif d'un identifiant technique (UUID complet ou tronqué) : à ne jamais afficher. */
const MOTIF_UUID = /\b[0-9a-f]{8}(?:-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|…|\.\.\.)/i;
export function ressembleAUnIdentifiant(s: string | null | undefined): boolean {
  return !!s && MOTIF_UUID.test(s);
}
