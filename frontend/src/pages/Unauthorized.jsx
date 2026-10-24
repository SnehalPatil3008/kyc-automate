import { useNavigate } from "react-router-dom";
import "./RoleDashboard.css";

export default function Unauthorized() {
  const navigate = useNavigate();
  return <main className="access-page"><section className="role-placeholder"><span className="placeholder-icon">!</span><h1>Access unavailable</h1><p>You do not have permission to view this page.</p><button onClick={() => navigate(-1)}>Go back</button></section></main>;
}
