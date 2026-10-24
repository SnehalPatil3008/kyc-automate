import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import { useAuth } from "../context/AuthContext";
import "./AuthPages.css";

export default function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");

    try {
      const response = await api.post("/api/auth/login", {
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
      setError("Invalid email or password");
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
      <div className="auth-brand"><span className="brand-mark">K</span><span>KYC Automate</span></div>
      <div className="auth-heading"><p className="eyebrow">SECURE VERIFICATION PORTAL</p><h1>Welcome back</h1><p>Sign in to continue your KYC verification.</p></div>
      <form onSubmit={handleSubmit}>
      <label>Email<input type="email" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
      <label>Password<input type="password" placeholder="Enter your password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>

      <button className="auth-submit" type="submit">Sign in</button>
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
