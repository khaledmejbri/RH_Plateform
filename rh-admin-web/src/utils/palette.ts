/**
 * Palette fixe de 8 couleurs du paramétrage (ADR lot 3 § 5, D-R02.b).
 * Chaque paire fond / texte a un contraste de 6,9:1 au minimum (WCAG AA exige 4,5:1) ; la couleur n'est
 * jamais seule : les pastilles portent toujours le libellé.
 */
export type CouleurCode = 'NEUTRE' | 'BLEU' | 'CYAN' | 'VERT' | 'AMBRE' | 'ORANGE' | 'ROUGE' | 'VIOLET';

export const PALETTE: ReadonlyArray<{ code: CouleurCode; nom: string; fond: string; texte: string; bord: string }> = [
  { code: 'NEUTRE', nom: 'Neutre', fond: '#EEF1F5', texte: '#2F3947', bord: '#B9C2CE' },
  { code: 'BLEU', nom: 'Bleu', fond: '#DCEBFF', texte: '#0B3F8A', bord: '#8DB4EC' },
  { code: 'CYAN', nom: 'Cyan', fond: '#D3F1F6', texte: '#0A4F59', bord: '#7FC8D3' },
  { code: 'VERT', nom: 'Vert', fond: '#D9F2E2', texte: '#0E5428', bord: '#86C99F' },
  { code: 'AMBRE', nom: 'Ambre', fond: '#FFEFC2', texte: '#5E3C00', bord: '#E3C26B' },
  { code: 'ORANGE', nom: 'Orange', fond: '#FFE1CC', texte: '#7F3300', bord: '#EAA473' },
  { code: 'ROUGE', nom: 'Rouge', fond: '#FCDADA', texte: '#8F1A1A', bord: '#E69090' },
  { code: 'VIOLET', nom: 'Violet', fond: '#E9DEFA', texte: '#47268A', bord: '#B79DE3' },
];

export function styleCouleur(code: string | null | undefined): { background: string; color: string; borderColor: string } {
  const c = PALETTE.find((p) => p.code === (code ?? 'NEUTRE').toUpperCase()) ?? PALETTE[0];
  return { background: c.fond, color: c.texte, borderColor: c.bord };
}

export function nomCouleur(code: string | null | undefined): string {
  return (PALETTE.find((p) => p.code === (code ?? 'NEUTRE').toUpperCase()) ?? PALETTE[0]).nom;
}
