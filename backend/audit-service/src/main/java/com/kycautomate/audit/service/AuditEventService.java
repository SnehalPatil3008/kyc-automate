package com.kycautomate.audit.service;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kycautomate.audit.dto.AuditEventRequest;
import com.kycautomate.audit.entity.AuditEvent;
import com.kycautomate.audit.enums.EventType;
import com.kycautomate.audit.repository.AuditEventRepository;

import java.time.Instant;

import com.kycautomate.audit.dto.EventCountResponse;
import com.kycautomate.audit.dto.StatusCountResponse;
import com.kycautomate.audit.dto.DashboardSummaryResponse;
@Service
public class AuditEventService {

	private final AuditEventRepository repository;

	public AuditEventService(AuditEventRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public AuditEvent recordEvent(AuditEventRequest request) {

		AuditEvent event = new AuditEvent();

		event.setApplicationId(request.applicationId());
		event.setEventType(request.eventType());
		event.setReason(request.reason());
		event.setActor(request.actor());
		event.setActorRole(request.actorRole());
		event.setDocumentType(request.documentType());
		return repository.save(event);
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> getApplicationTimeline(String applicationId) {

		return repository.findByApplicationIdOrderByEventTimestampAsc(applicationId);
	}

	@Transactional(readOnly = true)
	public List<EventCountResponse> getEventCounts() {

		return repository.countEventsByType().stream()
				.map(row -> new EventCountResponse((com.kycautomate.audit.enums.EventType) row[0],
						((Number) row[1]).longValue()))
				.toList();
	}

	@Transactional(readOnly = true)
	public AuditEvent getLatestEvent(String applicationId) {
		return repository.findTopByApplicationIdOrderByEventTimestampDesc(applicationId)
				.orElseThrow(() -> new RuntimeException("No audit events found for application: " + applicationId));
	}

	@Transactional(readOnly = true)
	public List<EventCountResponse> getEventCounts(Instant from, Instant to) {

		return repository.countEventsByTypeBetween(from, to).stream()
				.map(row -> new EventCountResponse((EventType) row[0], ((Number) row[1]).longValue())).toList();
	}

	@Transactional(readOnly = true)
	public List<AuditEvent> searchEvents(
	        String applicationId,
	        EventType eventType,
	        String reviewer,
	        String documentType,
	        Instant fromTime,
	        Instant toTime) {

	    return repository.searchEvents(
	            applicationId,
	            eventType,
	            reviewer,
	            documentType,
	            fromTime,
	            toTime
	    );
	}
	
	@Transactional(readOnly = true)
	public DashboardSummaryResponse getDashboardSummary(
	        Instant from,
	        Instant to,
	        String reviewer,
	        String documentType) {

	    /*
	     * First, identify applications matching the selected filters.
	     * For example: applications containing a PAN event in a date range.
	     */
	    List<AuditEvent> matchingEvents =
	            repository.searchEvents(
	                    null,
	                    null,
	                    reviewer,
	                    documentType,
	                    from,
	                    to
	            );

	    List<String> applicationIds =
	            matchingEvents.stream()
	                    .map(AuditEvent::getApplicationId)
	                    .distinct()
	                    .toList();

	    if (applicationIds.isEmpty()) {
	        return new DashboardSummaryResponse(
	                0,
	                0,
	                0,
	                0,
	                0,
	                0,
	                0,
	                List.of()
	        );
	    }

	    /*
	     * Then read the complete history of those applications.
	     * This ensures their current status is calculated from the
	     * actual latest event, not only from the filtered event.
	     */
	    List<AuditEvent> applicationHistory =
	            repository
	                    .findByApplicationIdInOrderByEventTimestampAsc(
	                            applicationIds
	                    );

	    Map<String, AuditEvent> latestByApplication =
	            applicationHistory.stream()
	                    .collect(Collectors.toMap(
	                            AuditEvent::getApplicationId,
	                            event -> event,
	                            (oldEvent, newEvent) -> newEvent
	                    ));

	    Map<String, Long> countsByStatus =
	            latestByApplication.values()
	                    .stream()
	                    .map(this::toStatus)
	                    .collect(Collectors.groupingBy(
	                            status -> status,
	                            Collectors.counting()
	                    ));

	    long documentFailureApplicationCount =
	            applicationHistory.stream()
	                    .filter(event ->
	                            event.getEventType()
	                                    == EventType.DOCUMENT_QUALITY_FAILED
	                                    ||
	                            event.getEventType()
	                                    == EventType.DOCUMENT_REJECTED
	                    )
	                    .map(AuditEvent::getApplicationId)
	                    .distinct()
	                    .count();

	    List<AuditEvent> recentEvents =
	            matchingEvents.stream()
	                    .sorted(
	                            Comparator.comparing(
	                                    AuditEvent::getEventTimestamp
	                            ).reversed()
	                    )
	                    .limit(10)
	                    .toList();

	    return new DashboardSummaryResponse(
	            latestByApplication.size(),
	            countsByStatus.getOrDefault("APPROVED", 0L),
	            countsByStatus.getOrDefault("REJECTED", 0L),
	            countsByStatus.getOrDefault("PROCESSING", 0L),
	            countsByStatus.getOrDefault("MANUAL_REVIEW", 0L),
	            countsByStatus.getOrDefault(
	                    "RESUBMISSION_REQUESTED",
	                    0L
	            ),
	            documentFailureApplicationCount,
	            recentEvents
	    );
	}	
	@Transactional(readOnly = true)
	public List<StatusCountResponse> getStatusCounts() {

		Map<String, AuditEvent> latestByApplication = repository.findAllByOrderByEventTimestampAsc().stream().collect(
				Collectors.toMap(AuditEvent::getApplicationId, event -> event, (oldEvent, newEvent) -> newEvent));

		return latestByApplication.values().stream().map(this::toStatus)
				.collect(Collectors.groupingBy(status -> status, Collectors.counting())).entrySet().stream()
				.map(entry -> new StatusCountResponse(entry.getKey(), entry.getValue())).toList();
	}

	private String toStatus(AuditEvent event) {
		return switch (event.getEventType()) {
		case REVIEWER_APPROVED -> "APPROVED";
		case REVIEWER_REJECTED -> "REJECTED";
		case RESUBMISSION_REQUESTED -> "RESUBMISSION_REQUESTED";
		case MANUAL_REVIEW_ASSIGNED -> "MANUAL_REVIEW";
		default -> "PROCESSING";
		};
	}
}