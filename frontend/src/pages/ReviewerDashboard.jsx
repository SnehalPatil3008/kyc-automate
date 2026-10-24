import PortalHeader from "../components/PortalHeader";
import "./RoleDashboard.css";

export default function ReviewerDashboard() {
  return <div className="portal-shell"><PortalHeader /><main className="role-dashboard"><p className="eyebrow">REVIEWER PORTAL</p><h1>Reviewer Dashboard</h1><p className="role-intro">Review submitted applications and make informed verification decisions.</p><section className="role-placeholder"><span className="placeholder-icon">R</span><h2>Review queue</h2><p>Submitted applications will appear here for review.</p></section></main></div>;
}
