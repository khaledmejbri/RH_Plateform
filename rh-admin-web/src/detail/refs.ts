export type DetailType =
  | 'collaborateur' | 'unite' | 'site' | 'campagne' | 'evaluation' | 'demande' | 'document'
  | 'formation' | 'plainte' | 'heures-sup' | 'pointage-manuel' | 'alerte';

export type DetailRef = { type: DetailType; id: string; libelle?: string };

/** Adresse de la page complète (URL propre, partageable) de chaque type d'objet. */
export function routeDe(ref: Pick<DetailRef, 'type' | 'id'>): string {
  const id = encodeURIComponent(ref.id);
  switch (ref.type) {
    case 'collaborateur': return `/app/collaborateurs/${id}`;
    case 'unite': return `/app/organisation/unites/${id}`;
    case 'site': return `/app/pointage/sites/${id}`;
    case 'campagne': return `/app/evaluations/campagnes/${id}`;
    case 'evaluation': return `/app/evaluations/${id}`;
    case 'demande': return `/app/demandes-administratives/${id}`;
    case 'document': return `/app/documents-administratifs/${id}`;
    case 'formation': return `/app/formations/${id}`;
    case 'plainte': return `/app/plaintes/${id}`;
    case 'heures-sup': return `/app/heures-sup/${id}`;
    case 'pointage-manuel': return `/app/pointage/manuels/${id}`;
    case 'alerte': return `/app/evaluations/alertes-ecart/${id}`;
  }
}

export const TITRE_PAR_DEFAUT: Record<DetailType, string> = {
  collaborateur: 'Collaborateur', unite: 'Unité', site: 'Site', campagne: 'Campagne', evaluation: 'Évaluation',
  demande: 'Demande administrative', document: 'Document administratif', formation: 'Formation',
  plainte: 'Plainte', 'heures-sup': 'Heures supplémentaires', 'pointage-manuel': 'Pointage manuel', alerte: 'Alerte d’écart',
};

/** Type d'objet porté par une notification (2F) vers type de fiche. */
export function typeDepuisNotification(objetType?: string | null): DetailType | null {
  switch ((objetType ?? '').toUpperCase()) {
    case 'DEMANDE': return 'demande';
    case 'DOCUMENT': return 'document';
    case 'PLAINTE': return 'plainte';
    case 'FORMATION': return 'formation';
    case 'EVALUATION': return 'evaluation';
    case 'ALERTE_ECART': return 'alerte';
    case 'POINTAGE_MANUEL': return 'pointage-manuel';
    case 'HEURES_SUP': return 'heures-sup';
    default: return null;
  }
}
