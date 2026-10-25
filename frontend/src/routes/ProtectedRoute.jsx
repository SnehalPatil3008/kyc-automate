import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function ProtectedRoute({ allowedRole, children }) {
  const { isAuthenticated, role } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const normalizedRole = role?.trim().toUpperCase();
  const normalizedAllowedRole = allowedRole?.trim().toUpperCase();

  if (normalizedRole !== normalizedAllowedRole) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
}
