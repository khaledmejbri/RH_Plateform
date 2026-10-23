import { Link, useLocation, useNavigate } from 'react-router-dom';
import { clearToken, getToken } from '../api/auth';
import { canAccessBackoffice, isDirectionOnly, rolesFromAccessToken } from '../auth/jwtRoles';
import { Icon } from '../components/ui';

export default function AccesRefusePage() {
  const loc = useLocation();
  const navigate = useNavigate();
  const token = getToken();
  const backoffice = canAccessBackoffice(token);
  const direction = isDirectionOnly(token);
  const roles = [...rolesFromAccessToken(token)].join(', ') || 'aucun';
  const from = (loc.state as { from?: string } | null)?.from;

  function logout() {
    clearToken();
    navigate('/login', { replace: true });
  }

  return (
    <div className="panel empty access-denied">
      <span className="empty__icon"><Icon name="lock" size={24} /></span>
      <h1 className="page__title">Accès refusé</h1>
      {!backoffice ? (
        <p className="muted">
          La Plateforme RH est réservée aux comptes RH, Direction et Admin.
          Utilisez RH Connect (mobile) pour vos demandes.
          {roles !== 'aucun' ? ` (rôles JWT : ${roles})` : null}
        </p>
      ) : direction ? (
        <p className="muted">
          Votre profil <strong>Direction</strong> ne permet pas d’ouvrir
          {from ? ` « ${from} »` : ' cet espace'}. Il est réservé à la RH.
        </p>
      ) : (
        <p className="muted">Vous n’avez pas les droits pour cette page.</p>
      )}
      <p className="empty__action">
        {backoffice ? (
          <Link className="btn btn--primary" to="/app/accueil">Retour à l’accueil</Link>
        ) : (
          <button type="button" className="btn btn--primary" onClick={logout}>Se déconnecter</button>
        )}
      </p>
    </div>
  );
}
