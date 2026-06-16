package com.example.demo.application.shared.command.inbound;

/**
 * 調整部門排序 Command
 */
public record ChangeDepartmentSortOrderCommand(String tenantId, String departmentId, int sortOrder, String operator) {
}