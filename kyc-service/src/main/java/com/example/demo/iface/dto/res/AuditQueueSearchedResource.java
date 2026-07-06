package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.KycGottenResult;
import com.example.demo.application.shared.dto.PageQueriedResult;

public record AuditQueueSearchedResource(String code, String message, PageQueriedResult<KycGottenResult> data) {
}
