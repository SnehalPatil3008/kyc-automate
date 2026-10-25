import { useCallback, useEffect, useState } from 'react';
import PortalHeader from '../components/PortalHeader';
import { reviewerApi, verificationApi, documentApi, requestError } from '../services/reviewerApi';
import './ReviewerDashboard.css';

const label = value => value?.replaceAll('_', ' ') || '—';
const types = ['PAN', 'AADHAAR', 'PASSPORT', 'DRIVING_LICENCE', 'VOTER_ID', 'ELECTRICITY_BILL'];
const date = value => value ? new Date(value).toLocaleString() : '—';

function DocumentCard({ document }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  async function preview() {
    setBusy(true); setError('');
    try { const response = await documentApi.get(`/api/documents/${document.documentId}/content`, { responseType: 'blob' }); setUrl(URL.createObjectURL(response.data)); }
    catch { setError('Unable to load document preview. Check service access.'); }
    finally { setBusy(false); }
  }
  return <article className="rv-document"><div className="rv-heading"><div><h3>{label(document.documentType)}</h3><p>{document.originalFilename}</p></div><span className="rv-badge">{label(document.processingStatus)}</span></div>
    <p>Document number: {document.documentNumber || 'Not available'} · OCR confidence: {document.averageConfidence == null ? '—' : `${document.averageConfidence}%`}</p>
    <button disabled={busy} onClick={() => url ? setUrl('') : preview()}>{busy ? 'Loading…' : url ? 'Close preview' : 'View document'}</button>
    {error && <p role="alert" className="rv-error">{error}</p>}
    {url && (document.mimeType?.startsWith('image/') ? <img className="rv-preview" src={url} alt={`${label(document.documentType)} uploaded document`} /> : document.mimeType === 'application/pdf' ? <iframe className="rv-preview" src={url} title="Uploaded document" /> : <a href={url} download={document.originalFilename}>Download document</a>)}
    <div className="rv-table-wrap"><table><thead><tr><th>Extracted field</th><th>Value</th><th>Confidence</th></tr></thead><tbody>{document.extractedFields?.map(field => <tr key={field.fieldName}><td>{label(field.fieldName)}</td><td>{field.fieldValue || '—'}</td><td>{field.confidenceScore == null ? '—' : `${field.confidenceScore}%`}</td></tr>)}</tbody></table></div>
  </article>;
}

