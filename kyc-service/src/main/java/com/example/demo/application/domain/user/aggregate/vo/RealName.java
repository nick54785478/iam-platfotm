package com.example.demo.application.domain.user.aggregate.vo;

/**
 * <b>[值物件] 真實姓名 (PII)</b>
 */
public record RealName(String firstName, String lastName, String fullName) {
}