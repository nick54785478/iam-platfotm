package com.example.demo.application.domain.user.aggregate.vo;

import org.springframework.util.StringUtils;

/**
 * <b>[值物件] 居住地實體地址 (Proof of Address, PII)</b>
 */
public record Address(String countryCode, String stateOrProvince, String city, String postalCode, String detailLine) {
	public Address {
		if (!StringUtils.hasText(countryCode) || countryCode.length() != 2)
			throw new IllegalArgumentException("Invalid ISO country code");
		if (!StringUtils.hasText(city))
			throw new IllegalArgumentException("City cannot be empty");
	}

	public String getMaskedAddress() {
		return String.format("%s, %s, ***", countryCode, city);
	}
}