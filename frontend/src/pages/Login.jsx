import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import { reviewerApi } from "../services/reviewerApi";
import { adminAuthApi } from "../services/adminApi";
import { useAuth } from "../context/AuthContext";
import "./AuthPages.css";

export default function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loginRole, setLoginRole] = useState("customer");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);

    try {
      let response;

      if (loginRole === "reviewer") {
        response = await reviewerApi.post(
          "/api/reviewer-auth/login",
          { username: identifier, password }
        );
      } else if (loginRole === "admin") {
        response = await adminAuthApi.post(
          "/login",
          { username: identifier, password }
        );
      } else {
        response = await api.post(
          "/api/auth/login",
          { email: identifier, password }
        );
      }

      login(response.data);

      const role = response.data.role;

      if (role === "CUSTOMER") {
        navigate("/customer");
      } else if (role === "REVIEWER") {
        navigate("/reviewer");
      } else if (role === "ADMIN") {
        navigate("/admin");
      }
    } catch (err) {
      setError(err.response?.status === 401 ? 'Invalid credentials' : 'Unable to sign in. Check credentials and service availability.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
      <div className="auth-brand"><span className="brand-mark">K</span><span>KYC Automate</span></div>
      <div className="auth-heading"><p className="eyebrow">SECURE VERIFICATION PORTAL</p><h1>Welcome back</h1><p>Sign in to continue your KYC verification.</p></div>
      <label className="auth-role-picker">Sign in as
        <select
          disabled={busy}
          value={loginRole}
          onChange={(event) => {
            setLoginRole(event.target.value);
            setIdentifier("");
            setPassword("");
            setShowPassword(false);
            setError("");
          }}
        >
          <option value="customer">Customer</option>
          <option value="reviewer">Reviewer</option>
          <option value="admin">Admin</option>
        </select>
      </label>
      <form onSubmit={handleSubmit}>
      <label>{loginRole === "customer" ? "Email" : "Username"}<input type={loginRole === "customer" ? "email" : "text"} placeholder={loginRole === "customer" ? "you@example.com" : `${loginRole === "admin" ? "Admin" : "Reviewer"} username`} autoComplete="username" value={identifier} onChange={(event) => setIdentifier(event.target.value)} required /></label>
      <label>Password
        <span className="password-field">
          <input type={showPassword ? "text" : "password"} placeholder="Enter your password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          <button type="button" className="password-toggle" aria-label={showPassword ? "Hide password" : "Show password"} aria-pressed={showPassword} onClick={() => setShowPassword((visible) => !visible)}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
              <circle cx="12" cy="12" r="2.5" />
              {showPassword && <path d="m4 4 16 16" />}
            </svg>
          </button>
        </span>
      </label>

      <button className="auth-submit" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      {loginRole === "customer" && <p className="auth-footer">
      Don’t have an account?{" "}
      <button
        type="button"
        onClick={() => navigate("/register")}
      >
        Register
      </button>
    </p>}
      {error && <p className="auth-error">{error}</p>}
      </form>
      </section>
    </main>
  );
}
