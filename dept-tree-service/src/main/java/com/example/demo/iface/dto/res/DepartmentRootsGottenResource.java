package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.DepartmentRootGottenResult;
import com.example.demo.application.shared.dto.PageQueriedResult;

public record DepartmentRootsGottenResource(String code, String message, PageQueriedResult<DepartmentRootGottenResult> data) {
}
