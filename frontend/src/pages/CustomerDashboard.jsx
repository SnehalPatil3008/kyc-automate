import { useEffect, useState } from "react";
import api from "../services/api";
import DocumentUpload from "../components/DocumentUpload";
import DocumentStatus from "../components/DocumentStatus";
import "./CustomerDashboard.css";

export default function CustomerDashboard() {
  const [profile, setProfile] = useState(null);
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [creatingApplication, setCreatingApplication] = useState(false);
  useEffect(() => {
    async function loadDashboard() {
      try {
        const [profileResponse, applicationsResponse] =
          await Promise.all([
            api.get("/api/customers/me"),
            api.get("/api/kyc/applications/me"),
          ]);

        setProfile(profileResponse.data);

        const applicationData = applicationsResponse.data;
        setApplications(
          Array.isArray(applicationData)
            ? applicationData
            : [applicationData]
        );
      } catch (err) {
        console.error("Dashboard loading failed:", err);
        setError(
          `Unable to load dashboard: ${
            err.response?.status || err.message
          }`
        );
      } finally {
        setLoading(false);
      }
    }

loadDashboard();
  }, []);

  if (loading) {
    return <p>Loading dashboard...</p>;
  }

  if (error) {
    return <p>{error}</p>;
  }

 async function submitApplication(applicationId) {
  try {
    const response = await api.post(
      `/api/kyc/applications/${applicationId}/submit`
    );

    setApplications((currentApplications) =>
      currentApplications.map((application) =>
        application.applicationId === applicationId
          ? response.data
          : application
      )
    );
  } catch (err) {
    alert(
      err.response?.data?.message ||
      "Unable to submit application"
    );
  }
}

 async function createApplication() {
  try {
    setCreatingApplication(true);

    const response = await api.post("/api/kyc/applications");

    setApplications((currentApplications) => [
      response.data,
      ...currentApplications,
    ]);
  } catch (err) {
    alert(
      err.response?.data?.message ||
      "Unable to create KYC application"
    );
  } finally {
    setCreatingApplication(false);
  }
}

return (
  <main className="dashboard">
    <header className="dashboard-header">
      <h1>Welcome, {profile?.fullName}</h1>
      <p>Manage your KYC verification from one place.</p>
    </header>

    <div className="dashboard-grid">
      {profile && (
        <section className="dashboard-card">
          <h2>Profile</h2>

          <p><strong>Email:</strong> {profile.email}</p>
          <p><strong>Mobile:</strong> {profile.mobileNumber}</p>
          <p><strong>Address:</strong> {profile.address}</p>
        </section>
      )}

      <section className="dashboard-card">
        <h2>My KYC Applications</h2>

        <button
          onClick={createApplication}
          disabled={creatingApplication || applications.length > 0}
        >
          {creatingApplication
            ? "Creating..."
            : "Create KYC Application"}
        </button>

        {applications.length === 0 ? (
          <p>No KYC applications found.</p>
        ) : (
          applications.map((application) => (
            <article
              className="application-card"
              key={application.applicationId}
            >
              <h3>{application.applicationRef}</h3>

              <p>
                <strong>Status:</strong>{" "}
                <span className="status">
                  {application.applicationStatus}
                </span>
              </p>

              <p>
                <strong>Created:</strong>{" "}
                {new Date(application.createdAt).toLocaleString()}
              </p>

              {["DRAFT", "RESUBMISSION_REQUIRED"].includes(
                application.applicationStatus
              ) && (
                <>
                  <DocumentUpload
                    applicationId={application.applicationId}
                  />

                  <DocumentStatus
                    applicationId={application.applicationId}
                  />
                </>
              )}

              {application.applicationStatus === "DRAFT" && (
                <button
                  onClick={() =>
                    submitApplication(application.applicationId)
                  }
                >
                  Submit Application
                </button>
              )}
            </article>
          ))
        )}
      </section>
    </div>
  </main>
);
}