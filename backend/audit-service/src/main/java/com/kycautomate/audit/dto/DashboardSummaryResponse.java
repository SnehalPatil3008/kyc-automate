package com.kycautomate.audit.dto;

import java.util.List;

import com.kycautomate.audit.entity.AuditEvent;

public record DashboardSummaryResponse(

        long totalApplications,

        long approvedCount,

        long rejectedCount,

        long processingCount,

        long manualReviewCount,

        long resubmissionRequiredCount,

        long documentFailureApplicationCount,

        List<AuditEvent> recentEvents
) {
}