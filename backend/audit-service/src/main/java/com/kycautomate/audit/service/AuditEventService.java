package com.kycautomate.audit.service;

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
	public List<AuditEvent> searchEvents(String applicationId, EventType eventType, String actor) {

		return repository.searchEvents(applicationId, eventType, actor);
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