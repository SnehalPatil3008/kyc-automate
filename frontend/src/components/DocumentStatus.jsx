import { useEffect, useRef, useState } from "react";
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

  const callbacksRef = useRef({
    onCompletenessChange,
    onStatusChange,
  });

  useEffect(() => {
    callbacksRef.current = {
      onCompletenessChange,
      onStatusChange,
    };
  }, [onCompletenessChange, onStatusChange]);

  useEffect(() => {
    if (!applicationId) return;

    let cancelled = false;
    let timeoutId;
    const controller = new AbortController();

    async function loadDocumentStatus() {
      let shouldPollAgain = false;

      try {
        if (!cancelled) {
          setError("");
        }

        const [
          documentsResponse,
          completenessResponse,
        ] = await Promise.all([
          api.get(
            `/api/documents/applications/${applicationId}`,
            { signal: controller.signal }
          ),
          api.get(
            `/api/documents/applications/${applicationId}/completeness`,
            { signal: controller.signal }
          ),
        ]);

        if (cancelled) return;

        const loadedDocuments = documentsResponse.data;
        const loadedCompleteness = completenessResponse.data;

        setDocuments(loadedDocuments);
        setCompleteness(loadedCompleteness);

        callbacksRef.current.onCompletenessChange?.(
          loadedCompleteness
        );

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

        callbacksRef.current.onStatusChange?.({
          allDocumentsProcessed,
          hasInvalidDocument,
        });

        shouldPollAgain = loadedDocuments.some(
          (document) =>
            document.processingStatus === "PENDING" ||
            document.processingStatus === "PROCESSING"
        );
      } catch (err) {
        if (cancelled || controller.signal.aborted) return;

        console.error(
          "Unable to load document status:",
          err.response?.data || err
        );

        setError("Unable to load document status");

        callbacksRef.current.onStatusChange?.({
          allDocumentsProcessed: false,
          hasInvalidDocument: true,
        });

        const status = err.response?.status;

        // Retry temporary errors, but stop for authentication failures.
        shouldPollAgain = status !== 401 && status !== 403;
      } finally {
        if (!cancelled && shouldPollAgain) {
          timeoutId = window.setTimeout(
            loadDocumentStatus,
            2000
          );
        }
      }
    }

    timeoutId = window.setTimeout(loadDocumentStatus, 0);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
      controller.abort();
    };
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
                <strong>
                  {document.documentType.replaceAll("_", " ")}
                </strong>
                <span>{document.originalFilename}</span>
              </div>

              <span
                className={
                  "document-processing-status status-" +
                  document.processingStatus?.toLowerCase()
                }
              >
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