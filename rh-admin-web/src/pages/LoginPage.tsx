import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { clearToken, getToken, signIn } from '../api/auth';
import { canAccessBackoffice } from '../auth/jwtRoles';

export default function LoginPage() {
  const nav = useNavigate();
  const [user, setUser] = useState('');
  const [pass, setPass] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const t = getToken();
    if (t && canAccessBackoffice(t)) nav('/app/accueil', { replace: true });
  }, [nav]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    try {
      await signIn(user, pass);
      const t = getToken();
      if (!canAccessBackoffice(t)) {
        clearToken();
        setErr('Accès réservé aux comptes RH, Direction ou Admin. Utilisez RH Connect.');
        return;
      }
      nav('/app/accueil', { replace: true });
    } catch (x) {
      setErr(x instanceof Error ? x.message : 'Connexion impossible');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <main className="login-card">
        <div className="login-brand">
          <div className="shell__logo" aria-hidden />
          <div>
            <div className="login-brand__name">RH-Évènement</div>
            <div className="login-brand__sub">Espace d’administration</div>
          </div>
        </div>
        <h1>Connexion</h1>
        <p className="lead">Réservé aux comptes RH, Direction ou Admin.</p>
        <form onSubmit={onSubmit}>
          <label htmlFor="login-f1" className="field-label">Utilisateur ou e-mail</label>
          <input id="login-f1"
            className="field-input"
            value={user}
            onChange={(e) => setUser(e.target.value)}
            autoComplete="username"
            required
          />
          <label htmlFor="login-f2" className="field-label">Mot de passe</label>
          <input id="login-f2"
            className="field-input"
            type="password"
            value={pass}
            onChange={(e) => setPass(e.target.value)}
            autoComplete="current-password"
            required
          />
          {err ? (
            <p className="alert alert--error" role="alert">
              {err}
            </p>
          ) : null}
          <button type="submit" className="btn btn--primary btn--lg" style={{ width: '100%', marginTop: 8 }} disabled={loading} aria-busy={loading}>
            {loading ? 'Connexion…' : 'Se connecter'}
          </button>
        </form>
        <p className="login-foot">Collaborateurs : utilisez l’application mobile RH Connect.</p>
      </main>
    </div>
  );
}
