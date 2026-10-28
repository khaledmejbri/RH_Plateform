import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import RequireWriteAccess from './auth/RequireWriteAccess';
import AppShell from './layout/AppShell';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import AccesRefusePage from './pages/AccesRefusePage';
import PlaintesSuiviPage from './pages/PlaintesSuiviPage';
import DemandesAdministrativesPage from './pages/DemandesAdministrativesPage';
import DocumentsAdministratifsPage from './pages/DocumentsAdministratifsPage';
import FormationsPage from './pages/FormationsPage';
import CollaborateursPage from './pages/CollaborateursPage';
import UnitesOrganisationPage from './pages/UnitesOrganisationPage';
import StructureOrganisationPage from './pages/StructureOrganisationPage';
import OrganigrammePage from './pages/OrganigrammePage';
import EvaluationsPage from './pages/EvaluationsPage';
import PointageSitesPage from './pages/PointageSitesPage';
import BornePage from './pages/BornePage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* Borne écran de pointage, sans compte (D-P05) */}
      <Route path="/borne/:jeton" element={<BornePage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/app/accueil" element={<HomePage />} />
          <Route path="/app/acces-refuse" element={<AccesRefusePage />} />
          <Route path="/app/plaintes" element={<PlaintesSuiviPage />} />
          <Route path="/app/demandes-administratives" element={<DemandesAdministrativesPage />} />
          <Route path="/app/documents-administratifs" element={<DocumentsAdministratifsPage />} />
          <Route path="/app/formations" element={<FormationsPage />} />
          <Route path="/app/collaborateurs" element={<CollaborateursPage />} />
          <Route path="/app/organigramme" element={<OrganigrammePage />} />
          <Route path="/app/evaluations" element={<EvaluationsPage />} />
          <Route path="/app/pointage" element={<PointageSitesPage />} />
          <Route element={<RequireWriteAccess />}>
            <Route path="/app/unites" element={<UnitesOrganisationPage />} />
            <Route path="/app/structure" element={<StructureOrganisationPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/app/accueil" replace />} />
      <Route path="/collaborateurs" element={<Navigate to="/app/collaborateurs" replace />} />
      <Route path="*" element={<Navigate to="/app/accueil" replace />} />
    </Routes>
  );
}
