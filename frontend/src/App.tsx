import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import LoadersListPage from './pages/LoadersListPage';

function App() {
  const isAuthenticated = () => {
    const token = localStorage.getItem('auth_token');
    return !!token;
  };

  return (
    <div className="min-h-screen bg-background">
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            isAuthenticated() ? (
              <Navigate to="/loaders" replace />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/loaders"
          element={
            isAuthenticated() ? (
              <LoadersListPage />
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
