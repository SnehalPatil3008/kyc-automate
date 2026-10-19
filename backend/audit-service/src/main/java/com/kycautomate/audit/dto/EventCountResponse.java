package com.kycautomate.audit.dto;

import com.kycautomate.audit.enums.EventType;

public record EventCountResponse(
        EventType eventType,
        long count
) {
}