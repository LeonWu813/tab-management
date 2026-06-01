import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './auth/auth-store';
import LoginPage from './auth/LoginPage';
import RegisterPage from './auth/RegisterPage';
import DashboardPage from './dashboard/DashboardPage';
import CategoriesPage from './categories/CategoriesPage';
import RemindersPage from './reminders/RemindersPage';
import SettingsPage from './settings/SettingsPage';
import ShareTargetPage from './share-target/ShareTargetPage';
import AppShell from './layout/AppShell';

function RequireAuth({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken);
  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Share Target landing page — handles URLs shared from mobile apps (AC-042, AC-043) */}
        <Route
          path="/share-target"
          element={
            <RequireAuth>
              <ShareTargetPage />
            </RequireAuth>
          }
        />

        {/* Main authenticated app shell */}
        <Route
          path="/"
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="categories" element={<CategoriesPage />} />
          <Route path="reminders" element={<RemindersPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
