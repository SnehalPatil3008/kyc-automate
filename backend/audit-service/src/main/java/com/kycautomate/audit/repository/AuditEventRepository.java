package com.kycautomate.audit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.kycautomate.audit.entity.AuditEvent;
import com.kycautomate.audit.enums.EventType;

import java.time.Instant;

import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

	List<AuditEvent> findByApplicationIdOrderByEventTimestampAsc(String applicationId);

	@Query("""
			SELECT a.eventType, COUNT(a)
			FROM AuditEvent a
			GROUP BY a.eventType
			ORDER BY a.eventType
			""")
	List<Object[]> countEventsByType();

	Optional<AuditEvent> findTopByApplicationIdOrderByEventTimestampDesc(String applicationId);

	@Query("""
			SELECT a.eventType, COUNT(a)
			FROM AuditEvent a
			WHERE a.eventTimestamp BETWEEN :from AND :to
			GROUP BY a.eventType
			ORDER BY a.eventType
			""")
	List<Object[]> countEventsByTypeBetween(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
			SELECT a
			FROM AuditEvent a
			WHERE (:applicationId IS NULL
			       OR a.applicationId = :applicationId)
			  AND (:eventType IS NULL
			       OR a.eventType = :eventType)
			  AND (:actor IS NULL
			       OR a.actor = :actor)
			ORDER BY a.eventTimestamp ASC
			""")
	List<AuditEvent> searchEvents(@Param("applicationId") String applicationId, @Param("eventType") EventType eventType,
			@Param("actor") String actor);
	
	List<AuditEvent> findAllByOrderByEventTimestampAsc();
}