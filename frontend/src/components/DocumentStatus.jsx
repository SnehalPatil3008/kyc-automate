import { useEffect, useState } from "react";
import api from "../services/api";

export default function DocumentStatus({ applicationId }) {
  const [documents, setDocuments] = useState([]);
  const [completeness, setCompleteness] = useState(null);
  const [error, setError] = useState("");

  async function loadDocumentStatus() {
    try {
      const [documentsResponse, completenessResponse] =
        await Promise.all([
          api.get(
            `/api/documents/applications/${applicationId}`
          ),
          api.get(
            `/api/documents/applications/${applicationId}/completeness`
          ),
        ]);

      setDocuments(documentsResponse.data);
      setCompleteness(completenessResponse.data);
    } catch (err) {
      setError("Unable to load document status");
    }
  }

  useEffect(() => {
    loadDocumentStatus();
  }, [applicationId]);

  return (
    <section>
      <h3>Uploaded Documents</h3>

      {error && <p>{error}</p>}

      {documents.length === 0 ? (
        <p>No documents uploaded yet.</p>
      ) : (
        documents.map((document) => (
          <article key={document.documentId}>
            <p>
              <strong>Type:</strong>{" "}
              {document.documentType}
            </p>
            <p>
              <strong>File:</strong>{" "}
              {document.originalFilename}
            </p>
            <p>
              <strong>Status:</strong>{" "}
              {document.processingStatus}
            </p>
          </article>
        ))
      )}

      {completeness && (
        <p>
          <strong>Completeness:</strong>{" "}
          {completeness.complete
            ? "Complete"
            : `Missing: ${completeness.missingDocuments.join(", ")}`}
        </p>
      )}
    </section>
  );
}