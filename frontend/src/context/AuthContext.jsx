import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(
    localStorage.getItem("accessToken")
  );

  const [role, setRole] = useState(
    localStorage.getItem("role")
  );

  function login(authResponse) {
    localStorage.setItem(
      "accessToken",
      authResponse.accessToken
    );

    localStorage.setItem("role", authResponse.role);

    setToken(authResponse.accessToken);
    setRole(authResponse.role);
  }

  function logout() {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("role");

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

export function useAuth() {
  return useContext(AuthContext);
}