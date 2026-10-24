import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import { useAuth } from "../context/AuthContext";

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
    <form onSubmit={handleSubmit}>
      <h1>Login</h1>

      <input
        type="email"
        placeholder="Email"
        value={email}
        onChange={(event) => setEmail(event.target.value)}
        required
      />

      <input
        type="password"
        placeholder="Password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        required
      />

      <button type="submit">Login</button>
      <p>
      Don’t have an account?{" "}
      <button
        type="button"
        onClick={() => navigate("/register")}
      >
        Register
      </button>
    </p>
      {error && <p>{error}</p>}
    </form>
  );
}