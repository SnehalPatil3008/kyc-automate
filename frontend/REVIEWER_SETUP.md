# Reviewer frontend

Only frontend source/configuration changed. Backend files are identical to the supplied ZIP.

## Run locally

1. Start the existing backend services, including decision-review (8084), verification (8083), document (8082), customer, and Eureka.
2. In frontend, run `npm ci` to install dependencies for your operating system.
3. Run `npm run dev` to start the UI. Open the URL printed by Vite.
4. On login choose Reviewer, then enter your configured reviewer username and password.
5. Select a pending case, inspect evidence/documents, and submit a decision.

Reviewer traffic uses same-origin `/review-api/decision`, `/review-api/verification`, and `/review-api/document` proxies defined in vite.config.js. This avoids the customer-only gateway JWT validation and missing reviewer-login gateway route. Customer API routing remains unchanged.

Vite dev and preview supply these proxy routes locally. A production static host must supply equivalent reverse-proxy routes; building the frontend alone does not configure a production proxy.

## Behavior

- Queue: GET /api/reviews/pending, search/filter, refresh, and active counts.
- Detail: GET /api/reviews/{reviewId}.
- Verification: GET /api/verifications/applications/{applicationId}/latest, scores, reasons and identity outcomes.
- Documents: GET /api/documents/applications/{applicationId}, extracted fields and authenticated content previews.
- Decisions follow backend transitions: AUTO_CHECKS_PASSED allows APPROVED or MANUAL_REVIEW; MANUAL_REVIEW allows APPROVED, REJECTED or RESUBMISSION_REQUIRED.
- A reason is required except for approval. Resubmission requires replacement document types.
- Completed review remains visible after submission and leaves the pending queue on refresh.
- PENDING_SYNC offers retry-sync; SYNC_CONFLICT is reported for reconciliation.
- Decision submission is disabled when verification evidence is unavailable or does not match the case verificationId.
- History lists review cases, not a full action audit trail.

## Existing backend limits

The reviewer APIs do not expose the full submitted CAF or structured field-by-field mismatch pairs. No CAF values are invented or retrieved with customer/internal credentials. The screen shows the evidence available through existing reviewer-authorized services.

The queue displays existing review cases. The frontend does not automatically call /process or /evaluate when listing applications. Create cases using the existing processing flow already tested in Insomnia.

The uploaded backend still contains its supplied InternalServiceClientConfig.java. Preserve the working JWT change you already applied locally; this frontend-only delivery does not modify it. Replace only your frontend folder when integrating.