function ReviewDetail({ initial, onChanged }) {
  const [review, setReview] = useState(initial);
  const [verification, setVerification] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [history, setHistory] = useState([]);
  const [errors, setErrors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [decision, setDecision] = useState('');
  const [reason, setReason] = useState('');
  const [requested, setRequested] = useState([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  useEffect(() => {
    let active = true;
    Promise.allSettled([
      reviewerApi.get(`/api/reviews/${initial.reviewId}`),
      verificationApi.get(`/api/verifications/applications/${initial.applicationId}/latest`),
      documentApi.get(`/api/documents/applications/${initial.applicationId}`),
      reviewerApi.get(`/api/decisions/applications/${initial.applicationId}`),
    ]).then(results => {
      if (!active) return;
      const names = ['Review', 'Verification', 'Documents', 'History'];
      setErrors(results.flatMap((result, index) => result.status === 'rejected' ? [`${names[index]}: ${requestError(result.reason)}`] : []));
      if (results[0].status === 'fulfilled') setReview(results[0].value.data);
      if (results[1].status === 'fulfilled') setVerification(results[1].value.data);
      if (results[2].status === 'fulfilled') setDocuments(results[2].value.data);
      if (results[3].status === 'fulfilled') setHistory(results[3].value.data);
      setLoading(false);
    });
    return () => { active = false; };
  }, [initial.reviewId, initial.applicationId]);
  const choices = review.targetStatus === 'AUTO_CHECKS_PASSED' ? ['APPROVED', 'MANUAL_REVIEW'] : review.targetStatus === 'MANUAL_REVIEW' ? ['APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED'] : [];
  async function submit(event) {
    event.preventDefault();
    if (!window.confirm(`Submit ${label(decision).toLowerCase()} for application ${review.applicationId}?`)) return;
    setBusy(true); setMessage('');
    try {
      const response = await reviewerApi.post(`/api/reviews/${review.reviewId}/decision`, { decision, reason: reason.trim(), requestedDocumentTypes: decision === 'RESUBMISSION_REQUIRED' ? requested : [] });
      setReview(response.data); setHistory(items => [response.data, ...items.filter(item => item.reviewId !== response.data.reviewId)]);
      setMessage(response.data.reviewState === 'COMPLETED'
        ? `Decision saved and customer application updated to ${label(response.data.targetStatus)}.`
        : `Decision saved, but customer synchronization is ${label(response.data.reviewState)}.`);
      setDecision(''); setReason(''); setRequested([]); onChanged();
    } catch (error) {
      try {
        const latest = await reviewerApi.get(`/api/reviews/${review.reviewId}`);
        setReview(latest.data);
        if (latest.data.reviewState === 'PENDING_SYNC') {
          setMessage('Decision saved, but Customer Service was not updated. Restore service connectivity, then click Retry synchronization.');
        } else if (latest.data.reviewState === 'SYNC_CONFLICT') {
          setMessage('Decision could not update the customer application because its status changed. Review the latest application state.');
        } else {
          setMessage(requestError(error));
        }
      } catch {
        setMessage(requestError(error));
      }
    }
    finally { setBusy(false); }
  }
  async function retry() {
    setBusy(true);
    try { const response = await reviewerApi.post(`/api/reviews/${review.reviewId}/retry-sync`); setReview(response.data); onChanged(); setMessage(response.data.reviewState === 'COMPLETED' ? `Customer application updated to ${label(response.data.targetStatus)}.` : `Synchronization state: ${label(response.data.reviewState)}.`); }
    catch (error) { setMessage(requestError(error)); }
    finally { setBusy(false); }
  }
  return <section className="rv-detail">
    <div className="rv-heading"><div><p className="rv-eyebrow">REVIEW #{review.reviewId}</p><h2>Application #{review.applicationId}</h2></div><span className="rv-badge">{label(review.reviewState)}</span></div>
    {loading && <p role="status">Loading review evidence…</p>}{errors.map(error => <p role="alert" className="rv-error" key={error}>{error}</p>)}
    <div className="rv-callout"><strong>{label(review.targetStatus)}</strong><p>{review.reason}</p></div>
    <p className="rv-muted">Created {date(review.createdAt)} · Reviewer: {review.reviewer || 'Awaiting decision'}</p>
    {verification && <section className="rv-section"><h3>Submitted CAF details</h3><div className="rv-caf-grid"><div><span>Customer name</span><strong>{verification.customerName || 'Unavailable'}</strong></div><div><span>Date of birth</span><strong>{verification.dob || 'Unavailable'}</strong></div><div><span>Address</span><strong>{verification.address || 'Unavailable'}</strong></div></div></section>}
    {verification && <section className="rv-section"><h3>Verification evidence</h3>{verification.verificationId !== review.verificationId && <p className="rv-error">The latest verification differs from this review. Confirm the correct evidence before deciding.</p>}<div className="rv-scores"><div>Name similarity<strong>{verification.nameScore == null ? 'Unavailable' : `${verification.nameScore}%`}</strong></div><div>Address similarity<strong>{verification.addressScore == null ? 'Unavailable' : `${verification.addressScore}%`}</strong></div></div><p>{verification.reason}</p>{verification.identityVerification?.map((item, index) => <div className="rv-callout" key={index}><strong>{item.verified ? 'Identity check passed' : 'Identity check requires attention'}</strong><p>{item.statusMessage}</p><p>Reference: {item.referenceId} · Confidence: {item.confidenceScore ?? '—'}</p>{item.registryDetails && <p>Name on record: {item.registryDetails.nameOnRecord || '—'} · Authority: {item.registryDetails.issuingAuthority || '—'} · Status: {item.registryDetails.status || '—'}</p>}</div>)}</section>}
    <section className="rv-section"><h3>Uploaded documents &amp; extracted data</h3><p className="rv-muted">Compare document images and extracted fields with the submitted CAF details and verification findings.</p>{!loading && !documents.length && <p>No document data available.</p>}{documents.map(document => <DocumentCard key={document.documentId} document={document} />)}</section>
    <section className="rv-section"><h3>Reviewer decision</h3>{review.reviewState === 'OPEN' && choices.length > 0 ? <form onSubmit={submit}><label>Decision<select required disabled={busy || loading} value={decision} onChange={event => { setDecision(event.target.value); setRequested([]); }}><option value="">Choose a decision</option>{choices.map(choice => <option key={choice} value={choice}>{label(choice)}</option>)}</select></label><label>Reason {decision === 'APPROVED' ? '(optional)' : '(required)'}<textarea maxLength={1000} disabled={busy} required={decision !== 'APPROVED'} value={reason} onChange={event => setReason(event.target.value)} rows={3} /></label>{decision === 'RESUBMISSION_REQUIRED' && <fieldset><legend>Documents to replace (select at least one)</legend>{types.map(type => <label className="rv-check" key={type}><input type="checkbox" disabled={busy} checked={requested.includes(type)} onChange={event => setRequested(values => event.target.checked ? [...values, type] : values.filter(value => value !== type))} />{label(type)}</label>)}</fieldset>}<button className="rv-primary" disabled={busy || loading || !decision || (decision !== 'APPROVED' && !reason.trim()) || (decision === 'RESUBMISSION_REQUIRED' && !requested.length) || verification?.verificationId !== review.verificationId}>{busy ? 'Saving…' : 'Submit decision'}</button></form> : <p>This review is {label(review.reviewState).toLowerCase()}. {review.reviewState === 'SYNC_CONFLICT' ? 'The customer status conflict requires reconciliation.' : ''}</p>}{review.reviewState === 'PENDING_SYNC' && <button disabled={busy} onClick={retry}>Retry synchronization</button>}{message && <p role="status" className="rv-callout">{message}</p>}</section>
    <section className="rv-section"><h3>Application review history</h3><p className="rv-muted">Review cases for this application; this is not an action-by-action audit log.</p>{history.map(item => <p key={item.reviewId}>Review #{item.reviewId} · {label(item.targetStatus)} · {label(item.reviewState)} · {date(item.updatedAt)}</p>)}</section>
  </section>;
}

export default function ReviewerDashboard() {
  const [queue, setQueue] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');
  const refresh = useCallback(async (silent = false) => {
    if (!silent) setLoading(true); setError('');
    try { setQueue((await reviewerApi.get('/api/reviews/pending')).data); }
    catch (failure) { setError(requestError(failure)); }
    finally { if (!silent) setLoading(false); }
  }, []);
  useEffect(() => {
    const initialLoadId = window.setTimeout(() => refresh(), 0);
    const intervalId = window.setInterval(() => refresh(true), 10000);
    return () => {
      window.clearTimeout(initialLoadId);
      window.clearInterval(intervalId);
    };
  }, [refresh]);
  const visible = queue.filter(review => `${review.applicationId} ${review.reviewId}`.includes(query.trim()) && (filter === 'ALL' || review.targetStatus === filter));
  return <div className="portal-shell reviewer-shell"><PortalHeader /><main className="rv-main"><div className="rv-heading"><div><p className="rv-eyebrow">REVIEWER PORTAL</p><h1>Application review</h1><p className="rv-muted">Inspect verification evidence and make a decision.</p></div><button disabled={loading} onClick={() => refresh()}>{loading ? 'Refreshing…' : 'Refresh queue'}</button></div>
    <div className="rv-stats"><div>Active reviews<strong>{queue.length}</strong></div><div>Manual review<strong>{queue.filter(item => item.targetStatus === 'MANUAL_REVIEW').length}</strong></div><div>Checks passed<strong>{queue.filter(item => item.targetStatus === 'AUTO_CHECKS_PASSED').length}</strong></div></div>
    {error && <p role="alert" className="rv-error">{error}</p>}<div className="rv-layout"><section className="rv-queue"><h2>Review queue</h2><label>Search application or review<input value={query} onChange={event => setQuery(event.target.value)} placeholder="Application or review ID" /></label><label>Status<select value={filter} onChange={event => setFilter(event.target.value)}><option value="ALL">All active reviews</option><option value="MANUAL_REVIEW">Manual review</option><option value="AUTO_CHECKS_PASSED">Checks passed</option></select></label>{!loading && !visible.length && <p className="rv-muted">{error ? 'Queue could not be loaded.' : 'No matching pending reviews.'}</p>}{visible.map(review => <button className={`rv-queue-item ${selected?.reviewId === review.reviewId ? 'is-selected' : ''}`} key={review.reviewId} onClick={() => setSelected(review)}><strong>Application #{review.applicationId}</strong><span className="rv-badge">{label(review.targetStatus)}</span><span>Review #{review.reviewId} · {label(review.reviewState)}</span><span>{review.reason}</span></button>)}</section>{selected ? <ReviewDetail key={selected.reviewId} initial={selected} onChanged={refresh} /> : <section className="rv-detail rv-empty"><h2>Select an application</h2><p>Choose a review from the queue to inspect its evidence and submit a decision.</p></section>}</div>
  </main></div>;
}
