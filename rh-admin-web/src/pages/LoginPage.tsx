import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getToken, signIn } from '../api/auth';

export default function LoginPage() {
  const nav = useNavigate();
  const [user, setUser] = useState('');
  const [pass, setPass] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (getToken()) nav('/app/accueil', { replace: true });
  }, [nav]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    try {
      await signIn(user, pass);
      nav('/app/accueil', { replace: true });
    } catch (x) {
      setErr(x instanceof Error ? x.message : 'Connexion impossible');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>Plateforme RH</h1>
        <p className="lead">Connexion sécurisée — compte avec rôle RH.</p>
        <form onSubmit={onSubmit}>
          <label className="field-label">Utilisateur ou e-mail</label>
          <input
            className="field-input"
            value={user}
            onChange={(e) => setUser(e.target.value)}
            autoComplete="username"
            required
          />
          <label className="field-label">Mot de passe</label>
          <input
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
          <button type="submit" className="btn btn--primary" style={{ width: '100%', marginTop: 8 }} disabled={loading}>
            {loading ? 'Connexion...' : 'Se connecter'}
          </button>
        </form>
      </div>
    </div>
  );
}
