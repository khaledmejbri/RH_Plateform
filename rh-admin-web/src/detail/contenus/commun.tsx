import type { ReactNode } from 'react';
import { TIRET } from '../../utils/format';
import { Icon, type IconName } from '../../components/ui';

export function Champs({ children }: { children: ReactNode }) {
  return <dl className="detail-champs">{children}</dl>;
}

export function Champ({ label, children }: { label: string; children?: ReactNode }) {
  const vide = children === null || children === undefined || children === '';
  return (
    <div>
      <dt>{label}</dt>
      <dd>{vide ? TIRET : children}</dd>
    </div>
  );
}

/** Carte titrée regroupant des champs (fiche collaborateur). */
export function Section({ titre, icone, children }: { titre: string; icone: IconName; children: ReactNode }) {
  return (
    <section className="fiche-section">
      <h3 className="fiche-section__titre"><Icon name={icone} size={16} />{titre}</h3>
      {children}
    </section>
  );
}

export function Tableau({ colonnes, children }: { colonnes: string[]; children: ReactNode }) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>{colonnes.map((c) => <th key={c} scope="col">{c}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

export function Cartes({ items }: { items: Array<{ label: string; valeur: ReactNode }> }) {
  return (
    <div className="detail-cartes">
      {items.map((i) => (
        <div key={i.label} className="detail-carte">
          <div className="detail-carte__val">{i.valeur}</div>
          <div className="detail-carte__lab">{i.label}</div>
        </div>
      ))}
    </div>
  );
}

/** 31 derniers jours (borne maximale des API de présence, D-P17.g). */
export function periode31(): { du: string; au: string } {
  const fin = new Date();
  const debut = new Date(fin.getTime() - 30 * 24 * 3600 * 1000);
  const iso = (d: Date) => {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  };
  return { du: iso(debut), au: iso(fin) };
}

export function titreOnglet(ids: string[], actif: string | undefined): string {
  return actif && ids.includes(actif) ? actif : ids[0];
}
