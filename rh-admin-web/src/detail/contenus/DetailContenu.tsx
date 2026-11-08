import type { DetailRef } from '../refs';
import CollaborateurContenu from './CollaborateurContenu';
import {
  AlerteContenu, CampagneContenu, DemandeContenu, DocumentContenu, EvaluationContenu, FormationContenu,
  HeuresSupContenu, PlainteContenu, PointageManuelContenu, SiteContenu, UniteContenu,
} from './AutresContenus';
import { peutVoirPlaintes } from '../contexte';
import { Vide } from '../Etats';

/** Contenu (onglets) d'un objet, commun à la modale et à la page complète. */
export default function DetailContenu({
  detail, onglet, onOnglet,
}: { detail: DetailRef; onglet?: string; onOnglet: (o: string) => void }) {
  const p = { id: detail.id, onglet, onOnglet };
  switch (detail.type) {
    case 'collaborateur': return <CollaborateurContenu {...p} />;
    case 'unite': return <UniteContenu {...p} />;
    case 'site': return <SiteContenu {...p} />;
    case 'campagne': return <CampagneContenu {...p} />;
    case 'evaluation': return <EvaluationContenu {...p} />;
    case 'alerte': return <AlerteContenu {...p} />;
    case 'demande': return <DemandeContenu {...p} />;
    case 'document': return <DocumentContenu {...p} />;
    case 'formation': return <FormationContenu {...p} />;
    case 'plainte': return peutVoirPlaintes() ? <PlainteContenu {...p} /> : <Vide>Vous n’avez pas accès à cette information.</Vide>;
    case 'heures-sup': return <HeuresSupContenu {...p} />;
    case 'pointage-manuel': return <PointageManuelContenu {...p} />;
  }
}
