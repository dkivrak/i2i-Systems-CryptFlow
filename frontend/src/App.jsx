import { Navigate, Route, Routes } from 'react-router-dom'
import { useState } from 'react'
import { token } from './api/client'
import ProtectedRoute from './auth/ProtectedRoute'
import AuthPage from './pages/AuthPage'
import DashboardPage from './pages/DashboardPage'

export default function App() {
  const [authenticated, setAuthenticated] = useState(() => Boolean(token.get()))

  return <Routes>
    <Route path="/" element={<Navigate to={authenticated ? '/dashboard' : '/login'} replace />} />
    <Route
      path="/login"
      element={authenticated
        ? <Navigate to="/dashboard" replace />
        : <AuthPage onAuth={() => setAuthenticated(true)} />}
    />
    <Route
      path="/dashboard"
      element={
        <ProtectedRoute authenticated={authenticated}>
          <DashboardPage onLogout={() => setAuthenticated(false)} />
        </ProtectedRoute>
      }
    />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
