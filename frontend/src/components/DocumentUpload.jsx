import { useState } from "react";
import api from "../services/api";

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
      setError(
        err.response?.data?.message ||
        "Document upload failed"
      );
    } finally {
      setUploading(false);
    }
  }

  return (
    <form onSubmit={handleUpload}>
      <h3>Upload Document</h3>

      <select
        value={documentType}
        onChange={(event) =>
          setDocumentType(event.target.value)
        }
      >
        <option value="PAN">PAN</option>
        <option value="AADHAAR">Aadhaar</option>
        <option value="PASSPORT">Passport</option>
      </select>

      <input
        type="file"
        accept="image/*,.pdf"
        onChange={(event) =>
          setFile(event.target.files[0])
        }
        required
      />

      <button type="submit" disabled={uploading}>
        {uploading ? "Uploading..." : "Upload Document"}
      </button>

      {message && <p>{message}</p>}
      {error && <p>{error}</p>}
    </form>
  );
}