package com.kycautomate.audit.entity;

import java.time.Instant;

import com.kycautomate.audit.enums.EventType;

import jakarta.persistence.*;

@Entity
@Table(name = "AUDIT_EVENTS")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EVENT_ID")
    private Long eventId;

    @Column(name = "APPLICATION_ID", nullable = false, length = 36)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "EVENT_TYPE", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "REASON", length = 2000)
    private String reason;

    @Column(name = "ACTOR", nullable = false, length = 255)
    private String actor;

    @Column(name = "ACTOR_ROLE", nullable = false, length = 30)
    private String actorRole;

    @Column(name = "EVENT_TIMESTAMP",
            nullable = false,
            columnDefinition = "TIMESTAMP(6) WITH TIME ZONE")
    private Instant eventTimestamp;

    @PrePersist
    protected void onCreate() {
        if (eventTimestamp == null) {
            eventTimestamp = Instant.now();
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }
} 	