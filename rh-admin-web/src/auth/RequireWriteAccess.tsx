import { Outlet } from 'react-router-dom';
import { getToken } from '../api/auth';
import AccesRefusePage from '../pages/AccesRefusePage';
import { isDirectionOnly } from './jwtRoles';

/** Routes réservées à RH/ADMIN (structure, unités, plaintes) : DIRECTION voit « Accès refusé » sans redirection. */
export default function RequireWriteAccess() {
  if (isDirectionOnly(getToken())) return <AccesRefusePage />;
  return <Outlet />;
}
