import { styleCouleur } from '../utils/palette';

/** Pastille colorée par la palette du paramétrage (contraste AA) ; toujours accompagnée de son libellé. */
export default function PastilleCouleur({ couleur, children }: { couleur: string | null | undefined; children: string }) {
  return (
    <span className="pastille" style={styleCouleur(couleur)}>
      {children}
    </span>
  );
}
