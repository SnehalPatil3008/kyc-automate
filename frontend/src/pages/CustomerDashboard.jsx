import { useCallback, useEffect, useState } from "react";
import api from "../services/api";
import DocumentUpload from "../components/DocumentUpload";
import DocumentStatus from "../components/DocumentStatus";
import PortalHeader from "../components/PortalHeader";
import "./CustomerDashboard.css";

const progressSteps = ["Application created", "Documents uploaded", "Review details", "Verification in progress", "Decision completed"];
const finalStatuses = ["APPROVED", "REJECTED"];
const submittedStatuses = ["SUBMITTED", "PROCESSING", "AUTO_CHECKS_PASSED", "MANUAL_REVIEW"];
const reviewerDecisionStatuses = ["APPROVED", "REJECTED", "RESUBMISSION_REQUIRED"];

function progressStep(application, complete) {
  if (!application) return 0;
  if (finalStatuses.includes(application.applicationStatus)) return 5;
  if (application.applicationStatus === "RESUBMISSION_REQUIRED") return 3;
  if (submittedStatuses.includes(application.applicationStatus)) return 4;
  if (complete) return 3;
  return application.applicationStatus === "DRAFT" ? 2 : 1;
}

function emptyForm(profile) {
  return {
    fullName: profile?.fullName || "",
    dateOfBirth: profile?.dateOfBirth || "",
    mobileNumber: profile?.mobileNumber || "",
    address: profile?.address || "",
    city: profile?.city || "",
    state: profile?.state || "",
    postalCode: profile?.postalCode || "",
  };
}

function statusLabel(result) {
  if (!result) return "";
  if (["RECOMMENDED_FOR_APPROVAL", "MANUAL_REVIEW"].includes(result.decision)) return "Under review";
  if (result.decision === "RESUBMISSION_REQUIRED") return "Action required";
  if (result.decision === "PROCESSING") return "Verification in progress";
  return result.decision?.replaceAll("_", " ") || "Verification update";
}

