package com.example.demo.iface.dto.res;

import com.example.demo.infra.shared.dto.DepartmentTreeNodeGottenView;

import java.util.List;

public record UserDepartmentTreeGottenResource(String code, String message, List<DepartmentTreeNodeGottenView> data) {
}
