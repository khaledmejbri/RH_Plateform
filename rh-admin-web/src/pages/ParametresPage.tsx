import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  getDescripteurs,
  getReglages,
  SERVICES,
  type DescripteurReferentiel,
  type Reglage,
  type ServiceParametre,
} from '../api/parametresApi';
import { useLectureSeule } from '../auth/useLectureSeule';
import { LoadingState, PageHeader } from '../components/ui';
import ReferentielPanel from './parametres/ReferentielPanel';
import JoursFeriesPanel from './parametres/JoursFeriesPanel';
import ReglagesPanel from './parametres/ReglagesPanel';
import './parametres/parametres.css';

/** Domaines de l'écran (D-R10), dans l'ordre d'affichage ; un domaine inconnu du serveur est ajouté à la fin. */
const DOMAINES: ReadonlyArray<{ code: string; titre: string }> = [
  { code: 'ORGANISATION', titre: 'Organisation et personnes' },
  { code: 'DEMANDES', titre: 'Demandes' },
  { code: 'DOCUMENTS', titre: 'Documents' },
  { code: 'PLAINTES_FORMATIONS', titre: 'Plaintes et formations' },
  { code: 'EVALUATIONS', titre: 'Évaluations' },
  { code: 'POINTAGE', titre: 'Pointage' },
];

/** Domaine d'affichage des réglages d'un service. */
const DOMAINE_REGLAGES: Record<ServiceParametre, string> = {
  referentiel: 'DEMANDES',
  evaluation: 'EVALUATIONS',
  presence: 'POINTAGE',
};

const SERVICE_LIBELLE: Record<ServiceParametre, string> = {
  referentiel: 'Organisation, demandes, documents, plaintes et formations',
  evaluation: 'Évaluations',
  presence: 'Pointage',
};

type Entree =
  | { genre: 'ref'; cle: string; domaine: string; titre: string; service: ServiceParametre; descripteur: DescripteurReferentiel }
  | { genre: 'reglages'; cle: string; domaine: string; titre: string; service: ServiceParametre }
  | { genre: 'feries'; cle: string; domaine: string; titre: string };

type Etat =
  | { statut: 'chargement' }
  | { statut: 'erreur' }
  | {
      statut: 'ok';
      descripteurs: Array<{ service: ServiceParametre; descripteur: DescripteurReferentiel }>;
      reglages: Record<ServiceParametre, Reglage[]>;
      indisponibles: ServiceParametre[];
    };

function titreDomaine(code: string): string {
  return DOMAINES.find((d) => d.code === code)?.titre ?? code.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
}

