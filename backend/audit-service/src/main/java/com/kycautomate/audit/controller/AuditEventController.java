package com.kycautomate.audit.controller;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.kycautomate.audit.dto.AuditEventRequest;
import com.kycautomate.audit.dto.EventCountResponse;
import com.kycautomate.audit.dto.StatusCountResponse;
import com.kycautomate.audit.entity.AuditEvent;
import com.kycautomate.audit.enums.EventType;
import com.kycautomate.audit.service.AuditEventService;

import jakarta.validation.Valid;
import com.kycautomate.audit.dto.DashboardSummaryResponse;
@RestController
@RequestMapping("/api/audit")
@Validated
public class AuditEventController {

	private final AuditEventService service;

	public AuditEventController(AuditEventService service) {
		this.service = service;
	}

	@PostMapping("/events")
	public ResponseEntity<AuditEvent> recordEvent(@Valid @RequestBody AuditEventRequest request) {

		AuditEvent savedEvent = service.recordEvent(request);

		return ResponseEntity.status(HttpStatus.CREATED).body(savedEvent);
	}

	@GetMapping("/applications/{applicationId}/timeline")
	public ResponseEntity<List<AuditEvent>> getTimeline(@PathVariable String applicationId) {

		return ResponseEntity.ok(service.getApplicationTimeline(applicationId));
	}

	@GetMapping("/reports/event-counts")
	public ResponseEntity<List<EventCountResponse>> getEventCounts() {
		return ResponseEntity.ok(service.getEventCounts());
	}

	@GetMapping("/applications/{applicationId}/latest")
	public ResponseEntity<AuditEvent> getLatestEvent(@PathVariable String applicationId) {

		return ResponseEntity.ok(service.getLatestEvent(applicationId));
	}

	@GetMapping("/reports/dashboard")
	public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(

	        @RequestParam(required = false)
	        @DateTimeFormat(
	                iso = DateTimeFormat.ISO.DATE_TIME
	        )
	        Instant from,

	        @RequestParam(required = false)
	        @DateTimeFormat(
	                iso = DateTimeFormat.ISO.DATE_TIME
	        )
	        Instant to,

	        @RequestParam(required = false)
	        String reviewer,

	        @RequestParam(required = false)
	        String documentType) {

	    return ResponseEntity.ok(
	            service.getDashboardSummary(
	                    from,
	                    to,
	                    reviewer,
	                    documentType
	            )
	    );
	}
	@GetMapping("/reports/event-counts/range")
	public ResponseEntity<List<EventCountResponse>> getEventCountsByRange(@RequestParam Instant from,
			@RequestParam Instant to) {

		return ResponseEntity.ok(service.getEventCounts(from, to));
	}

	@GetMapping("/events")
	public ResponseEntity<List<AuditEvent>> searchEvents(

	        @RequestParam(required = false)
	        String applicationId,

	        @RequestParam(required = false)
	        EventType eventType,

	        @RequestParam(required = false)
	        String reviewer,

	        @RequestParam(required = false)
	        String documentType,

	        @RequestParam(required = false)
	        @DateTimeFormat(
	                iso = DateTimeFormat.ISO.DATE_TIME
	        )
	        Instant from,

	        @RequestParam(required = false)
	        @DateTimeFormat(
	                iso = DateTimeFormat.ISO.DATE_TIME
	        )
	        Instant to) {

	    return ResponseEntity.ok(
	            service.searchEvents(
	                    applicationId,
	                    eventType,
	                    reviewer,
	                    documentType,
	                    from,
	                    to
	            )
	    );
	}

	@GetMapping("/reports/status-counts")
	public ResponseEntity<List<StatusCountResponse>> getStatusCounts() {

		return ResponseEntity.ok(service.getStatusCounts());
	}
}
