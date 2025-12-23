import { Routes, Route, Navigate } from 'react-router-dom';
import LoadersListPage from './pages/LoadersListPage';

function App() {
  return (
    <div className="min-h-screen bg-background">
      <Routes>
        <Route path="/" element={<Navigate to="/loaders" replace />} />
        <Route path="/loaders" element={<LoadersListPage />} />
        <Route path="*" element={<Navigate to="/loaders" replace />} />
      </Routes>
    </div>
  );
}

export default App;
