import { useState } from "react";
import api from "../services/api";
import "./DocumentUpload.css";

export default function DocumentUpload({ applicationId, onUploaded }) {
  const [documentType, setDocumentType] = useState("PAN");
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function handleUpload(event) {
    event.preventDefault();

    if (!file) {
      setError("Please select a file");
      return;
    }

    const formData = new FormData();
    formData.append("applicationId", applicationId);
    formData.append("documentType", documentType);
    formData.append("file", file);

    try {
      setUploading(true);
      setMessage("");
      setError("");

      const response = await api.post(
        "/api/documents",
        formData
      );

      setMessage("Document uploaded successfully");
      setFile(null);

      if (onUploaded) {
        onUploaded(response.data);
      }
} catch (err) {
  console.error("Document upload error:", err.response?.data || err);

  setError(
    err.response?.data?.message ||
    err.response?.data?.error ||
    `Document upload failed (${err.response?.status || "unknown error"})`
  );
} finally {
      setUploading(false);
    }
  }

  return (
    <form className="document-upload" onSubmit={handleUpload}>
      <div className="component-heading">
        <div>
          <h3>Upload document</h3>
          <p>Upload a clear image or PDF of your identity document.</p>
        </div>
      </div>

      <label>
        Document type
        <select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
        <option value="PAN">PAN</option>
        <option value="AADHAAR">Aadhaar</option>
        <option value="PASSPORT">Passport</option>
        <option value="DRIVING_LICENCE">Driving Licence</option>
        <option value="VOTER_ID">Voter ID</option>
        <option value="ELECTRICITY_BILL">Electricity Bill</option>
        <option value="GAS_BILL">Gas Bill</option>
        </select>
      </label>

      <label>
        Select file
        <input type="file" accept="image/*,.pdf" onChange={(event) => setFile(event.target.files[0])} required />
      </label>

      <button type="submit" disabled={uploading}>
        {uploading ? "Uploading..." : "Upload Document"}
      </button>

      {message && <p className="component-message success-text">{message}</p>}
      {error && <p className="component-message error-text">{error}</p>}
    </form>
  );
}