export default function ParametresPage() {
  const lectureSeule = useLectureSeule();
  const [params, setParams] = useSearchParams();
  const [etat, setEtat] = useState<Etat>({ statut: 'chargement' });

  const charger = useCallback(async () => {
    setEtat({ statut: 'chargement' });
    const resultats = await Promise.all(
      SERVICES.map(async (service) => {
        try {
          const [descripteurs, reglages] = await Promise.all([getDescripteurs(service), getReglages(service)]);
          return { service, ok: true as const, descripteurs, reglages };
        } catch {
          return { service, ok: false as const };
        }
      }),
    );
    const disponibles = resultats.filter((r) => r.ok);
    if (disponibles.length === 0) {
      setEtat({ statut: 'erreur' });
      return;
    }
    const reglages: Record<ServiceParametre, Reglage[]> = { referentiel: [], evaluation: [], presence: [] };
    const descripteurs: Array<{ service: ServiceParametre; descripteur: DescripteurReferentiel }> = [];
    for (const r of resultats) {
      if (!r.ok) continue;
      reglages[r.service] = r.reglages;
      r.descripteurs.forEach((d) => descripteurs.push({ service: r.service, descripteur: d }));
    }
    setEtat({ statut: 'ok', descripteurs, reglages, indisponibles: resultats.filter((r) => !r.ok).map((r) => r.service) });
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  const groupes = useMemo(() => {
    if (etat.statut !== 'ok') return [] as Array<{ code: string; titre: string; entrees: Entree[] }>;
    const entrees: Entree[] = etat.descripteurs.map(({ service, descripteur }) => ({
      genre: 'ref',
      cle: `ref:${descripteur.referentiel}`,
      domaine: descripteur.domaine,
      titre: descripteur.libelle,
      service,
      descripteur,
    }));
    for (const service of SERVICES) {
      if (etat.reglages[service].length > 0) {
        entrees.push({
          genre: 'reglages',
          cle: `reglages:${service}`,
          domaine: DOMAINE_REGLAGES[service],
          titre: service === 'referentiel' ? 'Réglages' : `Réglages (${SERVICE_LIBELLE[service].toLowerCase()})`,
          service,
        });
      }
    }
    entrees.push({ genre: 'feries', cle: 'jours-feries', domaine: 'DEMANDES', titre: 'Jours fériés' });
    const codes = Array.from(new Set([...DOMAINES.map((d) => d.code), ...entrees.map((e) => e.domaine)]));
    return codes
      .map((code) => ({ code, titre: titreDomaine(code), entrees: entrees.filter((e) => e.domaine === code) }))
      .filter((g) => g.entrees.length > 0);
  }, [etat]);

  const toutes = groupes.flatMap((g) => g.entrees);
  const choisie = toutes.find((e) => e.cle === params.get('element')) ?? toutes[0];

  function choisir(cle: string) {
    setParams((p) => {
      const suivant = new URLSearchParams(p);
      suivant.set('element', cle);
      return suivant;
    }, { replace: false });
  }

  /** La version des réglages est commune à tout le service : on relit la liste après chaque écriture. */
  async function rechargerReglages(service: ServiceParametre) {
    try {
      const liste = await getReglages(service);
      setEtat((e) => (e.statut === 'ok' ? { ...e, reglages: { ...e.reglages, [service]: liste } } : e));
    } catch {
      /* la liste affichée reste celle d'avant ; la prochaine écriture signalera un éventuel conflit */
    }
  }

  return (
    <div>
      <PageHeader
        title="Paramètres"
        lead="Listes de valeurs, libellés, couleurs et seuils utilisés dans toute l’application. Une modification est prise en compte partout en moins d’une minute."
      />

      {etat.statut === 'chargement' ? <LoadingState label="Chargement des paramètres…" /> : null}

      {etat.statut === 'erreur' ? (
        <div className="alert alert--error" role="alert">
          Les paramètres sont momentanément indisponibles. Rien n’a été modifié.{' '}
          <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
        </div>
      ) : null}

      {etat.statut === 'ok' ? (
        <>
          {etat.indisponibles.length > 0 ? (
            <div className="alert alert--warning" role="alert">
              Une partie des paramètres est indisponible ({etat.indisponibles.map((s) => SERVICE_LIBELLE[s]).join(', ')}).{' '}
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger()}>Réessayer</button>
            </div>
          ) : null}
          <div className="param-layout">
            <nav className="param-nav" aria-label="Référentiels">
              {groupes.map((g) => (
                <div key={g.code}>
                  <h3 className="param-nav__titre">{g.titre}</h3>
                  <ul className="param-nav__liste">
                    {g.entrees.map((e) => (
                      <li key={e.cle}>
                        <button
                          type="button"
                          className="param-nav__item"
                          aria-current={choisie?.cle === e.cle ? 'true' : undefined}
                          onClick={() => choisir(e.cle)}
                        >
                          {e.titre}
                          {e.genre === 'ref' && (e.descripteur.nature === 'COMPORTEMENT' || (!e.descripteur.operations.creer && !e.descripteur.operations.supprimer)) ? (
                            <span className="param-nav__fixe">liste fixe</span>
                          ) : null}
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </nav>
            <div>
              {choisie?.genre === 'ref' ? (
                <ReferentielPanel
                  key={choisie.cle}
                  service={choisie.service}
                  descripteur={choisie.descripteur}
                  lectureSeule={lectureSeule}
                />
              ) : choisie?.genre === 'feries' ? (
                <JoursFeriesPanel key={choisie.cle} lectureSeule={lectureSeule} />
              ) : choisie?.genre === 'reglages' ? (
                <ReglagesPanel
                  key={choisie.cle}
                  service={choisie.service}
                  titre={choisie.titre}
                  reglages={etat.reglages[choisie.service]}
                  lectureSeule={lectureSeule}
                  onMaj={() => void rechargerReglages(choisie.service)}
                  onRecharger={() => void rechargerReglages(choisie.service)}
                />
              ) : (
                <p className="detail-vide">Aucun référentiel à afficher.</p>
              )}
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
