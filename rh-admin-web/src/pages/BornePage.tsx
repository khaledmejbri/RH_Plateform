import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';

type BorneQr = {
  siteLibelle: string;
  qrPngBase64: string;
  expireDansSecondes: number;
  periodeSecondes: number;
};

/**
 * Borne écran de pointage (D-P05 — SCRUM-39) : page publique plein écran, sans compte.
 * Le QR change toutes les 30 s ; une photo partagée devient inutile en moins d'une minute.
 */
export default function BornePage() {
  const { jeton = '' } = useParams();
  const [qr, setQr] = useState<BorneQr | null>(null);
  const [restant, setRestant] = useState(0);
  const [erreur, setErreur] = useState<string | null>(null);
  const timer = useRef<number | null>(null);

  useEffect(() => {
    let annule = false;

    async function charger() {
      try {
        const res = await fetch(`/api/rh/v1/borne/presence/${encodeURIComponent(jeton)}/qr`, {
          headers: { Accept: 'application/json' },
          cache: 'no-store',
        });
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message ?? body?.erreur ?? 'Borne indisponible');
        }
        const raw = await res.json();
        if (annule) return;
        const suivant: BorneQr = {
          siteLibelle: String(raw.site_libelle ?? ''),
          qrPngBase64: String(raw.qr_png_base64 ?? ''),
          expireDansSecondes: Number(raw.expire_dans_secondes ?? 30),
          periodeSecondes: Number(raw.periode_secondes ?? 30),
        };
        setQr(suivant);
        setRestant(suivant.expireDansSecondes);
        setErreur(null);
        // Recharge juste après le changement de fenêtre
        timer.current = window.setTimeout(charger, (suivant.expireDansSecondes + 0.5) * 1000);
      } catch (e) {
        if (annule) return;
        setErreur(e instanceof Error ? e.message : 'Borne indisponible');
        timer.current = window.setTimeout(charger, 10_000);
      }
    }

    void charger();
    const tic = window.setInterval(() => setRestant((r) => Math.max(0, r - 1)), 1000);
    return () => {
      annule = true;
      window.clearInterval(tic);
      if (timer.current) window.clearTimeout(timer.current);
    };
  }, [jeton]);

  return (
    <main className="borne">
      {erreur && !qr ? (
        <div className="borne__carte" role="alert">
          <h1 className="borne__titre">Borne indisponible</h1>
          <p>{erreur}</p>
          <p className="borne__aide">Nouvelle tentative automatique dans quelques secondes.</p>
        </div>
      ) : !qr ? (
        <p className="borne__aide">Chargement…</p>
      ) : (
        <div className="borne__carte">
          <p className="borne__surtitre">Pointage</p>
          <h1 className="borne__titre">{qr.siteLibelle}</h1>
          <img
            className="borne__qr"
            src={`data:image/png;base64,${qr.qrPngBase64}`}
            alt={`Code QR de pointage du site ${qr.siteLibelle}`}
          />
          <div
            className="borne__barre"
            role="progressbar"
            aria-label="Renouvellement du code"
            aria-valuemin={0}
            aria-valuemax={qr.periodeSecondes}
            aria-valuenow={restant}
          >
            <span style={{ width: `${(restant / qr.periodeSecondes) * 100}%` }} />
          </div>
          <p className="borne__aide">
            Scannez avec RH Connect. Nouveau code dans {restant} s.
            {erreur ? ' Connexion instable, nouvel essai en cours.' : ''}
          </p>
        </div>
      )}
    </main>
  );
}
