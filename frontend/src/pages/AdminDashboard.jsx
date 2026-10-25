import { useCallback, useEffect, useMemo, useState } from "react";
import PortalHeader from "../components/PortalHeader";
import { adminAuditApi, adminRequestError } from "../services/adminApi";
import "./AdminDashboard.css";

const EMPTY_FILTERS = {
  from: "",
  to: "",
  reviewer: "",
  documentType: "",
};

const EMPTY_SUMMARY = {
  totalApplications: 0,
  approvedCount: 0,
  rejectedCount: 0,
  processingCount: 0,
  manualReviewCount: 0,
  resubmissionRequiredCount: 0,
  documentFailureApplicationCount: 0,
  recentEvents: [],
};

const DOCUMENT_TYPES = [
  "PAN",
  "AADHAAR",
  "PASSPORT",
  "DRIVING_LICENCE",
  "VOTER_ID",
  "ELECTRICITY_BILL",
];

function label(value) {
  return value?.replaceAll("_", " ") || "—";
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

function requestParams(filters) {
  return {
    from: filters.from ? `${filters.from}T00:00:00Z` : undefined,
    to: filters.to ? `${filters.to}T23:59:59.999Z` : undefined,
    reviewer: filters.reviewer || undefined,
    documentType: filters.documentType || undefined,
  };
}

function percentage(value, total) {
  return total ? `${Math.round((value / total) * 100)}%` : "0%";
}

export default function AdminDashboard() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [summary, setSummary] = useState(EMPTY_SUMMARY);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedApplicationId, setSelectedApplicationId] = useState("");
  const [timelineSearch, setTimelineSearch] = useState("");
  const [timeline, setTimeline] = useState([]);
  const [timelineLoading, setTimelineLoading] = useState(false);
  const [timelineError, setTimelineError] = useState("");

  const loadDashboard = useCallback(async (nextFilters) => {
    setLoading(true);
    setError("");

    try {
      const params = requestParams(nextFilters);
      const [summaryResponse, eventsResponse] = await Promise.all([
        adminAuditApi.get("/reports/dashboard", { params }),
        adminAuditApi.get("/events", { params }),
      ]);

      setSummary(summaryResponse.data || EMPTY_SUMMARY);
      setEvents(eventsResponse.data || []);
    } catch (failure) {
      setError(adminRequestError(failure));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initialLoad = window.setTimeout(
      () => loadDashboard(EMPTY_FILTERS),
      0
    );

    return () => window.clearTimeout(initialLoad);
  }, [loadDashboard]);

  const latestEvents = useMemo(
    () => [...events]
      .sort((first, second) => new Date(second.eventTimestamp) - new Date(first.eventTimestamp))
      .slice(0, 50),
    [events]
  );

  async function applyFilters(event) {
    event.preventDefault();

    if (filters.from && filters.to && filters.from > filters.to) {
      setError("The start date must be before the end date.");
      return;
    }

    await loadDashboard(filters);
  }

  async function clearFilters() {
    setFilters(EMPTY_FILTERS);
    await loadDashboard(EMPTY_FILTERS);
  }

  async function loadTimeline(applicationId) {
    const normalizedId = String(applicationId || "").trim();

    if (!normalizedId) {
      setTimelineError("Enter an application ID.");
      return;
    }

    setSelectedApplicationId(normalizedId);
    setTimelineSearch(normalizedId);
    setTimeline([]);
    setTimelineError("");
    setTimelineLoading(true);

    try {
      const response = await adminAuditApi.get(
        `/applications/${encodeURIComponent(normalizedId)}/timeline`
      );
      setTimeline(response.data || []);
    } catch (failure) {
      setTimelineError(adminRequestError(failure));
    } finally {
      setTimelineLoading(false);
    }
  }

  const total = summary.totalApplications || 0;
  const cards = [
    { label: "Total applications", value: total, tone: "primary" },
    { label: "Approved", value: summary.approvedCount, detail: percentage(summary.approvedCount, total), tone: "success" },
    { label: "Rejected", value: summary.rejectedCount, detail: percentage(summary.rejectedCount, total), tone: "danger" },
    { label: "In processing", value: summary.processingCount, detail: percentage(summary.processingCount, total), tone: "info" },
    { label: "Manual review", value: summary.manualReviewCount, detail: percentage(summary.manualReviewCount, total), tone: "warning" },
    { label: "Resubmission", value: summary.resubmissionRequiredCount, detail: percentage(summary.resubmissionRequiredCount, total), tone: "warning" },
    { label: "Document failures", value: summary.documentFailureApplicationCount, tone: "danger" },
  ];

  return (
    <div className="portal-shell">
      <PortalHeader />
      <main className="admin-dashboard">
        <header className="admin-heading">
          <div>
            <p className="eyebrow">ADMIN PORTAL</p>
            <h1>Operations overview</h1>
            <p>Monitor KYC outcomes and review the audit trail across applications.</p>
          </div>
          <button className="admin-refresh" type="button" disabled={loading} onClick={() => loadDashboard(filters)}>
            {loading ? "Refreshing…" : "Refresh data"}
          </button>
        </header>

        <form className="admin-panel admin-filters" onSubmit={applyFilters}>
          <div className="admin-panel-heading">
            <div>
              <h2>Dashboard filters</h2>
              <p>Narrow the operational view without changing the audit records.</p>
            </div>
          </div>
          <div className="admin-filter-grid">
            <label>From date
              <input type="date" value={filters.from} onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))} />
            </label>
            <label>To date
              <input type="date" value={filters.to} onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))} />
            </label>
            <label>Reviewer
              <select value={filters.reviewer} onChange={(event) => setFilters((current) => ({ ...current, reviewer: event.target.value }))}>
                <option value="">All reviewers</option>
                <option value="reviewer">Reviewer</option>
              </select>
            </label>
            <label>Document type
              <select value={filters.documentType} onChange={(event) => setFilters((current) => ({ ...current, documentType: event.target.value }))}>
                <option value="">All document types</option>
                {DOCUMENT_TYPES.map((type) => <option key={type} value={type}>{label(type)}</option>)}
              </select>
            </label>
          </div>
          <div className="admin-filter-actions">
            <button className="admin-primary" type="submit" disabled={loading}>Apply filters</button>
            <button className="admin-secondary" type="button" disabled={loading} onClick={clearFilters}>Clear</button>
          </div>
        </form>

        {error && <p className="admin-alert" role="alert">{error}</p>}

        <section className="admin-stat-grid" aria-label="KYC status summary">
          {cards.map((card) => (
            <article className={`admin-stat admin-stat-${card.tone}`} key={card.label}>
              <span>{card.label}</span>
              <div><strong>{loading ? "—" : card.value ?? 0}</strong>{card.detail && <small>{loading ? "" : card.detail}</small>}</div>
            </article>
          ))}
        </section>

        <div className="admin-content-grid">
          <section className="admin-panel admin-audit-panel">
            <div className="admin-panel-heading admin-audit-heading">
              <div>
                <h2>Audit activity</h2>
                <p>Latest matching actions. Select an application to inspect its full history.</p>
              </div>
              <span className="admin-result-count">{events.length} events</span>
            </div>

            {loading && <p className="admin-empty" role="status">Loading audit activity…</p>}
            {!loading && !latestEvents.length && <p className="admin-empty">No audit events match these filters.</p>}
            {!!latestEvents.length && (
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead><tr><th>Application</th><th>Event</th><th>Document</th><th>Actor</th><th>Date and time</th></tr></thead>
                  <tbody>
                    {latestEvents.map((item) => (
                      <tr key={item.eventId}>
                        <td><button className="admin-link" type="button" onClick={() => loadTimeline(item.applicationId)}>#{item.applicationId}</button></td>
                        <td><strong>{label(item.eventType)}</strong>{item.reason && <span>{item.reason}</span>}</td>
                        <td>{label(item.documentType)}</td>
                        <td>{item.actor || "—"}<span>{label(item.actorRole)}</span></td>
                        <td>{formatDate(item.eventTimestamp)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <aside className="admin-panel admin-timeline-panel">
            <div className="admin-panel-heading">
              <div>
                <h2>Application history</h2>
                <p>Open any application’s immutable audit trail.</p>
              </div>
            </div>
            <form className="admin-timeline-search" onSubmit={(event) => { event.preventDefault(); loadTimeline(timelineSearch); }}>
              <label htmlFor="timeline-application">Application ID</label>
              <div><input id="timeline-application" inputMode="numeric" placeholder="e.g. 101" value={timelineSearch} onChange={(event) => setTimelineSearch(event.target.value)} /><button type="submit" disabled={timelineLoading}>View</button></div>
            </form>

            {timelineError && <p className="admin-alert" role="alert">{timelineError}</p>}
            {timelineLoading && <p className="admin-empty" role="status">Loading application history…</p>}
            {!timelineLoading && selectedApplicationId && !timelineError && !timeline.length && <p className="admin-empty">No history was found for application #{selectedApplicationId}.</p>}
            {!selectedApplicationId && <p className="admin-empty">Select an application from the table or enter its ID.</p>}
            {!!timeline.length && (
              <ol className="admin-timeline">
                {timeline.map((item) => (
                  <li key={item.eventId}>
                    <span className="admin-timeline-dot" />
                    <div>
                      <strong>{label(item.eventType)}</strong>
                      <time>{formatDate(item.eventTimestamp)}</time>
                      {item.reason && <p>{item.reason}</p>}
                      <small>{item.actor || "System"} · {label(item.actorRole)}{item.documentType ? ` · ${label(item.documentType)}` : ""}</small>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </aside>
        </div>
      </main>
    </div>
  );
}
