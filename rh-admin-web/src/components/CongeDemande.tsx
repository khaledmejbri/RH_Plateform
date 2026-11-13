import type { ReactNode } from 'react';
import type { DemandeAdministrative } from '../api/rhClient';
import { formatDateHeure, formatJours, TIRET } from '../utils/format';

/** Congé annuel : seul type décompté du solde. */
export function estCongeAnnuel(d: Pick<DemandeAdministrative, 'type_demande' | 'contenu'>): boolean {
  return d.type_demande === 'CONGE' && (d.contenu as { type_conge?: string } | undefined)?.type_conge === 'ANNUEL';
}

/** « 2 jours » pour un congé annuel décompté, « — » sinon (les autres types ne touchent pas au solde). */
export function joursDecomptes(d: DemandeAdministrative): string {
  return estCongeAnnuel(d) && d.jours_decomptes !== undefined && d.jours_decomptes !== null ? formatJours(d.jours_decomptes) : TIRET;
}

/** Texte « Solde acquis : X j, dont Y réservés par d'autres demandes » (décideur de l'étape en cours). */
export function texteSoldeDemandeur(d: DemandeAdministrative): string | null {
  if (d.solde_disponible_demandeur === undefined || d.solde_disponible_demandeur === null) return null;
  const reserve = d.solde_reserve_autres_demandes ?? 0;
  return `Solde acquis : ${formatJours(d.solde_disponible_demandeur)}, dont ${formatJours(reserve)} réservés par d’autres demandes`;
}

/** Lignes (libellé, valeur) propres au congé, à ajouter à la grille de détail d'une demande. */
export function lignesConge(d: DemandeAdministrative): Array<[string, ReactNode]> {
  if (d.type_demande !== 'CONGE') return [];
  const lignes: Array<[string, ReactNode]> = [['Jours décomptés', joursDecomptes(d)]];
  if (d.periode_premier_jour_libelle) lignes.push(['Premier jour', d.periode_premier_jour_libelle]);
  if (d.periode_dernier_jour_libelle && d.periode_dernier_jour !== d.periode_premier_jour) {
    lignes.push(['Dernier jour', d.periode_dernier_jour_libelle]);
  }
  const solde = texteSoldeDemandeur(d);
  if (solde) lignes.push(['Solde du demandeur', solde]);
  if (d.solde_apres_demande !== undefined && d.solde_apres_demande !== null) {
    lignes.push(['Solde après cette demande', formatJours(d.solde_apres_demande)]);
  }
  return lignes;
}

/** Encadré « Annulée après approbation » : motif, auteur, date et jours recrédités. */
export function BlocAnnulationApresApprobation({ d }: { d: DemandeAdministrative }) {
  if (!d.annulee_apres_approbation) return null;
  return (
    <div className="callout">
      <p className="callout__title">Annulée après approbation</p>
      {d.motif_annulation ? <p>{d.motif_annulation}</p> : null}
      <p className="muted small">
        {[
          d.annule_par_nom ? `Par ${d.annule_par_nom}` : null,
          d.annule_apres_approbation_le ? formatDateHeure(d.annule_apres_approbation_le) : null,
          d.jours_recredites !== undefined && d.jours_recredites !== null ? `${formatJours(d.jours_recredites)} recrédités au solde` : null,
        ].filter(Boolean).join(' · ')}
      </p>
    </div>
  );
}
