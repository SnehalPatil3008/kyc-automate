import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./PortalHeader.css";

export default function PortalHeader() {
  const navigate = useNavigate();
  const { role, logout } = useAuth();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <>
    <header className="portal-header">
      <button className="brand" onClick={() => navigate(`/${role?.toLowerCase()}`)}>
        <span className="brand-mark">K</span>
        <span>KYC Automate</span>
      </button>

      <div className="header-actions">
        <span className="role-label">{role}</span>
        <button className="logout-button" onClick={handleLogout}>Log out</button>
      </div>
    </header>
    <aside className="portal-sidebar">
      <button className="sidebar-link" onClick={() => navigate("/" + role?.toLowerCase())}>⌂ <span>Dashboard</span></button>
      <button className="sidebar-link active" onClick={() => navigate("/" + role?.toLowerCase())}>▣ <span>KYC Applications</span></button>
      <button className="sidebar-link" onClick={() => navigate("/" + role?.toLowerCase())}>◯ <span>Profile</span></button>
      <button className="sidebar-link" onClick={() => navigate("/" + role?.toLowerCase())}>? <span>Help &amp; Support</span></button>
    </aside>
    </>
  );
}
