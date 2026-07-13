import { Navigate } from 'react-router-dom'
import { token } from '../api/client'

export default function ProtectedRoute({ authenticated, children }) {
  return authenticated && token.get() ? children : <Navigate to="/login" replace />
}
