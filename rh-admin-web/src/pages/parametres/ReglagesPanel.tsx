import { useEffect, useState } from 'react';
import { messageParametre, modifierReglage, ParametreApiError, type Reglage, type ServiceParametre } from '../../api/parametresApi';
import Onglets from '../../detail/Onglets';
import { EmptyState } from '../../components/ui';
import JournalPanel from './JournalPanel';

const UNITES: Record<string, string> = {
  MINUTES: 'minutes',
  METRES: 'mètres',
  JOURS: 'jours',
  HEURES: 'heures',
  ETOILES: 'étoiles',
  POINTAGES: 'pointages',
};

function uniteLisible(unite: string | null): string {
  if (!unite) return '';
  return UNITES[unite.toUpperCase()] ?? unite.toLowerCase();
}

type LigneProps = {
  service: ServiceParametre;
  reglage: Reglage;
  lectureSeule: boolean;
  onMaj: (r: Reglage) => void;
  onRecharger: () => void;
};

function LigneReglage({ service, reglage, lectureSeule, onMaj, onRecharger }: LigneProps) {
  const [saisie, setSaisie] = useState(String(reglage.valeur));
  const [erreur, setErreur] = useState<string | null>(null);
  const [conflit, setConflit] = useState(false);
  const [succes, setSucces] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const unite = uniteLisible(reglage.unite);
  // Rechargement depuis le serveur : la saisie reprend la valeur courante.
  useEffect(() => {
    setSaisie(String(reglage.valeur));
    setConflit(false);
    setErreur(null);
  }, [reglage.valeur, reglage.version]);
  const id = `reglage-${reglage.cle}`;
  const inchange = saisie.trim() === String(reglage.valeur);

  async function enregistrer() {
    if (envoi || saisie.trim() === '') return;
    setEnvoi(true);
    setErreur(null);
    setConflit(false);
    setSucces(false);
    try {
      const r = await modifierReglage(service, reglage.cle, Number(saisie), reglage.version);
      onMaj(r);
      setSaisie(String(r.valeur));
      setSucces(true);
    } catch (e) {
      if (e instanceof ParametreApiError && e.code === 'PARAMETRE_MODIFIE_ENTRE_TEMPS') setConflit(true);
      setErreur(messageParametre(e));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <div className="param-reglage">
      <div>
        <label htmlFor={id} className="field-label">{reglage.libelle}</label>
        <p className="field-help" style={{ margin: 0 }}>
          Entre {reglage.min} et {reglage.max}{unite ? ` ${unite}` : ''} · valeur par défaut : {reglage.defaut}{unite ? ` ${unite}` : ''}
        </p>
      </div>
      {lectureSeule ? (
        <strong>{reglage.valeur}{unite ? ` ${unite}` : ''}</strong>
      ) : (
        <div className="param-reglage__saisie">
          <input
            id={id}
            className="field-input"
            type="number"
            inputMode="numeric"
            min={reglage.min}
            max={reglage.max}
            value={saisie}
            aria-invalid={!!erreur && !conflit}
            aria-describedby={erreur ? `${id}-err` : undefined}
            onChange={(e) => { setSaisie(e.target.value); setSucces(false); }}
          />
          {unite ? <span className="muted">{unite}</span> : null}
          <button type="button" className="btn btn--secondary btn--sm" disabled={inchange || envoi || saisie.trim() === ''}
            aria-label={`Enregistrer : ${reglage.libelle}`} onClick={() => void enregistrer()}>
            {envoi ? 'Envoi…' : 'Enregistrer'}
          </button>
        </div>
      )}
      <div style={{ gridColumn: '1 / -1' }}>
        {erreur ? (
          <p id={`${id}-err`} className="field-error" role="alert">
            {erreur}{' '}
            {conflit ? <button type="button" className="btn btn--secondary btn--sm" onClick={onRecharger}>Recharger</button> : null}
          </p>
        ) : null}
        {succes ? <p className="muted small" role="status">Réglage enregistré.</p> : null}
      </div>
    </div>
  );
}

type Props = {
  service: ServiceParametre;
  titre: string;
  reglages: Reglage[];
  lectureSeule: boolean;
  onMaj: (r: Reglage) => void;
  onRecharger: () => void;
};

/** Réglages numériques bornés d'un service (D-R06), avec leur historique. */
export default function ReglagesPanel({ service, titre, reglages, lectureSeule, onMaj, onRecharger }: Props) {
  const [onglet, setOnglet] = useState('reglages');
  return (
    <section aria-labelledby="param-titre-reglages" className="panel">
      <div className="param-panel__tete">
        <h3 id="param-titre-reglages">{titre}</h3>
      </div>
      <Onglets
        onglets={[{ id: 'reglages', label: 'Réglages' }, { id: 'historique', label: 'Historique des modifications' }]}
        actif={onglet}
        onChange={setOnglet}
      >
        {onglet === 'historique' ? (
          <JournalPanel service={service} libelleDe={(cle) => reglages.find((r) => r.cle === cle)?.libelle} />
        ) : reglages.length === 0 ? (
          <EmptyState title="Aucun réglage">Ce service n’expose aucun réglage numérique.</EmptyState>
        ) : (
          reglages.map((r) => (
            <LigneReglage key={r.cle} service={service} reglage={r} lectureSeule={lectureSeule}
              onMaj={onMaj} onRecharger={onRecharger} />
          ))
        )}
      </Onglets>
    </section>
  );
}
