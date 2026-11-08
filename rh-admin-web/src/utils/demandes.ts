import type { DemandeAdministrative } from '../api/rhClient';
import { formatDate, formatDuree, formatHeure, formatPeriode as formatPeriodeFr } from './format';
import { libelle } from './libelles';

/** Intitulé d'une demande : « Congé annuel », « Autorisation de sortie »… */
export function intituleDemande(d: Pick<DemandeAdministrative, 'type_demande' | 'contenu'>): string {
  if (d.type_demande === 'CONGE') {
    const c = (d.contenu as { type_conge?: string } | undefined)?.type_conge;
    if (c) return libelle('typeConge', c);
  }
  return libelle('typeDemande', d.type_demande);
}

/** Un commentaire qui n'est qu'un code (ou en contient un) n'est pas affiché. */
export function commentaireLisible(c: string | null | undefined): string | null {
  const t = (c ?? '').trim();
  if (!t) return null;
  if (/^[A-Z0-9_ ]+$/.test(t) || /[A-Z]{3,}_[A-Z_]+/.test(t) || /\b(CONGE|RRH|RO)\b/.test(t)) return null;
  return t;
}

export function periodeDemande(d: Pick<DemandeAdministrative, 'type_demande' | 'contenu' | 'periode_debut' | 'periode_fin'>): string {
  if (d.type_demande === 'AUTORISATION_SORTIE') {
    const c = d.contenu as { date_jour?: string; heure_debut?: string; heure_fin?: string } | undefined;
    if (c?.date_jour) {
      const heures = c.heure_debut && c.heure_fin ? ` · ${formatHeure(c.heure_debut)} → ${formatHeure(c.heure_fin)}` : '';
      let duree = '';
      if (c.heure_debut && c.heure_fin) {
        const [hd, md] = c.heure_debut.split(':').map(Number);
        const [hf, mf] = c.heure_fin.split(':').map(Number);
        const mins = (hf * 60 + mf) - (hd * 60 + md);
        if (mins > 0) duree = ` (${formatDuree(mins)})`;
      }
      return `${formatDate(c.date_jour)}${heures}${duree}`;
    }
  }
  if (d.periode_debut) return formatPeriodeFr(d.periode_debut, d.periode_fin ?? d.periode_debut);
  return '—';
}

/** Champs saisis par le demandeur, hors type de congé et période. */
export function contenuLignes(d: Pick<DemandeAdministrative, 'contenu'>): Array<[string, string]> {
  const c = (d.contenu ?? {}) as Record<string, unknown>;
  const libelles: Record<string, string> = {
    motif: 'Motif', lieu: 'Lieu', certificat: 'Certificat', pieces_jointes: 'Pièces jointes',
  };
  return Object.entries(libelles)
    .filter(([cle]) => c[cle] != null && c[cle] !== '' && !(Array.isArray(c[cle]) && (c[cle] as unknown[]).length === 0))
    .map(([cle, lib]) => [lib, Array.isArray(c[cle]) ? (c[cle] as unknown[]).join(', ') : String(c[cle])]);
}
