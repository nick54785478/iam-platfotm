package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.application.shared.dto.PagedApiResourceRuleGottenResult;

public record ApiRulesSummaryGottenResource(String code, String message, PageQueriedResult<PagedApiResourceRuleGottenResult> data) {
}
