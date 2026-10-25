import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

function normalizeRole(role) {
  return typeof role === "string" ? role.trim().toUpperCase() : null;
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(
    sessionStorage.getItem("accessToken")
  );

  const [role, setRole] = useState(() =>
    normalizeRole(sessionStorage.getItem("role"))
  );

  function login(authResponse) {
    sessionStorage.setItem(
      "accessToken",
      authResponse.accessToken
    );

    const normalizedRole = normalizeRole(authResponse.role);

    sessionStorage.setItem("role", normalizedRole ?? "");

    setToken(authResponse.accessToken);
    setRole(normalizedRole);
  }

  function logout() {
    sessionStorage.removeItem("accessToken");
    sessionStorage.removeItem("role");

    setToken(null);
    setRole(null);
  }

  return (
    <AuthContext.Provider
      value={{
        token,
        role,
        login,
        logout,
        isAuthenticated: Boolean(token)
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}
