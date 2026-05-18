import { useState, useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import { getToken } from '../api/auth';

export default function NotificationBell() {
  const [notifications, setNotifications] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<'connecting' | 'connected' | 'error' | 'disconnected'>('connecting');

  useEffect(() => {
    // Get user ID from localStorage or JWT token
    const getUserId = () => {
      try {
        const token = getToken();
        if (token) {
          const parts = token.split('.');
          if (parts.length === 3) {
            const payload = JSON.parse(atob(parts[1]));
            return payload.identifiant_utilisateur || payload.sub;
          }
        }
      } catch (e) {
        console.error('Failed to extract user ID from token:', e);
      }
      return null;
    };

    const userId = getUserId();
    console.log('[STOMP] User ID:', userId);

    // Get token for STOMP auth header
    const token = getToken();

    // Use native WebSocket instead of SockJS for better compatibility
    const client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
      connectHeaders: token ? {
        Authorization: `Bearer ${token}`,
        authorization: `Bearer ${token}`,
      } : {},
      debug: (str) => {
        console.log('[STOMP]', str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('✅ Connected to WebSockets');
        setStatus('connected');
        
        // Subscribe to user-specific queue
        // Spring's convertAndSendToUser already prepends /user/{userId} internally,
        // so we subscribe to /user/queue/notifications (not /user/{id}/queue/notifications)
        if (userId) {
          const userQueue = `/user/queue/notifications`;
          console.log('📨 Subscribing to user queue:', userQueue);
          client.subscribe(userQueue, (msg) => {
            if (msg.body) {
              try {
                const parsed = JSON.parse(msg.body);
                console.log('📩 User notification received:', parsed);
                setNotifications((prev) => [{ ...parsed, id: Date.now(), read: false }, ...prev]);
              } catch (e) {
                console.error('Failed to parse notification:', e);
              }
            }
          });
        }
        
        // Subscribe to RH topic (broadcast)
        console.log('📨 Subscribing to /topic/RH');
        client.subscribe('/topic/RH', (msg) => {
          if (msg.body) {
            try {
              const parsed = JSON.parse(msg.body);
              console.log('📩 Broadcast notification received:', parsed);
              setNotifications((prev) => [{ ...parsed, id: Date.now(), read: false }, ...prev]);
            } catch (e) {
              console.error('Failed to parse notification:', e);
              setNotifications((prev) => [{ 
                id: Date.now(),
                subject: 'Info', 
                content: msg.body,
                read: false 
              }, ...prev]);
            }
          }
        });
      },
      onDisconnect: () => {
        console.warn('❌ WebSocket disconnected');
        setStatus('disconnected');
      },
      onStompError: (frame) => {
        console.error('⚠️ STOMP Error:', frame.headers['message']);
        setStatus('error');
      },
      onWebSocketError: (error) => {
        console.error('⚠️ WebSocket Error:', error);
        setStatus('error');
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <div style={{ position: 'relative', display: 'inline-block', marginRight: '20px' }}>
      <button 
        type="button" 
        className="btn btn--ghost" 
        onClick={() => setOpen(!open)}
        style={{ position: 'relative', padding: '8px' }}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" width="24" height="24">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M13.73 21a2 2 0 01-3.46 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {/* Status indicator */}
        <span style={{
          position: 'absolute', bottom: '2px', right: '2px',
          width: '10px', height: '10px', borderRadius: '50%',
          background: status === 'connected' ? '#10b981' : status === 'connecting' ? '#f59e0b' : '#ef4444',
          border: '2px solid white',
          boxShadow: '0 0 4px rgba(0,0,0,0.2)'
        }} />
        {unreadCount > 0 && (
          <span style={{ 
            position: 'absolute', top: '-2px', right: '-2px', 
            background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)', 
            color: 'white', borderRadius: '50%', 
            padding: '2px 6px', fontSize: '11px', fontWeight: 'bold',
            boxShadow: '0 2px 4px rgba(239, 68, 68, 0.3)',
            minWidth: '18px', textAlign: 'center'
          }}>
            {unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 8px)', right: '0', 
          width: '380px', background: 'white', 
          borderRadius: '12px', 
          boxShadow: '0 10px 40px rgba(0,0,0,0.15), 0 2px 8px rgba(0,0,0,0.1)', 
          zIndex: 1000, overflow: 'hidden',
          border: '1px solid #e5e7eb'
        }}>
          {/* Header */}
          <div style={{ 
            padding: '16px 20px', 
            borderBottom: '1px solid #f3f4f6', 
            background: 'linear-gradient(to right, #fafafa, #ffffff)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div>
              <div style={{ fontWeight: '700', fontSize: '15px', color: '#111827' }}>
                Notifications
              </div>
              <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '2px' }}>
                {unreadCount} non lue{unreadCount !== 1 ? 's' : ''}
              </div>
            </div>
            {unreadCount > 0 && (
              <button 
                onClick={() => setNotifications(prev => prev.map(n => ({ ...n, read: true })))}
                style={{ 
                  background: 'none', border: 'none', 
                  color: '#3b82f6', cursor: 'pointer', 
                  fontSize: '12px', fontWeight: '600',
                  padding: '4px 8px',
                  borderRadius: '6px',
                  transition: 'background 0.2s'
                }}
                onMouseEnter={(e) => e.currentTarget.style.background = '#eff6ff'}
                onMouseLeave={(e) => e.currentTarget.style.background = 'none'}
              >
                Tout marquer lu
              </button>
            )}
          </div>
          
          {/* Notification list */}
          <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
            {notifications.length === 0 ? (
              <div style={{ padding: '40px 20px', textAlign: 'center', color: '#9ca3af' }}>
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ marginBottom: '12px', opacity: 0.3 }}>
                  <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M13.73 21a2 2 0 01-3.46 0" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <div style={{ fontSize: '14px', fontWeight: '500' }}>Aucune notification</div>
                <div style={{ fontSize: '12px', marginTop: '4px' }}>Vous serez notifié en temps réel</div>
              </div>
            ) : (
              notifications.map((n, i) => (
                <div 
                  key={n.id || i} 
                  style={{ 
                    padding: '14px 20px', 
                    borderBottom: i < notifications.length - 1 ? '1px solid #f3f4f6' : 'none',
                    background: n.read ? 'white' : '#eff6ff',
                    transition: 'background 0.2s',
                    cursor: 'pointer'
                  }}
                  onClick={() => {
                    setNotifications(prev => 
                      prev.map((notif, idx) => 
                        idx === i ? { ...notif, read: true } : notif
                      )
                    );
                  }}
                >
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '12px'
                  }}>
                    {!n.read && (
                      <div style={{
                        width: '8px',
                        height: '8px',
                        borderRadius: '50%',
                        background: '#3b82f6',
                        marginTop: '6px',
                        flexShrink: 0
                      }} />
                    )}
                    <div style={{ flex: 1 }}>
                      <div style={{ 
                        fontWeight: n.read ? '600' : '700', 
                        fontSize: '14px', 
                        marginBottom: '6px',
                        color: n.read ? '#374151' : '#111827'
                      }}>
                        {n.subject || 'Notification'}
                      </div>
                      <div style={{ 
                        fontSize: '13px', 
                        color: '#6b7280',
                        lineHeight: '1.5'
                      }}>
                        {n.content || JSON.stringify(n)}
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
          
          {/* Footer with connection status */}
          <div style={{
            padding: '10px 20px',
            borderTop: '1px solid #f3f4f6',
            background: '#fafafa',
            fontSize: '11px',
            color: '#9ca3af',
            display: 'flex',
            alignItems: 'center',
            gap: '6px'
          }}>
            <span style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              background: status === 'connected' ? '#10b981' : status === 'connecting' ? '#f59e0b' : '#ef4444'
            }} />
            {status === 'connected' ? 'Connecté en temps réel' : 
             status === 'connecting' ? 'Connexion...' : 'Déconnecté'}
          </div>
        </div>
      )}
    </div>
  );
}
