import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './auth/ProtectedRoute';
import AppShell from './layout/AppShell';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import PlaintesSuiviPage from './pages/PlaintesSuiviPage';
import DemandesAdministrativesPage from './pages/DemandesAdministrativesPage';
import DocumentsAdministratifsPage from './pages/DocumentsAdministratifsPage';
import FormationsPage from './pages/FormationsPage';
import CollaborateursPage from './pages/CollaborateursPage';
import UnitesOrganisationPage from './pages/UnitesOrganisationPage';
import StructureOrganisationPage from './pages/StructureOrganisationPage';
import EvaluationsPage from './pages/EvaluationsPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/app/accueil" element={<HomePage />} />
          <Route path="/app/plaintes" element={<PlaintesSuiviPage />} />
          <Route path="/app/demandes-administratives" element={<DemandesAdministrativesPage />} />
          <Route path="/app/documents-administratifs" element={<DocumentsAdministratifsPage />} />
          <Route path="/app/formations" element={<FormationsPage />} />
          <Route path="/app/collaborateurs" element={<CollaborateursPage />} />
          <Route path="/app/unites" element={<UnitesOrganisationPage />} />
          <Route path="/app/structure" element={<StructureOrganisationPage />} />
          <Route path="/app/evaluations" element={<EvaluationsPage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/app/accueil" replace />} />
      <Route path="/collaborateurs" element={<Navigate to="/app/collaborateurs" replace />} />
      <Route path="*" element={<Navigate to="/app/accueil" replace />} />
    </Routes>
  );
}
