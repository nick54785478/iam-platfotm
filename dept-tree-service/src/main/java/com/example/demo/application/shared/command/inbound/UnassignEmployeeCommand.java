package com.example.demo.application.shared.command.inbound;

/**
 * 將員工從部門移出 Command
 */
public record UnassignEmployeeCommand(String tenantId, String departmentId, String employeeId, String operator) {
}