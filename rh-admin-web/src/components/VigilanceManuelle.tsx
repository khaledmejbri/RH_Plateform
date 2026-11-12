import type { ReactNode } from 'react';
import type { SignalVigilance, VigilanceManuelle } from '../api/presenceApi';
import { formatDateHeure, formatDelai, formatNombre } from '../utils/format';

/** Pastille « Vigilance » des listes (D-P24.a). */
export function PastilleVigilance({ avecSignal, signaux = [] }: { avecSignal: boolean; signaux?: SignalVigilance[] }) {
  if (!avecSignal) return null;
  return (
    <span className="badge badge--warning" title={signaux.map((s) => s.libelle).join(String.fromCharCode(10)) || undefined}>
      Vigilance
    </span>
  );
}

/** Bandeau des signaux de vigilance : libellés du serveur, tels quels. */
export function BandeauSignaux({ signaux, titre = 'Signaux de vigilance' }: { signaux: SignalVigilance[]; titre?: string }) {
  if (signaux.length === 0) return null;
  return (
    <div className="alert alert--warning" role="status">
      <strong>{titre}</strong>
      <ul style={{ margin: '4px 0 0', paddingLeft: 20 }}>
        {signaux.map((s) => (
          <li key={s.code || s.libelle}>{s.libelle}</li>
        ))}
      </ul>
    </div>
  );
}

/** « 11/11 à 08:02 » (D-P23.c). */
export function jourHeureCourt(iso: string): string {
  const s = formatDateHeure(iso);
  return s.length >= 16 ? `${s.slice(0, 5)} à ${s.slice(11, 16)}` : s;
}

const metres = (v: number) => `${formatNombre(v, 1)} m`;
const metresEntiers = (v: number) => `${formatNombre(v, 0)} m`;

/** Échéance affichée seulement pour une demande en attente ou expirée. */
export function libelleEcheance(statut: string | undefined, iso?: string | null): string | null {
  if (!iso) return null;
  if (statut === 'EN_ATTENTE') return `expire le ${jourHeureCourt(iso)}`;
  if (statut === 'EXPIREE') return `expirée le ${jourHeureCourt(iso)}`;
  return null;
}

/** Lignes d'information du valideur (D-P24) ; seuls les champs reçus sont servis. Pas de coordonnées. */
export function lignesVigilance(v: VigilanceManuelle, statut?: string): { label: string; valeur: ReactNode }[] {
  const l: { label: string; valeur: ReactNode }[] = [];
  if (v.statutRefusLibelle) l.push({ label: 'Refus d’origine', valeur: v.statutRefusLibelle });
  if (v.distanceMetres != null) {
    l.push({
      label: 'Distance au site',
      valeur: `${metres(v.distanceMetres)} du site${v.rayonMetres != null ? `, zone de ${metresEntiers(v.rayonMetres)}` : ''}`,
    });
  } else if (v.rayonMetres != null) {
    l.push({ label: 'Zone du site', valeur: metresEntiers(v.rayonMetres) });
  }
  if (v.precisionMetres != null) l.push({ label: 'Précision GPS', valeur: `± ${metres(v.precisionMetres)}` });
  if (v.modeQr) l.push({ label: 'Mode QR du site', valeur: v.modeQr === 'ECRAN' ? 'Écran' : 'Papier' });
  if (v.delaiDemandeMinutes != null) l.push({ label: 'Délai de la demande', valeur: `demandée ${formatDelai(v.delaiDemandeMinutes)} après` });
  const ech = libelleEcheance(statut, v.echeance);
  if (ech) l.push({ label: 'Échéance', valeur: ech });
  if (v.refusDuJour != null) l.push({ label: 'Refus de pointage du jour', valeur: formatNombre(v.refusDuJour, 0) });
  if (v.commentaireValideur) l.push({ label: 'Commentaire du valideur', valeur: v.commentaireValideur });
  if (v.delaiDecisionMinutes != null) l.push({ label: 'Délai de décision', valeur: formatDelai(v.delaiDecisionMinutes) });
  return l;
}

/** Bandeau d'information D-P25 sur un site en QR papier (texte du doc fonctionnel). */
export function BandeauQrPapier() {
  return (
    <div className="alert alert--info" role="note">
      QR papier : un code imprimé peut être photographié et scanné hors du site. Le mode Écran (code renouvelé toutes
      les 30 secondes) est recommandé.
    </div>
  );
}