export default function CustomerDashboard() {
  const [profile, setProfile] = useState(null);
  const [editForm, setEditForm] = useState(emptyForm(null));
  const [applications, setApplications] = useState([]);
  const [completeness, setCompleteness] = useState({});
  const [verificationResults, setVerificationResults] = useState({});
  const [documentState, setDocumentState] = useState({ allDocumentsProcessed: false, hasInvalidDocument: false });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [creatingApplication, setCreatingApplication] = useState(false);
  const [submittingApplication, setSubmittingApplication] = useState(null);
  const [resubmittingApplication, setResubmittingApplication] = useState(null);
  const [refreshDocuments, setRefreshDocuments] = useState(0);
  const [refreshing, setRefreshing] = useState(false);

  const loadDashboard = useCallback(async (silent = false) => {
    try {
      if (!silent) setError("");
        const [profileResponse, applicationsResponse] = await Promise.all([
          api.get("/api/customers/me"),
          api.get("/api/kyc/applications/me"),
        ]);
        setProfile(profileResponse.data);
        setEditForm(emptyForm(profileResponse.data));
        const data = applicationsResponse.data;
        const loadedApplications = Array.isArray(data) ? data : data ? [data] : [];
        setApplications(loadedApplications);
        const latestResults = await Promise.allSettled(
          loadedApplications.filter((item) => item.applicationStatus !== "DRAFT").map(async (item) => {
            try{
            const response = await api.get("/api/verifications/applications/" + item.applicationId + "/latest");
            return [item.applicationId, response.data];
              } catch (err) {
    console.error(
      "Latest verification failed:",
      err.response?.data || err
    );

    throw err;
  }
          })
        );
        setVerificationResults(Object.fromEntries(
          latestResults.filter((result) => result.status === "fulfilled").map((result) => result.value)
        ));
      } catch (err) {
        console.error("Dashboard loading failed:", err);
        if (!silent) {
        setError("Unable to load dashboard: " + (err.response?.status || err.message));
        }
      } finally {
        if (!silent) {
        setLoading(false);
        }
      }
  }, []);

  useEffect(() => {
    const initialLoadId = window.setTimeout(() => loadDashboard(), 0);

    // Keep the customer status in sync with reviewer decisions.
    const intervalId = window.setInterval(() => loadDashboard(true), 10000);
    return () => {
      window.clearTimeout(initialLoadId);
      window.clearInterval(intervalId);
    };
  }, [loadDashboard]);

  async function refreshStatus() {
    setRefreshing(true);
    await loadDashboard(true);
    setRefreshing(false);
  }

  const application = applications.reduce((latest, current) => {
    if (!latest) return current;
    return Number(current.applicationId) > Number(latest.applicationId) ? current : latest;
  }, null);
  const applicationCompleteness = application ? completeness[application.applicationId] : null;
  const activeStep = progressStep(application, applicationCompleteness?.complete);
  const verificationResult = application ? verificationResults[application.applicationId] : null;
  const hasReviewerDecision = application && reviewerDecisionStatuses.includes(application.applicationStatus);
  const detailsLocked = application && !["DRAFT", "RESUBMISSION_REQUIRED"].includes(application.applicationStatus);
  const canSubmit = application?.applicationStatus === "DRAFT" && applicationCompleteness?.complete === true &&
    documentState.allDocumentsProcessed === true && documentState.hasInvalidDocument === false;
  const canResubmit = application?.applicationStatus === "RESUBMISSION_REQUIRED" && applicationCompleteness?.complete === true &&
    documentState.allDocumentsProcessed === true && documentState.hasInvalidDocument === false;
  function updateApplication(updatedApplication) {
    setApplications((current) => current.map((item) => item.applicationId === updatedApplication.applicationId ? updatedApplication : item));
  }

  function handleDetailChange(event) {
    const { name, value } = event.target;
    setEditForm((current) => ({ ...current, [name]: value }));
  }

  async function runVerification(applicationId) {
    const response = await api.post("/api/verifications/applications/" + applicationId, {
      customerName: editForm.fullName,
      dob: editForm.dateOfBirth,
      address: [editForm.address, editForm.city, editForm.state, editForm.postalCode].filter(Boolean).join(", "),
    });
    const verificationData = response.data;
    setVerificationResults((current) => ({ ...current, [applicationId]: verificationData }));
    const localStatus = verificationData.decision === "RESUBMISSION_REQUIRED" ? "RESUBMISSION_REQUIRED" : "MANUAL_REVIEW";
    setApplications((current) => current.map((item) => item.applicationId === applicationId
      ? { ...item, applicationStatus: localStatus, statusReason: verificationData.reason } : item));
  }

  async function submitApplication(applicationId) {
    if (!canSubmit) {
      setActionMessage("Please upload all required documents and make sure they are readable before submitting.");
      return;
    }
    if (!window.confirm("Submit your KYC application for verification? You will not be able to edit these details afterward.")) return;
    try {
      setSubmittingApplication(applicationId);
      setActionMessage("Submitting your application...");
      const response = await api.post("/api/kyc/applications/" + applicationId + "/submit");
      updateApplication(response.data);
      setActionMessage("Application submitted. Verification is now in progress...");
      await runVerification(applicationId);
      setActionMessage("Automated verification completed. Your application is awaiting the next step.");
    } catch (err) {
      setActionMessage(err.response?.data?.message || err.response?.data?.error || "Unable to submit or verify the application.");
    } finally {
      setSubmittingApplication(null);
    }
  }

  async function resubmitApplication(applicationId) {
    if (!canResubmit) {
      setActionMessage("Replace the requested documents and wait until they are extracted before resubmitting.");
      return;
    }
    try {
      setResubmittingApplication(applicationId);
      setActionMessage("Resubmitting your application...");
      const response = await api.post("/api/kyc/applications/" + applicationId + "/resubmit");
      updateApplication(response.data);
      await runVerification(applicationId);
      setActionMessage("Your application was resubmitted for verification.");
    } catch (err) {
      setActionMessage(err.response?.data?.message || err.response?.data?.error || "Unable to resubmit the application.");
    } finally {
      setResubmittingApplication(null);
    }
  }

  async function createApplication() {
    try {
      setCreatingApplication(true);
      const response = await api.post("/api/kyc/applications");
      setApplications((current) => [response.data, ...current]);
      setActionMessage("Your KYC application has been created.");
    } catch (err) {
      setActionMessage(err.response?.data?.message || "Unable to create KYC application.");
    } finally {
      setCreatingApplication(false);
    }
  }

  if (loading) return <div className="page-message">Loading dashboard...</div>;
  if (error) return <div className="page-message error-message">{error}</div>;

  return (
    <div className="portal-shell">
      <PortalHeader />
      <main className="dashboard">
        <header className="dashboard-header">
          <div><p className="eyebrow">CUSTOMER PORTAL</p><h1>Welcome back, {profile?.fullName?.split(" ")[0]}</h1><p>Track your KYC verification status and complete any pending steps.</p></div>
          <button className="secondary-action" onClick={refreshStatus} disabled={refreshing}>{refreshing ? "Refreshing..." : "Refresh status"}</button>
        </header>

        {!application ? (
          <section className="dashboard-card empty-application"><h2>Start your KYC application</h2><p>Create an application to upload your documents and begin verification.</p><button onClick={createApplication} disabled={creatingApplication}>{creatingApplication ? "Creating..." : "Create KYC Application"}</button></section>
        ) : (
          <>
            <section className="progress-card dashboard-card">
              <div className="progress-card-heading"><div><p className="eyebrow">KYC APPLICATION</p><h2>{application.applicationRef}</h2><p>{application.applicationStatus === "DRAFT" ? "Complete your application before submitting." : "Your application is being processed securely."}</p></div><span className={"status status-" + application.applicationStatus?.toLowerCase()}>{application.applicationStatus?.replaceAll("_", " ")}</span></div>
              <div className="stepper">{progressSteps.map((step, index) => { const number = index + 1; return <div className={"step " + (number < activeStep ? "done " : "") + (number === activeStep ? "active" : "")} key={step}><span>{number < activeStep ? "✓" : number}</span><strong>{step}</strong></div>; })}</div>
            </section>

            {actionMessage && <div className="action-message">{actionMessage}</div>}

            <div className="customer-content-grid">
              <section className="dashboard-card details-card">
                <div className="details-form">
                  <label>Full name<input name="fullName" value={editForm.fullName} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  <label>Date of birth<input type="date" name="dateOfBirth" value={editForm.dateOfBirth} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  <label className="wide-field">Address<input name="address" value={editForm.address} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  <label>City<input name="city" value={editForm.city} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  <label>State<input name="state" value={editForm.state} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  <label>Postal code<input name="postalCode" value={editForm.postalCode} onChange={handleDetailChange} readOnly={detailsLocked} /></label>
                  {!detailsLocked && <p className="details-hint wide-field">Review and correct these details before submitting. They will be sent with your verification request.</p>}
                </div>
              </section>

              <section className="dashboard-card documents-card">
                {["DRAFT", "RESUBMISSION_REQUIRED"].includes(application.applicationStatus) && <DocumentUpload applicationId={application.applicationId} onUploaded={() => setRefreshDocuments((value) => value + 1)} />}
                <DocumentStatus applicationId={application.applicationId} refreshKey={refreshDocuments} onCompletenessChange={(value) => setCompleteness((current) => ({ ...current, [application.applicationId]: value }))} onStatusChange={setDocumentState} />
              </section>
            </div>

            {application.applicationStatus === "DRAFT" && <section className="submit-panel dashboard-card"><button className="primary-action" onClick={() => submitApplication(application.applicationId)} disabled={submittingApplication === application.applicationId || !canSubmit}>{submittingApplication === application.applicationId ? "Processing..." : "Submit Application"}</button><div><p>By submitting, you confirm that the information provided is accurate.</p>{documentState.hasInvalidDocument && <p className="error-text">Replace the unreadable or failed document before submitting.</p>}{!documentState.allDocumentsProcessed && !documentState.hasInvalidDocument && applicationCompleteness?.complete && <p className="processing-message">Please wait while your documents are being processed.</p>}</div></section>}
            {application.applicationStatus === "RESUBMISSION_REQUIRED" && <section className="submit-panel dashboard-card"><button className="primary-action" onClick={() => resubmitApplication(application.applicationId)} disabled={resubmittingApplication === application.applicationId || !canResubmit}>{resubmittingApplication === application.applicationId ? "Processing..." : "Resubmit Application"}</button><p>Replace the requested document, then resubmit after it is extracted.</p></section>}
            {(hasReviewerDecision || verificationResult || application.applicationStatus === "SUBMITTED") && (
  <section className={`verification-panel dashboard-card verification-${application.applicationStatus?.toLowerCase()}`}>
    <div>
      <p className="eyebrow">{hasReviewerDecision ? "REVIEWER DECISION" : "VERIFICATION STATUS"}</p>

      <h2>
        {hasReviewerDecision
          ? application.applicationStatus.replaceAll("_", " ")
          : verificationResult
          ? statusLabel(verificationResult)
          : "Verification in progress"}
      </h2>

      <p>
        {hasReviewerDecision
          ? application.statusReason || (application.applicationStatus === "APPROVED"
              ? "Your KYC application has been approved by the reviewer."
              : "The reviewer has completed the decision.")
          : verificationResult?.reason ||
          "Your application has been submitted and is being checked."}
      </p>

      {!hasReviewerDecision && !verificationResult && (
        <small>
          Please wait while your verification is being processed.
        </small>
      )}
    </div>
  </section>
)}
          </>
        )}
      </main>
    </div>
  );
}
