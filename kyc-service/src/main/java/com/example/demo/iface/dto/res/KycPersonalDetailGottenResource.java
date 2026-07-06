package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.KycPersonalDetailResult;

public record KycPersonalDetailGottenResource(String code, String message, KycPersonalDetailResult data) {
}
