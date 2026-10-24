import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import { reviewerApi } from "../services/reviewerApi";
import { useAuth } from "../context/AuthContext";
import "./AuthPages.css";

export default function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [reviewerLogin, setReviewerLogin] = useState(false);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);

    try {
      const response = reviewerLogin ? await reviewerApi.post('/api/reviewer-auth/login', { username: email, password }) : await api.post("/api/auth/login", {
        email,
        password,
      });

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
      <label>Sign in as<select disabled={busy} value={reviewerLogin ? 'reviewer' : 'customer'} onChange={event => { setReviewerLogin(event.target.value === 'reviewer'); setEmail(''); setPassword(''); setError(''); }}><option value="customer">Customer</option><option value="reviewer">Reviewer</option></select></label>
      <form onSubmit={handleSubmit}>
      <label>{reviewerLogin ? 'Username' : 'Email'}<input type={reviewerLogin ? 'text' : 'email'} placeholder={reviewerLogin ? 'Reviewer username' : 'you@example.com'} autoComplete="username" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
      <label>Password<input type="password" placeholder="Enter your password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>

      <button className="auth-submit" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      <p className="auth-footer">
      Don’t have an account?{" "}
      <button
        type="button"
        onClick={() => navigate("/register")}
      >
        Register
      </button>
    </p>
      {error && <p className="auth-error">{error}</p>}
      </form>
      </section>
    </main>
  );
}
