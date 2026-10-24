import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function ProtectedRoute({ allowedRole, children }) {
  const { isAuthenticated, role } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (role !== allowedRole) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
}