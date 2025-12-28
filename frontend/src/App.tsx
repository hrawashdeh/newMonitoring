import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import LoadersOverviewPage from './pages/LoadersOverviewPage';
import LoaderDetailsPage from './pages/LoaderDetailsPage';
import LoadersListPage from './pages/LoadersListPage';
import NewLoaderPage from './pages/NewLoaderPage';
import EditLoaderPage from './pages/EditLoaderPage';
import { VersionNotification } from './components/VersionNotification';
import { Toaster } from './components/ui/toaster';

function App() {
  const location = useLocation();

  const isAuthenticated = () => {
    const token = localStorage.getItem('auth_token');
    const user = localStorage.getItem('auth_user');

    // Must have both token and user data
    // Note: This is client-side check only. Backend validates the JWT on every API call.
    // If token is invalid/expired, axios interceptor will catch 401 and redirect to login
    return !!(token && user);
  };

  return (
    <div className="min-h-screen bg-background">
      <VersionNotification />
      <Toaster />
      <Routes key={location.pathname}>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            isAuthenticated() ? (
              <HomePage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders"
          element={
            isAuthenticated() ? (
              <LoadersOverviewPage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders/list"
          element={
            isAuthenticated() ? (
              <LoadersListPage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders/new"
          element={
            isAuthenticated() ? (
              <NewLoaderPage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders/:loaderCode/edit"
          element={
            isAuthenticated() ? (
              <EditLoaderPage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders/:loaderCode"
          element={
            isAuthenticated() ? (
              <LoaderDetailsPage />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  );
}

export default App;
