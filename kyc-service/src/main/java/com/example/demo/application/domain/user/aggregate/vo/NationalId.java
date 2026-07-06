package com.example.demo.application.domain.user.aggregate.vo;

import org.springframework.util.StringUtils;

/**
 * <b>[值物件] 國家核發之身分證明 (高敏感 PII)</b>
 */
public record NationalId(String idNumber, String countryCode, DocumentType documentType) {
    public enum DocumentType {
        PASSPORT, NATIONAL_ID_CARD, DRIVERS_LICENSE
    }

    public NationalId {
        if (!StringUtils.hasText(idNumber)) {
            throw new IllegalArgumentException("ID Number cannot be empty");
        }
        if (!StringUtils.hasText(countryCode) || countryCode.length() != 2) {
            throw new IllegalArgumentException("Country code must be ISO 3166-1 alpha-2");
        }
    }

    public String getMaskedNumber() {
        if (idNumber.length() <= 4)
            return "****";
        return idNumber.substring(0, 3) + "****" + idNumber.substring(idNumber.length() - 3);
    }
}