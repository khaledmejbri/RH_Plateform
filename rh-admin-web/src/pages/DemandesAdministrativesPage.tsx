import { useEffect, useState } from 'react';
import { getDemandesAdministrativesListe, type DemandeAdministrative } from '../api/rhClient';
import { Avatar, Chip, EmptyState, ErrorAlert, LoadingState, PageHeader, StatusBadge, formatDateTime, type IconName, type Tone } from '../components/ui';

export default function DemandesAdministrativesPage() {
  const [rows, setRows] = useState<DemandeAdministrative[]>([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let c = false;
    void (async () => {
      try {
        const data = await getDemandesAdministrativesListe();
        if (!c) setRows(data);
      } catch (e) {
        if (!c) setErr(e instanceof Error ? e.message : 'Erreur');
      } finally {
        if (!c) setLoading(false);
      }
    })();
    return () => {
      c = true;
    };
  }, []);

  function formatTypeDemande(type: string): string {
    if (type === 'AUTORISATION_SORTIE') return 'Autorisation de sortie';
    if (type === 'CONGE') return 'Congé';
    if (type === 'ORDRE_MISSION') return 'Ordre de mission';
    return type.replaceAll('_', ' ').toLowerCase().split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
  }

  function formatTypeConge(code: string | undefined): string | null {
    if (!code) return null;
    const map: Record<string, string> = {
      ANNUEL: 'Annuel',
      MALADIE: 'Maladie',
      MATERNITE: 'Maternité',
      SANS_SOLDE: 'Sans solde',
      AUTRE: 'Autre',
    };
    return map[code.toUpperCase()] ?? code;
  }

  function typeChip(type: string): { tone: Tone; icon: IconName } {
    if (type === 'AUTORISATION_SORTIE') return { tone: 'info', icon: 'clock' };
    if (type === 'CONGE') return { tone: 'warning', icon: 'calendar' };
    return { tone: 'success', icon: 'clipboard' };
  }

  function formatPeriode(d: DemandeAdministrative): string {
    if (d.type_demande === 'AUTORISATION_SORTIE') {
      const c = d.contenu as { date_jour?: string; heure_debut?: string; heure_fin?: string } | undefined;
      if (c?.date_jour) {
        const heures = c.heure_debut && c.heure_fin ? ` · ${c.heure_debut} → ${c.heure_fin}` : '';
        let duree = '';
        if (c.heure_debut && c.heure_fin) {
          const [hd, md] = c.heure_debut.split(':').map(Number);
          const [hf, mf] = c.heure_fin.split(':').map(Number);
          const mins = (hf * 60 + mf) - (hd * 60 + md);
          if (mins > 0) {
            const h = Math.floor(mins / 60), m = mins % 60;
            duree = ` (${h > 0 ? h + 'h' : ''}${m > 0 ? m + 'min' : ''})`;
          }
        }
        return `${c.date_jour}${heures}${duree}`;
      }
    }
    if (d.periode_debut && d.periode_fin) return `${d.periode_debut} → ${d.periode_fin}`;
    if (d.periode_debut) return d.periode_debut;
    return '-';
  }

  return (
    <div className="page">
      <PageHeader title="Demandes administratives" lead="Congés, autorisations de sortie et ordres de mission." />

      {err ? <ErrorAlert>{err}</ErrorAlert> : null}

      <div className="panel panel--flush">
        {loading ? (
          <LoadingState label="Chargement des demandes…" />
        ) : rows.length === 0 ? (
          <EmptyState icon="clipboard" title="Aucune demande administrative">
            Les demandes apparaîtront ici dès qu’un collaborateur les soumettra.
          </EmptyState>
        ) : (
          <div className="table-wrap">
            <table className="data-table data-table--responsive">
              <caption className="sr-only">Liste des demandes administratives</caption>
              <thead>
                <tr>
                  <th scope="col">Créée le</th>
                  <th scope="col">Type</th>
                  <th scope="col">Statut</th>
                  <th scope="col">Période</th>
                  <th scope="col">Demandeur</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => {
                  const chip = typeChip(d.type_demande);
                  const typeConge =
                    d.type_demande === 'CONGE'
                      ? formatTypeConge((d.contenu as { type_conge?: string } | undefined)?.type_conge)
                      : null;
                  return (
                    <tr key={d.identifiant}>
                      <td data-label="Créée le" className="cell-meta">{formatDateTime(d.cree_le)}</td>
                      <td data-label="Type">
                        <Chip tone={chip.tone} icon={chip.icon}>
                          {formatTypeDemande(d.type_demande)}
                          {typeConge ? <span className="muted">· {typeConge}</span> : null}
                        </Chip>
                      </td>
                      <td data-label="Statut">
                        <StatusBadge statut={d.statut} />
                      </td>
                      <td data-label="Période" className="cell-meta">{formatPeriode(d)}</td>
                      <td data-label="Demandeur">
                        <span className="cell-user">
                          <Avatar name={d.demandeur_identifiant} size={28} />
                          <code className="cell-code" title={d.demandeur_identifiant}>
                            {d.demandeur_identifiant.slice(0, 8)}…
                          </code>
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
