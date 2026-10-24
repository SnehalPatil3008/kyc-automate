import PortalHeader from "../components/PortalHeader";
import "./RoleDashboard.css";

export default function AdminDashboard() {
  return <div className="portal-shell"><PortalHeader /><main className="role-dashboard"><p className="eyebrow">ADMIN PORTAL</p><h1>Admin Dashboard</h1><p className="role-intro">Manage users, applications, and verification activity.</p><section className="role-placeholder"><span className="placeholder-icon">A</span><h2>Administration workspace</h2><p>Administrative controls and reporting will appear here.</p></section></main></div>;
}
