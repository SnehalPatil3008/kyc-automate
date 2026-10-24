import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../services/api";
import "./AuthPages.css";

export default function Register() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    email: "",
    password: "",
    fullName: "",
    dateOfBirth: "",
    mobileNumber: "",
    address: "",
    city: "",
    state: "",
    postalCode: "",
  });

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  function handleChange(event) {
    const { name, value } = event.target;

    setForm((currentForm) => ({
      ...currentForm,
      [name]: value,
    }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setMessage("");
    setError("");

    try {
      await api.post("/api/auth/register", form);

      setMessage("Registration successful. Redirecting to login...");

      setTimeout(() => {
        navigate("/login");
      }, 1000);
    } catch (err) {
  console.error(
    "Registration error:",
    err.response?.status,
    err.response?.data,
    err.message
  );

  setError(
    err.response?.data?.message ||
    JSON.stringify(err.response?.data) ||
    "Registration failed"
  );
}
  }

  return (
    <main className="auth-page">
      <section className="auth-card register-card">
      <div className="auth-brand"><span className="brand-mark">K</span><span>KYC Automate</span></div>
      <div className="auth-heading"><p className="eyebrow">GET STARTED</p><h1>Create your account</h1><p>Enter your details to begin your KYC verification.</p></div>

      <form className="register-form" onSubmit={handleSubmit}>
        <input
          name="fullName"
          placeholder="Full name"
          value={form.fullName}
          onChange={handleChange}
          required
        />

        <input
          type="date"
          name="dateOfBirth"
          value={form.dateOfBirth}
          onChange={handleChange}
          required
        />

        <input
          type="tel"
          name="mobileNumber"
          placeholder="Mobile number"
          value={form.mobileNumber}
          onChange={handleChange}
          required
        />

        <input
          type="email"
          name="email"
          placeholder="Email"
          value={form.email}
          onChange={handleChange}
          required
        />

        <input
          type="password"
          name="password"
          placeholder="Password"
          value={form.password}
          onChange={handleChange}
          required
        />

        <input
          name="address"
          placeholder="Address"
          value={form.address}
          onChange={handleChange}
          required
        />

        <input
          name="city"
          placeholder="City"
          value={form.city}
          onChange={handleChange}
          required
        />

        <input
          name="state"
          placeholder="State"
          value={form.state}
          onChange={handleChange}
          required
        />

        <input
          name="postalCode"
          placeholder="Postal code"
          value={form.postalCode}
          onChange={handleChange}
          required
        />

        <button type="submit">Register</button>

        {message && <p>{message}</p>}
        {error && <p className="auth-error">{error}</p>}
      </form>
      </section>
    </main>
  );
}
