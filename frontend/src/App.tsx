import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import HomePage from './pages/HomePage';
import LoadersOverviewPage from './pages/LoadersOverviewPage';
import LoaderDetailsPage from './pages/LoaderDetailsPage';
import LoadersListPage from './pages/LoadersListPage';
import NewLoaderPage from './pages/NewLoaderPage';
import EditLoaderPage from './pages/EditLoaderPage';
import ApprovalsPage from './pages/ApprovalsPage';
import { AdminLayout } from './components/layout/AdminLayout';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';
import ApiDiscoveryPage from './pages/admin/ApiDiscoveryPage';
import AdminLoadersListPage from './pages/admin/LoadersListPage';
import AdminPendingApprovalsPage from './pages/admin/PendingApprovalsPage';
import ApiPermissionsPage from './pages/admin/ApiPermissionsPage';
import UserManagementPage from './pages/admin/UserManagementPage';
import RoleManagementPage from './pages/admin/RoleManagementPage';
import SourceDatabasesPage from './pages/admin/SourceDatabasesPage';
import AuditLogsPage from './pages/admin/AuditLogsPage';
import { VersionNotification } from './components/VersionNotification';
import { Toaster } from './components/ui/toaster';
import { Footer } from './components/Footer';

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
    <div className="min-h-screen bg-background flex flex-col">
      <VersionNotification />
      <Toaster />
      <div className="flex-1">
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
          <Route
            path="/approvals"
            element={
              isAuthenticated() ? (
                <ApprovalsPage />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />

          {/* Admin Routes with Sidebar Layout */}
          <Route
            path="/admin"
            element={
              isAuthenticated() ? (
                <AdminLayout />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          >
            <Route index element={<AdminDashboardPage />} />
            <Route path="loaders" element={<AdminLoadersListPage />} />
            <Route path="loaders/new" element={<NewLoaderPage />} />
            <Route path="loaders/pending" element={<AdminPendingApprovalsPage />} />
            <Route path="loaders/:loaderCode/edit" element={<EditLoaderPage />} />
            <Route path="approvals/pending" element={<AdminPendingApprovalsPage />} />
            <Route path="approvals/history" element={<AdminPendingApprovalsPage />} />
            <Route path="api/discovery" element={<ApiDiscoveryPage />} />
            <Route path="api/permissions" element={<ApiPermissionsPage />} />
            <Route path="users" element={<UserManagementPage />} />
            <Route path="roles" element={<RoleManagementPage />} />
            <Route path="system/sources" element={<SourceDatabasesPage />} />
            <Route path="system/audit" element={<AuditLogsPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
      <Footer />
    </div>
  );
}

export default App;
