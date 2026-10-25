import { useEffect, useState } from "react";
import api from "../services/api";
import "./DocumentStatus.css";

export default function DocumentStatus({
  applicationId,
  refreshKey,
  onCompletenessChange,
  onStatusChange,
}) {
  const [documents, setDocuments] = useState([]);
  const [completeness, setCompleteness] = useState(null);
  const [error, setError] = useState("");

  async function loadDocumentStatus() {
    try {
      setError("");

      const [
        documentsResponse,
        completenessResponse,
      ] = await Promise.all([
        api.get(
          `/api/documents/applications/${applicationId}`
        ),
        api.get(
          `/api/documents/applications/${applicationId}/completeness`
        ),
      ]);

      const loadedDocuments = documentsResponse.data;
      const loadedCompleteness = completenessResponse.data;

      setDocuments(loadedDocuments);
      setCompleteness(loadedCompleteness);

      onCompletenessChange?.(loadedCompleteness);

      const allDocumentsProcessed =
        loadedDocuments.length > 0 &&
        loadedDocuments.every(
          (document) =>
            document.processingStatus === "EXTRACTED"
        );

      const hasInvalidDocument = loadedDocuments.some(
        (document) =>
          document.processingStatus === "UNREADABLE" ||
          document.processingStatus === "FAILED" ||
          document.processingStatus === "PENDING" ||
          document.processingStatus === "PROCESSING"
      );

      onStatusChange?.({
        allDocumentsProcessed,
        hasInvalidDocument,
      });
    } catch (err) {
      console.error(
        "Unable to load document status:",
        err.response?.data || err
      );

      setError("Unable to load document status");

      onStatusChange?.({
        allDocumentsProcessed: false,
        hasInvalidDocument: true,
      });
    }
  }

  useEffect(() => {
    const loadId = window.setTimeout(() => loadDocumentStatus(), 0);
    return () => window.clearTimeout(loadId);
    // loadDocumentStatus intentionally reloads when the application or refresh key changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId, refreshKey]);

  return (
    <section className="document-status">
      <h3>Uploaded Documents</h3>

      {error && (
        <p className="error-text">
          {error}
        </p>
      )}

      {documents.length === 0 ? (
        <p className="status-empty">
          No documents uploaded yet.
        </p>
      ) : (
        <div className="document-list">
          {documents.map((document) => (
            <article
              className="document-row"
              key={document.documentId}
            >
              <span className="document-icon">▤</span>
              <div className="document-information">
                <strong>{document.documentType.replaceAll("_", " ")}</strong>
                <span>{document.originalFilename}</span>
              </div>
              <span className={"document-processing-status status-" + document.processingStatus?.toLowerCase()}>
                {document.processingStatus}
              </span>
            </article>
          ))}
        </div>
      )}

      {completeness && (
        <p
          className={`completeness ${
            completeness.complete
              ? "is-complete"
              : "is-incomplete"
          }`}
        >
          <strong>
            {completeness.complete
              ? "Documents complete"
              : "Documents required"}
          </strong>{" "}
          {completeness.complete
            ? "All required documents are available."
            : `Missing: ${completeness.missingDocuments.join(
                ", "
              )}`}
        </p>
      )}
    </section>
  );
}
