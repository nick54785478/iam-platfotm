package com.example.demo.application.domain.user.aggregate.vo;

import java.time.LocalDateTime;

/**
 * <b>[值物件] 審核軌跡 (Audit Trail)</b>
 */
public record VerificationAudit(String reviewerId, LocalDateTime reviewedAt, String comments) {
}
