import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { getToken } from '../api/auth';
import {
  getCompteurNotifications, getNotifications, marquerNotificationLue, marquerToutesLues, type NotificationDto,
} from '../api/detailApi';
import { parseContenuNotification } from '../api/notificationContenu';
import { typeDepuisNotification } from '../detail/refs';
import { useDetail } from '../detail/contexte';
import { formatDateHeure } from '../utils/format';

/** Même origine que la page (ws/wss), surchargeable par VITE_WS_URL. */
function resolveBrokerUrl(): string {
  const fromEnv = import.meta.env.VITE_WS_URL as string | undefined;
  if (fromEnv) return fromEnv;
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws`;
}

function texteDe(n: NotificationDto): string {
  const c = n.contenu ?? '';
  return parseContenuNotification(c)?.texte ?? c;
}

function badge(n: number): string {
  return n > 99 ? '99+' : String(n);
}

/** Cloche : liste persistée paginée, compteur serveur, marquage lu, temps réel STOMP (2G, 2F). */
export default function NotificationBell() {
  const { ouvrir } = useDetail();
  const [ouvert, setOuvert] = useState(false);
  const [nonLues, setNonLues] = useState(0);
  const [seulementNonLues, setSeulementNonLues] = useState(false);
  const [items, setItems] = useState<NotificationDto[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [charge, setCharge] = useState<'chargement' | 'ok' | 'erreur'>('chargement');
  const [connecte, setConnecte] = useState(false);
  const racine = useRef<HTMLDivElement>(null);

  const rafraichirCompteur = useCallback(() => {
    getCompteurNotifications().then((c) => setNonLues(c.non_lues)).catch(() => undefined);
  }, []);

  const charger = useCallback(async (p: number, filtre: boolean) => {
    setCharge('chargement');
    try {
      const r = await getNotifications(p, filtre);
      setItems((prev) => (p === 0 ? r.contenu : [...prev, ...r.contenu]));
      setPage(p);
      setTotal(r.total);
      setCharge('ok');
    } catch {
      setCharge('erreur');
    }
  }, []);

  // Connexion temps réel : jeton au CONNECT, compteur relu à chaque (re)connexion, +1 par message.
  useEffect(() => {
    rafraichirCompteur();
    const client = new Client({
      brokerURL: resolveBrokerUrl(),
      // Jeton relu à chaque (re)connexion : un jeton renouvelé est pris en compte sans recharger la page.
      beforeConnect: () => {
        const token = getToken();
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setConnecte(true);
        rafraichirCompteur();
        client.subscribe('/user/queue/notifications', (msg) => {
          if (!msg.body) return;
          try {
            const n = JSON.parse(msg.body) as NotificationDto;
            setNonLues((x) => x + 1);
            if (n.id) setItems((prev) => (prev.some((p) => p.id === n.id) ? prev : [{ ...n, lu: false }, ...prev]));
          } catch {
            rafraichirCompteur();
          }
        });
      },
      onDisconnect: () => setConnecte(false),
      onWebSocketClose: () => setConnecte(false),
    });
    client.activate();
    return () => { void client.deactivate(); };
  }, [rafraichirCompteur]);

  useEffect(() => {
    if (ouvert) void charger(0, seulementNonLues);
  }, [ouvert, seulementNonLues, charger]);

  useEffect(() => {
    if (!ouvert) return undefined;
    const clicExterieur = (e: MouseEvent) => {
      if (!racine.current?.contains(e.target as Node)) setOuvert(false);
    };
    const echap = (e: KeyboardEvent) => { if (e.key === 'Escape') setOuvert(false); };
    document.addEventListener('mousedown', clicExterieur);
    document.addEventListener('keydown', echap);
    return () => {
      document.removeEventListener('mousedown', clicExterieur);
      document.removeEventListener('keydown', echap);
    };
  }, [ouvert]);

  async function ouvrirNotification(n: NotificationDto) {
    if (!n.lu) {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, lu: true } : x)));
      setNonLues((x) => Math.max(0, x - 1));
      try { await marquerNotificationLue(n.id); } catch { rafraichirCompteur(); }
    }
    const type = typeDepuisNotification(n.objet_type);
    if (type && n.objet_id) {
      setOuvert(false);
      ouvrir({ type, id: n.objet_id, libelle: n.objet_numero ?? undefined });
    }
  }

  async function toutLire() {
    try {
      await marquerToutesLues();
      setItems((prev) => prev.map((x) => ({ ...x, lu: true })));
      rafraichirCompteur();
      if (seulementNonLues) void charger(0, true);
    } catch {
      setCharge('erreur');
    }
  }

  return (
    <div className="notif" ref={racine}>
      <button
        type="button"
        className="btn btn--ghost"
        aria-label={nonLues > 0 ? `Notifications, ${nonLues} non lue${nonLues > 1 ? 's' : ''}` : 'Notifications'}
        aria-expanded={ouvert}
        aria-haspopup="true"
        onClick={() => setOuvert((o) => !o)}
        style={{ position: 'relative', padding: 8 }}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" width="24" height="24" aria-hidden>
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M13.73 21a2 2 0 01-3.46 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {nonLues > 0 ? <span className="notif__badge">{badge(nonLues)}</span> : null}
      </button>

      {ouvert ? (
        <div className="notif__panel" role="region" aria-label="Mes notifications">
          <div className="notif__head">
            <strong>Mes notifications</strong>
            <span style={{ display: 'flex', gap: 8 }}>
              <button type="button" className="btn btn--ghost btn--sm" aria-pressed={seulementNonLues}
                onClick={() => setSeulementNonLues((v) => !v)}>
                {seulementNonLues ? 'Toutes' : 'Non lues'}
              </button>
              {nonLues > 0 ? (
                <button type="button" className="btn btn--ghost btn--sm" onClick={() => void toutLire()}>
                  Tout marquer comme lu
                </button>
              ) : null}
            </span>
          </div>
          {charge === 'erreur' ? (
            <div className="alert alert--error" role="alert" style={{ margin: 12 }}>
              Impossible de charger les notifications.{' '}
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger(0, seulementNonLues)}>Réessayer</button>
            </div>
          ) : null}
          {charge === 'chargement' && items.length === 0 ? <p className="detail-vide" style={{ padding: '12px 16px' }}>Chargement…</p> : null}
          {charge === 'ok' && items.length === 0 ? <p className="detail-vide" style={{ padding: '12px 16px' }}>Aucune notification</p> : null}
          {items.length > 0 ? (
            <ul className="notif__list">
              {items.map((n) => (
                <li key={n.id}>
                  <button type="button" className={`notif__item${n.lu ? '' : ' notif__item--nonlue'}`} onClick={() => void ouvrirNotification(n)}>
                    <div className="notif__sujet">{!n.lu ? <span className="sr-only">Non lue : </span> : null}{n.sujet || 'Notification'}</div>
                    <div className="small muted">{texteDe(n)}</div>
                    <div className="cell-meta">{formatDateHeure(n.recu_le ?? n.emis_le)}</div>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {items.length < total ? (
            <div style={{ padding: 8, textAlign: 'center' }}>
              <button type="button" className="btn btn--secondary btn--sm" onClick={() => void charger(page + 1, seulementNonLues)}>
                Voir plus
              </button>
            </div>
          ) : null}
          <div className="notif__foot">{connecte ? 'Connecté en temps réel' : 'Temps réel indisponible'}</div>
        </div>
      ) : null}
    </div>
  );
}
